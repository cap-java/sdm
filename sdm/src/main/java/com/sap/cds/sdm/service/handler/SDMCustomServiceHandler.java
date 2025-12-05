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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
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
  private static final String OBJECT_ID_KEY = "objectId";
  private static final String FAILURE_REASON_KEY = "failureReason";

  // Result class for copyAttachmentsToSDM method
  private static class CopyAttachmentsResult {
    private final List<Map<String, String>> attachmentsMetadata;
    private final List<CmisDocument> populatedDocuments;

    public CopyAttachmentsResult(
        List<Map<String, String>> attachmentsMetadata, List<CmisDocument> populatedDocuments) {
      this.attachmentsMetadata = attachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
    }

    public List<Map<String, String>> getAttachmentsMetadata() {
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

  /**
   * Encapsulates the result of a batch move operation in SDM. Contains metadata for successfully
   * moved attachments and tracks failures.
   */
  private static class MoveAttachmentsResult {
    private final List<List<String>> movedAttachmentsMetadata;
    private final List<CmisDocument> populatedDocuments;
    private final List<Map<String, String>> failedAttachments;
    private final List<String> successfulObjectIds;

    public MoveAttachmentsResult(
        List<List<String>> movedAttachmentsMetadata,
        List<CmisDocument> populatedDocuments,
        List<Map<String, String>> failedAttachments,
        List<String> successfulObjectIds) {
      this.movedAttachmentsMetadata = movedAttachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
      this.failedAttachments = failedAttachments;
      this.successfulObjectIds = successfulObjectIds;
    }

    public List<List<String>> getMovedAttachmentsMetadata() {
      return movedAttachmentsMetadata;
    }

    public List<CmisDocument> getPopulatedDocuments() {
      return populatedDocuments;
    }

    public List<Map<String, String>> getFailedAttachments() {
      return failedAttachments;
    }

    public List<String> getSuccessfulObjectIds() {
      return successfulObjectIds;
    }
  }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    Optional<CdsEntity> entity =
        context.getModel().findEntity(parentEntity + "." + compositionName);

    Map<String, String> customPropertyDefinitions = new HashMap<>();
    if (entity.isPresent()) {
      CdsEntity cdsEntity = entity.get();

      // Get columns with @SDM.Attachments.AdditionalProperty annotation and their name values
      // Filter out associations - only include actual database columns
      customPropertyDefinitions =
          cdsEntity
              .elements()
              .filter(
                  element ->
                      element
                              .findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME)
                              .isPresent()
                          && !element.getType().isAssociation())
              .collect(
                  Collectors.toMap(
                      CdsElement::getName,
                      element ->
                          element
                              .findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME)
                              .get()
                              .getValue()
                              .toString()));
    }

    Set<String> customPropertiesInSDM = new HashSet<>(customPropertyDefinitions.values());
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

    CopyAttachmentsResult copyResult = copyAttachmentsToSDM(request, customPropertiesInSDM);

    List<Map<String, String>> attachmentsMetadata = copyResult.getAttachmentsMetadata();
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
            .customPropertyValues(null)
            .build();

    createDraftEntries(draftRequest, customPropertyDefinitions);

    context.setCompleted();
  }

  /**
   * Moves attachments from source entity to target entity in SDM. Executes moves in parallel,
   * updates database records, and cleans up source metadata. If any step fails, the operation is
   * rolled back to maintain consistency.
   *
   * @param context the move event context containing source and target information
   * @throws IOException if there's an error during the move operation
   */
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

    // Ensure target folder exists in SDM before attempting moves
    TargetFolderInfo folderInfo =
        ensureTargetFolderReady(
            targetFolderName, repositoryId, sdmCredentials, isSystemUser, objectIds, context);
    String targetFolderId = folderInfo.targetFolderId;
    boolean targetFolderExists = folderInfo.targetFolderExists;

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
    List<Map<String, String>> failedAttachments = moveResult.getFailedAttachments();
    List<String> successfulObjectIds = moveResult.getSuccessfulObjectIds();

    // Return failed attachments to caller
    context.setFailedAttachments(new ArrayList<>(failedAttachments));

    // Show warning if there are failures
    if (!failedAttachments.isEmpty()) {
      StringBuilder warningMessage =
          new StringBuilder("Failed to move the following attachments:\n");
      for (Map<String, String> failure : failedAttachments) {
        warningMessage
            .append("  - ObjectId: ")
            .append(failure.get(OBJECT_ID_KEY))
            .append(", Reason: ")
            .append(failure.get(FAILURE_REASON_KEY))
            .append("\n");
      }
      context.getMessages().warn(warningMessage.toString());
    }

    // Process successfully moved attachments
    if (!movedAttachmentsMetadata.isEmpty()) {
      String upIdKey = resolveUpIdKey(context, parentEntity, compositionName);

      try {
        updateDatabaseAndCleanupSource(
            movedAttachmentsMetadata,
            populatedDocuments,
            parentEntity,
            compositionName,
            upID,
            upIdKey,
            repositoryId,
            targetFolderId,
            successfulObjectIds,
            context);
      } catch (ServiceException e) {
        DatabaseFailureContext failureContext =
            new DatabaseFailureContext(
                successfulObjectIds,
                sourceFolderId,
                targetFolderId,
                repositoryId,
                sdmCredentials,
                isSystemUser,
                context,
                failedAttachments);
        handleDatabaseUpdateFailure(e, failureContext);
      }
    }

    context.setCompleted();
  }

  /**
   * Updates database with moved attachments and cleans up source entity metadata.
   *
   * @throws ServiceException if database operations fail
   */
  private void updateDatabaseAndCleanupSource(
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      String parentEntity,
      String compositionName,
      String upID,
      String upIdKey,
      String repositoryId,
      String folderId,
      List<String> successfulObjectIds,
      AttachmentMoveEventContext context)
      throws ServiceException {
    // Query source entity's up__ID before creating target records
    // This ensures we get the correct source ID, especially important when moving
    // between entities of the same type (e.g., Book A to Book B)
    String sourceUpId = null;
    if (!successfulObjectIds.isEmpty()) {
      sourceUpId =
          dbQuery.getSourceUpIdForObjectIds(persistenceService, successfulObjectIds, context);
      logger.info("Retrieved source up__ID for cleanup: {}", sourceUpId);
    }

    // Create draft entries for moved attachments with secondary properties
    createDraftEntriesForMove(
        movedAttachmentsMetadata,
        populatedDocuments,
        parentEntity,
        compositionName,
        upID,
        upIdKey,
        repositoryId,
        folderId);

    // Clean up source entity metadata after successful move
    if (!successfulObjectIds.isEmpty() && sourceUpId != null) {
      try {
        int deletedCount =
            dbQuery.deleteAttachmentsByObjectIds(
                persistenceService, successfulObjectIds, sourceUpId, context);
        logger.info(
            "Cleaned up {} attachment metadata records from source entity for {} successfully"
                + " moved attachments",
            deletedCount,
            successfulObjectIds.size());
      } catch (Exception cleanupException) {
        logger.warn(
            "Failed to clean up source entity metadata for {} attachments: {}. Attachments were"
                + " successfully moved to target.",
            successfulObjectIds.size(),
            cleanupException.getMessage());
      }
    }
  }

  /** Helper class to hold database failure context information. */
  private static class DatabaseFailureContext {
    final List<String> successfulObjectIds;
    final String sourceFolderId;
    final String targetFolderId;
    final String repositoryId;
    final SDMCredentials sdmCredentials;
    final Boolean isSystemUser;
    final AttachmentMoveEventContext context;
    final List<Map<String, String>> failedAttachments;

    DatabaseFailureContext(
        List<String> successfulObjectIds,
        String sourceFolderId,
        String targetFolderId,
        String repositoryId,
        SDMCredentials sdmCredentials,
        Boolean isSystemUser,
        AttachmentMoveEventContext context,
        List<Map<String, String>> failedAttachments) {
      this.successfulObjectIds = successfulObjectIds;
      this.sourceFolderId = sourceFolderId;
      this.targetFolderId = targetFolderId;
      this.repositoryId = repositoryId;
      this.sdmCredentials = sdmCredentials;
      this.isSystemUser = isSystemUser;
      this.context = context;
      this.failedAttachments = failedAttachments;
    }
  }

  /**
   * Handles database update failure by rolling back SDM moves and marking attachments as failed.
   */
  private void handleDatabaseUpdateFailure(
      ServiceException e, DatabaseFailureContext failureContext) {
    // Database update failed - rollback all SDM moves to maintain consistency
    logger.error(
        "Failed to update DB for moved attachments after retries. Rolling back SDM moves: {}",
        e.getMessage());
    rollbackMovedAttachments(
        failureContext.successfulObjectIds,
        failureContext.sourceFolderId,
        failureContext.targetFolderId,
        failureContext.repositoryId,
        failureContext.sdmCredentials,
        failureContext.isSystemUser,
        failureContext.context);
    // Mark rolled-back attachments as failed
    for (String objectId : failureContext.successfulObjectIds) {
      Map<String, String> failureMap = new HashMap<>();
      failureMap.put(OBJECT_ID_KEY, objectId);
      failureMap.put(
          FAILURE_REASON_KEY, "Database update failed, move rolled back: " + e.getMessage());
      failureContext.failedAttachments.add(failureMap);
    }
    failureContext.context.setFailedAttachments(new ArrayList<>(failureContext.failedAttachments));

    // Add warning message
    StringBuilder warningMessage =
        new StringBuilder(
            "Move operation failed during database update. Rolled back attachments:\n");
    for (String objectId : failureContext.successfulObjectIds) {
      warningMessage.append("  - ObjectId: ").append(objectId).append("\n");
    }
    failureContext.context.getMessages().warn(warningMessage.toString());

    logger.warn(
        "Move operation completed with failures. Total failed: {}, Rolled back: {}",
        failureContext.failedAttachments.size(),
        failureContext.successfulObjectIds.size());
  }

  /**
   * Ensures target folder exists and returns the folder state information.
   *
   * @return array containing [targetFolderId, targetFolderExists]
   * @throws IOException if folder creation/verification fails
   */
  private TargetFolderInfo ensureTargetFolderReady(
      String targetFolderName,
      String repositoryId,
      SDMCredentials sdmCredentials,
      Boolean isSystemUser,
      List<String> objectIds,
      AttachmentMoveEventContext context)
      throws IOException {
    try {
      boolean targetFolderExists =
          sdmService.getFolderIdByPath(targetFolderName, repositoryId, sdmCredentials, isSystemUser)
              != null;
      String targetFolderId =
          ensureFolderExists(targetFolderName, repositoryId, sdmCredentials, isSystemUser);
      return new TargetFolderInfo(targetFolderId, targetFolderExists);
    } catch (IOException e) {
      logger.error(
          "Failed to create/verify target folder '{}': {}. Marking all {} attachments as failed.",
          targetFolderName,
          e.getMessage(),
          objectIds.size());
      // Create failed attachments list with error reason
      List<Map<String, String>> failedAttachments = new ArrayList<>();
      for (String objectId : objectIds) {
        Map<String, String> failureMap = new HashMap<>();
        failureMap.put(OBJECT_ID_KEY, objectId);
        failureMap.put(FAILURE_REASON_KEY, "Failed to create target folder: " + e.getMessage());
        failedAttachments.add(failureMap);
      }
      context.setFailedAttachments(failedAttachments);
      context.setCompleted();
      throw e;
    }
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

  /** Helper class to hold target folder information. */
  private static class TargetFolderInfo {
    final String targetFolderId;
    final boolean targetFolderExists;

    TargetFolderInfo(String targetFolderId, boolean targetFolderExists) {
      this.targetFolderId = targetFolderId;
      this.targetFolderExists = targetFolderExists;
    }
  }

  private CopyAttachmentsResult copyAttachmentsToSDM(
      CopyAttachmentsRequest request, Set<String> customPropertiesInSDM) throws IOException {
    List<Map<String, String>> attachmentsMetadata = new ArrayList<>();
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
        Map<String, String> attachmentData =
            sdmService.copyAttachment(
                cmisDocument,
                request.getSdmCredentials(),
                request.getIsSystemUser(),
                customPropertiesInSDM);

        attachmentsMetadata.add(attachmentData);
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

  /** Helper class to hold SDM validation data. */
  private static class SDMValidationData {
    final List<String> validSecondaryProperties;
    final Map<String, String> entityAnnotations;

    SDMValidationData(
        List<String> validSecondaryProperties, Map<String, String> entityAnnotations) {
      this.validSecondaryProperties = validSecondaryProperties;
      this.entityAnnotations = entityAnnotations;
    }
  }

  /**
   * Fetches SDM validation data including secondary types and properties.
   *
   * @throws IOException if SDM operations fail
   */
  private SDMValidationData fetchSDMValidationData(MoveAttachmentsRequest request)
      throws IOException {
    // Fetch secondary types from SDM repository (similar to updateAttachments)
    List<String> secondaryTypes;
    try {
      secondaryTypes =
          sdmService.getSecondaryTypes(
              request.getRepositoryId(), request.getSdmCredentials(), request.getIsSystemUser());
      logger.info(
          "Fetched {} secondary types from SDM repository: {}",
          secondaryTypes.size(),
          secondaryTypes);
    } catch (Exception e) {
      logger.error("Failed to fetch secondary types from SDM: {}", e.getMessage(), e);
      throw new IOException("Failed to fetch secondary types from SDM", e);
    }

    // Fetch valid secondary properties from SDM (similar to updateAttachments)
    List<String> validSecondaryProperties;
    try {
      validSecondaryProperties =
          sdmService.getValidSecondaryProperties(
              secondaryTypes,
              request.getSdmCredentials(),
              request.getRepositoryId(),
              request.getIsSystemUser());
      logger.info(
          "Fetched {} valid secondary properties from SDM: {}",
          validSecondaryProperties.size(),
          validSecondaryProperties);
    } catch (Exception e) {
      logger.error("Failed to fetch valid secondary properties from SDM: {}", e.getMessage(), e);
      throw new IOException("Failed to fetch valid secondary properties from SDM", e);
    }

    // Get entity annotations for filtering properties during DB insertion
    Map<String, String> entityAnnotations =
        dbQuery.getValidSecondaryPropertiesForMove(request.getContext());
    logger.info(
        "Target entity has {} annotated secondary properties for DB mapping: {}",
        entityAnnotations.size(),
        entityAnnotations);

    return new SDMValidationData(validSecondaryProperties, entityAnnotations);
  }

  /**
   * Executes attachment moves in parallel using a thread pool. Tracks successful and failed moves
   * separately for further processing.
   *
   * @param request the move request containing attachments to move
   * @return result containing moved metadata and success/failure tracking
   * @throws IOException if parallel execution fails
   */
  private MoveAttachmentsResult moveAttachmentsInSDM(MoveAttachmentsRequest request)
      throws IOException {
    List<List<String>> movedAttachmentsMetadata =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<CmisDocument> populatedDocuments =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<Map<String, String>> failedAttachments =
        java.util.Collections.synchronizedList(new ArrayList<>());
    List<String> successfulObjectIds = java.util.Collections.synchronizedList(new ArrayList<>());

    // Fetch SDM validation data
    SDMValidationData validationData = fetchSDMValidationData(request);
    List<String> validSecondaryProperties = validationData.validSecondaryProperties;
    Map<String, String> entityAnnotations = validationData.entityAnnotations;

    // Preserve request context for authentication in parallel threads
    com.sap.cds.services.runtime.RequestContextRunner contextRunner =
        request.getContext().getCdsRuntime().requestContext();

    // Create results wrapper for successful processing
    AttachmentProcessingResults processingResults =
        new AttachmentProcessingResults(
            successfulObjectIds, movedAttachmentsMetadata, populatedDocuments);

    // Process each attachment in parallel: Move → Validate → Process/Rollback immediately
    List<CompletableFuture<Void>> processFutures =
        request.getObjectIds().stream()
            .map(
                objectId ->
                    CompletableFuture.runAsync(
                        () ->
                            contextRunner.run(
                                ctx -> {
                                  AttachmentMoveContext moveContext =
                                      new AttachmentMoveContext(
                                          objectId,
                                          request,
                                          validSecondaryProperties,
                                          entityAnnotations,
                                          processingResults,
                                          failedAttachments);
                                  processSingleAttachmentMove(moveContext);
                                  return null;
                                }),
                        executorService))
            .toList();

    // Wait for all operations to complete
    try {
      CompletableFuture.allOf(processFutures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      throw new IOException("Error during parallel move and validation operations", e);
    }

    logger.info(
        "Move operation completed - Successful: {}, Failed: {}",
        successfulObjectIds.size(),
        failedAttachments.size());

    return new MoveAttachmentsResult(
        movedAttachmentsMetadata, populatedDocuments, failedAttachments, successfulObjectIds);
  }

  /**
   * Handles validation failure by rolling back the attachment and recording the failure.
   *
   * @param objectId the attachment object ID
   * @param invalidProperties list of invalid properties found
   * @param request the move request containing credentials and folder info
   * @param failedAttachments list to add the failure record to
   */
  private void handleValidationFailure(
      String objectId,
      List<String> invalidProperties,
      MoveAttachmentsRequest request,
      List<Map<String, String>> failedAttachments) {
    logger.error(
        "Attachment {} validation FAILED - Found {} invalid properties: {}. Rolling back...",
        objectId,
        invalidProperties.size(),
        invalidProperties);
    try {
      rollbackSingleAttachment(
          objectId,
          request.getSourceFolderId(),
          request.getTargetFolderId(),
          request.getRepositoryId(),
          request.getSdmCredentials(),
          request.getIsSystemUser());
      logger.info("Successfully rolled back attachment {} to source folder", objectId);
    } catch (Exception rollbackEx) {
      logger.error("Failed to rollback attachment {}: {}", objectId, rollbackEx.getMessage());
    }

    Map<String, String> failure = new HashMap<>();
    failure.put(OBJECT_ID_KEY, objectId);
    failure.put(
        FAILURE_REASON_KEY,
        "Target entity properties "
            + invalidProperties
            + " found in SDM response but not in valid secondary properties list. Attachment"
            + " rolled back.");
    failedAttachments.add(failure);
  }

  /** Helper class to hold attachment processing context for parallel execution. */
  private static class AttachmentMoveContext {
    final String objectId;
    final MoveAttachmentsRequest request;
    final List<String> validSecondaryProperties;
    final Map<String, String> entityAnnotations;
    final AttachmentProcessingResults processingResults;
    final List<Map<String, String>> failedAttachments;

    AttachmentMoveContext(
        String objectId,
        MoveAttachmentsRequest request,
        List<String> validSecondaryProperties,
        Map<String, String> entityAnnotations,
        AttachmentProcessingResults processingResults,
        List<Map<String, String>> failedAttachments) {
      this.objectId = objectId;
      this.request = request;
      this.validSecondaryProperties = validSecondaryProperties;
      this.entityAnnotations = entityAnnotations;
      this.processingResults = processingResults;
      this.failedAttachments = failedAttachments;
    }
  }

  /**
   * Processes a single attachment move: Move in SDM → Validate → Process or Rollback.
   *
   * @param moveContext the context containing all necessary information for processing
   */
  private void processSingleAttachmentMove(AttachmentMoveContext moveContext) {
    try {
      // Step 1: Move attachment in SDM
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setObjectId(moveContext.objectId);
      cmisDocument.setRepositoryId(moveContext.request.getRepositoryId());
      cmisDocument.setSourceFolderId(moveContext.request.getSourceFolderId());
      cmisDocument.setFolderId(moveContext.request.getTargetFolderId());

      String sdmResponseJson =
          sdmService.moveAttachment(
              cmisDocument,
              moveContext.request.getSdmCredentials(),
              moveContext.request.getIsSystemUser());
      org.json.JSONObject sdmResponse = new org.json.JSONObject(sdmResponseJson);
      org.json.JSONObject succinctProperties = sdmResponse.getJSONObject("succinctProperties");

      String fileName = succinctProperties.optString("cmis:name");
      String mimeType = succinctProperties.optString("cmis:contentStreamMimeType");
      String description = succinctProperties.optString("cmis:description");
      String movedObjectId = succinctProperties.optString("cmis:objectId");

      logger.debug(
          "Successfully moved attachment {} to target folder. FileName: {}, MimeType: {}",
          moveContext.objectId,
          fileName,
          mimeType);

      // Step 2: Validate target entity properties in SDM response
      // Get target entity's SDM property names from annotations
      Set<String> targetEntitySdmProperties = new HashSet<>(moveContext.entityAnnotations.values());
      Set<String> sdmResponseProperties = new HashSet<>(succinctProperties.keySet());

      logger.debug(
          "Validating attachment {} - Target entity expects {} SDM properties: {}",
          moveContext.objectId,
          targetEntitySdmProperties.size(),
          targetEntitySdmProperties);
      logger.debug(
          "SDM response has {} properties: {}",
          sdmResponseProperties.size(),
          sdmResponseProperties);

      // Validate: Check target entity properties that exist in SDM response
      List<String> invalidProperties = new ArrayList<>();
      for (String targetSdmProperty : targetEntitySdmProperties) {
        if (sdmResponseProperties.contains(targetSdmProperty)
            && !moveContext.validSecondaryProperties.contains(targetSdmProperty)) {
          // Property defined in target entity, present in SDM response, but NOT in valid list →
          // INVALID
          invalidProperties.add(targetSdmProperty);
          logger.warn(
              "Attachment {} - Property '{}' is defined in target entity and in SDM response but"
                  + " NOT in valid secondary properties list",
              moveContext.objectId,
              targetSdmProperty);
        }
      }

      if (!invalidProperties.isEmpty()) {
        // Step 3a: Validation failed → Rollback immediately
        handleValidationFailure(
            moveContext.objectId,
            invalidProperties,
            moveContext.request,
            moveContext.failedAttachments);

      } else {
        // Step 3b: Validation passed → Process for DB insertion
        processValidatedAttachment(
            moveContext.objectId,
            fileName,
            mimeType,
            description,
            movedObjectId,
            succinctProperties,
            moveContext.entityAnnotations,
            moveContext.processingResults);
      }

    } catch (ServiceException | IOException e) {
      // Move operation failed
      logger.error("Failed to move attachment {}: {}", moveContext.objectId, e.getMessage());
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.objectId);
      failure.put(FAILURE_REASON_KEY, "SDM move failed: " + e.getMessage());
      moveContext.failedAttachments.add(failure);
    } catch (Exception e) {
      // Validation/processing failed
      logger.error(
          "Failed to validate/process attachment {}: {}", moveContext.objectId, e.getMessage(), e);
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.objectId);
      failure.put(FAILURE_REASON_KEY, "Validation/processing failed: " + e.getMessage());
      moveContext.failedAttachments.add(failure);
    }
  }

  /** Helper class to hold results for processed attachments. */
  private static class AttachmentProcessingResults {
    final List<String> successfulObjectIds;
    final List<List<String>> movedAttachmentsMetadata;
    final List<CmisDocument> populatedDocuments;

    AttachmentProcessingResults(
        List<String> successfulObjectIds,
        List<List<String>> movedAttachmentsMetadata,
        List<CmisDocument> populatedDocuments) {
      this.successfulObjectIds = successfulObjectIds;
      this.movedAttachmentsMetadata = movedAttachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
    }
  }

  /**
   * Processes a successfully validated attachment for DB insertion.
   *
   * @param objectId the attachment object ID
   * @param fileName the file name
   * @param mimeType the MIME type
   * @param description the description (note) from cmis:description
   * @param movedObjectId the new object ID after move
   * @param succinctProperties SDM response properties
   * @param entityAnnotations mapping of DB fields to SDM properties
   * @param results holder for successful processing results
   */
  private void processValidatedAttachment(
      String objectId,
      String fileName,
      String mimeType,
      String description,
      String movedObjectId,
      org.json.JSONObject succinctProperties,
      Map<String, String> entityAnnotations,
      AttachmentProcessingResults results) {
    logger.info("Attachment {} validation PASSED - Processing for DB insertion", objectId);

    CmisDocument populatedDocument = new CmisDocument();
    populatedDocument.setType(succinctProperties.optString("sap:type", null));
    populatedDocument.setUrl(succinctProperties.optString("sap:linkURL", null));

    // Filter secondary properties for DB
    Map<String, Object> filteredSecondaryProps = new HashMap<>();
    for (Map.Entry<String, String> propEntry : entityAnnotations.entrySet()) {
      String dbPropertyName = propEntry.getKey();
      String sdmPropertyName = propEntry.getValue();

      if (succinctProperties.has(sdmPropertyName)) {
        Object value = succinctProperties.get(sdmPropertyName);
        if (value != null && !org.json.JSONObject.NULL.equals(value)) {
          filteredSecondaryProps.put(dbPropertyName, value);
        }
      }
    }

    populatedDocument.setSecondaryProperties(filteredSecondaryProps);

    logger.info(
        "Attachment {} - Prepared {} properties for DB insertion",
        objectId,
        filteredSecondaryProps.size());

    // Add to successful results
    results.successfulObjectIds.add(objectId);
    results.movedAttachmentsMetadata.add(List.of(fileName, mimeType, description, movedObjectId));
    results.populatedDocuments.add(populatedDocument);
  }

  // Rollback a single attachment to source folder
  private void rollbackSingleAttachment(
      String objectId,
      String sourceFolderId,
      String targetFolderId,
      String repositoryId,
      SDMCredentials sdmCredentials,
      Boolean isSystemUser)
      throws IOException {
    CmisDocument rollbackDoc = new CmisDocument();
    rollbackDoc.setObjectId(objectId);
    rollbackDoc.setRepositoryId(repositoryId);
    rollbackDoc.setSourceFolderId(targetFolderId); // Move back from target
    rollbackDoc.setFolderId(sourceFolderId); // To source
    sdmService.moveAttachment(rollbackDoc, sdmCredentials, isSystemUser);
  }

  private void handleCopyFailure(
      AttachmentCopyEventContext context,
      String folderId,
      boolean folderExists,
      List<Map<String, String>> attachmentsMetadata,
      ServiceException e)
      throws IOException {
    if (!folderExists) {
      sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
    } else {
      for (Map<String, String> attachmentMetadata : attachmentsMetadata) {
        sdmService.deleteDocument(
            "delete", attachmentMetadata.get("cmis:objectId"), context.getUserInfo().getName());
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

  /**
   * Creates draft entries for moved attachments with secondary properties support. This method is
   * specifically for move operations where we need to preserve validated secondary properties from
   * the SDM response.
   */
  private void createDraftEntriesForMove(
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      String parentEntity,
      String compositionName,
      String upID,
      String upIdKey,
      String repositoryId,
      String folderId) {

    for (int i = 0; i < movedAttachmentsMetadata.size(); i++) {
      List<String> attachmentMetadata = movedAttachmentsMetadata.get(i);
      CmisDocument cmisDocument = populatedDocuments.get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get(0);
      String mimeType = attachmentMetadata.get(1);
      String description = attachmentMetadata.get(2);
      String newObjectId = attachmentMetadata.get(3);

      updatedFields.put(OBJECT_ID_KEY, newObjectId);
      updatedFields.put("repositoryId", repositoryId);
      updatedFields.put("folderId", folderId);
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("type", cmisDocument.getType());
      updatedFields.put("fileName", fileName);
      updatedFields.put("note", description);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      updatedFields.put("linkUrl", cmisDocument.getUrl());
      updatedFields.put(
          "contentId",
          newObjectId
              + ":"
              + folderId
              + ":"
              + parentEntity
              + "."
              + compositionName
              + ":"
              + mimeType);
      updatedFields.put(upIdKey, upID);

      // Include secondary properties from moved attachment
      // Properties are already filtered and validated in processValidatedAttachment()
      // to only include those annotated with @SDM.Attachments.AdditionalProperty
      // and present in valid secondary properties list
      if (cmisDocument.getSecondaryProperties() != null) {
        updatedFields.putAll(cmisDocument.getSecondaryProperties());
      }

      String baseKeyField = upIdKey != null ? upIdKey.replace("up__", "") : "ID";
      var insert =
          Insert.into(parentEntity, e -> e.filter(e.get(baseKeyField).eq(upID)).to(compositionName))
              .entry(updatedFields);

      DraftService matchingService =
          draftService.stream()
              .filter(ds -> parentEntity.contains(ds.getName()))
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
        throw new ServiceException("No suitable service found for entity: " + parentEntity);
      }
    }
  }

  /**
   * Creates draft entries for copied attachments with custom properties support. This method is for
   * copy operations where custom properties come from the SDM copyAttachment response.
   */
  private void createDraftEntries(
      CreateDraftEntriesRequest request, Map<String, String> customPropertyDefinitions) {

    for (int i = 0; i < request.getAttachmentsMetadata().size(); i++) {
      Map<String, String> attachmentMetadata = request.getAttachmentsMetadata().get(i);
      CmisDocument cmisDocument = request.getPopulatedDocuments().get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get("cmis:name");
      String mimeType = attachmentMetadata.get("cmis:contentStreamMimeType");
      String description = attachmentMetadata.get("cmis:description");
      String newObjectId = attachmentMetadata.get("cmis:objectId");

      updatedFields.put(OBJECT_ID_KEY, newObjectId);
      updatedFields.put("repositoryId", request.getRepositoryId());
      updatedFields.put("folderId", request.getFolderId());
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("type", cmisDocument.getType()); // Individual type for each attachment
      updatedFields.put("fileName", fileName);
      updatedFields.put("note", description); // Map cmis:description to note field
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

      // Extract custom properties from the attachmentMetadata using customPropertyDefinitions
      if (customPropertyDefinitions != null && !customPropertyDefinitions.isEmpty()) {
        for (Map.Entry<String, String> customProperty : customPropertyDefinitions.entrySet()) {
          String columnName = customProperty.getKey(); // CDS column name
          String sdmPropertyName = customProperty.getValue(); // SDM property name

          if (attachmentMetadata.containsKey(sdmPropertyName)) {
            String value = attachmentMetadata.get(sdmPropertyName);
            updatedFields.put(columnName, value);
          }
        }
      }

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
   * Rolls back successfully moved attachments when database update fails. Moves attachments back to
   * their original source folder in parallel. Continues with remaining rollbacks even if individual
   * operations fail.
   *
   * @param successfulObjectIds list of objectIds that were successfully moved in SDM
   * @param sourceFolderId original source folder ID
   * @param targetFolderId target folder ID where attachments were moved
   * @param repositoryId SDM repository ID
   * @param sdmCredentials SDM credentials for authentication
   * @param isSystemUser whether this is a system user operation
   * @param context the move event context
   */
  private void rollbackMovedAttachments(
      List<String> successfulObjectIds,
      String sourceFolderId,
      String targetFolderId,
      String repositoryId,
      SDMCredentials sdmCredentials,
      boolean isSystemUser,
      AttachmentMoveEventContext context) {
    logger.warn(
        "Rolling back {} moved attachments from {} to {}",
        successfulObjectIds.size(),
        targetFolderId,
        sourceFolderId);

    com.sap.cds.services.runtime.RequestContextRunner contextRunner =
        context.getCdsRuntime().requestContext();

    List<CompletableFuture<Void>> rollbackFutures =
        successfulObjectIds.stream()
            .map(
                objectId ->
                    CompletableFuture.runAsync(
                        () ->
                            contextRunner.run(
                                ctx -> {
                                  try {
                                    CmisDocument rollbackDoc = new CmisDocument();
                                    rollbackDoc.setObjectId(objectId);
                                    rollbackDoc.setRepositoryId(repositoryId);
                                    rollbackDoc.setSourceFolderId(targetFolderId);
                                    rollbackDoc.setFolderId(sourceFolderId);

                                    sdmService.moveAttachment(
                                        rollbackDoc, sdmCredentials, isSystemUser);
                                    logger.info(
                                        "Successfully rolled back attachment {} to source folder",
                                        objectId);
                                  } catch (Exception e) {
                                    logger.error(
                                        "Failed to rollback attachment {}: {}",
                                        objectId,
                                        e.getMessage());
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
