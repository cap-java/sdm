package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.AttachmentMoveContext;
import com.sap.cds.sdm.model.AttachmentProcessingResults;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentsRequest;
import com.sap.cds.sdm.model.CopyAttachmentsResult;
import com.sap.cds.sdm.model.CreateDraftEntriesRequest;
import com.sap.cds.sdm.model.DatabaseFailureContext;
import com.sap.cds.sdm.model.DatabaseUpdateRequest;
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
import com.sap.cds.sdm.utilities.SDMUtils;
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

    // Pass the entity for type conversion
    CdsEntity targetEntity = entity.isPresent() ? entity.get() : null;
    createDraftEntries(draftRequest, customPropertyDefinitions, targetEntity);

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

    // Check maxCount constraint before attempting move
    List<Map<String, String>> failedAttachments =
        checkMaxCountConstraintForMove(context, parentEntity, compositionName, upID, objectIds);
    if (!failedAttachments.isEmpty()) {
      // All attachments failed maxCount validation
      context.setFailedAttachments(failedAttachments);
      context.setCompleted();
      return;
    }

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
    List<Map<String, String>> moveFailures = moveResult.getFailedAttachments();
    List<String> successfulObjectIds = moveResult.getSuccessfulObjectIds();

    // Return failed attachments to caller
    context.setFailedAttachments(new ArrayList<>(moveFailures));

    // Show warning if there are failures
    if (!moveFailures.isEmpty()) {
      StringBuilder warningMessage =
          new StringBuilder("Failed to move the following attachments:\n");
      for (Map<String, String> failure : moveFailures) {
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
        DatabaseUpdateRequest updateRequest =
            new DatabaseUpdateRequest(
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
        updateDatabaseAndCleanupSource(updateRequest);
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
   * Checks if moving attachments would exceed the maxCount constraint on the target entity.
   *
   * @param context the move event context
   * @param parentEntity the parent entity name
   * @param compositionName the composition name
   * @param upID the up ID
   * @param objectIds list of attachment object IDs to move
   * @return list of failed attachments if constraint is violated, empty list otherwise
   */
  private List<Map<String, String>> checkMaxCountConstraintForMove(
      AttachmentMoveEventContext context,
      String parentEntity,
      String compositionName,
      String upID,
      List<String> objectIds) {
    List<Map<String, String>> failedAttachments = new ArrayList<>();

    try {
      // Get target attachment entity
      Optional<CdsEntity> targetEntityOptional =
          context.getModel().findEntity(parentEntity + "." + compositionName);
      if (targetEntityOptional.isEmpty()) {
        logger.warn(
            "Target entity {}.{} not found, skipping maxCount validation",
            parentEntity,
            compositionName);
        return failedAttachments;
      }

      CdsEntity targetAttachmentEntity = targetEntityOptional.get();

      long maxCount =
          SDMUtils.getAttachmentCountAndMessage(
              context.getModel().entities().toList(), targetAttachmentEntity);

      // If maxCount is 0 or negative, no limit is enforced
      if (maxCount <= 0) {
        return failedAttachments;
      }

      // Get target attachment draft entity for querying existing attachments
      Optional<CdsEntity> draftEntityOptional =
          context.getModel().findEntity(targetAttachmentEntity.getQualifiedName() + "_drafts");
      if (draftEntityOptional.isEmpty()) {
        logger.warn(
            "Draft entity for {} not found, skipping maxCount validation",
            targetAttachmentEntity.getQualifiedName());
        return failedAttachments;
      }

      CdsEntity attachmentDraftEntity = draftEntityOptional.get();
      String upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity);

      // Count existing attachments in target entity
      Result result =
          dbQuery.getAttachmentsForUPIDAndRepository(
              attachmentDraftEntity, persistenceService, upID, upIdKey);
      long existingCount = result.rowCount();
      long totalCountAfterMove = existingCount + objectIds.size();

      logger.info(
          "MaxCount validation - Target entity: {}, MaxCount: {}, Existing: {}, Moving: {},"
              + " Total after move: {}",
          targetAttachmentEntity.getQualifiedName(),
          maxCount,
          existingCount,
          objectIds.size(),
          totalCountAfterMove);

      // Check if total would exceed maxCount
      if (totalCountAfterMove > maxCount) {
        String failureReason = SDMUtils.getErrorMessage("MAX_COUNT_ERROR_MESSAGE");

        logger.warn(
            "Move operation rejected: Total count {} exceeds maxCount {}. Marking all {} attachments"
                + " as failed.",
            totalCountAfterMove,
            maxCount,
            objectIds.size());

        // Mark all attachments as failed
        for (String objectId : objectIds) {
          Map<String, String> failure = new HashMap<>();
          failure.put(OBJECT_ID_KEY, objectId);
          failure.put(FAILURE_REASON_KEY, failureReason);
          failedAttachments.add(failure);
        }

        // Show warning message
        context.getMessages().warn(String.format(failureReason, maxCount));
      }
    } catch (Exception e) {
      logger.error(
          "Error during maxCount validation for move operation: {}. Proceeding without"
              + " validation.",
          e.getMessage(),
          e);
      // Don't block the move operation if validation fails
    }

    return failedAttachments;
  }

  /**
   * Updates database with moved attachments and cleans up source entity metadata.
   *
   * @param request encapsulated database update request data
   * @throws ServiceException if database operations fail
   */
  private void updateDatabaseAndCleanupSource(DatabaseUpdateRequest request)
      throws ServiceException {
    // Query source entity's up__ID before creating target records
    // This ensures we get the correct source ID, especially important when moving
    // between entities of the same type (e.g., Book A to Book B)
    String sourceUpId = null;
    if (!request.getSuccessfulObjectIds().isEmpty()) {
      sourceUpId =
          dbQuery.getSourceUpIdForObjectIds(
              persistenceService, request.getSuccessfulObjectIds(), request.getContext());
      logger.info("Retrieved source up__ID for cleanup: {}", sourceUpId);
    }

    // Create draft entries for moved attachments with secondary properties
    DraftEntryMoveData draftData =
        new DraftEntryMoveData(
            request.getMovedAttachmentsMetadata(),
            request.getPopulatedDocuments(),
            request.getParentEntity(),
            request.getCompositionName(),
            request.getUpID(),
            request.getUpIdKey(),
            request.getRepositoryId(),
            request.getFolderId());
    createDraftEntriesForMove(draftData);

    // Clean up source entity metadata after successful move
    if (!request.getSuccessfulObjectIds().isEmpty() && sourceUpId != null) {
      try {
        long deletedCount =
            dbQuery.deleteAttachmentsByObjectIds(
                persistenceService,
                request.getSuccessfulObjectIds(),
                sourceUpId,
                request.getContext());
        logger.info(
            "Cleaned up {} attachment metadata records from source entity for {} successfully"
                + " moved attachments",
            deletedCount,
            request.getSuccessfulObjectIds().size());
      } catch (Exception cleanupException) {
        logger.warn(
            "Failed to clean up source entity metadata for {} attachments: {}. Attachments were"
                + " successfully moved to target.",
            request.getSuccessfulObjectIds().size(),
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
      failureContext.addFailedAttachment(failureMap);
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
      populatedDocument.setUploadStatus(cmisDocument.getUploadStatus());
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
        SDMUtils.getErrorMessage("INVALID_SECONDARY_PROPERTIES_FOR_MOVE_PREFIX")
            + String.join(", ", invalidProperties)
            + SDMUtils.getErrorMessage("INVALID_SECONDARY_PROPERTIES_FOR_MOVE_SUFFIX"));
    failedAttachments.add(failure);
  }

  /**
   * Parses SDM error message to extract meaningful failure reason. Handles different SDM error
   * types similar to createAttachment() flow.
   *
   * @param exception The exception from SDM operation
   * @return A user-friendly failure reason
   */
  private String parseSDMErrorMessage(Exception exception) {
    String errorMessage = extractErrorMessage(exception);

    if (errorMessage == null || errorMessage.isEmpty()) {
      return SDMUtils.getErrorMessage("SDM_MOVE_OPERATION_FAILED");
    }

    // Try to match specific error types
    String specificError = matchSpecificErrorType(errorMessage);
    if (specificError != null) {
      return specificError;
    }

    // Generic SDM error pattern: "errorType : detailed message"
    return extractDetailedMessage(errorMessage);
  }

  /**
   * Extracts error message from exception chain.
   *
   * @param exception the exception to extract message from
   * @return the most detailed error message found
   */
  private String extractErrorMessage(Exception exception) {
    String errorMessage = exception.getMessage();

    // If the main message is generic, check the cause chain
    if (isGenericMessage(errorMessage) && exception.getCause() != null) {
      Throwable cause = exception.getCause();
      while (cause != null) {
        String causeMessage = cause.getMessage();
        if (!isGenericMessage(causeMessage)) {
          return causeMessage;
        }
        cause = cause.getCause();
      }
    }

    return errorMessage;
  }

  /**
   * Checks if a message is generic and should be replaced with a more detailed one.
   *
   * @param message the message to check
   * @return true if the message is null, empty, or generic
   */
  private boolean isGenericMessage(String message) {
    return message == null
        || message.isEmpty()
        || message.equals(SDMUtils.getErrorMessage("FAILED_TO_MOVE_ATTACHMENT"));
  }

  /**
   * Matches error message against specific SDM error types.
   *
   * @param errorMessage the error message to match
   * @return specific error message if matched, null otherwise
   */
  private String matchSpecificErrorType(String errorMessage) {
    String lowerCaseMessage = errorMessage.toLowerCase();

    if (lowerCaseMessage.contains("duplicate")
        || lowerCaseMessage.contains("nameconstraintviolation")) {
      return parseDuplicateError(errorMessage);
    }

    if (lowerCaseMessage.contains("virus") || lowerCaseMessage.contains("malware")) {
      return "File contains potential malware and cannot be moved";
    }

    if (lowerCaseMessage.contains("unauthorized")
        || lowerCaseMessage.contains("not authorized")
        || lowerCaseMessage.contains("permission")) {
      return SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR");
    }

    if (lowerCaseMessage.contains("blocked") || lowerCaseMessage.contains("mimetype")) {
      return SDMUtils.getErrorMessage("MIMETYPE_INVALID_ERROR");
    }

    if (lowerCaseMessage.contains("not found") || lowerCaseMessage.contains("object not found")) {
      return SDMUtils.getErrorMessage("FILE_NOT_FOUND_ERROR");
    }

    return null;
  }

  /**
   * Parses duplicate file error and extracts filename.
   *
   * @param errorMessage the error message
   * @return parsed duplicate error message
   */
  private String parseDuplicateError(String errorMessage) {
    int colonIndex = errorMessage.indexOf(" : ");
    if (colonIndex == -1) {
      return "Duplicate file already exists in the target location";
    }

    String detailedMessage = errorMessage.substring(colonIndex + 3).trim();
    if (detailedMessage.startsWith("Child ")) {
      int withIndex = detailedMessage.indexOf(" with Id");
      if (withIndex != -1) {
        String filename = detailedMessage.substring(6, withIndex).trim();
        return SDMErrorMessages.getDuplicateFilesError(filename);
      }
    }
    return detailedMessage;
  }

  /**
   * Extracts detailed message from generic SDM error pattern.
   *
   * @param errorMessage the error message
   * @return detailed message or original message
   */
  private String extractDetailedMessage(String errorMessage) {
    int colonIndex = errorMessage.indexOf(" : ");
    if (colonIndex != -1) {
      String detailedMessage = errorMessage.substring(colonIndex + 3).trim();
      if (!detailedMessage.isEmpty()) {
        return detailedMessage;
      }
    }
    return errorMessage;
  }

  /**
   * Builds a detailed validation failure message including invalid properties if available.
   *
   * @param moveContext The move context containing validation state
   * @param exception The exception that occurred
   * @return A detailed failure message
   */
  private String buildValidationFailureMessage(
      AttachmentMoveContext moveContext, Exception exception) {
    // Check if we have invalid properties information in the context
    if (moveContext.getInvalidProperties() != null
        && !moveContext.getInvalidProperties().isEmpty()) {
      return SDMUtils.getErrorMessage("INVALID_SECONDARY_PROPERTIES_FOR_MOVE_PREFIX")
          + String.join(", ", moveContext.getInvalidProperties())
          + SDMUtils.getErrorMessage("INVALID_SECONDARY_PROPERTIES_FOR_MOVE_SUFFIX");
    }

    // Detect specific failure types and provide meaningful messages
    String exceptionType = exception.getClass().getSimpleName();
    String exceptionMessage = exception.getMessage();

    // Database fetch failure
    if (exceptionType.contains("ServiceException")
        || exceptionMessage != null && exceptionMessage.contains("database")) {
      return "Failed to retrieve attachment metadata from database. Attachment rolled back to source.";
    }

    // JSON parsing failure
    if (exceptionType.contains("JSONException")
        || exceptionMessage != null && exceptionMessage.contains("JSON")) {
      return "Failed to parse SDM response. Attachment rolled back to source.";
    }

    // Processing/other failures with specific message
    if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
      return "Processing failed: " + exceptionMessage + ". Attachment rolled back to source.";
    }

    // Generic fallback
    return "Attachment processing failed. Attachment rolled back to source.";
  }

  /**
   * Fetches attachment metadata either from database or directly from SDM.
   *
   * @param moveContext the context containing attachment and request information
   * @param threadName the name of the current thread for logging
   * @return CmisDocument with attachment metadata
   */
  private CmisDocument fetchAttachmentMetadata(
      AttachmentMoveContext moveContext, String threadName) {
    AttachmentMoveEventContext eventContext = moveContext.getRequest().getContext();

    // If sourceFacet is provided, fetch from database; otherwise fetch from SDM directly
    if (eventContext.getSourceParentEntity() != null
        && !eventContext.getSourceParentEntity().isEmpty()) {
      // Source facet provided - fetch metadata from database
      logger.info(
          "[Thread: {}] Fetching attachment metadata from database (sourceFacet provided)",
          threadName);
      AttachmentCopyEventContext copyContext = AttachmentCopyEventContext.create();
      copyContext.setParentEntity(eventContext.getSourceParentEntity());
      copyContext.setCompositionName(eventContext.getSourceCompositionName());

      return dbQuery.getAttachmentForObjectID(
          persistenceService, moveContext.getObjectId(), copyContext);
    } else {
      // No source facet - fetch metadata directly from SDM
      logger.info(
          "[Thread: {}] Fetching attachment metadata from SDM (no sourceFacet provided)",
          threadName);
      return fetchAttachmentMetadataFromSDM(moveContext);
    }
  }

  /**
   * Fetches attachment metadata directly from SDM repository.
   *
   * @param moveContext the context containing attachment and request information
   * @return CmisDocument with attachment metadata from SDM
   */
  private CmisDocument fetchAttachmentMetadataFromSDM(AttachmentMoveContext moveContext) {
    try {
      JSONObject sdmMetadata =
          sdmService.getObject(
              moveContext.getObjectId(),
              moveContext.getRequest().getSdmCredentials(),
              moveContext.getRequest().getIsSystemUser());

      if (sdmMetadata == null || sdmMetadata.isEmpty()) {
        throw new ServiceException("Attachment not found in SDM: " + moveContext.getObjectId());
      }

      // Create CmisDocument with metadata from SDM
      CmisDocument cmisDocument = new CmisDocument();
      JSONObject succinctProperties = sdmMetadata.optJSONObject("succinctProperties");
      if (succinctProperties != null) {
        cmisDocument.setFileName(succinctProperties.optString("cmis:name")); // cmis:name
        String description = succinctProperties.optString("cmis:description");
        if (description != null && !description.isEmpty()) {
          cmisDocument.setDescription(description); // cmis:description
        }
      }
      // Type and URL will be null for non-link attachments (which is fine for move)
      return cmisDocument;
    } catch (IOException e) {
      throw new ServiceException(
          "Failed to fetch attachment metadata from SDM: " + e.getMessage(), e);
    }
  }

  /**
   * Fetches link URL from SDM content stream and sets it on the CmisDocument. Link URLs are stored
   * in the content stream in format: [InternetShortcut]\nURL=<url>. If fetching fails, continues
   * with null URL as the type is still correctly set.
   *
   * @param cmisDocument the document to set the URL on
   * @param movedObjectId the objectId to fetch the link URL for
   * @param moveContext the move context containing request credentials
   */
  private void fetchAndSetLinkUrl(
      CmisDocument cmisDocument, String movedObjectId, AttachmentMoveContext moveContext) {
    try {
      String linkUrl =
          sdmService.getLinkUrl(
              movedObjectId,
              moveContext.getRequest().getSdmCredentials(),
              moveContext.getRequest().getIsSystemUser());
      if (linkUrl != null) {
        cmisDocument.setUrl(linkUrl);
        logger.info(
            "[Thread: {}] Fetched and set linkUrl for attachment {}: {}",
            Thread.currentThread().getName(),
            moveContext.getObjectId(),
            linkUrl);
      }
    } catch (Exception e) {
      logger.warn(
          "[Thread: {}] Failed to fetch link URL for attachment {}: {}",
          Thread.currentThread().getName(),
          moveContext.getObjectId(),
          e.getMessage());
      // Continue with null URL - the type is still correctly set
    }
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
      // Step 1: Fetch attachment metadata
      CmisDocument cmisDocument = fetchAttachmentMetadata(moveContext, threadName);

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
      String objectTypeId = succinctProperties.optString("cmis:objectTypeId");

      // Determine attachment type based on cmis:objectTypeId from SDM response
      // Link attachments: "sap:link" -> "sap-icon://internet-browser"
      // Document attachments: "cmis:document" -> "sap-icon://document"
      String attachmentType =
          "sap:link".equals(objectTypeId) ? "sap-icon://internet-browser" : "sap-icon://document";
      cmisDocument.setType(attachmentType);

      // For link attachments, fetch the actual URL from SDM content if not already available
      // Link URLs are stored in the content stream in format: [InternetShortcut]\nURL=<url>
      if ("sap:link".equals(objectTypeId) && cmisDocument.getUrl() == null) {
        fetchAndSetLinkUrl(cmisDocument, movedObjectId, moveContext);
      }

      logger.info(
          "[Thread: {}] Successfully moved attachment {} to target folder. FileName: {}, MimeType: {}, ObjectTypeId: {}, Type: {}, LinkUrl: {}",
          Thread.currentThread().getName(),
          moveContext.getObjectId(),
          fileName,
          mimeType,
          objectTypeId,
          cmisDocument.getType(),
          cmisDocument.getUrl());
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
        // Store invalid properties in context for detailed error message
        moveContext.setInvalidProperties(invalidProperties);
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
          e.getMessage(),
          e);
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.getObjectId());
      // Parse SDM error message to extract meaningful failure reason
      // Check both the exception message and cause for detailed error
      String failureReason = parseSDMErrorMessage(e);
      failure.put(FAILURE_REASON_KEY, failureReason);
      moveContext.addFailedAttachment(failure);
    } catch (Exception e) {
      // Validation/processing failed
      logger.error(
          "Failed to validate/process attachment {}: {}",
          moveContext.getObjectId(),
          e.getMessage(),
          e);
      Map<String, String> failure = new HashMap<>();
      failure.put(OBJECT_ID_KEY, moveContext.getObjectId());
      // Provide detailed validation error with invalid properties if available
      String detailedReason = buildValidationFailureMessage(moveContext, e);
      failure.put(FAILURE_REASON_KEY, detailedReason);
      moveContext.addFailedAttachment(failure);
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
            data.getSuccinctProperties(), data.getEntityAnnotations(), data.getTargetEntity());

    populatedDocument.setSecondaryProperties(filteredSecondaryProps);

    logger.info(
        "Attachment {} - Prepared {} properties for DB insertion: {}",
        data.getObjectId(),
        filteredSecondaryProps.size(),
        filteredSecondaryProps);

    // Add to successful results
    data.addSuccessfulObjectId(data.getObjectId());
    data.addMovedAttachmentMetadata(
        List.of(
            data.getFileName(),
            data.getMimeType(),
            data.getDescription(),
            data.getMovedObjectId()));
    data.addPopulatedDocument(populatedDocument);
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
   * @param succinctProperties SDM response properties
   * @param entityAnnotations mapping of DB fields to SDM properties
   * @param targetEntity the target attachment entity (for type checking)
   * @return filtered and converted properties map
   */
  private Map<String, Object> filterSecondaryProperties(
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
    // Handle Long values - convert to Instant for DateTime fields
    if (value instanceof Long) {
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

    // For all other types (String, Integer, Boolean, etc.), return as-is
    // This ensures codelist/dropdown String values are properly handled
    logger.info(
        "Keeping value {} (type: {}) as-is for field '{}'",
        value,
        value != null ? value.getClass().getSimpleName() : "null",
        dbPropertyName);
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

  /**
   * Converts custom property value from String to appropriate type based on CDS field definition.
   * Used for copy operations where values come as String from SDM response.
   *
   * @param value the String value from SDM
   * @param dbPropertyName the DB field name
   * @param targetEntity the target entity for type checking
   * @return converted value or original value if no conversion needed
   */
  private Object convertCustomPropertyValue(
      String value, String dbPropertyName, CdsEntity targetEntity) {
    if (value == null || value.isEmpty() || targetEntity == null) {
      return value;
    }

    CdsElement element = targetEntity.getElement(dbPropertyName);
    if (element == null || element.getType() == null) {
      return value;
    }

    String fieldType = element.getType().getQualifiedName();

    try {
      // Handle DateTime fields - convert Long timestamp to Instant
      if ("cds.DateTime".equals(fieldType)) {
        Long timestamp = Long.parseLong(value);
        Object converted = Instant.ofEpochMilli(timestamp);
        logger.info(
            "Converted String timestamp '{}' to Instant {} for DateTime field '{}'",
            value,
            converted,
            dbPropertyName);
        return converted;
      }

      // Handle Integer fields
      if ("cds.Integer".equals(fieldType)) {
        Integer convertedInt = Integer.parseInt(value);
        logger.info(
            "Converted String '{}' to Integer {} for field '{}'",
            value,
            convertedInt,
            dbPropertyName);
        return convertedInt;
      }

      // Handle Boolean fields
      if ("cds.Boolean".equals(fieldType)) {
        Boolean convertedBool = Boolean.parseBoolean(value);
        logger.info(
            "Converted String '{}' to Boolean {} for field '{}'",
            value,
            convertedBool,
            dbPropertyName);
        return convertedBool;
      }

    } catch (NumberFormatException e) {
      logger.warn(
          "Failed to convert value '{}' for field '{}' of type '{}', keeping as String: {}",
          value,
          dbPropertyName,
          fieldType,
          e.getMessage());
    }

    // For String fields (including codelist/dropdown) and other types, return as-is
    logger.info(
        "Keeping String value '{}' as-is for field '{}' (type: {})",
        value,
        dbPropertyName,
        fieldType);
    return value;
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
      CreateDraftEntriesRequest request,
      Map<String, String> customPropertyDefinitions,
      CdsEntity targetEntity) {

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
            // Convert value based on CDS field type
            Object convertedValue = convertCustomPropertyValue(value, columnName, targetEntity);
            updatedFields.put(columnName, convertedValue);
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
