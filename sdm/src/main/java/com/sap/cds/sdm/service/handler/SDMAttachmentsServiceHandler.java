package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.*;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = AttachmentService.class)
public class SDMAttachmentsServiceHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DocumentUploadService documentService;
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsServiceHandler.class);

  public SDMAttachmentsServiceHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      DocumentUploadService documentService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.documentService = documentService;
  }

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) throws IOException {
    logger.info(
        "CREATE_ATTACHMENT Event Received with content length "
            + context.getParameterInfo().getHeaders().get("content-length")
            + " At "
            + System.currentTimeMillis());
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
      String userEmail = context.getDeletionUserInfo().getName();
      String entity = contextValues[2];
      String subdomain = contextValues[3];
      // check if only attachment exists against the folderId
      List<CmisDocument> cmisDocuments =
          DBQuery.getAttachmentsForFolder(entity, persistenceService, folderId, context);
      if (cmisDocuments.isEmpty()) {
        // deleteFolder API
        sdmService.deleteDocument("deleteTree", folderId, userEmail, subdomain);
      } else {
        if (!isObjectIdPresent(cmisDocuments, objectId)) {
          sdmService.deleteDocument("delete", objectId, userEmail, subdomain);
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
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    String[] contentIdParts = context.getContentId().split(":");
    String objectId = contentIdParts[0];
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    try {
      sdmService.readDocument(objectId, jwtToken, sdmCredentials, context);
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
    JwtTokenAuthenticationInfo jwtTokenInfo =
        eventContext.getAuthenticationInfo().as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String repocheck = sdmService.checkRepositoryType(jwtToken, repositoryId);
    if (SDMConstants.REPOSITORY_VERSIONED.equals(repocheck)) {
      throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
    }
  }

  private void processEntities(AttachmentCreateEventContext eventContext)
      throws ServiceException, IOException {
    Map<String, Object> attachmentIds = eventContext.getAttachmentIds();
    CdsEntity attachmentDraftEntity = getAttachmentDraftEntity(eventContext);
    String upIdKey = getUpIdKey(attachmentDraftEntity);
    String upID = (String) attachmentIds.get(upIdKey);

    Result result =
        DBQuery.getAttachmentsForUPID(attachmentDraftEntity, persistenceService, upID, upIdKey);
    checkAttachmentConstraints(eventContext, result);

    MediaData data = eventContext.getData();
    validateFileName(data.getFileName(), result, attachmentIds);
    createDocumentInSDM(data, result, eventContext, attachmentIds, upIdKey, upID);
  }

  private CdsEntity getAttachmentDraftEntity(AttachmentCreateEventContext eventContext) {
    CdsModel model = eventContext.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(eventContext.getAttachmentEntity() + "_drafts");
    return attachmentDraftEntity.orElseThrow(
        () -> new ServiceException(SDMConstants.DRAFT_NOT_FOUND));
  }

  private String getUpIdKey(CdsEntity attachmentDraftEntity) {
    String upIdKey = "";
    Optional<CdsElement> upAssociation = attachmentDraftEntity.findAssociation("up_");
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      // get association type
      CdsAssociationType assocType = association.getType();
      // get the refs of the association
      List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
      upIdKey = fkElements.get(0);
    }
    return upIdKey;
  }

  private void checkAttachmentConstraints(AttachmentCreateEventContext eventContext, Result result)
      throws ServiceException {
    long rowCount = result.rowCount();
    String errorMessageCount =
        SDMUtils.getAttachmentCountAndMessage(
            eventContext.getModel().entities().toList(), eventContext.getAttachmentEntity());

    String[] maxCountArr = errorMessageCount.split("__");
    long maxCount = Long.parseLong(maxCountArr[0]);
    if (maxCount > 0 && rowCount > maxCount) {
      String message = maxCountArr[1];
      if (message != null && !"null".equalsIgnoreCase(message)) {
        throw new ServiceException(message);
      }
      throw new ServiceException(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, maxCount));
    }
  }

  private void validateFileName(String filename, Result result, Map<String, Object> attachmentIds)
      throws ServiceException {
    if (SDMUtils.isRestrictedCharactersInName(filename)) {
      throw new ServiceException(
          SDMConstants.nameConstraintMessage(Collections.singletonList(filename), "Upload"));
    }
    String fileid = (String) attachmentIds.get("ID");
    if (duplicateCheck(filename, fileid, result)) {
      throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
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
    String jwtToken =
        eventContext.getAuthenticationInfo().as(JwtTokenAuthenticationInfo.class).getToken();
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String entityName = eventContext.getAttachmentEntity().getQualifiedName().split("\\.")[2];
    String folderName = upID + "__" + entityName;
    String folderId = sdmService.getFolderId(result, persistenceService, folderName, jwtToken);
    String len = eventContext.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    setCmisDocumentProperties(
        cmisDocument, data, attachmentIds, folderId, repositoryId, upIdKey, contentLen);

    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    JSONObject createResult = null;
    try {
      createResult =
          documentService.createDocument(cmisDocument, sdmCredentials, jwtToken);
      logger.info("Synchronous Response from documentServiceRx: " + createResult.toString());
      logger.info("Upload Finished at: " + System.currentTimeMillis());
    } catch (Exception e) {
      logger.error("Error in documentServiceRx: \n" + Arrays.toString(e.getStackTrace()));
      throw new ServiceException(
          SDMConstants.getGenericError(AttachmentService.EVENT_CREATE_ATTACHMENT), e);
    }
    logger.info("Synchronous Response from documentServiceRx: " + createResult.toString());
    logger.info("Upload Finished at: " + System.currentTimeMillis());
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
        throw new ServiceException(SDMConstants.getDuplicateFilesError(cmisDocument.getFileName()));
      case "virus":
        throw new ServiceException(SDMConstants.getVirusFilesError(cmisDocument.getFileName()));
      case "fail":
        throw new ServiceException(createResult.get("message").toString());
      default:
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        addAttachmentToDraft(
            getAttachmentDraftEntity(eventContext), persistenceService, cmisDocument);
        finalizeContext(eventContext, cmisDocument);
    }
  }

  private void finalizeContext(
      AttachmentCreateEventContext eventContext, CmisDocument cmisDocument) {
    String subdomain =
        TokenHandler.getSubdomainFromToken(
            eventContext.getAuthenticationInfo().as(JwtTokenAuthenticationInfo.class).getToken());
    eventContext.setContentId(
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + eventContext.getAttachmentEntity()
            + ":"
            + subdomain);
    eventContext.getData().setStatus("Clean");
    eventContext.getData().setContent(null);
    eventContext.setCompleted();
  }
}
