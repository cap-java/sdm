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
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.model.MoveAttachmentInput;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import com.sap.cds.services.ServiceDelegator;
import com.sap.cds.services.request.UserInfo;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMAttachmentsService extends ServiceDelegator
    implements AttachmentService, RegisterService {
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsService.class);

  public SDMAttachmentsService() {
    super(SDM_NAME);
  }

  @Override
  public void copyAttachments(CopyAttachmentInput input, boolean isSystemUser) {
    logger.info(
        "Copying attachments for upId: {}, facet: {}, objectIds: {}, isSystemUser: {}",
        input.upId(),
        input.facet(),
        input.objectIds(),
        isSystemUser);

    // Parse facet to extract parent entity and composition name
    String[] facetParts = input.facet().split("\\.");
    if (facetParts.length < 3) {
      throw new IllegalArgumentException(
          String.format(SDMConstants.INVALID_FACET_FORMAT_ERROR, input.facet()));
    }

    String parentEntity = facetParts[0] + "." + facetParts[1]; // Service.Entity
    String compositionName = facetParts[2]; // composition name

    var copyContext = AttachmentCopyEventContext.create();
    copyContext.setUpId(input.upId());
    copyContext.setParentEntity(parentEntity);
    copyContext.setCompositionName(compositionName);
    copyContext.setObjectIds(input.objectIds());
    copyContext.setSystemUser(isSystemUser);

    emit(copyContext);
  }

  @Override
  public Map<String, Object> moveAttachments(MoveAttachmentInput input, boolean isSystemUser) {
    logger.info(
        "Moving attachments from sourceFolderId: {} (sourceFacet: {}) to upId: {}, targetFacet:"
            + " {}, objectIds: {}, isSystemUser: {}",
        input.sourceFolderId(),
        input.sourceFacet(),
        input.targetUpId(),
        input.targetFacet(),
        input.objectIds(),
        isSystemUser);

    // Parse target facet to extract parent entity and composition name
    String[] targetFacetParts = input.targetFacet().split("\\.");
    if (targetFacetParts.length < 3) {
      throw new IllegalArgumentException(
          String.format(SDMConstants.INVALID_FACET_FORMAT_ERROR, input.targetFacet()));
    }

    String targetParentEntity = targetFacetParts[0] + "." + targetFacetParts[1]; // Service.Entity
    String targetCompositionName = targetFacetParts[2]; // composition name

    // Parse source facet to extract source entity information for cleanup
    String sourceParentEntity = null;
    String sourceCompositionName = null;
    if (input.sourceFacet() != null && !input.sourceFacet().isEmpty()) {
      String[] sourceFacetParts = input.sourceFacet().split("\\.");
      if (sourceFacetParts.length >= 3) {
        sourceParentEntity = sourceFacetParts[0] + "." + sourceFacetParts[1]; // Service.Entity
        sourceCompositionName = sourceFacetParts[2]; // composition name
      }
    }

    var moveContext = AttachmentMoveEventContext.create();
    moveContext.setSourceFolderId(input.sourceFolderId());
    moveContext.setSourceParentEntity(sourceParentEntity);
    moveContext.setSourceCompositionName(sourceCompositionName);
    moveContext.setUpId(input.targetUpId());
    moveContext.setParentEntity(targetParentEntity);
    moveContext.setCompositionName(targetCompositionName);
    moveContext.setObjectIds(input.objectIds());
    moveContext.setSystemUser(isSystemUser);

    emit(moveContext);

    // Get the failed attachments and return them in a structured format
    List<Map<String, String>> failedAttachments = moveContext.getFailedAttachments();
    if (failedAttachments != null && !failedAttachments.isEmpty()) {
      logger.warn("Move operation completed with {} failed attachments", failedAttachments.size());
      for (Map<String, String> failure : failedAttachments) {
        logger.warn(
            "  - ObjectId: {}, Reason: {}", failure.get("objectId"), failure.get("failureReason"));
      }
    } else {
      logger.info(
          "Move operation completed successfully for all {} attachments", input.objectIds().size());
    }

    // Return structured result that OData can serialize
    Map<String, Object> result = new HashMap<>();
    result.put("failedAttachments", failedAttachments != null ? failedAttachments : List.of());
    return result;
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
