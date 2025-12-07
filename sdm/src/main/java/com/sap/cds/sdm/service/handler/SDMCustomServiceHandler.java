package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.AttachmentMoveContext;
import com.sap.cds.sdm.model.AttachmentProcessingResults;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentsRequest;
import com.sap.cds.sdm.model.CopyAttachmentsResult;
import com.sap.cds.sdm.model.CreateDraftEntriesRequest;
import com.sap.cds.sdm.model.DatabaseFailureContext;
import com.sap.cds.sdm.model.DraftEntryMoveData;
import com.sap.cds.sdm.model.MoveAttachmentsRequest;
import com.sap.cds.sdm.model.MoveAttachmentsResult;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.model.SDMValidationData;
import com.sap.cds.sdm.model.TargetFolderInfo;
import com.sap.cds.sdm.model.ValidatedAttachmentData;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.RequestContextRunner;
import io.reactivex.Flowable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    String targetFolderId = folderInfo.getTargetFolderId();
    boolean targetFolderExists = folderInfo.getTargetFolderExists();

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
    DraftEntryMoveData draftData =
        new DraftEntryMoveData(
            movedAttachmentsMetadata,
            populatedDocuments,
            parentEntity,
            compositionName,
            upID,
            upIdKey,
            repositoryId,
            folderId);
    createDraftEntriesForMove(draftData);

    // Clean up source entity metadata after successful move
    if (!successfulObjectIds.isEmpty() && sourceUpId != null) {
      try {
        long deletedCount =
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
        failureContext.getSuccessfulObjectIds(),
        failureContext.getSourceFolderId(),
        failureContext.getTargetFolderId(),
        failureContext.getRepositoryId(),
        failureContext.getSdmCredentials(),
        failureContext.getIsSystemUser(),
        failureContext.getContext());
    // Mark rolled-back attachments as failed
    for (String objectId : failureContext.getSuccessfulObjectIds()) {
      Map<String, String> failureMap = new HashMap<>();
      failureMap.put(OBJECT_ID_KEY, objectId);
      failureMap.put(
          FAILURE_REASON_KEY, "Database update failed, move rolled back: " + e.getMessage());
      failureContext.getFailedAttachments().add(failureMap);
    }
    failureContext
        .getContext()
        .setFailedAttachments(new ArrayList<>(failureContext.getFailedAttachments()));

    // Add warning message
    StringBuilder warningMessage =
        new StringBuilder(
            "Move operation failed during database update. Rolled back attachments:\n");
    for (String objectId : failureContext.getSuccessfulObjectIds()) {
      warningMessage.append("  - ObjectId: ").append(objectId).append("\n");
    }
    failureContext.getContext().getMessages().warn(warningMessage.toString());

    logger.warn(
        "Move operation completed with failures. Total failed: {}, Rolled back: {}",
        failureContext.getFailedAttachments().size(),
        failureContext.getSuccessfulObjectIds().size());
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

    // Get entity annotations and target entity for filtering properties during DB insertion
    Object[] entityData = dbQuery.getValidSecondaryPropertiesWithEntity(request.getContext());
    @SuppressWarnings("unchecked")
    Map<String, String> entityAnnotations = (Map<String, String>) entityData[0];
    CdsEntity targetEntity = (CdsEntity) entityData[1];
    logger.info(
        "Target entity has {} annotated secondary properties for DB mapping: {}",
        entityAnnotations.size(),
        entityAnnotations);

    return new SDMValidationData(validSecondaryProperties, entityAnnotations, targetEntity);
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
    List<List<String>> movedAttachmentsMetadata = Collections.synchronizedList(new ArrayList<>());
    List<CmisDocument> populatedDocuments = Collections.synchronizedList(new ArrayList<>());
    List<Map<String, String>> failedAttachments = Collections.synchronizedList(new ArrayList<>());
    List<String> successfulObjectIds = Collections.synchronizedList(new ArrayList<>());

    // Fetch SDM validation data
    SDMValidationData validationData = fetchSDMValidationData(request);
    List<String> validSecondaryProperties = validationData.getValidSecondaryProperties();
    Map<String, String> entityAnnotations = validationData.getEntityAnnotations();
    CdsEntity targetEntity = validationData.getTargetEntity();

    // Preserve request context for authentication in parallel threads
    RequestContextRunner contextRunner = request.getContext().getCdsRuntime().requestContext();

    // Create results wrapper for successful processing
    AttachmentProcessingResults processingResults =
        new AttachmentProcessingResults(
            successfulObjectIds, movedAttachmentsMetadata, populatedDocuments);

    logger.info(
        "Starting parallel move operation for {} attachments using {} threads",
        request.getObjectIds().size(),
        PARALLEL_MOVE_THREAD_POOL_SIZE);

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
                                          targetEntity,
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

  /**
   * Processes a single attachment move: Move in SDM → Validate → Process or Rollback.
   *
   * @param moveContext the context containing all necessary information for processing
   */
  private void processSingleAttachmentMove(AttachmentMoveContext moveContext) {
    String threadName = Thread.currentThread().getName();
    logger.info(
        "[Thread: {}] Starting move for attachment: {}", threadName, moveContext.getObjectId());
    try {
      // Step 1: Fetch attachment metadata from database to get type and URL (needed for link
      // attachments)
      // Create a copy event context to fetch attachment from source location
      AttachmentMoveEventContext eventContext = moveContext.getRequest().getContext();
      AttachmentCopyEventContext copyContext = AttachmentCopyEventContext.create();
      copyContext.setParentEntity(eventContext.getSourceParentEntity());
      copyContext.setCompositionName(eventContext.getSourceCompositionName());

      CmisDocument cmisDocument =
          dbQuery.getAttachmentForObjectID(
              persistenceService, moveContext.getObjectId(), copyContext);

      // Set move operation specific fields
      cmisDocument.setObjectId(moveContext.getObjectId());
      cmisDocument.setRepositoryId(moveContext.getRequest().getRepositoryId());
      cmisDocument.setSourceFolderId(moveContext.getRequest().getSourceFolderId());
      cmisDocument.setFolderId(moveContext.getRequest().getTargetFolderId());

      String sdmResponseJson =
          sdmService.moveAttachment(
              cmisDocument,
              moveContext.getRequest().getSdmCredentials(),
              moveContext.getRequest().getIsSystemUser());
      JSONObject sdmResponse = new JSONObject(sdmResponseJson);
      JSONObject succinctProperties = sdmResponse.getJSONObject("succinctProperties");

      String fileName = succinctProperties.optString("cmis:name");
      String mimeType = succinctProperties.optString("cmis:contentStreamMimeType");
      String description = succinctProperties.optString("cmis:description");
      String movedObjectId = succinctProperties.optString("cmis:objectId");

      logger.info(
          "[Thread: {}] Successfully moved attachment {} to target folder. FileName: {}, MimeType: {}",
          Thread.currentThread().getName(),
          moveContext.getObjectId(),
          fileName,
          mimeType);
      logger.info(
          "SDM response for attachment {} contains {} total properties: {}",
          moveContext.getObjectId(),
          succinctProperties.length(),
          succinctProperties.keySet());

      // Step 2: Validate target entity properties in SDM response
      // Get target entity's SDM property names from annotations
      Set<String> targetEntitySdmProperties =
          new HashSet<>(moveContext.getEntityAnnotations().values());
      Set<String> sdmResponseProperties = new HashSet<>(succinctProperties.keySet());

      logger.info(
          "[Thread: {}] Validating attachment {} - Target entity expects {} SDM properties: {}",
          Thread.currentThread().getName(),
          moveContext.getObjectId(),
          targetEntitySdmProperties.size(),
          targetEntitySdmProperties);
      logger.info(
          "SDM response has {} properties: {}",
          sdmResponseProperties.size(),
          sdmResponseProperties);
      logger.info(
          "Valid secondary properties list has {} entries: {}",
          moveContext.getValidSecondaryProperties().size(),
          moveContext.getValidSecondaryProperties());

      // Validate: Check target entity properties that exist in SDM response
      List<String> invalidProperties = new ArrayList<>();
      for (String targetSdmProperty : targetEntitySdmProperties) {
        if (sdmResponseProperties.contains(targetSdmProperty)
            && !moveContext.getValidSecondaryProperties().contains(targetSdmProperty)) {
          // Property defined in target entity, present in SDM response, but NOT in valid list →
          // INVALID
          invalidProperties.add(targetSdmProperty);
          logger.warn(
              "Attachment {} - Property '{}' is defined in target entity and in SDM response but"
                  + " NOT in valid secondary properties list",
              moveContext.getObjectId(),
              targetSdmProperty);
        }
      }

      if (!invalidProperties.isEmpty()) {
        // Step 3a: Validation failed → Rollback immediately
        handleValidationFailure(
            moveContext.getObjectId(),
            invalidProperties,
            moveContext.getRequest(),
            moveContext.getFailedAttachments());

      } else {
        // Step 3b: Validation passed → Process for DB insertion
        ValidatedAttachmentData validatedData =
            new ValidatedAttachmentData(
                moveContext.getObjectId(),
                fileName,
                mimeType,
                description,
                movedObjectId,
                succinctProperties,
                moveContext.getEntityAnnotations(),
                moveContext.getTargetEntity(),
                moveContext.getProcessingResults().getSuccessfulObjectIds(),
                moveContext.getProcessingResults().getMovedAttachmentsMetadata(),
                moveContext.getProcessingResults().getPopulatedDocuments(),
                cmisDocument);
        processValidatedAttachment(validatedData);
      }

    } catch (ServiceException | IOException e) {
      // Move operation failed
      logger.error(
          "[Thread: {}] Failed to move attachment {}: {}",
          Thread.currentThread().getName(),
          moveContext.getObjectId(),
          e.getMessage());
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.getObjectId());
      failure.put(FAILURE_REASON_KEY, "SDM move failed: " + e.getMessage());
      moveContext.getFailedAttachments().add(failure);
    } catch (Exception e) {
      // Validation/processing failed
      logger.error(
          "Failed to validate/process attachment {}: {}",
          moveContext.getObjectId(),
          e.getMessage(),
          e);
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.getObjectId());
      failure.put(FAILURE_REASON_KEY, "Validation/processing failed: " + e.getMessage());
      moveContext.getFailedAttachments().add(failure);
    }
  }

  /**
   * Processes a successfully validated attachment for DB insertion.
   *
   * @param data encapsulated validated attachment data
   */
  private void processValidatedAttachment(ValidatedAttachmentData data) {
    logger.info(
        "[Thread: {}] Attachment {} validation PASSED - Processing for DB insertion",
        Thread.currentThread().getName(),
        data.getObjectId());
    logger.info(
        "Entity annotations mapping (DB field -> SDM property): {}", data.getEntityAnnotations());

    CmisDocument populatedDocument = createPopulatedDocument(data.getSourceCmisDocument());
    Map<String, Object> filteredSecondaryProps =
        filterSecondaryProperties(
            data.getObjectId(),
            data.getSuccinctProperties(),
            data.getEntityAnnotations(),
            data.getTargetEntity());

    populatedDocument.setSecondaryProperties(filteredSecondaryProps);

    logger.info(
        "Attachment {} - Prepared {} properties for DB insertion: {}",
        data.getObjectId(),
        filteredSecondaryProps.size(),
        filteredSecondaryProps);

    // Add to successful results
    data.getSuccessfulObjectIds().add(data.getObjectId());
    data.getMovedAttachmentsMetadata()
        .add(
            List.of(
                data.getFileName(),
                data.getMimeType(),
                data.getDescription(),
                data.getMovedObjectId()));
    data.getPopulatedDocuments().add(populatedDocument);
  }

  /**
   * Creates a CmisDocument with basic metadata from source document.
   *
   * @param sourceCmisDocument the original cmisDocument from database with type and URL
   * @return populated CmisDocument
   */
  private CmisDocument createPopulatedDocument(CmisDocument sourceCmisDocument) {
    CmisDocument document = new CmisDocument();
    // Preserve type and URL from source document (fetched from database)
    // This is essential for link attachments where URL is not in SDM response
    document.setType(sourceCmisDocument.getType());
    document.setUrl(sourceCmisDocument.getUrl());
    return document;
  }

  /**
   * Filters and converts secondary properties from SDM response for DB insertion.
   *
   * @param objectId the attachment object ID (for logging)
   * @param succinctProperties SDM response properties
   * @param entityAnnotations mapping of DB fields to SDM properties
   * @param targetEntity the target attachment entity (for type checking)
   * @return filtered and converted properties map
   */
  private Map<String, Object> filterSecondaryProperties(
      String objectId,
      JSONObject succinctProperties,
      Map<String, String> entityAnnotations,
      CdsEntity targetEntity) {
    Map<String, Object> filteredProperties = new HashMap<>();

    for (Map.Entry<String, String> propEntry : entityAnnotations.entrySet()) {
      String dbPropertyName = propEntry.getKey();
      String sdmPropertyName = propEntry.getValue();

      logger.info(
          "Checking property - DB field: '{}', SDM property: '{}', exists in SDM response: {}",
          dbPropertyName,
          sdmPropertyName,
          succinctProperties.has(sdmPropertyName));

      if (succinctProperties.has(sdmPropertyName)) {
        Object value = succinctProperties.get(sdmPropertyName);
        processSecondaryProperty(
            dbPropertyName, sdmPropertyName, value, targetEntity, filteredProperties);
      }
    }

    return filteredProperties;
  }

  /**
   * Processes a single secondary property: logs, converts if needed, and adds to filtered map.
   *
   * @param dbPropertyName the DB field name
   * @param sdmPropertyName the SDM property name
   * @param value the property value from SDM
   * @param targetEntity the target entity for type checking
   * @param filteredProperties the map to add the processed property to
   */
  private void processSecondaryProperty(
      String dbPropertyName,
      String sdmPropertyName,
      Object value,
      CdsEntity targetEntity,
      Map<String, Object> filteredProperties) {
    logger.info(
        "Found SDM property '{}' with value: {} (type: {})",
        sdmPropertyName,
        value,
        value != null ? value.getClass().getSimpleName() : "null");

    if (value == null || JSONObject.NULL.equals(value)) {
      return;
    }

    Object convertedValue = convertValueIfNeeded(value, dbPropertyName, targetEntity);
    filteredProperties.put(dbPropertyName, convertedValue);
    logger.info("Added to DB map: '{}' = '{}'", dbPropertyName, convertedValue);
  }

  /**
   * Converts value to appropriate type based on CDS field definition. Specifically handles Long to
   * Instant conversion for DateTime fields.
   *
   * @param value the original value
   * @param dbPropertyName the DB field name
   * @param targetEntity the target entity for type checking
   * @return converted value or original value if no conversion needed
   */
  private Object convertValueIfNeeded(Object value, String dbPropertyName, CdsEntity targetEntity) {
    if (!(value instanceof Long)) {
      return value;
    }

    CdsElement element = targetEntity.getElement(dbPropertyName);
    if (isDateTimeField(element)) {
      Object converted = Instant.ofEpochMilli((Long) value);
      logger.info(
          "Converted Long timestamp {} to Instant {} for DateTime field '{}'",
          value,
          converted,
          dbPropertyName);
      return converted;
    }

    logger.info(
        "Keeping Long value {} as-is for non-DateTime field '{}' (type: {})",
        value,
        dbPropertyName,
        element != null && element.getType() != null
            ? element.getType().getQualifiedName()
            : "unknown");
    return value;
  }

  /**
   * Checks if a CDS element is a DateTime field.
   *
   * @param element the CDS element
   * @return true if the element is a DateTime field
   */
  private boolean isDateTimeField(CdsElement element) {
    return element != null
        && element.getType() != null
        && "cds.DateTime".equals(element.getType().getQualifiedName());
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
   *
   * @param data encapsulated draft entry creation data
   */
  private void createDraftEntriesForMove(DraftEntryMoveData data) {

    for (int i = 0; i < data.getMovedAttachmentsMetadata().size(); i++) {
      List<String> attachmentMetadata = data.getMovedAttachmentsMetadata().get(i);
      CmisDocument cmisDocument = data.getPopulatedDocuments().get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get(0);
      String mimeType = attachmentMetadata.get(1);
      String description = attachmentMetadata.get(2);
      String newObjectId = attachmentMetadata.get(3);

      updatedFields.put(OBJECT_ID_KEY, newObjectId);
      updatedFields.put("repositoryId", data.getRepositoryId());
      updatedFields.put("folderId", data.getFolderId());
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
              + data.getFolderId()
              + ":"
              + data.getParentEntity()
              + "."
              + data.getCompositionName()
              + ":"
              + mimeType);
      updatedFields.put(data.getUpIdKey(), data.getUpID());

      // Include secondary properties from moved attachment
      // Properties are already filtered and validated in processValidatedAttachment()
      // to only include those annotated with @SDM.Attachments.AdditionalProperty
      // and present in valid secondary properties list
      if (cmisDocument.getSecondaryProperties() != null) {
        logger.info(
            "Adding {} secondary properties to DB insert for attachment {}: {}",
            cmisDocument.getSecondaryProperties().size(),
            newObjectId,
            cmisDocument.getSecondaryProperties());
        updatedFields.putAll(cmisDocument.getSecondaryProperties());
      } else {
        logger.warn("No secondary properties to add for attachment {}", newObjectId);
      }

      logger.info(
          "Final DB insert map for attachment {} contains {} fields: {}",
          newObjectId,
          updatedFields.size(),
          updatedFields.keySet());

      String baseKeyField =
          data.getUpIdKey() != null ? data.getUpIdKey().replace("up__", "") : "ID";
      var insert =
          Insert.into(
                  data.getParentEntity(),
                  e ->
                      e.filter(e.get(baseKeyField).eq(data.getUpID()))
                          .to(data.getCompositionName()))
              .entry(updatedFields);

      DraftService matchingService =
          draftService.stream()
              .filter(ds -> data.getParentEntity().contains(ds.getName()))
              .findFirst()
              .orElse(null);

      if (matchingService != null) {
        // Wrap DB insert with retry logic to handle transient DB failures
        try {
          Flowable.fromCallable(
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
            "No suitable service found for entity: " + data.getParentEntity());
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
          Flowable.fromCallable(
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

    RequestContextRunner contextRunner = context.getCdsRuntime().requestContext();

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
