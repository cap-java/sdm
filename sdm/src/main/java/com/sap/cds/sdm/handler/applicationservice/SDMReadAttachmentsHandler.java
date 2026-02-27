package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElementDefinition;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.ErrorMessageKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorKeys;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.SDMBeforeReadItemsModifier;
import com.sap.cds.sdm.handler.common.SDMApplicationHandlerHelper;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SDMReadAttachmentsHandler.class);

  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

  public SDMReadAttachmentsHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  /*
    Error message caching requires the CAP context to retrieve localized messages, which may not be
    available at all error throw sites. To ensure availability, error messages are cached during the
    before read event when the context is guaranteed to be present.
  */
  private void setErrorMessagesInCache(CdsReadEventContext context) {
    logger.debug("Setting error messages in cache");
    // Check if cache is available
    Cache<ErrorMessageKey, String> errorMessageCache = CacheConfig.getErrorMessageCache();
    if (errorMessageCache == null) {
      logger.debug("Error message cache not initialized, skipping");
      return; // Cache not initialized, skip
    }

    // Check if localized error messages are already cached
    ErrorMessageKey cacheCheckKey = new ErrorMessageKey();
    cacheCheckKey.setKey("localizedErrorMessagesSetInCache");
    String cacheValue = errorMessageCache.get(cacheCheckKey);

    if ("true".equals(cacheValue)) {
      logger.debug("Error messages already cached, skipping");
      return; // Skip processing if already cached
    }

    Map<String, Object> errorMessages = SDMErrorMessages.getAllErrorMessages();
    Map<String, Object> errorKeys = SDMErrorKeys.getAllErrorKeys();
    logger.debug("Caching {} error messages", errorMessages.size());
    String localizedMessage;
    String localizedErrorMessageKey;
    for (Map.Entry<String, Object> entry : errorMessages.entrySet()) {
      String errorMessage = entry.getKey();
      Object errorValue = entry.getValue();
      localizedErrorMessageKey = String.valueOf(errorKeys.get(errorMessage + "_KEY"));
      localizedMessage =
          context
              .getCdsRuntime()
              .getLocalizedMessage(
                  localizedErrorMessageKey, null, context.getParameterInfo().getLocale());
      ErrorMessageKey errorMessageKey = new ErrorMessageKey();
      errorMessageKey.setKey(errorMessage);
      errorMessageCache.put(
          errorMessageKey,
          java.util.Objects.equals(localizedMessage, localizedErrorMessageKey)
              ? String.valueOf(errorValue)
              : localizedMessage);
    }

    // Mark that localized error messages have been cached
    errorMessageCache.put(cacheCheckKey, "true");
    logger.debug("Error messages cached successfully");
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY + 500)
  public void processBefore(CdsReadEventContext context) throws IOException {
    logger.info("Processing read request for entity: {}", context.getTarget().getQualifiedName());
    logger.debug(
        "START: Reading attachments for entity: {}", context.getTarget().getQualifiedName());
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (repositoryId == null) {
      logger.debug("Repository ID is null, skipping processing");
      return;
    }
    setErrorMessagesInCache(context);
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      try {
        // update the uploadStatus of all blank attachments with success this is for existing
        // attachments
        logger.debug("Target is a media entity, processing attachment logic");
        RepoValue repoValue = checkRepositoryTypeWithFallback(repositoryId, context);

        // Only process virus scan logic if repository info is available
        if (repoValue != null) {
          logger.debug(
              "Repository value found. Async virus scan enabled: {}",
              repoValue.getIsAsyncVirusScanEnabled());
          Optional<CdsEntity> attachmentDraftEntity =
              context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
          String upIdKey = "", upID = "";
          if (attachmentDraftEntity.isPresent()) {
            upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity.get());
            CqnSelect select = (CqnSelect) context.get("cqn");
            upID = SDMUtils.fetchUPIDFromCQN(select, attachmentDraftEntity.get());
            logger.debug("Processing attachments for upID: {}", upID);

            if (!repoValue.getIsAsyncVirusScanEnabled()) {
              logger.debug("Sync virus scan mode: updating in-progress upload status to success");
              dbQuery.updateInProgressUploadStatusToSuccess(
                  attachmentDraftEntity.get(), persistenceService, upID, upIdKey);
            }
            if (repoValue.getIsAsyncVirusScanEnabled()) {
              logger.debug("Async virus scan mode: processing virus scan in-progress attachments");
              processVirusScanInProgressAttachments(context, upID, upIdKey);
            }
          }

          // Get attachment associations to handle deep reads with expand
          CdsModel cdsModel = context.getModel();
          List<String> fieldNames =
              getAttachmentAssociations(cdsModel, context.getTarget(), "", new ArrayList<>());
          logger.debug("Found {} attachment associations", fieldNames.size());

          // Create a combined modifier that handles both expand scenarios and repositoryId filter
          final SDMBeforeReadItemsModifier itemsModifier =
              new SDMBeforeReadItemsModifier(fieldNames);
          final Predicate repositoryFilter =
              CQL.or(CQL.get("repositoryId").eq(repositoryId), CQL.get("repositoryId").isNull());
          logger.debug(
              "Creating CQN modifier with {} field names and repository filter", fieldNames.size());

          CqnSelect modifiedCqn =
              CQL.copy(
                  context.getCqn(),
                  new Modifier() {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    @Override
                    public List items(List items) {
                      // Always handle items for expand scenarios
                      return itemsModifier.items(items);
                    }

                    @Override
                    public Predicate where(Predicate where) {
                      // Always apply repositoryId filter for all reads
                      if (where == null) {
                        return repositoryFilter;
                      }
                      return CQL.and(where, repositoryFilter);
                    }
                  });
          context.setCqn(modifiedCqn);
          logger.debug("CQN query modified with repository filter and required fields");
        } else {
          logger.warn(
              "Repository value is null for repository ID: {}. Proceeding with limited functionality",
              repositoryId);
          context.setCqn(context.getCqn());
        }
      } catch (Exception e) {
        logger.error("Error in SDMReadAttachmentsHandler.processBefore: {}", e.getMessage(), e);
        // Re-throw to maintain error handling behavior
        throw e;
      }

    } else {
      logger.debug(
          "Target entity {} is not a media entity, skipping attachment processing",
          context.getTarget().getQualifiedName());
      context.setCqn(context.getCqn());
    }
    logger.debug("END: Read attachments processing completed");
  }

  /**
   * Recursively get all attachment associations in the entity tree. This is needed to properly
   * handle deep navigation like Books/covers with $expand=statusNav
   */
  private List<String> getAttachmentAssociations(
      CdsModel model, CdsEntity entity, String associationName, List<String> processedEntities) {
    List<String> associationNames = new ArrayList<>();
    if (SDMApplicationHandlerHelper.isMediaEntity(entity)) {
      logger.debug("Found media entity association: {}", associationName);
      associationNames.add(associationName);
    }

    Map<String, CdsEntity> annotatedEntities =
        entity
            .associations()
            .collect(
                Collectors.toMap(
                    CdsElementDefinition::getName,
                    element -> element.getType().as(CdsAssociationType.class).getTarget()));

    if (annotatedEntities.isEmpty()) {
      return associationNames;
    }

    for (Entry<String, CdsEntity> associatedElement : annotatedEntities.entrySet()) {
      if (!associationNames.contains(associatedElement.getKey())
          && !processedEntities.contains(associatedElement.getKey())
          && !Drafts.SIBLING_ENTITY.equals(associatedElement.getKey())) {
        processedEntities.add(associatedElement.getKey());
        List<String> result =
            getAttachmentAssociations(
                model, associatedElement.getValue(), associatedElement.getKey(), processedEntities);
        associationNames.addAll(result);
      }
    }
    return associationNames;
  }

  /**
   * Processes attachment data and sets criticality values based on upload status. Java equivalent
   * of the frontend JavaScript logic. This method will be called after data is read to enhance it
   * with criticality values.
   *
   * @param context the CDS read event context containing attachment data
   */
  private void processVirusScanInProgressAttachments(
      CdsReadEventContext context, String upID, String upIDkey) {
    try {
      logger.debug("START: Processing virus scan in-progress attachments for upID: {}", upID);
      // Get the statuses of existing attachments and assign color code
      // Get all attachments with virus scan in progress
      Optional<CdsEntity> attachmentDraftEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
      Optional<CdsEntity> attachmentActiveEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName());

      List<CmisDocument> attachmentsInProgress =
          dbQuery.getAttachmentsWithVirusScanInProgress(
              attachmentDraftEntity.orElse(null),
              attachmentActiveEntity.orElse(null),
              persistenceService,
              upID,
              upIDkey);
      logger.debug(
          "Found {} attachments with virus scan in progress", attachmentsInProgress.size());

      // Get SDM credentials
      var sdmCredentials = tokenHandler.getSDMCredentials();

      // Iterate through each attachment and call getObject
      for (CmisDocument attachment : attachmentsInProgress) {
        processAttachmentVirusScanStatus(
            attachment,
            sdmCredentials,
            attachmentDraftEntity.orElse(null),
            attachmentActiveEntity.orElse(null),
            persistenceService);
      }

      if (!attachmentsInProgress.isEmpty()) {
        logger.info(
            "Processed {} attachments with virus scan status updates",
            attachmentsInProgress.size());
      }
      logger.debug("END: Process virus scan in-progress attachments");

    } catch (Exception e) {
      logger.error("Error processing virus scan in progress attachments: {}", e.getMessage(), e);
    }
  }

  /**
   * Processes a single attachment to check and update its virus scan status.
   *
   * @param attachment the attachment document to process
   * @param sdmCredentials the SDM credentials for API calls
   * @param attachmentDraftEntity the draft entity for the attachment
   * @param attachmentActiveEntity the active entity for the attachment
   * @param persistenceService the persistence service for database operations
   */
  private void processAttachmentVirusScanStatus(
      CmisDocument attachment,
      SDMCredentials sdmCredentials,
      CdsEntity attachmentDraftEntity,
      CdsEntity attachmentActiveEntity,
      PersistenceService persistenceService) {
    try {
      String objectId = attachment.getObjectId();
      if (objectId != null && !objectId.isEmpty()) {
        logger.debug(
            "Checking virus scan status for objectId: {}, filename: {}",
            objectId,
            attachment.getFileName());

        // Call getObject to check the current state
        JSONObject objectResponse = sdmService.getObject(objectId, sdmCredentials, false);

        if (objectResponse != null) {
          JSONObject succinctProperties = objectResponse.getJSONObject("succinctProperties");
          String currentFileName = succinctProperties.getString("cmis:name");

          // Extract scanStatus if available
          String scanStatus = null;
          if (succinctProperties.has("sap:virusScanStatus")) {
            scanStatus = succinctProperties.getString("sap:virusScanStatus");
            logger.debug("Virus scan status from SDM: {}", scanStatus);
          } else {
            logger.debug("No virus scan status found in SDM response for objectId: {}", objectId);
          }

          logger.debug(
              "Retrieved object for attachmentId: {}, filename: {}, scanStatus: {}",
              attachment.getAttachmentId(),
              currentFileName,
              scanStatus);

          // Update the uploadStatus based on the scan status
          if (scanStatus != null) {
            SDMConstants.ScanStatus scanStatusEnum = SDMConstants.ScanStatus.fromValue(scanStatus);
            dbQuery.updateUploadStatusByScanStatus(
                attachmentDraftEntity,
                attachmentActiveEntity,
                persistenceService,
                objectId,
                scanStatusEnum);
            logger.debug(
                "Updated uploadStatus for objectId: {} based on scanStatus: {}",
                objectId,
                scanStatus);
          }
        } else {
          logger.warn(
              "Object not found for attachmentId: {}, objectId: {}",
              attachment.getAttachmentId(),
              objectId);
        }
      }
    } catch (IOException e) {
      logger.error(
          "Error processing attachment with objectId: {}, error: {}",
          attachment.getObjectId(),
          e.getMessage());
    } catch (Exception e) {
      logger.error(
          "Unexpected error processing attachment with objectId: {}, error: {}",
          attachment.getObjectId(),
          e.getMessage());
    }
  }

  /**
   * Checks the repository type with fallback handling. Returns null if the check fails, allowing
   * the caller to proceed with limited functionality.
   *
   * @param repositoryId the repository ID to check
   * @param context the CDS read event context containing user information
   * @return the RepoValue if successful, null otherwise
   */
  private RepoValue checkRepositoryTypeWithFallback(
      String repositoryId, CdsReadEventContext context) {
    try {
      return sdmService.checkRepositoryType(repositoryId, context.getUserInfo().getTenant());
    } catch (Exception e) {
      logger.warn(
          "Failed to check repository type, proceeding without repository info: {}",
          e.getMessage());
      return null;
    }
  }
}
