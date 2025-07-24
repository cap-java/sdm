package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {
  private final SDMService sdmService;
  private final List<DraftService> draftService;
  private final TokenHandler tokenHandler;

  public SDMCustomServiceHandler(
      SDMService sdmService, List<DraftService> draftService, TokenHandler tokenHandler) {
    this.sdmService = sdmService;
    this.draftService = draftService;
    this.tokenHandler = tokenHandler;
  }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    String[] splitFacet = context.getFacet().split("\\.");
    if (splitFacet.length < 3) {
      throw new ServiceException(SDMConstants.FAILED_TO_FETCH_FACET);
    }
    String facet = splitFacet[2];
    String upID = context.getUpId();
    String folderName = upID + "__" + facet;
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Boolean isSystemUser = context.getSystemUser();
    Boolean folderExists = true;

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
    if (folderId == null) {
      folderExists = false;
      folderId =
          sdmService.createFolder(
              folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
    }
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setFolderId(folderId);
    List<String> objectIds = context.getObjectIds();
    List<List<String>> attachmentsMetadata = new ArrayList<>();
    for (String objectId : objectIds) {
      cmisDocument.setObjectId(objectId);
      try {
        attachmentsMetadata.add(
            sdmService.copyAttachment(cmisDocument, sdmCredentials, isSystemUser));
      } catch (ServiceException e) {
        if (!folderExists) {
          // deleteFolder
          sdmService.deleteDocument("deleteTree", folderId, "ewdew");
          throw new ServiceException(e.getMessage());
        } else {
          for (List<String> attachmentMetadata : attachmentsMetadata) {
            // delete the copied attachments
            sdmService.deleteDocument("delete", attachmentMetadata.get(2), "edewd");
          }
          throw new ServiceException(e.getMessage());
        }
      }
    }

    String upIdKey = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(context.getFacet() + "_drafts");
    Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      CdsAssociationType assocType = association.getType();
      List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
      upIdKey = fkElements.get(0);
    }
    Map<String, Object> updatedFields = new HashMap<>();
    for (List<String> attachmentMetadata : attachmentsMetadata) {
      String fileName = attachmentMetadata.get(0);
      String mimeType = attachmentMetadata.get(1);
      String newObjectId = attachmentMetadata.get(2);
      updatedFields.put("objectId", newObjectId);
      updatedFields.put("repositoryId", repositoryId);
      updatedFields.put("folderId", folderId);
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("fileName", fileName);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      updatedFields.put(
          "contentId", newObjectId + ":" + folderId + ":" + context.getFacet() + ":" + mimeType);
      updatedFields.put(upIdKey, upID);

      var insert = Insert.into(context.getFacet()).entry(updatedFields);
      for (DraftService draftS : draftService) {
        // Check if the draft service name matches the context facet
        if (context.getFacet().contains(draftS.getName())) {
          draftS.newDraft(insert);
        }
      }
    }
    context.setCompleted();
  }
}
