package com.sap.cds.handler;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMAttachmentsHandler implements EventHandler {
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsHandler.class);

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) {
    logger.info(
        "Default Attachment Service handler called for creating attachment for entity name: {}",
        context.getAttachmentEntity().getQualifiedName());
    logger.info("Other details: {}", context.getAttachmentIds());
    logger.info("Other details entity: {}", context.getAttachmentEntity());
  }

  @On(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
  public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context) {}

  @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
  public void restoreAttachment(AttachmentRestoreEventContext context) {}

  @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
  public void readAttachment(AttachmentReadEventContext context) {}

  public String performAction() {
    return "Action Performed";
  }
}
