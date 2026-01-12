package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElementDefinition;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
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

  @Before
  @HandlerOrder(HandlerOrder.EARLY + 500)
  public void processBefore(CdsReadEventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      try {
        // update the uploadStatus of all blank attachments with success this is for existing
        // attachments
        RepoValue repoValue =
            sdmService.checkRepositoryType(repositoryId, context.getUserInfo().getTenant());
        Optional<CdsEntity> attachmentDraftEntity =
            context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");

        if (attachmentDraftEntity.isPresent()) {
          String upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity.get());
          CqnSelect select = (CqnSelect) context.get("cqn");
          String upID = SDMUtils.fetchUPIDFromCQN(select, attachmentDraftEntity.get());

          if (!repoValue.getIsAsyncVirusScanEnabled()) {
            dbQuery.updateInProgressUploadStatusToSuccess(
                attachmentDraftEntity.get(), persistenceService, upID, upIdKey);
          }
          if (repoValue.getIsAsyncVirusScanEnabled()) {
            processVirusScanInProgressAttachments(context, upID, upIdKey);
          }
        }

        // Get attachment associations to handle deep reads with expand
        CdsModel cdsModel = context.getModel();
        List<String> fieldNames =
            getAttachmentAssociations(cdsModel, context.getTarget(), "", new ArrayList<>());

        // Use the new modifier to handle expand scenarios
        CqnSelect modifiedCqn =
            CQL.copy(context.getCqn(), new SDMBeforeReadItemsModifier(fieldNames));

        // Only add repositoryId filter if this is a collection read (no keys specified)
        CqnSelect select = (CqnSelect) context.get("cqn");
        boolean hasKeys = select.ref() != null && select.ref().rootSegment().filter() != null;

        if (!hasKeys) {
          // Apply repositoryId filter for collection reads
          modifiedCqn =
              CQL.copy(
                  modifiedCqn,
                  new Modifier() {
                    @Override
                    public Predicate where(Predicate where) {
                      return CQL.and(where, CQL.get("repositoryId").eq(repositoryId));
                    }
                  });
        }

        context.setCqn(modifiedCqn);
      } catch (Exception e) {
        logger.error("Error in SDMReadAttachmentsHandler.processBefore: {}", e.getMessage(), e);
        // Re-throw to maintain error handling behavior
        throw e;
      }
    }
  }

  /**
   * Recursively get all attachment associations in the entity tree. This is needed to properly
   * handle deep navigation like Books/covers with $expand=statusNav
   */
  private List<String> getAttachmentAssociations(
      CdsModel model, CdsEntity entity, String associationName, List<String> processedEntities) {
    List<String> associationNames = new ArrayList<>();
    if (SDMApplicationHandlerHelper.isMediaEntity(entity)) {
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
      // Get the statuses of existing attachments and assign color code
      // Get all attachments with virus scan in progress
      Optional<CdsEntity> attachmentDraftEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");

      List<CmisDocument> attachmentsInProgress =
          dbQuery.getAttachmentsWithVirusScanInProgress(
              attachmentDraftEntity.get(), persistenceService, upID, upIDkey);

      // Get SDM credentials
      var sdmCredentials = tokenHandler.getSDMCredentials();

      // Iterate through each attachment and call getObject
      for (CmisDocument attachment : attachmentsInProgress) {
        processAttachmentVirusScanStatus(
            attachment, sdmCredentials, attachmentDraftEntity.get(), persistenceService);
      }

      if (!attachmentsInProgress.isEmpty()) {
        logger.info(
            "Processed {} attachments with virus scan in progress", attachmentsInProgress.size());
      }

    } catch (Exception e) {
      logger.error("Error processing virus scan in progress attachments: {}", e.getMessage());
    }
  }

  /**
   * Processes a single attachment to check and update its virus scan status.
   *
   * @param attachment the attachment document to process
   * @param sdmCredentials the SDM credentials for API calls
   * @param attachmentDraftEntity the draft entity for the attachment
   * @param persistenceService the persistence service for database operations
   */
  private void processAttachmentVirusScanStatus(
      CmisDocument attachment,
      SDMCredentials sdmCredentials,
      CdsEntity attachmentDraftEntity,
      PersistenceService persistenceService) {
    try {
      String objectId = attachment.getObjectId();
      if (objectId != null && !objectId.isEmpty()) {
        logger.info(
            "Processing attachment with objectId: {} and filename: {}",
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
          }

          logger.info(
              "Successfully retrieved object for attachmentId: {}, filename: {}, scanStatus: {}",
              attachment.getAttachmentId(),
              currentFileName,
              scanStatus);

          // Update the uploadStatus based on the scan status
          if (scanStatus != null) {
            SDMConstants.ScanStatus scanStatusEnum = SDMConstants.ScanStatus.fromValue(scanStatus);
            Result r =
                dbQuery.updateUploadStatusByScanStatus(
                    attachmentDraftEntity, persistenceService, objectId, scanStatusEnum);
            logger.info(
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
}
