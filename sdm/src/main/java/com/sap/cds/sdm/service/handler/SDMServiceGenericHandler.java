package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.VersioningService;
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
  private final VersioningService versioningService;

  public SDMServiceGenericHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      DraftService draftService,
      VersioningService versioningService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.draftService = draftService;
    this.versioningService = versioningService;
  }

  @On(event = "createLink")
  public void create(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    createLink(context);
  }

  @On(event = "editLink")
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
    String upIdKey = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(context.getTarget().getQualifiedName() + "_drafts");
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
        DBQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, up__ID, upIdKey);
    String folderId = sdmService.getFolderId(result, persistenceService, up__ID, jwtToken);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFolderId(folderId);
    cmisDocument.setFileName(context.get("name").toString());
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    JSONObject createResult = sdmService.createDocument(cmisDocument, sdmCredentials, jwtToken);

    cmisDocument.setObjectId(createResult.get("objectId").toString());
    cmisDocument.setParentId(up__ID);

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    updatedFields.put(upIdKey, cmisDocument.getParentId());
    updatedFields.put("mimeType", cmisDocument.getMimeType());
    updatedFields.put("fileName", cmisDocument.getFileName());
    updatedFields.put("HasDraftEntity", false);
    updatedFields.put("HasActiveEntity", false);
    var data = List.of(updatedFields);
    var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
    System.out.println(ApplicationHandlerHelper.isMediaEntity(context.getTarget()));
    var test = draftService.newDraft(insert);
    // execute a select query to refresh the list
    DBQuery.getAttachmentsForUPID(
        attachmentDraftEntity.get(), persistenceService, cmisDocument.getParentId(), upIdKey);
    context.setCompleted();
  }

  private void editLink(EventContext context) {
    System.out.println(
        "Parameters from edit "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString());
  }

  @On(event = "checkIn")
  public void CheckIn(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    String repositoryId = SDMConstants.REPOSITORY_ID;
    System.out.println("Context Cehck in " + context.getParameterInfo().getQueryParams());
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);

    System.out.println("Keysyy " + cqnAnalyzer.analyze(select).rootKeys());
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setObjectId(context.get("").toString());
    //    InputStream contentStream = (InputStream) data.get("content");
    //    cmisDocument.setContent(contentStream);
    //    cmisDocument.setMimeType(mimeType);
    int resCode = versioningService.setContentStream(sdmCredentials, jwtToken, cmisDocument);
    if (resCode == 200) {
      String objectId =
          versioningService.checkOutDocument(
              repositoryId, sdmCredentials, jwtToken, context.get("").toString());

      String attachmentId = cqnAnalyzer.analyze(select).rootKeys().get("ID").toString();
      DBQuery.updateObjectId(context.getTarget(), persistenceService, objectId, attachmentId);
    }
  }

  @On(event = "checkOut")
  public void CheckOut(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    System.out.println("Target Keys " + targetKeys);
    String upIdKey = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(context.getTarget().getQualifiedName() + "_drafts");
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
    System.out.println("UP ID KEY" + upIdKey);
    String up__ID = targetKeys.get(upIdKey);
    System.out.println("UP ID VALUE" + up__ID);
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    // get the objectId against the Id
    String ID = targetKeys.get("ID");
    System.out.println("ID of " + ID);
    String objectIdForAttachment =
        DBQuery.getObjectIdForAttachmentID(context.getTarget(), persistenceService, ID);
    System.out.println("OBJ " + objectIdForAttachment);
    String objectId =
        versioningService.checkOutDocument(
            repositoryId, sdmCredentials, jwtToken, objectIdForAttachment);
    System.out.println("RETURNED OBJ " + objectId);
    String attachmentId = cqnAnalyzer.analyze(select).rootKeys().get("ID").toString();
    DBQuery.updateObjectId(context.getTarget(), persistenceService, objectId, attachmentId);
  }

  @On(event = "cancelCheckOut")
  public void CancelCheckOut(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
  }
}
