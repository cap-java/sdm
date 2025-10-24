package com.sap.cds.sdm.service.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final RegisterService attachmentService;
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final DocumentUploadService documentService;
  private final List<DraftService> draftService;
  private final DBQuery dbQuery;
  private final TokenHandler tokenHandler;
  private static final Logger logger = LoggerFactory.getLogger(SDMServiceGenericHandler.class);

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
    validateRepository(context);
    createLink(context);
  }

  @On(event = "editLink")
  public void edit(EventContext context) throws IOException {
    logger.info("Handling event " + context.getEvent());
    editLink(context);
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
    String id = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);

    if (cmisDocument.getFileName() == null || cmisDocument.getFileName().isEmpty()) {
      // open attachment is triggered on non-draft entity
      attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName());
      cmisDocument =
          dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);
    }
    if (cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")) {
      context.setResult(cmisDocument.getUrl());
    } else {
      context.setResult("None");
    }
  }

  private void validateRepository(EventContext eventContext) throws ServiceException, IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    RepoValue repoValue =
        sdmService.checkRepositoryType(repositoryId, eventContext.getUserInfo().getTenant());
    if (repoValue.getVersionEnabled()) {
      throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
    }
  }

  private void createLink(EventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CdsModel cdsModel = context.getModel();

    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");

    String upIdKey =
        attachmentDraftEntity.isPresent() ? getUpIdKey(attachmentDraftEntity.get()) : "up__ID";
    CqnSelect select = (CqnSelect) context.get("cqn");
    String upID = fetchUPIDFromCQN(select);
    String filenameInRequest = context.get("name").toString();

    Result result =
        dbQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, upID, upIdKey);

    checkAttachmentConstraints(context, attachmentDraftEntity.get(), upID, upIdKey);
    validateLinkName(filenameInRequest, result);

    Boolean isSystemUser = context.getUserInfo().isSystemUser();
    String entityName = context.getTarget().getQualifiedName().split("\\.")[2];
    String folderName = upID + "__" + entityName;

    String folderId = sdmService.getFolderId(result, persistenceService, folderName, isSystemUser);

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
    handleCreateLinkResult(cmisDocument, createResult, context, upID, upIdKey);
  }

  private void editLink(EventContext context) throws IOException {
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    String ID = targetKeys.get("ID").toString();
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentDraftEntity.get(), persistenceService, ID);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    cmisDocument.setRepositoryId(SDMConstants.REPOSITORY_ID);
    Boolean isSystemUser = context.getUserInfo().isSystemUser();
    JSONObject response = sdmService.editLink(cmisDocument, sdmCredentials, isSystemUser);
    String status = response.get("status").toString();
    if (status.equals("success")) {
      Map<String, Object> updatedFields = new HashMap<>();
      updatedFields.put("linkUrl", cmisDocument.getUrl());
      var update =
          Update.entity(attachmentDraftEntity.get())
              .data(updatedFields)
              .where(doc -> doc.get("ID").eq(ID));
      persistenceService.run(update);
      logger.info("Successfully edited link");
    } else {
      if (status.equals("unauthorized")) {
        throw new ServiceException(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG);
      } else {
        throw new ServiceException("Failed to edit link");
      }
    }
    context.setCompleted();
  }

  private String getUpIdKey(CdsEntity attachmentDraftEntity) {
    String upIdKey = "";
    Optional<CdsElement> upAssociation = attachmentDraftEntity.findAssociation("up_");
    if (upAssociation.isPresent()) {
      CdsElement association = upAssociation.get();
      // get association type
      CdsAssociationType associationType = association.getType();
      // get the refs of the association
      List<String> fkElements = associationType.refs().map(ref -> "up__" + ref.path()).toList();
      upIdKey = fkElements.get(0);
    }
    return upIdKey;
  }

  private void checkAttachmentConstraints(
      EventContext context, CdsEntity attachmentDraftEntity, String upID, String upIdKey)
      throws ServiceException {
    CdsModel cdsModel = context.getModel();
    CdsEntity attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName()).get();

    // Fetch the row count for current repository
    Result result =
        dbQuery.getAttachmentsForUPIDAndRepository(
            attachmentDraftEntity, persistenceService, upID, upIdKey);
    long rowCount = result.rowCount();
    String errorMessageAndCount =
        SDMUtils.getAttachmentCountAndMessage(
            context.getModel().entities().toList(), attachmentEntity);
    String[] maxCountArr = errorMessageAndCount.split("__");
    long maxCount = Long.parseLong(maxCountArr[0]);
    String message = maxCountArr.length > 1 ? maxCountArr[1] : null;
    if (maxCount > 0 && rowCount >= maxCount) {
      if (message != null && !"null".equalsIgnoreCase(message)) {
        throw new ServiceException(message);
      }
      throw new ServiceException(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, maxCount));
    }
  }

  private void validateLinkName(String filename, Result result) throws ServiceException {
    if (SDMUtils.isRestrictedCharactersInName(filename)) {
      throw new ServiceException(
          SDMConstants.linkNameConstraintMessage(Collections.singletonList(filename), "created"));
    }
    if (duplicateCheck(filename, result)) {
      throw new ServiceException(SDMConstants.getDuplicateFilesError(filename));
    }
  }

  public boolean duplicateCheck(String filenameToCheck, Result result) {
    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream().map(m -> (Map<String, Object>) m).toList();
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

        try {
          var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
          for (DraftService draftS : draftService) {
            if (context.getTarget().getQualifiedName().contains(draftS.getName())) {
              draftS.newDraft(insert);
            }
          }
        } catch (Exception e) {
          logger.info("Exception in insert : " + e.getMessage());
        }
        context.setCompleted();
    }
  }

  private String fetchUPIDFromCQN(CqnSelect select) {
    try {
      String upID = null;
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(select.toString());
      JsonNode refArray = root.path("SELECT").path("from").path("ref");
      JsonNode secondLast = refArray.get(refArray.size() - 2);
      JsonNode whereArray = secondLast.path("where");
      for (int i = 0; i < whereArray.size(); i++) {
        JsonNode node = whereArray.get(i);
        if (node.has("ref")
            && node.get("ref").isArray()
            && node.get("ref").get(0).asText().equals("ID")) {
          JsonNode valNode = whereArray.get(i + 2);
          upID = valNode.path("val").asText();
          break;
        }
      }
      if (upID == null) {
        throw new ServiceException(SDMConstants.ENTITY_PROCESSING_ERROR_LINK);
      }
      return upID;
    } catch (Exception e) {
      logger.error(SDMConstants.ENTITY_PROCESSING_ERROR_LINK, e);
      throw new ServiceException(SDMConstants.ENTITY_PROCESSING_ERROR_LINK, e);
    }
  }
}
