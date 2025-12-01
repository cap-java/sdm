package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.List;
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
  public void processBefore(CdsReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      // Fetch all the attachments with uploadStatus VIRUS_SCAN_INPROGRESS and then have a for loop
      // for those entries  and call getObject for individual attachment and update the attachment
      // table with getObject Response

      processVirusScanInProgressAttachments(context);

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

  private void processVirusScanInProgressAttachments(CdsReadEventContext context) {
    try {
      // Get all attachments with virus scan in progress
      List<CmisDocument> attachmentsInProgress =
          dbQuery.getAttachmentsWithVirusScanInProgress(context.getTarget(), persistenceService);

      // Get SDM credentials
      var sdmCredentials = tokenHandler.getSDMCredentials();

      // Iterate through each attachment and call getObject
      for (CmisDocument attachment : attachmentsInProgress) {
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
              if (succinctProperties.has("scanStatus")) {
                scanStatus = succinctProperties.getString("scanStatus");
              }

              logger.info(
                  "Successfully retrieved object for attachmentId: {}, filename: {}, scanStatus: {}",
                  attachment.getAttachmentId(),
                  currentFileName,
                  scanStatus);

              // Update the uploadStatus based on the scan status
              if (scanStatus != null) {
                SDMConstants.ScanStatus scanStatusEnum =
                    SDMConstants.ScanStatus.fromValue(scanStatus);
                dbQuery.updateUploadStatusByScanStatus(
                    context.getTarget(), persistenceService, objectId, scanStatusEnum);
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

      if (!attachmentsInProgress.isEmpty()) {
        logger.info(
            "Processed {} attachments with virus scan in progress", attachmentsInProgress.size());
      }

    } catch (Exception e) {
      logger.error("Error processing virus scan in progress attachments: {}", e.getMessage());
    }
  }
}
