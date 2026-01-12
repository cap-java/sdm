package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.*;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(
    value = "*",
    type = {AttachmentService.class})
public class SDMAttachmentsServiceHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DocumentUploadService documentService;
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsServiceHandler.class);
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

  public SDMAttachmentsServiceHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      DocumentUploadService documentService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.documentService = documentService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) throws IOException {
    logger.info(
        "CREATE_ATTACHMENT Event Received with content length {} At {}",
        (context.getParameterInfo() != null && context.getParameterInfo().getHeaders() != null)
            ? context.getParameterInfo().getHeaders().get("content-length")
            : null,
        System.currentTimeMillis());
    validateRepository(context);
    processEntities(context);
  }

  @On(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
  public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context)
      throws IOException {
    String[] contextValues = context.getContentId().split(":");
    if (contextValues.length > 0 && !(contextValues[0].equalsIgnoreCase("null"))) {
      String objectId = contextValues[0];
      String folderId = contextValues[1];
      String entity = contextValues[2];
      // check if only attachment exists against the folderId
      List<CmisDocument> cmisDocuments =
          dbQuery.getAttachmentsForFolder(entity, persistenceService, folderId, context);
      if (cmisDocuments.isEmpty()) {
        // deleteFolder API
        sdmService.deleteDocument("deleteTree", folderId, context.getDeletionUserInfo().getName());
      } else {
        if (!isObjectIdPresent(cmisDocuments, objectId)) {
          sdmService.deleteDocument("delete", objectId, context.getDeletionUserInfo().getName());
        }
      }
    }
    context.setCompleted();
  }

  @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
  public void restoreAttachment(AttachmentRestoreEventContext context) {
    context.setCompleted();
  }

  @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
  public void readAttachment(AttachmentReadEventContext context) throws IOException {
    String[] contentIdParts = context.getContentId().split(":");
    String objectId = contentIdParts[0];
    String entity = contentIdParts.length > 2 ? contentIdParts[2] : contentIdParts[0];
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    CmisDocument cmisDocument =
        dbQuery.getuploadStatusForAttachment(entity, persistenceService, objectId, context);
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument
            .getUploadStatus()
            .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED))
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_DETECTED_FILE_ERROR"));
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS))
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_SCAN_IN_PROGRESS_FILE_ERROR"));
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS))
      throw new ServiceException(SDMUtils.getErrorMessage("UPLOAD_IN_PROGRESS_FILE_ERROR"));
    try {
      sdmService.readDocument(objectId, sdmCredentials, context);
    } catch (Exception e) {
      throw new ServiceException(e.getMessage());
    }
    context.setCompleted();
  }

  public boolean duplicateCheck(String filename, String fileid, Result result) {

    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream()
            .map(map -> (Map<String, Object>) map)
            .collect(Collectors.toList());

    Map<String, Object> duplicate = null;
    for (Map<String, Object> attachment : resultList) {
      String resultFileName = (String) attachment.get("fileName");
      String resultId = (String) attachment.get("ID");
      String repositoryId = (String) attachment.get("repositoryId");
      // add a check with repositoryId
      if (filename.equals(resultFileName)
          && !fileid.equals(resultId)
          && SDMConstants.REPOSITORY_ID.equals(repositoryId)) {
        duplicate = attachment;
        break;
      }
    }

    return duplicate != null;
  }

  private boolean isObjectIdPresent(List<CmisDocument> documents, String objectId) {
    for (CmisDocument doc : documents) {
      if (objectId.equals(doc.getObjectId())) {
        return true;
      }
    }
    return false;
  }

  private void validateRepository(AttachmentCreateEventContext eventContext)
      throws ServiceException, IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    RepoValue repoValue =
        sdmService.checkRepositoryType(repositoryId, eventContext.getUserInfo().getTenant());
    if (repoValue.getVersionEnabled()) {
      throw new ServiceException(SDMUtils.getErrorMessage("VERSIONED_REPO_ERROR"));
    }
    String len = eventContext.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    // Check if repository is virus scanned
    if (!repoValue.getIsAsyncVirusScanEnabled()
        && repoValue.getVirusScanEnabled()
        && contentLen > 400 * 1024 * 1024
        && !repoValue.getDisableVirusScannerForLargeFile()) {
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_REPO_ERROR_MORE_THAN_400MB"));
    }
  }

  private void processEntities(AttachmentCreateEventContext eventContext)
      throws ServiceException, IOException {

    Map<String, Object> attachmentIds = eventContext.getAttachmentIds();
    CdsEntity attachmentDraftEntity = getAttachmentDraftEntity(eventContext);
    String upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity);
    String upID = (String) attachmentIds.get(upIdKey);

    Result result =
        dbQuery.getAttachmentsForUPID(attachmentDraftEntity, persistenceService, upID, upIdKey);
    checkAttachmentConstraints(eventContext, attachmentDraftEntity, upID, upIdKey);

    MediaData data = eventContext.getData();
    validateFileName(data.getFileName(), result, attachmentIds);
    createDocumentInSDM(data, result, eventContext, attachmentIds, upIdKey, upID);
  }

  private CdsEntity getAttachmentDraftEntity(AttachmentCreateEventContext eventContext) {
    CdsModel model = eventContext.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(eventContext.getAttachmentEntity() + "_drafts");
    return attachmentDraftEntity.orElseThrow(
        () -> new ServiceException(SDMUtils.getErrorMessage("DRAFT_NOT_FOUND")));
  }

  private void checkAttachmentConstraints(
      AttachmentCreateEventContext eventContext,
      CdsEntity attachmentDraftEntity,
      String upID,
      String upIdKey)
      throws ServiceException {
    // Fetch the row count for current repository
    Result result =
        dbQuery.getAttachmentsForUPIDAndRepository(
            attachmentDraftEntity, persistenceService, upID, upIdKey);
    long rowCount = result.rowCount();
    Long maxCount =
        SDMUtils.getAttachmentCountAndMessage(
            eventContext.getModel().entities().toList(), eventContext.getAttachmentEntity());
    if (maxCount > 0 && rowCount >= maxCount) {
      throw new ServiceException(
          String.format(SDMUtils.getErrorMessage("MAX_COUNT_ERROR_MESSAGE"), maxCount.toString()));
    }
  }

  private void validateFileName(String filename, Result result, Map<String, Object> attachmentIds)
      throws ServiceException {
    if (filename == null || filename.isBlank()) {
      throw new ServiceException(SDMUtils.getErrorMessage("FILENAME_WHITESPACE_ERROR_MESSAGE"));
    }
    if (SDMUtils.hasRestrictedCharactersInName(filename)) {
      throw new ServiceException(
          SDMErrorMessages.nameConstraintMessage(Collections.singletonList(filename)));
    }
    String fileid = (String) attachmentIds.get("ID");
    if (duplicateCheck(filename, fileid, result)) {
      throw new ServiceException(SDMErrorMessages.getDuplicateFilesError(filename));
    }
  }

  private void createDocumentInSDM(
      MediaData data,
      Result result,
      AttachmentCreateEventContext eventContext,
      Map<String, Object> attachmentIds,
      String upIdKey,
      String upID)
      throws ServiceException, IOException {

    CmisDocument cmisDocument = new CmisDocument();
    Boolean isSystemUser = eventContext.getUserInfo().isSystemUser();
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String entityName = eventContext.getAttachmentEntity().getQualifiedName().split("\\.")[2];
    String folderName = upID + "__" + entityName;
    String folderId = sdmService.getFolderId(result, persistenceService, folderName, isSystemUser);
    String len = eventContext.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    setCmisDocumentProperties(
        cmisDocument, data, attachmentIds, folderId, repositoryId, upIdKey, contentLen);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    JSONObject createResult = null;
    try {
      createResult =
          documentService.createDocument(
              cmisDocument, sdmCredentials, isSystemUser, eventContext, persistenceService);
      logger.info("Synchronous Response from documentService: {}", createResult);
      logger.info("Upload Finished at: {}", System.currentTimeMillis());
    } catch (Exception e) {
      logger.error("Error in documentService: \n{}", Arrays.toString(e.getStackTrace()));
      throw new ServiceException(
          SDMErrorMessages.getGenericError(AttachmentService.EVENT_CREATE_ATTACHMENT), e);
    }
    logger.info("Synchronous Response from documentService: {}", createResult);
    logger.info("Upload Finished at: {}", System.currentTimeMillis());
    handleCreateDocumentResult(cmisDocument, createResult, eventContext);
  }

  private void setCmisDocumentProperties(
      CmisDocument cmisDocument,
      MediaData data,
      Map<String, Object> attachmentIds,
      String folderId,
      String repositoryId,
      String upIdKey,
      long contentlen) {
    cmisDocument.setFileName(data.getFileName());
    cmisDocument.setAttachmentId((String) attachmentIds.get("ID"));
    cmisDocument.setContent((InputStream) data.get("content"));
    cmisDocument.setParentId((String) attachmentIds.get(upIdKey));
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setFolderId(folderId);
    cmisDocument.setMimeType((String) data.get("mimeType"));
    cmisDocument.setContentLength(contentlen);
  }

  private void handleCreateDocumentResult(
      CmisDocument cmisDocument, JSONObject createResult, AttachmentCreateEventContext eventContext)
      throws ServiceException {
    String status = createResult.get("status").toString();

    switch (status) {
      case "duplicate":
        Object[] duplicatemessage = new Object[1];
        duplicatemessage[0] = cmisDocument.getFileName();
        throw new ServiceException(
            String.format(
                SDMUtils.getErrorMessage("SINGLE_DUPLICATE_FILENAME"),
                duplicatemessage[0].toString()));
      case "virus":
        Object[] message = new Object[1];
        message[0] = cmisDocument.getFileName();
        throw new ServiceException(SDMErrorMessages.getVirusFilesError(message[0].toString()));

      case "fail":
        throw new ServiceException(createResult.get("message").toString());
      case "unauthorized":
        throw new ServiceException(SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR"));
      case "blocked":
        throw new ServiceException(SDMUtils.getErrorMessage("MIMETYPE_INVALID_ERROR"));
      default:
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        cmisDocument.setUploadStatus(
            (createResult.get("uploadStatus") != null)
                ? createResult.get("uploadStatus").toString()
                : SDMConstants.UPLOAD_STATUS_IN_PROGRESS);
        dbQuery.addAttachmentToDraft(
            getAttachmentDraftEntity(eventContext), persistenceService, cmisDocument);
        finalizeContext(eventContext, cmisDocument);
    }
  }

  private void finalizeContext(
      AttachmentCreateEventContext eventContext, CmisDocument cmisDocument) {
    eventContext.setContentId(
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + eventContext.getAttachmentEntity().getQualifiedName());
    eventContext.getData().setStatus("Clean");
    eventContext.getData().setContent(null);
    eventContext.setCompleted();
  }
}
