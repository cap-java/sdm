package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
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

  @On(event = "createLink")
  public void create(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    createLink(context);
  }

  @On(event = SDMCustomService.EVENT_EDIT_LINK)
  public void edit(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    editLink(context);
  }

  private void createLink(EventContext context) throws IOException {
    String entityName = "";

    String subdomain = "";
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    String up__ID = cqnAnalyzer.analyze(select).rootKeys().get("ID").toString();
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Result result =
        DBQuery.getAttachmentsForUPID(attachmentDraftEntity.get(), persistenceService, up__ID);
    String folderId = sdmService.getFolderId(result, persistenceService, up__ID, jwtToken);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFolderId(folderId);
    cmisDocument.setFileName(context.get("name").toString());
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    JSONObject createResult =
        sdmService.createDocument(cmisDocument, sdmCredentials, jwtToken, "link");

    cmisDocument.setObjectId(createResult.get("objectId").toString());
    cmisDocument.setParentId(up__ID);

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    updatedFields.put("up__ID", cmisDocument.getParentId());
    updatedFields.put("mimeType", cmisDocument.getMimeType());
    updatedFields.put("fileName", cmisDocument.getFileName());
    updatedFields.put("HasDraftEntity", false);
    updatedFields.put("HasActiveEntity", false);
    var data = List.of(updatedFields);
    var ref = ((CqnSelect) context.get("cqn")).ref();
    var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
    System.out.println(ApplicationHandlerHelper.isMediaEntity(context.getTarget()));
    var test = draftService.newDraft(insert);

    // context.setCompleted();
  }

  private void editLink(EventContext context) {
    System.out.println(
        "Parameters from edit "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString());
  }
}
