package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentsRequest;
import com.sap.cds.sdm.model.CreateDraftEntriesRequest;
import com.sap.cds.sdm.model.MoveAttachmentsRequest;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {
  private final SDMService sdmService;
  private final DBQuery dbQuery;
  private final List<DraftService> draftService;
  private final TokenHandler tokenHandler;
  private final PersistenceService persistenceService;
  private final ExecutorService executorService;

  private static final int PARALLEL_MOVE_THREAD_POOL_SIZE = 10;
  private static final Logger logger = LoggerFactory.getLogger(SDMCustomServiceHandler.class);

  // Result class for copyAttachmentsToSDM method
  private static class CopyAttachmentsResult {
    private final List<List<String>> attachmentsMetadata;
    private final List<CmisDocument> populatedDocuments;

    public CopyAttachmentsResult(
        List<List<String>> attachmentsMetadata, List<CmisDocument> populatedDocuments) {
      this.attachmentsMetadata = attachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
    }

    public List<List<String>> getAttachmentsMetadata() {
      return attachmentsMetadata;
    }

    public List<CmisDocument> getPopulatedDocuments() {
      return populatedDocuments;
    }
  }

  public SDMCustomServiceHandler(
      SDMService sdmService,
      List<DraftService> draftService,
      TokenHandler tokenHandler,
      DBQuery dbQuery,
      PersistenceService persistenceService) {
    this.sdmService = sdmService;
    this.draftService = draftService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
    this.persistenceService = persistenceService;
    this.executorService = Executors.newFixedThreadPool(PARALLEL_MOVE_THREAD_POOL_SIZE);
  }

  // Result class for moveAttachmentsInSDM method
  private static class MoveAttachmentsResult {
    private final List<List<String>> movedAttachmentsMetadata;
    private final List<CmisDocument> populatedDocuments;
    private final List<String> failedObjectIds;
    private final Map<String, String> successfulMovesMap; // objectId -> newObjectId mapping

    public MoveAttachmentsResult(
        List<List<String>> movedAttachmentsMetadata,
        List<CmisDocument> populatedDocuments,
        List<String> failedObjectIds,
        Map<String, String> successfulMovesMap) {
      this.movedAttachmentsMetadata = movedAttachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
      this.failedObjectIds = failedObjectIds;
      this.successfulMovesMap = successfulMovesMap;
    }

    public List<List<String>> getMovedAttachmentsMetadata() {
      return movedAttachmentsMetadata;
    }

    public List<CmisDocument> getPopulatedDocuments() {
      return populatedDocuments;
    }

    public List<String> getFailedObjectIds() {
      return failedObjectIds;
    }

    public Map<String, String> getSuccessfulMovesMap() {
      return successfulMovesMap;
    }
  }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    String upID = context.getUpId();
    String folderName = upID + "__" + compositionName;
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Boolean isSystemUser = context.getSystemUser();

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    // Check if folder exists before trying to create it
    boolean folderExists =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser)
            != null;
    String folderId = ensureFolderExists(folderName, repositoryId, sdmCredentials, isSystemUser);

    List<String> objectIds = context.getObjectIds();

    CopyAttachmentsRequest request =
        CopyAttachmentsRequest.builder()
            .context(context)
            .objectIds(objectIds)
            .folderId(folderId)
            .repositoryId(repositoryId)
            .sdmCredentials(sdmCredentials)
            .isSystemUser(isSystemUser)
            .folderExists(folderExists)
            .build();

    CopyAttachmentsResult copyResult = copyAttachmentsToSDM(request);

    List<List<String>> attachmentsMetadata = copyResult.getAttachmentsMetadata();
    List<CmisDocument> populatedDocuments = copyResult.getPopulatedDocuments();

    String upIdKey = resolveUpIdKey(context, parentEntity, compositionName);

    CreateDraftEntriesRequest draftRequest =
        CreateDraftEntriesRequest.builder()
            .attachmentsMetadata(attachmentsMetadata)
            .populatedDocuments(populatedDocuments)
            .parentEntity(parentEntity)
            .compositionName(compositionName)
            .upID(upID)
            .upIdKey(upIdKey)
            .repositoryId(repositoryId)
            .folderId(folderId)
            .build();

    createDraftEntries(draftRequest);

    context.setCompleted();
  }

  @On(event = RegisterService.EVENT_MOVE_ATTACHMENT)
  public void moveAttachments(AttachmentMoveEventContext context) throws IOException {
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    String upID = context.getUpId();
    String sourceFolderId = context.getSourceFolderId();
    String targetFolderName = upID + "__" + compositionName;
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Boolean isSystemUser = context.getSystemUser();
    List<String> objectIds = context.getObjectIds();

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();

    // Check if target folder exists before trying to create it
    boolean targetFolderExists;
    String targetFolderId;
    try {
      targetFolderExists =
          sdmService.getFolderIdByPath(targetFolderName, repositoryId, sdmCredentials, isSystemUser)
              != null;
      targetFolderId =
          ensureFolderExists(targetFolderName, repositoryId, sdmCredentials, isSystemUser);
    } catch (IOException e) {
      // Folder creation/verification failed - mark all objectIds as failed
      logger.error(
          "Failed to create/verify target folder '{}': {}. Marking all {} attachments as failed.",
          targetFolderName,
          e.getMessage(),
          objectIds.size());
      context.setFailedObjectIds(objectIds);
      context.setCompleted();
      return;
    }

    MoveAttachmentsRequest request =
        MoveAttachmentsRequest.builder()
            .context(context)
            .sourceFolderId(sourceFolderId)
            .objectIds(objectIds)
            .targetFolderId(targetFolderId)
            .repositoryId(repositoryId)
            .sdmCredentials(sdmCredentials)
            .isSystemUser(isSystemUser)
            .targetFolderExists(targetFolderExists)
            .build();

    MoveAttachmentsResult moveResult = moveAttachmentsInSDM(request);

    List<List<String>> movedAttachmentsMetadata = moveResult.getMovedAttachmentsMetadata();
    List<CmisDocument> populatedDocuments = moveResult.getPopulatedDocuments();
    List<String> failedObjectIds = moveResult.getFailedObjectIds();
    Map<String, String> successfulMovesMap = moveResult.getSuccessfulMovesMap();

    // Set the failed object IDs in the context for the caller
    // Convert to ArrayList to avoid Collections$SynchronizedRandomAccessList serialization issue
    context.setFailedObjectIds(new ArrayList<>(failedObjectIds));

    // Only create draft entries for successfully moved attachments
    if (!movedAttachmentsMetadata.isEmpty()) {
      String upIdKey = resolveUpIdKey(context, parentEntity, compositionName);

      CreateDraftEntriesRequest draftRequest =
          CreateDraftEntriesRequest.builder()
              .attachmentsMetadata(movedAttachmentsMetadata)
              .populatedDocuments(populatedDocuments)
              .parentEntity(parentEntity)
              .compositionName(compositionName)
              .upID(upID)
              .upIdKey(upIdKey)
              .repositoryId(repositoryId)
              .folderId(targetFolderId)
              .build();

      try {
        createDraftEntries(draftRequest);
      } catch (ServiceException e) {
        // If DB update fails after retries, rollback the SDM moves
        logger.error(
            "Failed to update DB for moved attachments after retries. Rolling back SDM moves: {}",
            e.getMessage());
        rollbackMovedAttachments(
            successfulMovesMap,
            sourceFolderId,
            targetFolderId,
            repositoryId,
            sdmCredentials,
            isSystemUser,
            context);
        // Add all successfully moved objects to failed list since we rolled back
        failedObjectIds.addAll(successfulMovesMap.keySet());
        // Convert to ArrayList to avoid Collections$SynchronizedRandomAccessList serialization
        // issue
        context.setFailedObjectIds(new ArrayList<>(failedObjectIds));
        // Don't throw exception - return failed IDs to caller as per acceptance criteria
        logger.warn(
            "Move operation completed with failures. Total failed: {}, Rolled back: {}",
            failedObjectIds.size(),
            successfulMovesMap.size());
      }
    }

    context.setCompleted();
  }

  private String ensureFolderExists(
      String folderName, String repositoryId, SDMCredentials sdmCredentials, Boolean isSystemUser)
      throws IOException {
    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
    if (folderId == null) {
      folderId =
          sdmService.createFolder(
              folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
    }
    return folderId;
  }

  private CopyAttachmentsResult copyAttachmentsToSDM(CopyAttachmentsRequest request)
      throws IOException {
    List<List<String>> attachmentsMetadata = new ArrayList<>();
    List<CmisDocument> populatedDocuments = new ArrayList<>();

    for (String objectId : request.getObjectIds()) {
      CmisDocument cmisDocument =
          dbQuery.getAttachmentForObjectID(persistenceService, objectId, request.getContext());
      cmisDocument.setObjectId(objectId);
      cmisDocument.setRepositoryId(request.getRepositoryId());
      cmisDocument.setFolderId(request.getFolderId());

      // Create individual document for each attachment with its own type and linkUrl
      CmisDocument populatedDocument = new CmisDocument();
      populatedDocument.setType(cmisDocument.getType());
      populatedDocument.setUrl(cmisDocument.getUrl());
      populatedDocuments.add(populatedDocument);

      try {
        attachmentsMetadata.add(
            sdmService.copyAttachment(
                cmisDocument, request.getSdmCredentials(), request.getIsSystemUser()));
      } catch (ServiceException e) {
        handleCopyFailure(
            request.getContext(),
            request.getFolderId(),
            request.isFolderExists(),
            attachmentsMetadata,
            e);
      }
    }

    return new CopyAttachmentsResult(attachmentsMetadata, populatedDocuments);
  }

  private MoveAttachmentsResult moveAttachmentsInSDM(MoveAttachmentsRequest request)
      throws IOException {
    List<List<String>> movedAttachmentsMetadata =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<CmisDocument> populatedDocuments =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<String> failedObjectIds = java.util.Collections.synchronizedList(new ArrayList<>());
    Map<String, String> successfulMovesMap =
        new java.util.concurrent.ConcurrentHashMap<>(); // objectId -> newObjectId

    // Capture the current request context to propagate to background threads
    com.sap.cds.services.runtime.RequestContextRunner contextRunner =
        request.getContext().getCdsRuntime().requestContext();

    // Create parallel tasks for each attachment move
    List<CompletableFuture<Void>> moveFutures =
        request.getObjectIds().stream()
            .map(
                objectId ->
                    CompletableFuture.runAsync(
                        () ->
                            // Run within the captured request context to preserve authentication
                            contextRunner.run(
                                ctx -> {
                                  try {
                                    CmisDocument cmisDocument =
                                        dbQuery.getAttachmentForObjectID(
                                            persistenceService, objectId, request.getContext());
                                    cmisDocument.setObjectId(objectId);
                                    cmisDocument.setRepositoryId(request.getRepositoryId());
                                    cmisDocument.setSourceFolderId(request.getSourceFolderId());
                                    cmisDocument.setFolderId(request.getTargetFolderId());

                                    // Create individual document for each attachment with its own
                                    // type and linkUrl
                                    CmisDocument populatedDocument = new CmisDocument();
                                    populatedDocument.setType(cmisDocument.getType());
                                    populatedDocument.setUrl(cmisDocument.getUrl());

                                    // Perform SDM move operation with retry
                                    List<String> metadata =
                                        sdmService.moveAttachment(
                                            cmisDocument,
                                            request.getSdmCredentials(),
                                            request.getIsSystemUser());

                                    // Track successful move for rollback (thread-safe)
                                    String newObjectId = metadata.get(2);
                                    successfulMovesMap.put(objectId, newObjectId);
                                    movedAttachmentsMetadata.add(metadata);
                                    populatedDocuments.add(populatedDocument);
                                  } catch (ServiceException | IOException e) {
                                    // Add to failed list and continue with next attachment
                                    // (thread-safe)
                                    logger.error(
                                        "Failed to move attachment with objectId {}: {}",
                                        objectId,
                                        e.getMessage());
                                    failedObjectIds.add(objectId);
                                  }
                                  return null;
                                }),
                        executorService))
            .toList();

    // Wait for all move operations to complete
    try {
      CompletableFuture.allOf(moveFutures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      throw new IOException("Error during parallel move operations", e);
    }

    return new MoveAttachmentsResult(
        movedAttachmentsMetadata, populatedDocuments, failedObjectIds, successfulMovesMap);
  }

  private void handleCopyFailure(
      AttachmentCopyEventContext context,
      String folderId,
      boolean folderExists,
      List<List<String>> attachmentsMetadata,
      ServiceException e)
      throws IOException {
    if (!folderExists) {
      sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
    } else {
      for (List<String> attachmentMetadata : attachmentsMetadata) {
        sdmService.deleteDocument(
            "delete", attachmentMetadata.get(2), context.getUserInfo().getName());
      }
    }
    throw new ServiceException(e.getMessage());
  }

  private String resolveUpIdKey(EventContext context, String parentEntity, String compositionName) {
    CdsModel model = context.getModel();
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException("Unable to find parent entity: " + parentEntity);
    }

    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          "Unable to find composition '" + compositionName + "' in entity: " + parentEntity);
    }

    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
      if (upAssociation.isPresent()) {
        CdsElement association = upAssociation.get();
        CdsAssociationType upAssocType = association.getType();
        List<String> fkElements = upAssocType.refs().map(ref -> "up__" + ref.path()).toList();
        String upIdKey = fkElements.get(0);
        return upIdKey;
      }
    }
    return null;
  }

  private void createDraftEntries(CreateDraftEntriesRequest request) {

    for (int i = 0; i < request.getAttachmentsMetadata().size(); i++) {
      List<String> attachmentMetadata = request.getAttachmentsMetadata().get(i);
      CmisDocument cmisDocument = request.getPopulatedDocuments().get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get(0);
      String mimeType = attachmentMetadata.get(1);
      String newObjectId = attachmentMetadata.get(2);

      updatedFields.put("objectId", newObjectId);
      updatedFields.put("repositoryId", request.getRepositoryId());
      updatedFields.put("folderId", request.getFolderId());
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("type", cmisDocument.getType()); // Individual type for each attachment
      updatedFields.put("fileName", fileName);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      updatedFields.put("linkUrl", cmisDocument.getUrl()); // Individual linkUrl for each attachment
      updatedFields.put(
          "contentId",
          newObjectId
              + ":"
              + request.getFolderId()
              + ":"
              + request.getParentEntity()
              + "."
              + request.getCompositionName()
              + ":"
              + mimeType);
      updatedFields.put(request.getUpIdKey(), request.getUpID());

      String baseKeyField =
          request.getUpIdKey() != null ? request.getUpIdKey().replace("up__", "") : "ID";
      var insert =
          Insert.into(
                  request.getParentEntity(),
                  e ->
                      e.filter(e.get(baseKeyField).eq(request.getUpID()))
                          .to(request.getCompositionName()))
              .entry(updatedFields);

      DraftService matchingService =
          draftService.stream()
              .filter(ds -> request.getParentEntity().contains(ds.getName()))
              .findFirst()
              .orElse(null);

      if (matchingService != null) {
        // Wrap DB insert with retry logic to handle transient DB failures
        try {
          io.reactivex.Flowable.fromCallable(
                  () -> {
                    matchingService.newDraft(insert);
                    return true;
                  })
              .retryWhen(com.sap.cds.sdm.service.RetryUtils.retryLogic(5)) // Retry up to 5 times
              .blockingFirst();
        } catch (Exception e) {
          throw new ServiceException(
              "Failed to insert attachment entry in DB after retries: " + e.getMessage(), e);
        }
      } else {
        throw new ServiceException(
            "No suitable service found for entity: " + request.getParentEntity());
      }
    }
  }

  /**
   * Rollback moved attachments by moving them back to the source folder. This is called when DB
   * update fails after SDM move succeeds.
   */
  private void rollbackMovedAttachments(
      Map<String, String> successfulMovesMap,
      String sourceFolderId,
      String targetFolderId,
      String repositoryId,
      SDMCredentials sdmCredentials,
      boolean isSystemUser,
      AttachmentMoveEventContext context) {
    logger.warn(
        "Rolling back {} moved attachments from {} to {}",
        successfulMovesMap.size(),
        targetFolderId,
        sourceFolderId);

    // Capture the current request context to propagate to background threads
    com.sap.cds.services.runtime.RequestContextRunner contextRunner =
        context.getCdsRuntime().requestContext();

    List<CompletableFuture<Void>> rollbackFutures =
        successfulMovesMap.entrySet().stream()
            .map(
                entry ->
                    CompletableFuture.runAsync(
                        () ->
                            contextRunner.run(
                                ctx -> {
                                  String originalObjectId = entry.getKey();
                                  String newObjectId = entry.getValue();
                                  try {
                                    // Create CMIS document for rollback move
                                    CmisDocument rollbackDoc = new CmisDocument();
                                    rollbackDoc.setObjectId(newObjectId);
                                    rollbackDoc.setRepositoryId(repositoryId);
                                    rollbackDoc.setSourceFolderId(targetFolderId);
                                    rollbackDoc.setFolderId(sourceFolderId);

                                    // Move back to source folder with retry
                                    sdmService.moveAttachment(
                                        rollbackDoc, sdmCredentials, isSystemUser);
                                    logger.info(
                                        "Successfully rolled back attachment {} to source folder",
                                        originalObjectId);
                                  } catch (Exception e) {
                                    logger.error(
                                        "Failed to rollback attachment {} during rollback: {}",
                                        originalObjectId,
                                        e.getMessage());
                                    // Continue with other rollbacks even if one fails
                                  }
                                  return null;
                                }),
                        executorService))
            .toList();

    // Wait for all rollback operations to complete
    try {
      CompletableFuture.allOf(rollbackFutures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      logger.error("Error during rollback operations: {}", e.getMessage());
    }
  }
}
