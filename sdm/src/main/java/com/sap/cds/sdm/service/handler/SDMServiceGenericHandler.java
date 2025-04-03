package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMCustomService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

// Ensures this handler is registered for all services
@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DraftService draftService;

  public SDMServiceGenericHandler(
      PersistenceService persistenceService, SDMService sdmService, DraftService draftService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.draftService = draftService;
  }

  @On(event = "editLink")
  public void getActions(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println("Handling event: " + eventName);

    // ApplicationHandlerHelper.callProcessor(entity, data,
    // ApplicationHandlerHelper.MEDIA_CONTENT_FILTER, converter);
    switch (eventName) {
      case "createLink":
        createLink(context);
        break;
      case "editLink":
        editLink(context);
        break;
    }
  }

  @On(event = SDMCustomService.EVENT_CREATE_LINK)
  public void create(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));

    // ApplicationHandlerHelper.callProcessor(entity, data,
    // ApplicationHandlerHelper.MEDIA_CONTENT_FILTER, converter);

    switch (eventName) {
      case "createLink":
        createLink(context);
        break;
      case "editLink":
        editLink(context);
        break;
    }
  }

  private void createLink(EventContext context) throws IOException {
    System.out.println(
        "Parameters from createLink "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString()
            + context.keySet().stream().findFirst()
            + ":");
    String entityName = "";

    String subdomain = "";
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    System.out.println("Auth token " + jwtToken);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName(context.get("name").toString());
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    JSONObject createResult =
        sdmService.createDocument(cmisDocument, sdmCredentials, jwtToken, "link");

    cmisDocument.setObjectId(createResult.get("objectId").toString());
    // add links to attachment  table
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    String up__ID = cqnAnalyzer.analyze(select).rootKeys().get("ID").toString();
    System.out.println("Keys " + cqnAnalyzer.analyze(select).rootKeys().get("ID") + ":" + up__ID);
    System.out.println(context.getTarget());
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName());
    cmisDocument.setParentId(up__ID);

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    // updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    updatedFields.put("up__ID", cmisDocument.getParentId());
    updatedFields.put("mimeType", cmisDocument.getMimeType());
    updatedFields.put("fileName", cmisDocument.getFileName());
    updatedFields.put("HasDraftEntity", false);
    updatedFields.put("HasActiveEntity", false);
    // updatedFields.put("up_", "eqd");
    var data = List.of(updatedFields);
    var ref = ((CqnSelect) context.get("cqn")).ref();
    var insert =
        Insert.into(CQL.to(ref.segments()).to(attachmentDraftEntity.get().getQualifiedName()))
            .entry(updatedFields);
    System.out.println(ApplicationHandlerHelper.isMediaEntity(context.getTarget()));
    var test = draftService.newDraft(insert);
    // set values
    //    draftService.patchDraft(
    //
    // Update.entity(CQL.to(ref.segments()).to(attachmentDraftEntity.get().getQualifiedName()))
    //            .data(updatedFields));
    // patch draft
    //    adminService.patchDraft(Update.entity(ORDERS).data(order)
    //            .where(o -> o.ID().eq(order.getId()).and(o.IsActiveEntity().eq(false))));
    //    persistenceService.run(insert);
    //    // DBQuery.addLinkToDraft(attachmentDraftEntity.get(), persistenceService, cmisDocument);
    //    CqnSelect q = Select.from(attachmentDraftEntity.get());
    //    Result result = persistenceService.run(q);
    //    long count = result.rowCount();
    //    System.out.println("Count " + count);
    //    if (count != 0) {
    //      System.out.println("Result " + result.list().get(0));
    //    }

    context.setCompleted();
  }

  private void editLink(EventContext context) {
    System.out.println(
        "Parameters from edit "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString());
  }
}
