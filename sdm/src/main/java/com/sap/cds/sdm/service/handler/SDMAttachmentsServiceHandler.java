package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.*;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    String len = context.getParameterInfo().getHeaders().get("content-length");
    long contentLen = !StringUtils.isEmpty(len) ? Long.parseLong(len) : -1;
    String subdomain = "";
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    String repocheck = sdmService.checkRepositoryType(jwtToken, repositoryId);
    CmisDocument cmisDocument = new CmisDocument();
    if ("Versioned".equals(repocheck)) {
      throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
    }
    Map<String, Object> attachmentIds = context.getAttachmentIds();
    String upIdKey = "";
    String upID = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(context.getAttachmentEntity() + "_drafts");
    Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
    // if association is found, try to get foreign key to parent entity
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      // get association type
      CdsAssociationType assocType = association.getType();
      // get the refs of the association
      List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
      upIdKey = fkElements.get(0);
      upID = (String) attachmentIds.get(upIdKey);
    }
    Result result =
        DBQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, upID, upIdKey);
    if (!result.list().isEmpty()) {
      MediaData data = context.getData();

      String filename = data.getFileName();
      String fileid = (String) attachmentIds.get("ID");
      String mimeType = (String) data.get("mimeType");
      String errorMessageDI = "";
      boolean nameConstraint = SDMUtils.isRestrictedCharactersInName(filename);
      if (nameConstraint) {
        throw new ServiceException(
            SDMConstants.nameConstraintMessage(Collections.singletonList(filename), "Upload"));
      }
      Boolean duplicate = duplicateCheck(filename, fileid, result);
      if (Boolean.TRUE.equals(duplicate)) {
        throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
      }
      subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
      String folderId = sdmService.getFolderId(result, persistenceService, upID, jwtToken);
      cmisDocument.setFileName(filename);
      cmisDocument.setAttachmentId(fileid);
      InputStream contentStream = (InputStream) data.get("content");
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId((String) attachmentIds.get(upIdKey));
      cmisDocument.setRepositoryId(repositoryId);
      cmisDocument.setFolderId(folderId);
      cmisDocument.setMimeType(mimeType);
      cmisDocument.setContentLength(contentLen);
      SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
      JSONObject createResult = null;
      try {
        createResult =
            documentService.createDocumentRx(cmisDocument, sdmCredentials, jwtToken).blockingGet();
        logger.info("Synchronous Response from documentServiceRx: " + createResult.toString());
        logger.info("Upload Finished at: " + System.currentTimeMillis());
      } catch (Exception e) {
        logger.error("Error in documentServiceRx: \n" + Arrays.toString(e.getStackTrace()));
        throw new ServiceException(
            SDMConstants.getGenericError(AttachmentService.EVENT_CREATE_ATTACHMENT), e);
      }

      if (createResult.get("status") == "duplicate") {
        throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
      } else if (createResult.get("status") == "virus") {
        throw new ServiceException(SDMConstants.getVirusFilesError(filename));
      } else if (createResult.get("status") == "fail") {
        errorMessageDI = createResult.get("message").toString();
        throw new ServiceException(errorMessageDI);
      } else {
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        addAttachmentToDraft(attachmentDraftEntity.get(), persistenceService, cmisDocument);
      }
    }

    context.setContentId(
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + context.getAttachmentEntity()
            + ":"
            + subdomain);
    context.getData().setStatus("Clean");
    context.getData().setContent(null);
    context.setCompleted();
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
      Optional<CdsEntity> attachmentEntity = context.getModel().findEntity(entity);
      List<CmisDocument> cmisDocuments =
          DBQuery.getAttachmentsForFolder(attachmentEntity.get(), persistenceService, folderId);
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
}
