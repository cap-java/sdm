package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.constants.SDMConstants.ATTACHMENT_MAXCOUNT_ERROR_MSG;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.*;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.draft.DraftCancelEventContext;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
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

@ServiceName(value = "*", type = ApplicationService.class)
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

    // Use the full target qualified name as the facet
    String facet = context.getTarget().getQualifiedName();

    var copyEventInput = new CopyAttachmentInput(upID, facet, objectIds);

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

  @Before(event = DraftService.EVENT_DRAFT_CANCEL)
  public void handleDraftDiscardForLinks(DraftCancelEventContext context) throws IOException {
    CdsEntity parentDraftEntity = context.getTarget();
    CqnAnalyzer analyzer = CqnAnalyzer.create(context.getModel());
    Map<String, Object> parentKeys = analyzer.analyze(context.getCqn()).rootKeys();
    String parentEntityName = parentDraftEntity.getQualifiedName().replace("_drafts", "");

    Optional<CdsEntity> parentActiveEntityOpt = context.getModel().findEntity(parentEntityName);
    Map<String, String> compositionPathMapping =
        parentActiveEntityOpt
            .map(
                cdsEntity ->
                    AttachmentsHandlerUtils.getAttachmentPathMapping(
                        context.getModel(), cdsEntity, persistenceService))
            .orElse(new HashMap<>());

    for (Map.Entry<String, String> entry : compositionPathMapping.entrySet()) {
      String attachmentCompositionDefinition = entry.getKey();
      revertLinksForComposition(context, parentKeys, attachmentCompositionDefinition);
    }
    revertNestedEntityLinks(context);
  }

  private void revertNestedEntityLinks(DraftCancelEventContext context) throws IOException {

    CdsEntity parentDraftEntity = context.getTarget();
    String parentEntityName = parentDraftEntity.getQualifiedName().replace("_drafts", "");
    Optional<CdsEntity> parentActiveEntityOpt = context.getModel().findEntity(parentEntityName);

    if (parentActiveEntityOpt.isPresent()) {
      CdsEntity parentActiveEntity = parentActiveEntityOpt.get();

      parentActiveEntity
          .compositions()
          .forEach(
              composition -> {
                try {
                  processNestedEntityComposition(context, composition);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  private void processNestedEntityComposition(
      DraftCancelEventContext context, CdsElement composition) throws IOException {

    CdsAssociationType associationType = (CdsAssociationType) composition.getType();
    String targetEntityName = associationType.getTarget().getQualifiedName();
    String draftTargetEntityName = targetEntityName + "_drafts";

    Optional<CdsEntity> nestedDraftEntity = context.getModel().findEntity(draftTargetEntityName);

    if (nestedDraftEntity.isPresent()) {
      Map<String, String> nestedAttachmentMapping =
          AttachmentsHandlerUtils.getAttachmentPathMapping(
              context.getModel(), associationType.getTarget(), persistenceService);

      if (nestedAttachmentMapping.isEmpty()) {
        return;
      }

      // Get the actual key field names from the entity instead of hardcoding "ID"
      List<String> keyElementNames = getKeyElementNames(nestedDraftEntity.get());

      Result nestedRecords =
          persistenceService.run(
              Select.from(nestedDraftEntity.get()).where(e -> e.get("IsActiveEntity").eq(false)));

      for (Row nestedRecord : nestedRecords) {
        Map<String, Object> nestedEntityKeys = new HashMap<>();

        // Populate the key map with all actual key field names and values
        for (String keyName : keyElementNames) {
          nestedEntityKeys.put(keyName, nestedRecord.get(keyName));
        }
        nestedEntityKeys.put("IsActiveEntity", false);

        for (Map.Entry<String, String> entry : nestedAttachmentMapping.entrySet()) {
          String attachmentPath = entry.getKey();
          revertLinksForComposition(context, nestedEntityKeys, attachmentPath);
        }
      }
    }
  }

  private void revertLinksForComposition(
      DraftCancelEventContext context,
      Map<String, Object> parentKeys,
      String attachmentCompositionDefinition)
      throws IOException {

    CdsModel model = context.getModel();
    String draftEntityName = attachmentCompositionDefinition + "_drafts";
    CdsEntity draftEntity = model.findEntity(draftEntityName).get();
    CdsEntity activeEntity = model.findEntity(attachmentCompositionDefinition).get();

    final String upIdKey = SDMUtils.getUpIdKey(draftEntity);
    if (upIdKey == null || upIdKey.isEmpty()) {
      return;
    }
    String parentKeyName = upIdKey.replaceFirst("^up__", "");
    Object parentId = parentKeys.get(parentKeyName);

    CqnSelect selectDraftLinks =
        Select.from(draftEntity)
            .where(
                a ->
                    a.get(upIdKey)
                        .eq(parentId)
                        .and(a.get("mimeType").eq(SDMConstants.MIMETYPE_INTERNET_SHORTCUT))
                        .and(a.get("IsActiveEntity").eq(false)));

    Result draftLinks = persistenceService.run(selectDraftLinks);
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    Boolean isSystemUser = context.getUserInfo().isSystemUser();

    for (Row draftLinkRow : draftLinks) {
      Map<String, Object> draftLink = new HashMap<>();
      draftLink.put("ID", draftLinkRow.get("ID"));
      draftLink.put("linkUrl", draftLinkRow.get("linkUrl"));
      draftLink.put("objectId", draftLinkRow.get("objectId"));
      draftLink.put("fileName", draftLinkRow.get("fileName"));
      String attachmentId = (String) draftLink.get("ID");
      String draftLinkUrl = (String) draftLink.get("linkUrl");
      String objectId = (String) draftLink.get("objectId");
      String filename = (String) draftLink.get("fileName");

      String originalUrl =
          getOriginalUrlFromActiveTable(activeEntity, attachmentId, parentId, upIdKey);

      if (originalUrl != null && !originalUrl.equals(draftLinkUrl)) {
        revertLinkInSDM(objectId, filename, originalUrl, sdmCredentials, isSystemUser);
      }
    }
  }

  private String getOriginalUrlFromActiveTable(
      CdsEntity activeEntity, String attachmentId, Object parentId, String upIdKey) {
    CqnSelect selectActiveLink =
        Select.from(activeEntity)
            .columns("linkUrl")
            .where(
                a ->
                    a.get("ID")
                        .eq(attachmentId)
                        .and(a.get(upIdKey).eq(parentId))
                        .and(a.get("IsActiveEntity").eq(true))
                        .and(a.get("mimeType").eq(SDMConstants.MIMETYPE_INTERNET_SHORTCUT)));

    Result activeResult = persistenceService.run(selectActiveLink);

    if (activeResult.rowCount() > 0) {
      Row activeRow = activeResult.single();
      String originalUrl =
          activeRow.get("linkUrl") != null ? activeRow.get("linkUrl").toString() : null;
      return originalUrl;
    } else {
      return null;
    }
  }

  private void revertLinkInSDM(
      String objectId,
      String filename,
      String originalUrl,
      SDMCredentials sdmCredentials,
      Boolean isSystemUser)
      throws IOException {

    CmisDocument cmisDocToRevert = new CmisDocument();
    cmisDocToRevert.setObjectId(objectId);
    cmisDocToRevert.setFileName(filename);

    cmisDocToRevert.setUrl(originalUrl);
    cmisDocToRevert.setRepositoryId(SDMConstants.REPOSITORY_ID);
    sdmService.editLink(cmisDocToRevert, sdmCredentials, isSystemUser);
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
    if (cmisDocument.getMimeType().equalsIgnoreCase(SDMConstants.MIMETYPE_INTERNET_SHORTCUT)) {
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
      String errorMessage =
          eventContext
              .getCdsRuntime()
              .getLocalizedMessage(
                  "SDM.Repository.versionedRepoError",
                  null,
                  eventContext.getParameterInfo().getLocale());
      if (errorMessage.equalsIgnoreCase(SDMConstants.VERSIONED_REPO_ERROR_MSG))
        throw new ServiceException(SDMConstants.VERSIONED_REPO_ERROR);
      throw new ServiceException(errorMessage);
    }
  }

  private void createLink(EventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CdsModel cdsModel = context.getModel();

    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");

    String upIdKey =
        attachmentDraftEntity.isPresent()
            ? SDMUtils.getUpIdKey(attachmentDraftEntity.get())
            : "up__ID";
    CqnSelect select = (CqnSelect) context.get("cqn");
    // Get parent entity to extract its key names dynamically
    String parentEntityName = null;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(select.toString());
    JsonNode refArray = root.path("SELECT").path("from").path("ref");
    if (refArray.isArray() && refArray.size() >= 2) {
      JsonNode parentNode = refArray.get(refArray.size() - 2);
      parentEntityName = parentNode.path("id").asText();
    }

    Optional<CdsEntity> parentEntity =
        parentEntityName != null ? cdsModel.findEntity(parentEntityName) : Optional.empty();

    String upID = fetchUPIDFromCQN(select, parentEntity.orElse(null));
    String filenameInRequest = context.get("name").toString();

    Result result =
        dbQuery.getAttachmentsForUPID(
            attachmentDraftEntity.get(), persistenceService, upID, upIdKey);

    checkAttachmentConstraints(context, attachmentDraftEntity.get(), upID, upIdKey);
    validateLinkName(filenameInRequest, result);

    Boolean isSystemUser = context.getUserInfo().isSystemUser();
    String[] parts = context.getTarget().getQualifiedName().split("\\.");
    String entityName = parts[parts.length - 1];
    String folderName = upID + "__" + entityName;

    String folderId = sdmService.getFolderId(result, persistenceService, folderName, isSystemUser);

    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFolderId(folderId);
    cmisDocument.setFileName(filenameInRequest);
    cmisDocument.setMimeType(SDMConstants.MIMETYPE_INTERNET_SHORTCUT);
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
        String errorMessage =
            context
                .getCdsRuntime()
                .getLocalizedMessage(
                    "SDM.Authorization.userNotAuthorizedError",
                    null,
                    context.getParameterInfo().getLocale());
        if (errorMessage.equalsIgnoreCase(SDMConstants.USER_NOT_AUTHORISED_ERROR_MSG))
          throw new ServiceException(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG);
        throw new ServiceException(errorMessage);
      } else {
        String errorMessage =
            context
                .getCdsRuntime()
                .getLocalizedMessage(
                    "SDM.Link.failedToEditLinkError", null, context.getParameterInfo().getLocale());
        if (errorMessage.equalsIgnoreCase(SDMConstants.FAILED_TO_EDIT_LINK_MSG))
          throw new ServiceException(SDMConstants.FAILED_TO_EDIT_LINK);
        throw new ServiceException(errorMessage);
      }
    }
    context.setCompleted();
  }

  /**
   * Retrieves the key element names from a CdsEntity. This method extracts the names of all key
   * fields defined in the entity, allowing for dynamic key field handling instead of hardcoding
   * "ID".
   *
   * @param entity the CdsEntity to extract key element names from
   * @return a list of key element names
   */
  private List<String> getKeyElementNames(CdsEntity entity) {
    return entity.elements().filter(CdsElement::isKey).map(CdsElement::getName).toList();
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
        String errorMessage =
            context
                .getCdsRuntime()
                .getLocalizedMessage(
                    "SDM.Attachments.maxCountError", null, context.getParameterInfo().getLocale());
        if (errorMessage.equalsIgnoreCase(ATTACHMENT_MAXCOUNT_ERROR_MSG))
          throw new ServiceException(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, maxCount));
        throw new ServiceException(errorMessage);
      }
      throw new ServiceException(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, maxCount));
    }
  }

  private void validateLinkName(String filename, Result result) throws ServiceException {
    if (filename == null || filename.isBlank()) {
      throw new ServiceException(SDMConstants.FILENAME_WHITESPACE_ERROR_MESSAGE);
    }
    if (SDMUtils.hasRestrictedCharactersInName(filename)) {
      throw new ServiceException(
          SDMConstants.nameConstraintMessage(Collections.singletonList(filename)));
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
        String errorMessage =
            context
                .getCdsRuntime()
                .getLocalizedMessage(
                    "SDM.Authorization.userNotAuthorizedLinkError",
                    null,
                    context.getParameterInfo().getLocale());
        if (errorMessage.equalsIgnoreCase(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK_MSG))
          throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK);
        throw new ServiceException(errorMessage);
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

  private String fetchUPIDFromCQN(CqnSelect select, CdsEntity parentEntity) {
    try {
      String upID = null;
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(select.toString());
      JsonNode refArray = root.path("SELECT").path("from").path("ref");
      JsonNode secondLast = refArray.get(refArray.size() - 2);
      JsonNode whereArray = secondLast.path("where");

      // Get the actual key field names from the parent entity
      List<String> keyElementNames = getKeyElementNames(parentEntity);

      for (int i = 0; i < whereArray.size(); i++) {
        JsonNode node = whereArray.get(i);

        if (node.has("ref") && node.get("ref").isArray()) {
          String fieldName = node.get("ref").get(0).asText();

          if (keyElementNames.contains(fieldName) && !fieldName.equals("IsActiveEntity")) {
            JsonNode valNode = whereArray.get(i + 2);
            upID = valNode.path("val").asText();
            break;
          }
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
