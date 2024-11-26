package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.*;

import com.google.gson.JsonObject;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.json.JSONObject;

@ServiceName(value = "*", type = AttachmentService.class)
public class SDMAttachmentsServiceHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMAttachmentsServiceHandler(
      PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) throws IOException {
    String subdomain = "";
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String repocheck = sdmService.checkRepositoryType(repositoryId);
    CmisDocument cmisDocument = new CmisDocument();
    if ("Versioned".equals(repocheck)) {
      throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
    } else {
      Map<String, Object> attachmentIds = context.getAttachmentIds();
      String upID = (String) attachmentIds.get("up__ID");
      CdsModel model = context.getModel();
      Optional<CdsEntity> attachmentDraftEntity =
          model.findEntity(context.getAttachmentEntity() + "_drafts");
      Result result =
          DBQuery.getAttachmentsForUPID(attachmentDraftEntity.get(), persistenceService, upID);
      if (!result.list().isEmpty()) {
        MediaData data = context.getData();

        String filename = data.getFileName();
        String fileid = (String) attachmentIds.get("ID");
        String mimeType = (String) data.get("mimeType");
        String errorMessageDI = "";

        Boolean duplicate = duplicateCheck(filename, fileid, result);
        if (Boolean.TRUE.equals(duplicate)) {
          throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
        } else {
          AuthenticationInfo authInfo = context.getAuthenticationInfo();
          JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
          String jwtToken = jwtTokenInfo.getToken();
          JsonObject tokenDetails = TokenHandler.getTokenFields(jwtToken);
          JsonObject tenantDetails = tokenDetails.get("ext_attr").getAsJsonObject();
          subdomain = tenantDetails.get("zdn").getAsString();
          String folderId = sdmService.getFolderId(jwtToken, result, persistenceService, upID);
          cmisDocument.setFileName(filename);
          cmisDocument.setAttachmentId(fileid);
          InputStream contentStream = (InputStream) data.get("content");
          cmisDocument.setContent(contentStream);
          cmisDocument.setParentId((String) attachmentIds.get("up__ID"));
          cmisDocument.setRepositoryId(repositoryId);
          cmisDocument.setFolderId(folderId);
          cmisDocument.setMimeType(mimeType);
          SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
          JSONObject createResult =
              sdmService.createDocument(cmisDocument, jwtToken, sdmCredentials);

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
      throw new ServiceException(SDMConstants.NOT_FOUND_ERROR);
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
      if (filename.equals(resultFileName) && !fileid.equals(resultId)) {
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
