package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.*;

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
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String repocheck = sdmService.checkRepositoryType(repositoryId);
    CmisDocument cmisDocument = new CmisDocument();
    if ("Versioned".equals(repocheck)) {
      context.getMessages().error("Upload not supported for versioned repositories");
    } else {
      Map<String, Object> attachmentIds = context.getAttachmentIds();
      String upID = (String) attachmentIds.get("up__ID");

      CdsModel model = context.getModel();
      Optional<CdsEntity> attachmentDraftEntity =
          model.findEntity(context.getAttachmentEntity() + "_drafts");
      Result result =
          DBQuery.getAttachmentsForUPID(attachmentDraftEntity.get(), persistenceService, upID);

      MediaData data = context.getData();

      String filename = (String) data.get("fileName");
      String fileid = (String) attachmentIds.get("ID");

      Boolean duplicate = duplicateCheck(filename, fileid, result);
      if (Boolean.TRUE.equals(duplicate)) {
        deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
        context
            .getMessages()
            .warn("This attachment already exists. Please remove it and try again");
      } else {
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        String folderId = sdmService.getFolderId(jwtToken, result, persistenceService, upID);
        cmisDocument.setFileName(filename);
        cmisDocument.setAttachmentId(fileid);
        InputStream contentStream = (InputStream) data.get("content");
        cmisDocument.setContent(contentStream);
        cmisDocument.setParentId((String) attachmentIds.get("up__ID"));
        cmisDocument.setRepositoryId(repositoryId);
        cmisDocument.setFolderId(folderId);
        SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
        JSONObject createResult = sdmService.createDocument(cmisDocument, jwtToken, sdmCredentials);

        StringBuilder error = new StringBuilder();
        if (createResult.get("status") == "duplicate") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files already exist and cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
        } else if (createResult.get("status") == "virus") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files contain potential malware and cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
        } else if (createResult.get("status") == "fail") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
        } else {
          cmisDocument.setObjectId(createResult.get("url").toString());
          addAttachmentToDraft(attachmentDraftEntity.get(), persistenceService, cmisDocument);
        }
      }
    }
  }

  @On(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
  public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context) {}

  @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
  public void restoreAttachment(AttachmentRestoreEventContext context) {}

  @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
  public void readAttachment(AttachmentReadEventContext context) {}

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
}
