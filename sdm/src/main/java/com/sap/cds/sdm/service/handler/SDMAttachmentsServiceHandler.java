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
    long startTime = System.currentTimeMillis();
    String contentLength =
        (context.getParameterInfo() != null && context.getParameterInfo().getHeaders() != null)
            ? context.getParameterInfo().getHeaders().get("content-length")
            : null;
    logger.info(
        "CREATE_ATTACHMENT Event Received with content length {} At {}", contentLength, startTime);
    logger.debug(
        "User: {}, Tenant: {}", context.getUserInfo().getId(), context.getUserInfo().getTenant());

    validateRepository(context);
    logger.debug("Repository validation completed");

    processEntities(context);
    long endTime = System.currentTimeMillis();
    logger.info("CREATE_ATTACHMENT completed successfully in {} ms", (endTime - startTime));
  }

  @On(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
  public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context)
      throws IOException {
    String contentId = context.getContentId();
    logger.debug("START: Mark attachment as deleted with contentId: {}", contentId);
    String[] contextValues = context.getContentId().split(":");
    if (contextValues.length > 0 && !(contextValues[0].equalsIgnoreCase("null"))) {
      String objectId = contextValues[0];
      String folderId = contextValues[1];
      String entity = contextValues[2];
      logger.debug(
          "Processing deletion - objectId: {}, folderId: {}, entity: {}",
          objectId,
          folderId,
          entity);

      // check if only attachment exists against the folderId
      List<CmisDocument> cmisDocuments =
          dbQuery.getAttachmentsForFolder(entity, persistenceService, folderId, context);
      logger.debug("Found {} attachments for folder: {}", cmisDocuments.size(), folderId);

      if (cmisDocuments.isEmpty()) {
        // deleteFolder API
        logger.info("Deleting folder: {} for entity: {}", folderId, entity);
        sdmService.deleteDocument("deleteTree", folderId, context.getDeletionUserInfo().getName());
        logger.info("Folder deleted successfully: {}", folderId);
      } else {
        if (!isObjectIdPresent(cmisDocuments, objectId)) {
          logger.info("Deleting document: {} from repository", objectId);
          sdmService.deleteDocument("delete", objectId, context.getDeletionUserInfo().getName());
          logger.info("Document deleted successfully: {}", objectId);
        } else {
          logger.debug("ObjectId {} is still referenced, not deleting", objectId);
        }
      }
    } else {
      logger.warn("Invalid contentId format for deletion: {}", contentId);
    }
    context.setCompleted();
    logger.debug("END: Mark attachment as deleted");
  }

  @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
  public void restoreAttachment(AttachmentRestoreEventContext context) {
    logger.debug("Restore attachment event received - marking as completed");
    context.setCompleted();
  }

  @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
  public void readAttachment(AttachmentReadEventContext context) throws IOException {
    logger.debug("START: Read attachment");
    long startTime = System.currentTimeMillis();
    String[] contentIdParts = context.getContentId().split(":");
    String objectId = contentIdParts[0];
    String entity = contentIdParts.length > 2 ? contentIdParts[2] : contentIdParts[0];
    logger.debug("Reading attachment - objectId: {}, entity: {}", objectId, entity);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    CmisDocument cmisDocument =
        dbQuery.getuploadStatusForAttachment(entity, persistenceService, objectId, context);
    logger.debug("Attachment upload status: {}", cmisDocument.getUploadStatus());

    if (cmisDocument.getUploadStatus() != null
        && cmisDocument
            .getUploadStatus()
            .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED)) {
      logger.warn("Virus detected in attachment: {}", objectId);
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_DETECTED_FILE_ERROR"));
    }
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS)) {
      logger.warn("Virus scan is in progress for attachment: {}", objectId);
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_SCAN_IN_PROGRESS_FILE_ERROR"));
    }
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument
            .getUploadStatus()
            .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS)) {
      logger.warn("Upload is in progress for attachment: {}", objectId);
      throw new ServiceException(SDMUtils.getErrorMessage("UPLOAD_IN_PROGRESS_FILE_ERROR"));
    }
    try {
      logger.info("Initiating document read from repository for objectId: {}", objectId);
      sdmService.readDocument(objectId, sdmCredentials, context);
      logger.info(
          "Document read completed for objectId: {} in {} ms",
          objectId,
          (System.currentTimeMillis() - startTime));
    } catch (Exception e) {
      logger.error("Error reading document {} from repository: {}", objectId, e.getMessage(), e);
      throw new ServiceException(e.getMessage());
    }
    context.setCompleted();
    logger.debug("END: Read attachment");
  }

  public boolean duplicateCheck(String filename, String fileid, Result result) {
    logger.debug("Checking for duplicate fileName: {} with ID: {}", filename, fileid);

    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream()
            .map(map -> (Map<String, Object>) map)
            .collect(Collectors.toList());
    logger.debug("Checking against {} existing attachments", resultList.size());

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

    boolean isDuplicate = duplicate != null;
    logger.debug("Duplicate check result for {}: {}", filename, isDuplicate);
    return isDuplicate;
  }

  private boolean isObjectIdPresent(List<CmisDocument> documents, String objectId) {
    logger.debug("Checking if objectId {} exists in {} documents", objectId, documents.size());
    for (CmisDocument doc : documents) {
      if (objectId.equals(doc.getObjectId())) {
        logger.debug("ObjectId {} found in documents", objectId);
        return true;
      }
    }
    logger.debug("ObjectId {} not found in documents", objectId);
    return false;
  }

  private void validateRepository(AttachmentCreateEventContext eventContext)
      throws ServiceException, IOException {
    logger.debug("START: Validate repository");
    String repositoryId = SDMConstants.REPOSITORY_ID;
    logger.debug("Checking repository type for: {}", repositoryId);
    RepoValue repoValue =
        sdmService.checkRepositoryType(repositoryId, eventContext.getUserInfo().getTenant());

    if (repoValue.getVersionEnabled()) {
      logger.warn("Repository is versioned which is not allowed: {}", repositoryId);
      throw new ServiceException(SDMUtils.getErrorMessage("VERSIONED_REPO_ERROR"));
    }

    String len = eventContext.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    logger.debug(
        "Content length: {} bytes, Async virus scan enabled: {}, Virus scan enabled: {}",
        contentLen,
        repoValue.getIsAsyncVirusScanEnabled(),
        repoValue.getVirusScanEnabled());

    // Check if repository is virus scanned
    if (!repoValue.getIsAsyncVirusScanEnabled()
        && repoValue.getVirusScanEnabled()
        && contentLen > 400 * 1024 * 1024
        && !repoValue.getDisableVirusScannerForLargeFile()) {
      logger.warn("File size exceeds 400MB and synchronous virus scan is enabled");
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_REPO_ERROR_MORE_THAN_400MB"));
    }
    logger.debug("END: Repository validation successful");
  }

  private void processEntities(AttachmentCreateEventContext eventContext)
      throws ServiceException, IOException {
    logger.debug("START: Process entities for attachment creation");

    Map<String, Object> attachmentIds = eventContext.getAttachmentIds();
    CdsEntity attachmentDraftEntity = getAttachmentDraftEntity(eventContext);
    String upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity);
    String upID = (String) attachmentIds.get(upIdKey);
    logger.debug("Processing attachments for upID: {} with key: {}", upID, upIdKey);

    Result result =
        dbQuery.getAttachmentsForUPID(attachmentDraftEntity, persistenceService, upID, upIdKey);
    checkAttachmentConstraints(eventContext, attachmentDraftEntity, upID, upIdKey);

    MediaData data = eventContext.getData();
    logger.debug("Attachment fileName: {}", data.getFileName());
    validateFileName(data.getFileName(), result, attachmentIds);
    createDocumentInSDM(data, result, eventContext, attachmentIds, upIdKey, upID);
    logger.debug("END: Process entities");
  }

  private CdsEntity getAttachmentDraftEntity(AttachmentCreateEventContext eventContext) {
    CdsModel model = eventContext.getModel();
    String draftEntityName = eventContext.getAttachmentEntity() + "_drafts";
    logger.debug("Looking for attachment draft entity: {}", draftEntityName);
    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(draftEntityName);
    return attachmentDraftEntity.orElseThrow(
        () -> {
          logger.error("Draft entity not found: {}", draftEntityName);
          return new ServiceException(SDMUtils.getErrorMessage("DRAFT_NOT_FOUND"));
        });
  }

  private void checkAttachmentConstraints(
      AttachmentCreateEventContext eventContext,
      CdsEntity attachmentDraftEntity,
      String upID,
      String upIdKey)
      throws ServiceException {
    logger.debug("START: Check attachment constraints for upID: {}", upID);
    // Fetch the row count for current repository
    Result result =
        dbQuery.getAttachmentsForUPIDAndRepository(
            attachmentDraftEntity, persistenceService, upID, upIdKey);
    long rowCount = result.rowCount();
    Long maxCount =
        SDMUtils.getAttachmentCountAndMessage(
            eventContext.getModel().entities().toList(), eventContext.getAttachmentEntity());

    logger.debug("Current attachment count: {}, Max allowed count: {}", rowCount, maxCount);
    if (maxCount > 0 && rowCount >= maxCount) {
      logger.warn("Attachment count exceeds maximum limit: {} >= {}", rowCount, maxCount);
      throw new ServiceException(
          String.format(SDMUtils.getErrorMessage("MAX_COUNT_ERROR_MESSAGE"), maxCount.toString()));
    }
    logger.debug("END: Attachment constraints validation passed");
  }

  private void validateFileName(String filename, Result result, Map<String, Object> attachmentIds)
      throws ServiceException {
    logger.debug("Validating fileName: {}", filename);
    if (filename == null || filename.isBlank()) {
      logger.error("Invalid fileName: empty or null");
      throw new ServiceException(SDMUtils.getErrorMessage("FILENAME_WHITESPACE_ERROR_MESSAGE"));
    }
    if (SDMUtils.hasRestrictedCharactersInName(filename)) {
      logger.warn("FileName contains restricted characters: {}", filename);
      throw new ServiceException(
          SDMErrorMessages.nameConstraintMessage(Collections.singletonList(filename)));
    }
    String fileid = (String) attachmentIds.get("ID");
    if (duplicateCheck(filename, fileid, result)) {
      logger.warn("Duplicate fileName detected: {}", filename);
      throw new ServiceException(SDMErrorMessages.getDuplicateFilesError(filename));
    }
    logger.debug("fileName validation passed");
  }

  private void createDocumentInSDM(
      MediaData data,
      Result result,
      AttachmentCreateEventContext eventContext,
      Map<String, Object> attachmentIds,
      String upIdKey,
      String upID)
      throws ServiceException, IOException {
    logger.debug("START: Create document in SDM for attachment");
    long startTime = System.currentTimeMillis();

    CmisDocument cmisDocument = new CmisDocument();
    Boolean isSystemUser = eventContext.getUserInfo().isSystemUser();
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String entityName = eventContext.getAttachmentEntity().getQualifiedName().split("\\.")[2];
    String folderName = upID + "__" + entityName;
    logger.debug(
        "Creating folder structure - folderName: {}, isSystemUser: {}", folderName, isSystemUser);

    String folderId = sdmService.getFolderId(result, persistenceService, folderName, isSystemUser);
    logger.debug("Obtained folderId: {} for folder: {}", folderId, folderName);

    String len = eventContext.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    setCmisDocumentProperties(
        cmisDocument, data, attachmentIds, folderId, repositoryId, upIdKey, contentLen);
    logger.debug(
        "CMIS document properties set - fileName: {}, contentLength: {}",
        data.getFileName(),
        contentLen);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    JSONObject createResult = null;
    try {
      logger.info("Initiating document creation in SDM repository");
      createResult =
          documentService.createDocument(cmisDocument, sdmCredentials, isSystemUser, eventContext);
      logger.debug(
          "Document creation response received: {}",
          createResult != null ? createResult.toString() : "null");
    } catch (Exception e) {
      logger.error("Error creating document in SDM service: {}", e.getMessage(), e);
      throw new ServiceException(
          SDMErrorMessages.getGenericError(AttachmentService.EVENT_CREATE_ATTACHMENT), e);
    }

    logger.info(
        "Upload Finished at: {} (duration: {} ms)",
        System.currentTimeMillis(),
        (System.currentTimeMillis() - startTime));
    handleCreateDocumentResult(cmisDocument, createResult, eventContext);
    logger.debug("END: Create document in SDM");
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
    logger.debug("Document creation result status: {}", status);

    switch (status) {
      case "duplicate":
        logger.warn("Duplicate document detected: {}", cmisDocument.getFileName());
        Object[] duplicatemessage = new Object[1];
        duplicatemessage[0] = cmisDocument.getFileName();
        throw new ServiceException(
            String.format(
                SDMUtils.getErrorMessage("SINGLE_DUPLICATE_FILENAME"),
                duplicatemessage[0].toString()));
      case "virus":
        logger.error("Virus detected in document: {}", cmisDocument.getFileName());
        Object[] message = new Object[1];
        message[0] = cmisDocument.getFileName();
        throw new ServiceException(SDMErrorMessages.getVirusFilesError(message[0].toString()));

      case "fail":
        logger.error("Document creation failed: {}", createResult.get("message"));
        throw new ServiceException(createResult.get("message").toString());
      case "unauthorized":
        logger.error("User not authorized to upload document");
        throw new ServiceException(SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR"));
      case "blocked":
        logger.warn("Document MIME type is blocked: {}", cmisDocument.getMimeType());
        throw new ServiceException(SDMUtils.getErrorMessage("MIMETYPE_INVALID_ERROR"));
      default:
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        cmisDocument.setUploadStatus(
            (createResult.get("uploadStatus") != null)
                ? createResult.get("uploadStatus").toString()
                : SDMConstants.UPLOAD_STATUS_IN_PROGRESS);
        logger.info(
            "Document created successfully with objectId: {} and status: {}",
            cmisDocument.getObjectId(),
            cmisDocument.getUploadStatus());
        dbQuery.addAttachmentToDraft(
            getAttachmentDraftEntity(eventContext), persistenceService, cmisDocument);
        finalizeContext(eventContext, cmisDocument);
    }
  }

  private void finalizeContext(
      AttachmentCreateEventContext eventContext, CmisDocument cmisDocument) {
    logger.debug("Finalizing attachment context for objectId: {}", cmisDocument.getObjectId());
    eventContext.setContentId(
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + eventContext.getAttachmentEntity().getQualifiedName());
    eventContext.getData().setStatus("Clean");
    eventContext.getData().setContent(null);
    eventContext.setCompleted();
    logger.debug("Attachment context finalized and marked as completed");
  }
}
