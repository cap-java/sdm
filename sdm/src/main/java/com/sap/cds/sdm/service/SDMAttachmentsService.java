package com.sap.cds.sdm.service;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.service.AttachmentModificationResult;
import com.sap.cds.feature.attachments.service.model.service.CreateAttachmentInput;
import com.sap.cds.feature.attachments.service.model.service.MarkAsDeletedInput;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo;
import com.sap.cds.services.ServiceDelegator;
import com.sap.cds.services.request.UserInfo;
import java.io.InputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMAttachmentsService extends ServiceDelegator
    implements AttachmentService, RegisterService {
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsService.class);

  public SDMAttachmentsService() {
    super(SDM_NAME);
  }

  @Override
  public InputStream readAttachment(String contentId) {
    logger.info("Reading attachment with document id: {}", contentId);

    var readContext = AttachmentReadEventContext.create();
    readContext.setContentId(contentId);
    readContext.setData(MediaData.create());

    emit(readContext);

    return readContext.getData().getContent();
  }

  @Override
  public AttachmentModificationResult createAttachment(CreateAttachmentInput input) {
    logger.info(
        "Creating attachment for entity name: {}", input.attachmentEntity().getQualifiedName());
    var createContext = AttachmentCreateEventContext.create();
    createContext.setAttachmentIds(input.attachmentIds());
    createContext.setAttachmentEntity(input.attachmentEntity());
    var mediaData = MediaData.create();
    mediaData.setFileName(input.fileName());
    mediaData.setMimeType(input.mimeType());
    mediaData.setContent(input.content());
    createContext.setData(mediaData);

    emit(createContext);

    return new AttachmentModificationResult(
        Boolean.TRUE.equals(createContext.getIsInternalStored()),
        createContext.getContentId(),
        createContext.getData().getStatus());
  }

  @Override
  public void markAttachmentAsDeleted(MarkAsDeletedInput input) {
    logger.info("Marking attachment as deleted for document id in SDM{}", input.contentId());

    var deleteContext = AttachmentMarkAsDeletedEventContext.create();
    deleteContext.setContentId(input.contentId());
    deleteContext.setDeletionUserInfo(fillDeletionUserInfo(input.userInfo()));

    emit(deleteContext);
  }

  @Override
  public void restoreAttachment(Instant restoreTimestamp) {
    logger.info("Restoring deleted attachment for timestamp: {}", restoreTimestamp);
    var restoreContext = AttachmentRestoreEventContext.create();
    restoreContext.setRestoreTimestamp(restoreTimestamp);

    emit(restoreContext);
  }

  private DeletionUserInfo fillDeletionUserInfo(UserInfo userInfo) {
    var deletionUserInfo = DeletionUserInfo.create();
    deletionUserInfo.setName(userInfo.getName());
    return deletionUserInfo;
  }
}
