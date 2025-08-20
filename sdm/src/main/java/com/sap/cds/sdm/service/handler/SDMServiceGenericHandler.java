package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.AttachmentLogContext;
import com.sap.cds.sdm.model.AttachmentReadContext;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

@ServiceName(value = "*")
public class SDMServiceGenericHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final List<DraftService> draftService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

  public SDMServiceGenericHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      List<DraftService> draftService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.draftService = draftService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  @On(event = "createLink")
  public void create(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    createLink(context);
  }

  @On(event = "download")
  public void download(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println("Download event");
    context.setCompleted();
  }

  @On(event = "changelog")
  public void changelog(AttachmentLogContext context) throws IOException {
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    // get the objectId against the Id
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();

    JSONObject jsonObject =
        sdmService.getChangeLog(
            cmisDocument.getObjectId(), sdmCredentials, context.getUserInfo().isSystemUser());
    jsonObject.put("filename", cmisDocument.getFileName());
    context.setResult(jsonObject);
  }

  @On(event = "openAttachment")
  public void openAttachment(AttachmentReadContext context) throws Exception {
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    // get the objectId against the Id
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    if (cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")) {
      context.setUrl(cmisDocument.getUrl());
    } else {
      cmisDocument.setUrl("None");
    }
    context.setResult(cmisDocument.getUrl());
  }

  private void createLink(EventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);

    String up__ID = cqnAnalyzer.analyze(select).rootKeys().get("ID").toString();
    System.out.println("UPID " + up__ID);
    String upIdKey = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    System.out.println("Entity" + context.getTarget().getQualifiedName());
    Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
    // if association is found, try to get foreign key to parent entity
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      // get association type
      CdsAssociationType assocType = association.getType();
      // get the refs of the association
      List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
      upIdKey = fkElements.get(0);
    }
    Result result =
        dbQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, up__ID, upIdKey);
    String folderId = sdmService.getFolderId(result, persistenceService, up__ID, false);
    System.out.println("folderId" + folderId);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFolderId(folderId);
    cmisDocument.setFileName(context.get("name").toString());
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    JSONObject createResult = sdmService.createDocument(cmisDocument, sdmCredentials);
    System.out.println("createResult" + createResult);
    cmisDocument.setObjectId(createResult.get("objectId").toString());
    cmisDocument.setParentId(up__ID);

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    updatedFields.put("type", "sap-icon://internet-browser");
    String content =
        String.format(
            "<Link text=\"%s\" target=\"_blank\" href=\"%s\" />",
            cmisDocument.getFileName(), cmisDocument.getUrl());
    // updatedFields.put("content", cmisDocument.getFileName());
    updatedFields.put(upIdKey, cmisDocument.getParentId());
    updatedFields.put("mimeType", cmisDocument.getMimeType());
    updatedFields.put(
        "contentId",
        cmisDocument.getObjectId() + ":" + cmisDocument.getFolderId() + ":" + context.getTarget());
    updatedFields.put("fileName", cmisDocument.getFileName());
    updatedFields.put("HasDraftEntity", false);
    updatedFields.put("HasActiveEntity", false);
    updatedFields.put("linkUrl", cmisDocument.getUrl());
    //    Insert insert = null;
    //    for (CdsEntity cdsEntity : context.getModel().entities().toList()) {
    //      if (SDMUtils.isRelatedEntity(context.getTarget(), cdsEntity)) {
    //        insert = Insert.into(cdsEntity.getQualifiedName()).entry(updatedFields);
    //      }
    //    }
    // Insert.into(context., o -> o.matching(Map.of("ID", 1001))).items()).entry(Map.of("book",
    // Map.of("ID", 251), "amount", 1));
    var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
    for (DraftService draftS : draftService) {
      // Process each draftService object
      System.out.println(
          "Draft Service " + context.getTarget().getQualifiedName().contains(draftS.getName()));
      if (context.getTarget().getQualifiedName().contains(draftS.getName())) {
        System.out.println("yes");
        // draftS.run(insert);
        draftS.newDraft(insert);
      }
      // You can call methods or perform operations on draftService here
    }

    context.setCompleted();
  }

  //  private void editLink(EventContext context) throws IOException {
  //    System.out.println("Parameters from edit " + context.get("url").toString());
  //    CdsModel cdsModel = context.getModel();
  //    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
  //    Optional<CdsEntity> attachmentDraftEntity =
  //            cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
  //    Map<String, Object> targetKeys =
  //            cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
  //    // get the objectId against the Id
  //    String ID = targetKeys.get("ID").toString();
  //    CmisDocument cmisDocument =
  //            DBQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService,
  // ID);
  //    cmisDocument.setUrl(context.get("url").toString());
  //    // call setContentStream
  //    AuthenticationInfo authInfo = context.getAuthenticationInfo();
  //    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
  //    String jwtToken = jwtTokenInfo.getToken();
  //    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
  //    cmisDocument.setRepositoryId(SDMConstants.REPOSITORY_ID);
  //    System.out.println("URL " + cmisDocument.getUrl());
  //    int responseCode = sdmService.editLink(cmisDocument, sdmCredentials, jwtToken);
  //    System.out.println("Res code " + responseCode);
  //    if (responseCode == 201) {
  //      Map<String, Object> updatedFields = new HashMap<>();
  //      updatedFields.put("linkUrl", cmisDocument.getUrl());
  //      var update =
  //              Update.entity(attachmentDraftEntity.get())
  //                      .data(updatedFields)
  //                      .where(doc -> doc.get("ID").eq(ID));
  //      var t = persistenceService.run(update);
  //      System.out.println("UPDATE " + update + ":" + t);
  //    }
  //    context.setCompleted();
  //  }

}
