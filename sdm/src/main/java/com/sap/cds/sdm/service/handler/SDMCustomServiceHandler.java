package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    String facet = context.getFacet().split("\\.")[2];
    String upID = context.getUpId();
    String folderName = upID + "__" + facet;
    String repositoryId = SDMConstants.REPOSITORY_ID;

    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();

    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, jwtToken);
    if (folderId == null) {
      folderId =
          sdmService.createFolder(folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, jwtToken);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
    }
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setFolderId(folderId);
    List<String> objectIds = context.getObjectIds();
    Boolean flag = false;
    List<List<String>> attachmentsMetadata = new ArrayList<>();
    for (String objectId : objectIds) {
      cmisDocument.setObjectId(objectId);
      try {
        attachmentsMetadata.add(sdmService.copyAttachment(cmisDocument, jwtToken, sdmCredentials));
        System.out.println("Successful copy : " + attachmentsMetadata);
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

    Map<String, Object> updatedFields = new HashMap<>();
    Iterator<List<String>> metadataIterator = attachmentsMetadata.iterator();
    for (String objectId : objectIds) {
      if (!metadataIterator.hasNext()) {
        throw new IllegalStateException(
            "Attachments metadata does not match the number of objectIds");
      }
      List<String> metadata = metadataIterator.next();
      String fileName = metadata.get(0); // First value from the list
      String mimeType = metadata.get(1); // Second value from the list
      updatedFields.put("objectId", objectId);
      updatedFields.put("repositoryId", repositoryId);
      updatedFields.put("folderId", folderId);
      updatedFields.put("status", "Clean");
      updatedFields.put(folderName, upID);
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("fileName", fileName);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      var insert = Insert.into(context.getFacet()).entry(updatedFields);
      draftService.newDraft(insert);
    }

    // copyAttachmentsImpl(context);
    context.setCompleted();
  }
}
