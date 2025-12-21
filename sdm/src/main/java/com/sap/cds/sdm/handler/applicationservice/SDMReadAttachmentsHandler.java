package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsReadEventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      // update the uploadStatus of all blank attachments with success this is for existing
      // attachments
      RepoValue repoValue =
          sdmService.checkRepositoryType(repositoryId, context.getUserInfo().getTenant());
      Optional<CdsEntity> attachmentDraftEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
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
      CqnSelect copy =
          CQL.copy(
              context.getCqn(),
              new Modifier() {
                @Override
                public Predicate where(Predicate where) {
                  return CQL.and(where, CQL.get("repositoryId").eq(repositoryId));
                }
              });
      context.setCqn(copy);

    } else {
      context.setCqn(context.getCqn());
    }
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
