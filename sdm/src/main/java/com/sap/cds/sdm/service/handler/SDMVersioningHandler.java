package com.sap.cds.sdm.service.handler;

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
import com.sap.cds.sdm.service.VersioningService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ServiceName({"*"})
public class SDMVersioningHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final DraftService draftService;
  private final VersioningService versioningService;

  public SDMVersioningHandler(
      PersistenceService persistenceService,
      DraftService draftService,
      VersioningService versioningService) {
    this.persistenceService = persistenceService;
    this.draftService = draftService;
    this.versioningService = versioningService;
  }

  @On(event = "checkIn")
  public void CheckIn(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);

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
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    // get the objectId against the Id
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        DBQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    if (cmisDocument.getAttachmentStatus() == null
        || cmisDocument.getAttachmentStatus().equalsIgnoreCase("CANCEL_CHECKED_OUT")) {
      throw new ServiceException("Document should be checked out before checkIn");
    }
    System.out.println("Target Keys " + targetKeys);
    System.out.println("UP ID KEY" + upIdKey);
    String up__ID = targetKeys.get(upIdKey).toString();
    System.out.println("UP ID VALUE" + up__ID);
    System.out.println("context.get(\"isMajorVersion\")" + context.get("isMajorVersion"));
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();

    cmisDocument.setRepositoryId(repositoryId);
    versioningService.setContentStream(sdmCredentials, jwtToken, cmisDocument);

    InputStream contentStream = (InputStream) context.get("FileUploadParameter");
    cmisDocument.setContent(contentStream);
    String mimeType =
        URLConnection.guessContentTypeFromName(context.get("FileUploadParameter").toString());
    cmisDocument.setMimeType(mimeType);
    String obj = versioningService.setContentStream(sdmCredentials, jwtToken, cmisDocument);
    if (obj != null) {
      cmisDocument.setIsMajorVersion((Boolean) context.get("isMajorVersion"));
      cmisDocument.setComment(context.get("checkinComment").toString());
      cmisDocument.setObjectId(obj);
      String objId =
          versioningService.checkInDocument(repositoryId, sdmCredentials, jwtToken, cmisDocument);
      // update previous records to isLatestVersion false and PWC_ObjectId null

      DBQuery.updatePWCObjectIdForCheckIn(
          context.getTarget(), persistenceService, cmisDocument.getVersionSeriesId());
      // insert new row in to draftservice
      Map<String, Object> updatedFields = new HashMap<>();
      updatedFields.put("objectId", objId);
      updatedFields.put("repositoryId", repositoryId);
      updatedFields.put("folderId", cmisDocument.getFolderId());
      updatedFields.put("status", "Clean");
      cmisDocument.setParentId(up__ID);
      updatedFields.put(upIdKey, cmisDocument.getParentId());
      updatedFields.put("mimeType", cmisDocument.getMimeType());
      updatedFields.put("fileName", cmisDocument.getFileName());
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      updatedFields.put("isLatestVersion", true);
      updatedFields.put("PWC_objectId", objId);
      updatedFields.put("versionSeriesId", cmisDocument.getVersionSeriesId());
      updatedFields.put("attachmentStatus", "CHECKED_IN");
      var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
      var insertCount = draftService.newDraft(insert);
      if (insertCount.rowCount() > 0) {
        context.getMessages().success("Document CheckedIn Successfully");
      }
      context.setCompleted();
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
        model.findEntity(context.getTarget().getQualifiedName());
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
    String up__ID = targetKeys.get(upIdKey).toString();
    String ID = targetKeys.get("ID").toString();
    System.out.println("ID of " + ID);
    CmisDocument cmisDocument =
        DBQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    System.out.println("UP ID VALUE" + up__ID);
    if (cmisDocument.getAttachmentStatus() != null
        && cmisDocument.getAttachmentStatus().equalsIgnoreCase("CANCEL_CHECKED_OUT")) {
      throw new ServiceException("Document should be checked in before check out");
    }
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    // get the objectId against the Id

    System.out.println("OBJ " + cmisDocument.getObjectId());
    //    String objectId =
    //        versioningService.checkOutDocument(
    //            repositoryId, sdmCredentials, jwtToken, cmisDocument.getObjectId());
    //    System.out.println("RETURNED OBJ " + objectId);
    //    if (objectId != null) {
    //      DBQuery.updatePWCObjectIdForCheckOut(
    //          attachmentDraftEntity.get(), persistenceService, objectId, ID);
    //      context.getMessages().success("Document checked out successfully");
    //    }
    context.setCompleted();
  }

  @On(event = "cancelCheckOut")
  public void CancelCheckOut(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println("Handling event: " + eventName);
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CqnSelect select = (CqnSelect) context.get("cqn");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);

    String upIdKey = "";
    CdsModel model = context.getModel();
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    System.out.println("attachmentDraftEntity: " + attachmentDraftEntity.get());
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
    System.out.println("upIdKey: " + upIdKey);
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        DBQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    if (cmisDocument.getAttachmentStatus() != null
        && !cmisDocument.getAttachmentStatus().equalsIgnoreCase("CHECKED_OUT")) {
      throw new ServiceException("Document should be checked out before cancelling checkout");
    }
    System.out.println("Target Keys " + targetKeys);
    System.out.println("UP ID KEY" + upIdKey);
    String up__ID = targetKeys.get(upIdKey).toString();
    System.out.println("UP ID VALUE" + up__ID);
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    // get the objectId against the Id

    int resCode =
        versioningService.cancelCheckOut(
            repositoryId, sdmCredentials, jwtToken, cmisDocument.getPwcobjectId());
    if (resCode == 200) {
      DBQuery.updatePWCObjectIdForCancelCheckOut(
          attachmentDraftEntity.get(), persistenceService, cmisDocument.getVersionSeriesId());
      context.getMessages().success("Document check out is cancelled.");
    }
    context.setCompleted();
  }
}
