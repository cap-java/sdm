package com.sap.cds.sdm.service.handler;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DraftService draftService;

  public SDMCustomServiceHandler(
      PersistenceService persistenceService, SDMService sdmService, DraftService draftService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.draftService = draftService;
  }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    System.out.println("Inside correct method - Nexus");

    String facet = context.getFacet().split("\\.")[2];
    String upID = context.getUpId();
    String folderName = upID + "__" + facet;
    String repositoryId = System.getenv("REPOSITORY_ID");

    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();

    Optional<CdsEntity> attachmentEntity =
        context.getModel().findEntity(context.getFacet() + "_drafts");
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getUpId() + ":" + context.getObjectIds());

    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, jwtToken);
    if (folderId == null) {
      folderId =
          sdmService.createFolder(folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, jwtToken);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
    }
    // folderId = "folderId";
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setFolderId(folderId);
    List<String> objectIds = context.getObjectIds();
    Boolean flag = false;

    for (String objectId : objectIds) {
      cmisDocument.setObjectId(objectId);
      try {
        sdmService.copyAttachment(cmisDocument, jwtToken, sdmCredentials);
      } catch (ServiceException e) {
        if (e.getMessage().equals("Failed to copy attachment")) {
          flag = true;
          break;
        }
      }
    }

    if (flag) {
      throw new ServiceException(
          "Failed to copy attachment for UP ID: " + upID + " and facet: " + facet);
    }
    // copyAttachmentsImpl(context);
    context.setCompleted();
  }
}
