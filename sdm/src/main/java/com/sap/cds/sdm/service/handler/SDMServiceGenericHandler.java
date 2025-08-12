package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.AttachmentReadContext;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final RegisterService attachmentService;
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DocumentUploadService documentService;
  private final List<DraftService> draftService;
  private final DBQuery dbQuery;
  private final TokenHandler tokenHandler;

  public SDMServiceGenericHandler(
      RegisterService attachmentService,
      PersistenceService persistenceService,
      SDMService sdmService,
      DocumentUploadService documentService,
      List<DraftService> draftService,
      DBQuery dbQuery,
      TokenHandler tokenHandler) {
    this.attachmentService = attachmentService;
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.documentService = documentService;
    this.draftService = draftService;
    this.dbQuery = dbQuery;
    this.tokenHandler = tokenHandler;
  }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    String upID = context.get("up__ID").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();
    var copyEventInput =
        new CopyAttachmentInput(upID, context.getTarget().getQualifiedName(), objectIds);
    attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
    context.setCompleted();
  }

  @On(event = "createLink")
  public void create(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    validateRepository(context);
    // processEntities(context);
    createLink(context);
  }

  @On(event = "openAttachment")
  public void openAttachment(AttachmentReadContext context) throws Exception {
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    // get the objectId against the Id
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, ID);

    if (cmisDocument.getFileName() == null || cmisDocument.getFileName().isEmpty()) {
      // open attachment is triggered on non-draft entity
      attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName());
      cmisDocument =
          dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, ID);
    }
    if (cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")) {
      context.setUrl(cmisDocument.getUrl());
    } else {
      cmisDocument.setUrl("None");
    }
    context.setResult(cmisDocument.getUrl());
  }

  private void createLink(EventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CdsModel cdsModel = context.getModel();
    System.out.println("Model: " + cdsModel);

    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    System.out.println("Entity:" + context.getTarget().getQualifiedName());

    String upIdKey =
        attachmentDraftEntity.isPresent() ? getUpIdKey(attachmentDraftEntity.get()) : "up__ID";
    System.out.println("UpIdKey: " + upIdKey);

    CqnSelect select = (CqnSelect) context.get("cqn");
    System.out.println("Select query: " + select);

    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    System.out.println("CqnAnalyzer: " + cqnAnalyzer);

    System.out.println("IDs: " + cqnAnalyzer.analyze(select).rootKeys().toString());
    String id = upIdKey.replaceFirst("^up__", "");
    String upID = cqnAnalyzer.analyze(select).rootKeys().get(id).toString();
    String filenameInRequest = context.get("name").toString();
    System.out.println("UPID " + upID);

    Result result =
        dbQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, upID, upIdKey);
    System.out.println("Result: \n" + result);

    checkAttachmentConstraints(context, attachmentDraftEntity.get(), upID, upIdKey);
    validateFileName(filenameInRequest, result);

    Boolean isSystemUser = context.getUserInfo().isSystemUser();
    String entityName = context.getTarget().getQualifiedName().split("\\.")[2];
    String folderName = upID + "__" + entityName;

    String folderId = sdmService.getFolderId(result, persistenceService, folderName, isSystemUser);
    System.out.println("folderId: " + folderId);

    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFolderId(folderId);
    cmisDocument.setFileName(filenameInRequest);
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    JSONObject createResult = null;

    try {
      createResult = documentService.createDocument(cmisDocument, sdmCredentials, isSystemUser);
    } catch (Exception e) {
      throw new ServiceException(
          SDMConstants.getGenericError(AttachmentService.EVENT_CREATE_ATTACHMENT), e);
    }
    System.out.println("createResult" + createResult);
    handleCreateLinkResult(cmisDocument, createResult, context, upID, upIdKey);
  }

  private void validateRepository(EventContext eventContext) throws ServiceException, IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String repocheck =
        sdmService.checkRepositoryType(repositoryId, eventContext.getUserInfo().getTenant());
    System.out.println("Repository check: " + repocheck);
    if (SDMConstants.REPOSITORY_VERSIONED.equals(repocheck)) {
      throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
    }
  }

  private String getUpIdKey(CdsEntity attachmentDraftEntity) {
    String upIdKey = "";
    Optional<CdsElement> upAssociation = attachmentDraftEntity.findAssociation("up_");
    System.out.println("Up Association: " + upAssociation);
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      System.out.println("Association: " + association);
      // get association type
      CdsAssociationType assocType = association.getType();
      System.out.println("Association Type: " + assocType);
      // get the refs of the association
      List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
      System.out.println("FK Elements: " + fkElements);
      upIdKey = fkElements.get(0);
      System.out.println("UpIdKey: " + upIdKey);
    }
    // return upIdKey.replaceFirst("^up__", "");
    return upIdKey;
  }

  private void checkAttachmentConstraints(
      EventContext context, CdsEntity attachmentDraftEntity, String upID, String upIdKey)
      throws ServiceException {
    // Fetch the row count for current repository
    CdsModel cdsModel = context.getModel();
    Optional<CdsEntity> attachmentEntityOpt =
        cdsModel.findEntity(context.getTarget().getQualifiedName());
    if (attachmentEntityOpt.isEmpty()) {
      throw new ServiceException(
          "Target entity not found: " + context.getTarget().getQualifiedName());
    }
    CdsEntity attachmentEntity = attachmentEntityOpt.get();

    Result result =
        dbQuery.getAttachmentsForUPIDAndRepository(
            attachmentDraftEntity, persistenceService, upID, upIdKey);
    long rowCount = result.rowCount();
    String errorMessageCount =
        SDMUtils.getAttachmentCountAndMessage(
            context.getModel().entities().toList(), attachmentEntity);
    String[] maxCountArr = errorMessageCount.split("__");
    long maxCount = Long.parseLong(maxCountArr[0]);
    if (maxCount > 0 && rowCount >= maxCount) {
      String message = maxCountArr[1];
      if (message != null && !"null".equalsIgnoreCase(message)) {
        throw new ServiceException(message);
      }
      throw new ServiceException(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, maxCount));
    }
  }

  private void validateFileName(String filename, Result result) throws ServiceException {
    System.out.println("Validating file name: " + filename);
    if (SDMUtils.isRestrictedCharactersInName(filename)) {
      throw new ServiceException(
          SDMConstants.nameConstraintMessage(Collections.singletonList(filename), "created"));
    }
    if (duplicateCheck(filename, result)) {
      System.out.println("Duplicate file found: " + filename);
      throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
    }
  }

  public boolean duplicateCheck(String filenameToCheck, Result result) {
    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream().map(m -> (Map<String, Object>) m).toList();
    System.out.println("Result List: " + resultList);
    return resultList.stream()
        .anyMatch(
            attachment ->
                filenameToCheck.equals(attachment.get("fileName"))
                    && SDMConstants.REPOSITORY_ID.equals(attachment.get("repositoryId")));
  }

  private void handleCreateLinkResult(
      CmisDocument cmisDocument,
      JSONObject createResult,
      EventContext context,
      String upID,
      String upIdKey)
      throws ServiceException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String status = createResult.get("status").toString();

    switch (status) {
      case "duplicate":
        throw new ServiceException(SDMConstants.getDuplicateFilesError(cmisDocument.getFileName()));
      case "fail":
        throw new ServiceException(createResult.get("message").toString());
      case "unauthorized":
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK);
      default:
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        cmisDocument.setParentId(upID);

        Map<String, Object> updatedFields = new HashMap<>();
        updatedFields.put("objectId", cmisDocument.getObjectId());
        updatedFields.put("repositoryId", repositoryId);
        updatedFields.put("folderId", cmisDocument.getFolderId());
        updatedFields.put("status", "Clean");
        updatedFields.put("type", "sap-icon://internet-browser");
        updatedFields.put(upIdKey, cmisDocument.getParentId());
        updatedFields.put("mimeType", cmisDocument.getMimeType());
        updatedFields.put("fileName", cmisDocument.getFileName());
        updatedFields.put("HasDraftEntity", false);
        updatedFields.put("HasActiveEntity", false);
        updatedFields.put("linkUrl", cmisDocument.getUrl());
        updatedFields.put(
            "contentId",
            cmisDocument.getObjectId()
                + ":"
                + cmisDocument.getFolderId()
                + ":"
                + context.getTarget());

        var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
        for (DraftService draftS : draftService) {
          // Process each draftService object
          System.out.println(
              "Draft Service " + context.getTarget().getQualifiedName() + " : " + draftS.getName());
          if (context.getTarget().getQualifiedName().contains(draftS.getName())) {
            System.out.println("yes");
            draftS.newDraft(insert);
            System.out.println("DraftService called for: " + draftS.getName());
          }
          // You can call methods or perform operations on draftService here
        }
        System.out.println("Draft updated successfully.");
        context.setCompleted();
        System.out.println("Handle create link result completed for UPID: " + upID);
    }
  }
}
