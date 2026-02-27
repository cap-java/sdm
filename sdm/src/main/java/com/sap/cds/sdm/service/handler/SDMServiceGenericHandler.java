package com.sap.cds.sdm.service.handler;

import com.sap.cds.Result;
import com.sap.cds.Row;
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
import com.sap.cds.sdm.constants.SDMErrorMessages;
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

  @On(event = "changelog")
  public void changelog(AttachmentLogContext context) throws IOException {
    logger.debug("START: Changelog event");
    CdsModel cdsModel = context.getModel();

    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);

    Optional<CdsEntity> attachmentEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");

    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();

    // get the objectId against the Id
    String id = targetKeys.get("ID").toString();
    logger.debug("Fetching changelog for attachment ID: {}", id);

    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);

    if (cmisDocument.getFileName() == null || cmisDocument.getFileName().isEmpty()) {
      // open attachment is triggered on non-draft entity
      logger.debug("Draft entity returned empty fileName, fetching from active entity");
      attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName());

      cmisDocument =
          dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);
    }

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();

    JSONObject jsonObject =
        sdmService.getChangeLog(
            cmisDocument.getObjectId(), sdmCredentials, context.getUserInfo().isSystemUser());

    jsonObject.put("filename", cmisDocument.getFileName());
    logger.info("Changelog fetched for objectId: {}", cmisDocument.getObjectId());

    context.setResult(jsonObject);
    logger.debug("END: Changelog event");
  }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    logger.debug("START: Copy attachments event");
    String upID = context.get("up__ID").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();
    logger.debug("Copy request - upID: {}, objectIds count: {}", upID, objectIds.size());

    // Use the full target qualified name as the facet
    String facet = context.getTarget().getQualifiedName();

    var copyEventInput = new CopyAttachmentInput(upID, facet, objectIds);

    attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
    logger.info("Copy attachments completed for upID: {}", upID);
    context.setCompleted();
    logger.debug("END: Copy attachments event");
  }

  @On(event = "moveAttachments")
  public void moveAttachments(AttachmentMoveRequestContext context) throws IOException {
    logger.debug("START: Move attachments event");
    String upID = context.get("up__ID").toString();
    String sourceFolderId = context.get("sourceFolderId").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();
    String sourceFacet =
        context.get("sourceFacet") != null ? context.get("sourceFacet").toString() : null;
    String targetFacet = context.get("targetFacet").toString();
    logger.debug(
        "Move request - upID: {}, sourceFolderId: {}, targetFacet: {}, objectIds count: {}",
        upID,
        sourceFolderId,
        targetFacet,
        objectIds.size());
    var moveEventInput =
        new MoveAttachmentInput(sourceFolderId, upID, targetFacet, objectIds, sourceFacet);

    Map<String, Object> result =
        attachmentService.moveAttachments(moveEventInput, context.getUserInfo().isSystemUser());

    logger.info("Move operation result: {}", result);

    context.setResult(result);
    context.setCompleted();
    logger.debug("END: Move attachments event");
  }

  @On(event = "createLink")
  public void create(EventContext context) throws IOException {
    logger.debug("START: Create link event");
    validateRepository(context);
    createLink(context);
    logger.debug("END: Create link event");
  }

  @On(event = "editLink")
  public void edit(EventContext context) throws IOException {
    logger.debug("START: Edit link event for {}", context.getEvent());
    editLink(context);
    logger.debug("END: Edit link event");
  }

  @Before(event = DraftService.EVENT_DRAFT_CANCEL)
  public void handleDraftDiscardForLinks(DraftCancelEventContext context) throws IOException {
    logger.debug("START: Handle draft discard for links");
    CdsEntity parentDraftEntity = context.getTarget();
    CqnAnalyzer analyzer = CqnAnalyzer.create(context.getModel());
    Map<String, Object> parentKeys = analyzer.analyze(context.getCqn()).rootKeys();
    String parentEntityName = parentDraftEntity.getQualifiedName().replace("_drafts", "");
    logger.debug("Processing draft cancel for entity: {}", parentEntityName);

    Optional<CdsEntity> parentActiveEntityOpt = context.getModel().findEntity(parentEntityName);
    Map<String, String> compositionPathMapping =
        parentActiveEntityOpt
            .map(
                cdsEntity ->
                    AttachmentsHandlerUtils.getAttachmentPathMapping(
                        context.getModel(), cdsEntity, persistenceService))
            .orElse(new HashMap<>());

    logger.debug("Found {} composition paths to process", compositionPathMapping.size());
    for (Map.Entry<String, String> entry : compositionPathMapping.entrySet()) {
      String attachmentCompositionDefinition = entry.getKey();
      revertLinksForComposition(context, parentKeys, attachmentCompositionDefinition);
    }
    revertNestedEntityLinks(context);
    logger.debug("END: Handle draft discard for links");
  }

  private void revertNestedEntityLinks(DraftCancelEventContext context) throws IOException {
    logger.debug("START: Revert nested entity links");

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
    logger.debug("END: Revert nested entity links");
  }

  private void processNestedEntityComposition(
      DraftCancelEventContext context, CdsElement composition) throws IOException {
    logger.debug("Processing nested entity composition: {}", composition.getName());

    CdsAssociationType associationType = (CdsAssociationType) composition.getType();
    String targetEntityName = associationType.getTarget().getQualifiedName();
    String draftTargetEntityName = targetEntityName + "_drafts";

    Optional<CdsEntity> nestedDraftEntity = context.getModel().findEntity(draftTargetEntityName);

    if (nestedDraftEntity.isPresent()) {
      Map<String, String> nestedAttachmentMapping =
          AttachmentsHandlerUtils.getAttachmentPathMapping(
              context.getModel(), associationType.getTarget(), persistenceService);

      if (nestedAttachmentMapping.isEmpty()) {
        logger.debug("No attachment mapping found for nested entity: {}", targetEntityName);
        return;
      }

      // Get the actual key field names from the entity instead of hardcoding "ID"
      List<String> keyElementNames = getKeyElementNames(nestedDraftEntity.get());

      Result nestedRecords =
          persistenceService.run(
              Select.from(nestedDraftEntity.get()).where(e -> e.get("IsActiveEntity").eq(false)));
      logger.debug("Found {} nested records to process", nestedRecords.rowCount());

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
    logger.debug("Reverting links for composition: {}", attachmentCompositionDefinition);

    CdsModel model = context.getModel();
    String draftEntityName = attachmentCompositionDefinition + "_drafts";
    CdsEntity draftEntity = model.findEntity(draftEntityName).get();
    CdsEntity activeEntity = model.findEntity(attachmentCompositionDefinition).get();

    final String upIdKey = SDMUtils.getUpIdKey(draftEntity);
    if (upIdKey == null || upIdKey.isEmpty()) {
      logger.debug("No upIdKey found, skipping revert for: {}", attachmentCompositionDefinition);
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
    logger.debug("Found {} draft links to process", draftLinks.rowCount());
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
        logger.debug("Reverting link {} from {} to {}", objectId, draftLinkUrl, originalUrl);
        revertLinkInSDM(objectId, filename, originalUrl, sdmCredentials, isSystemUser);
      }
    }
  }

  private String getOriginalUrlFromActiveTable(
      CdsEntity activeEntity, String attachmentId, Object parentId, String upIdKey) {
    logger.debug("Fetching original URL for attachment: {}", attachmentId);
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
      logger.debug("Found original URL: {}", originalUrl);
      return originalUrl;
    } else {
      logger.debug("No original URL found for attachment: {}", attachmentId);
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
    logger.debug("Reverting link in SDM - objectId: {}, filename: {}", objectId, filename);

    CmisDocument cmisDocToRevert = new CmisDocument();
    cmisDocToRevert.setObjectId(objectId);
    cmisDocToRevert.setFileName(filename);

    cmisDocToRevert.setUrl(originalUrl);
    cmisDocToRevert.setRepositoryId(SDMConstants.REPOSITORY_ID);
    sdmService.editLink(cmisDocToRevert, sdmCredentials, isSystemUser);
    logger.debug("Link reverted successfully in SDM for objectId: {}", objectId);
  }

  @On(event = "openAttachment")
  public void openAttachment(AttachmentReadContext context) throws Exception {
    logger.debug("START: Open attachment event");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    // get the objectId against the Id
    String id = targetKeys.get("ID").toString();
    logger.debug("Opening attachment with ID: {}", id);
    CmisDocument cmisDocument =
        dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument
            .getUploadStatus()
            .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED))
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_DETECTED_FILE_ERROR"));
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS))
      throw new ServiceException(SDMUtils.getErrorMessage("VIRUS_SCAN_IN_PROGRESS_FILE_ERROR"));
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS))
      throw new ServiceException(SDMUtils.getErrorMessage("UPLOAD_IN_PROGRESS_FILE_ERROR"));

    if (cmisDocument.getFileName() == null || cmisDocument.getFileName().isEmpty()) {
      // open attachment is triggered on non-draft entity
      attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName());
      cmisDocument =
          dbQuery.getObjectIdForAttachmentID(attachmentEntity.get(), persistenceService, id);
    }

    if (cmisDocument.getMimeType().equalsIgnoreCase(SDMConstants.MIMETYPE_INTERNET_SHORTCUT)) {
      // Verify access to the object by calling getObject from SDMService
      try {
        SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
        JSONObject objectResponse =
            sdmService.getObject(
                cmisDocument.getObjectId(), sdmCredentials, context.getUserInfo().isSystemUser());

        if (objectResponse == null) {
          logger.warn("File not found in SDM for objectId: {}", cmisDocument.getObjectId());
          throw new ServiceException(SDMConstants.FILE_NOT_FOUND_ERROR);
        }
      } catch (ServiceException e) {
        if (e.getMessage() != null
            && e.getMessage().contains("User does not have required scope")) {
          logger.warn("User not authorized to open link: {}", cmisDocument.getObjectId());
          throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR_OPEN_LINK);
        }
        throw e;
      }
      logger.info("Opening link attachment: {}", cmisDocument.getFileName());
      context.setResult(cmisDocument.getUrl());
    } else {
      logger.debug("Attachment is not a link, returning None");
      context.setResult("None");
    }
    logger.debug("END: Open attachment event");
  }

  private void validateRepository(EventContext eventContext) throws ServiceException, IOException {
    logger.debug("Validating repository");
    String repositoryId = SDMConstants.REPOSITORY_ID;
    RepoValue repoValue =
        sdmService.checkRepositoryType(repositoryId, eventContext.getUserInfo().getTenant());
    if (repoValue.getVersionEnabled()) {
      logger.warn("Repository is versioned which is not allowed: {}", repositoryId);
      throw new ServiceException(SDMUtils.getErrorMessage("VERSIONED_REPO_ERROR"));
    }
    logger.debug("Repository validation successful");
  }

  private void createLink(EventContext context) throws IOException {
    logger.debug("START: Create link");
    String repositoryId = SDMConstants.REPOSITORY_ID;
    CdsModel cdsModel = context.getModel();

    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");

    String upIdKey =
        attachmentDraftEntity.isPresent()
            ? SDMUtils.getUpIdKey(attachmentDraftEntity.get())
            : "up__ID";
    CqnSelect select = (CqnSelect) context.get("cqn");

    // Derive the parent entity name from the target qualified name
    // Target is like "AdminService.Chapters.attachments", parent is "AdminService.Chapters"
    String targetQualifiedName = context.getTarget().getQualifiedName();
    String parentEntityName = null;
    int lastDotIndex = targetQualifiedName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      parentEntityName = targetQualifiedName.substring(0, lastDotIndex);
    }

    Optional<CdsEntity> parentEntity =
        parentEntityName != null ? cdsModel.findEntity(parentEntityName) : Optional.empty();

    if (parentEntity.isEmpty()) {
      throw new ServiceException(SDMUtils.getErrorMessage("ENTITY_PROCESSING_ERROR_LINK"));
    }

    String upID = SDMUtils.fetchUPIDFromCQN(select, parentEntity.get());
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
    logger.debug("Creating link - fileName: {}, folderId: {}", filenameInRequest, folderId);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    JSONObject createResult = null;

    try {
      createResult =
          documentService.createDocument(cmisDocument, sdmCredentials, isSystemUser, null);
    } catch (Exception e) {
      logger.error("Failed to create link: {}", e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("ENTITY_PROCESSING_ERROR_LINK"), e);
    }
    handleCreateLinkResult(cmisDocument, createResult, context, upID, upIdKey);
    logger.debug("END: Create link");
  }

  private void editLink(EventContext context) throws IOException {
    logger.debug("START: Edit link");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    Map<String, Object> targetKeys =
        cqnAnalyzer.analyze((CqnSelect) context.get("cqn")).targetKeyValues();
    String ID = targetKeys.get("ID").toString();
    logger.debug("Editing link with ID: {}", ID);
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
      logger.info("Successfully edited link for ID: {}", ID);
    } else {
      if (status.equals("unauthorized")) {
        logger.warn("User not authorized to edit link");
        throw new ServiceException(SDMUtils.getErrorMessage("SDM_MISSING_ROLES_EXCEPTION"));
      } else {
        logger.error("Failed to edit link - status: {}", status);
        throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_EDIT_LINK"));
      }
    }
    context.setCompleted();
    logger.debug("END: Edit link");
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
    logger.debug("Checking attachment constraints for upID: {}", upID);
    CdsModel cdsModel = context.getModel();
    CdsEntity attachmentEntity = cdsModel.findEntity(context.getTarget().getQualifiedName()).get();

    // Fetch the row count for current repository
    Result result =
        dbQuery.getAttachmentsForUPIDAndRepository(
            attachmentDraftEntity, persistenceService, upID, upIdKey);
    long rowCount = result.rowCount();
    Long maxCount =
        SDMUtils.getAttachmentCountAndMessage(
            context.getModel().entities().toList(), attachmentEntity);
    logger.debug("Current count: {}, Max allowed: {}", rowCount, maxCount);
    if (maxCount > 0 && rowCount >= maxCount) {
      logger.warn("Attachment count {} exceeds max allowed {}", rowCount, maxCount);
      throw new ServiceException(
          String.format(SDMUtils.getErrorMessage("MAX_COUNT_ERROR_MESSAGE"), maxCount.toString()));
    }
  }

  private void validateLinkName(String filename, Result result) throws ServiceException {
    logger.debug("Validating link name: {}", filename);
    if (filename == null || filename.isBlank()) {
      logger.error("Link name is blank or null");
      throw new ServiceException(SDMUtils.getErrorMessage("FILENAME_WHITESPACE_ERROR_MESSAGE"));
    }
    if (SDMUtils.hasRestrictedCharactersInName(filename)) {
      logger.warn("Link name contains restricted characters: {}", filename);
      throw new ServiceException(
          SDMErrorMessages.nameConstraintMessage(Collections.singletonList(filename)));
    }
    if (duplicateCheck(filename, result)) {
      logger.warn("Duplicate link name detected: {}", filename);
      throw new ServiceException(SDMErrorMessages.getDuplicateFilesError(filename));
    }
    logger.debug("Link name validation passed");
  }

  public boolean duplicateCheck(String filenameToCheck, Result result) {
    logger.debug("Checking for duplicate: {}", filenameToCheck);
    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream().map(m -> (Map<String, Object>) m).toList();
    boolean isDuplicate =
        resultList.stream()
            .anyMatch(
                attachment ->
                    filenameToCheck.equals(attachment.get("fileName"))
                        && SDMConstants.REPOSITORY_ID.equals(attachment.get("repositoryId")));
    logger.debug("Duplicate check result for {}: {}", filenameToCheck, isDuplicate);
    return isDuplicate;
  }

  private void handleCreateLinkResult(
      CmisDocument cmisDocument,
      JSONObject createResult,
      EventContext context,
      String upID,
      String upIdKey)
      throws ServiceException {
    logger.debug("Handling create link result");
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String status = createResult.get("status").toString();
    logger.debug("Create link result status: {}", status);

    switch (status) {
      case "duplicate":
        logger.warn("Duplicate link detected: {}", cmisDocument.getFileName());
        throw new ServiceException(
            SDMErrorMessages.getDuplicateFilesError(cmisDocument.getFileName()));
      case "fail":
        logger.error("Link creation failed: {}", createResult.get("message"));
        throw new ServiceException(createResult.get("message").toString());
      case "unauthorized":
        logger.warn("User not authorized to create link");
        throw new ServiceException(SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR_LINK"));
      default:
        cmisDocument.setObjectId(createResult.get("objectId").toString());
        cmisDocument.setParentId(upID);
        logger.info(
            "Link created successfully - objectId: {}, fileName: {}",
            cmisDocument.getObjectId(),
            cmisDocument.getFileName());

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
        updatedFields.put("uploadStatus", SDMConstants.UPLOAD_STATUS_SUCCESS);

        try {
          var insert = Insert.into(context.getTarget().getQualifiedName()).entry(updatedFields);
          for (DraftService draftS : draftService) {
            if (context.getTarget().getQualifiedName().contains(draftS.getName())) {
              draftS.newDraft(insert);
            }
          }
          logger.debug("Link draft entry created successfully");
        } catch (Exception e) {
          logger.error("Exception in insert: {}", e.getMessage(), e);
        }
        context.setCompleted();
    }
  }
}
