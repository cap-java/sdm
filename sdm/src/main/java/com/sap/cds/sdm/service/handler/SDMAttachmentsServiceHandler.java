package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.IOException;

@ServiceName(value = "*", type = AttachmentService.class)
public class SDMAttachmentsServiceHandler implements EventHandler {

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) throws IOException {
    String subdomain = "";
    long startTime = System.currentTimeMillis() / 60000;
    System.out.println("Start time " + startTime);
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    String repocheck = sdmService.checkRepositoryType(jwtToken, repositoryId);
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
        boolean nameConstraint = SDMUtils.isRestrictedCharactersInName(filename);
        if (nameConstraint) {
          throw new ServiceException(
              SDMConstants.nameConstraintMessage(Collections.singletonList(filename), "Upload"));
        }
        Boolean duplicate = duplicateCheck(filename, fileid, result);
        if (Boolean.TRUE.equals(duplicate)) {
          throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
        } else {
          subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
          String folderId = sdmService.getFolderId(result, persistenceService, upID, jwtToken);
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
              sdmService.createDocument(cmisDocument, sdmCredentials, jwtToken);

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
    long end = System.currentTimeMillis() / 60000;
    long diff = end - startTime;
    System.out.println("end time " + end);
    System.out.println("Time taken  " + diff);
    context.setCompleted();
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
