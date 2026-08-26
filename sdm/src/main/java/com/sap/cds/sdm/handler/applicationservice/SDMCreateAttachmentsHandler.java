package com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.constants.SDMConstants.SDM_READONLY_CONTEXT;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.draft.DraftSaveEventContext;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.OrderConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateAttachmentsHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;
  private static final Logger logger = LoggerFactory.getLogger(SDMCreateAttachmentsHandler.class);

  // compositionEntityPath → list of (attachmentId, uploadStatus) captured from draft rows
  private static final ThreadLocal<Map<String, List<CmisDocument>>>
      DRAFT_UPLOAD_STATUS_THREADLOCAL = new ThreadLocal<>();

  // Safety bound for the recursive draft composition-tree walk.
  private static final int MAX_DRAFT_TREE_DEPTH = 10;

  public SDMCreateAttachmentsHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  /**
   * After handler for ApplicationService CREATE to update active entity attachments with SDM
   * metadata (objectId, folderId, repositoryId, etc.) after the record has been INSERTed.
   *
   * <p>During active entity attachment creation, the AttachmentService @On handler uploads to SDM
   * and stores metadata in a ThreadLocal. The framework then INSERTs the record with contentId (set
   * via finalizeContext). This @After handler runs AFTER the INSERT, so the record exists and can
   * be UPDATEd with the remaining SDM metadata.
   */
  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void updateActiveEntitySdmMetadata(CdsCreateEventContext _context) {
    handleUpdateActiveEntitySdmMetadata();
  }

  private void handleUpdateActiveEntitySdmMetadata() {
    logger.debug(
        "[CREATE] handleUpdateActiveEntitySdmMetadata: checking ThreadLocal for SDM metadata");
    Map<String, Object> metadata = SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get();
    if (metadata == null) {
      logger.debug(
          "[CREATE] handleUpdateActiveEntitySdmMetadata: no ThreadLocal metadata found, skipping");
      return;
    }
    try {
      SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.remove();
      logger.debug(
          "[CREATE] handleUpdateActiveEntitySdmMetadata: ThreadLocal metadata keys: {}",
          metadata.keySet());
      com.sap.cds.reflect.CdsEntity attachmentEntity =
          (com.sap.cds.reflect.CdsEntity) metadata.get("attachmentEntity");
      if (attachmentEntity == null) {
        logger.warn("No attachmentEntity in ThreadLocal metadata, skipping post-INSERT update");
        return;
      }
      logger.debug(
          "[CREATE] handleUpdateActiveEntitySdmMetadata: attachmentEntity={}",
          attachmentEntity.getQualifiedName());
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setAttachmentId((String) metadata.get("attachmentId"));
      cmisDocument.setObjectId((String) metadata.get("objectId"));
      cmisDocument.setFolderId((String) metadata.get("folderId"));
      cmisDocument.setMimeType((String) metadata.get("mimeType"));
      cmisDocument.setUploadStatus((String) metadata.get("uploadStatus"));
      logger.debug(
          "[CREATE] handleUpdateActiveEntitySdmMetadata: cmisDocument attachmentId={} objectId={} folderId={} mimeType={} uploadStatus={}",
          cmisDocument.getAttachmentId(),
          cmisDocument.getObjectId(),
          cmisDocument.getFolderId(),
          cmisDocument.getMimeType(),
          cmisDocument.getUploadStatus());
      logger.info(
          "Post-INSERT: Updating active entity attachment {} with objectId {}",
          cmisDocument.getAttachmentId(),
          cmisDocument.getObjectId());
      dbQuery.addAttachmentToDraft(attachmentEntity, persistenceService, cmisDocument);
      logger.info("Post-INSERT: Successfully updated active entity attachment with SDM metadata");
    } catch (Exception e) {
      logger.error(
          "Failed to update active entity SDM metadata after INSERT: {}", e.getMessage(), e);
    }
  }

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsCreateEventContext context, List<CdsData> data) throws IOException {
    logger.info(
        "START: Process attachments before persistence for entity: {}",
        context.getTarget().getQualifiedName());
    logger.info("Number of entities to process: {}", data.size());

    for (CdsData entityData : data) {
      Map<String, Map<String, String>> attachmentCompositionDetails =
          AttachmentsHandlerUtils.getAttachmentCompositionDetails(
              context.getModel(),
              context.getTarget(),
              persistenceService,
              context.getTarget().getQualifiedName(),
              entityData);
      logger.info("Attachment compositions found: {}", attachmentCompositionDetails.keySet());
      updateName(context, data, attachmentCompositionDetails);
      // Remove uploadStatus from attachment data to prevent validation errors
      cleanupReadonlyContextsForAttachments(context, entityData, attachmentCompositionDetails);
    }
    logger.info("END: Process attachments before persistence");
  }

  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void processAfter(CdsCreateEventContext context, List<CdsData> data) {
    // Update uploadStatus to Success after entity is persisted
    logger.info(
        "START: Post-processing attachments after persistence for entity: {}",
        context.getTarget().getQualifiedName());

    int totalProcessed = 0;
    for (CdsData entityData : data) {
      Map<String, Map<String, String>> attachmentCompositionDetails =
          AttachmentsHandlerUtils.getAttachmentCompositionDetails(
              context.getModel(),
              context.getTarget(),
              persistenceService,
              context.getTarget().getQualifiedName(),
              entityData);

      for (Map.Entry<String, Map<String, String>> entry : attachmentCompositionDetails.entrySet()) {
        String attachmentCompositionDefinition = entry.getKey();
        String attachmentCompositionName = entry.getValue().get("name");
        Optional<CdsEntity> attachmentEntity =
            context.getModel().findEntity(attachmentCompositionDefinition);

        if (!attachmentEntity.isPresent()) {
          logger.warn(
              "[SDM] CREATE: Attachment entity '{}' not found in CDS model — skipping uploadStatus persistence for composition '{}'",
              attachmentCompositionDefinition,
              attachmentCompositionName);
          continue;
        }

        String targetEntity = context.getTarget().getQualifiedName();
        List<Map<String, Object>> attachments =
            AttachmentsHandlerUtils.fetchAttachments(
                targetEntity, entityData, attachmentCompositionName);

        if (attachments != null && !attachments.isEmpty()) {
          logger.info(
              "[SDM] CREATE: Persisting uploadStatus for {} attachment(s) in composition '{}'",
              attachments.size(),
              attachmentCompositionName);
          for (Map<String, Object> attachment : attachments) {
            String id = (String) attachment.get("ID");
            String uploadStatus = (String) attachment.get("uploadStatus");
            if (id != null) {
              logger.debug("Saving uploadStatus '{}' for attachment ID: {}", uploadStatus, id);
              CmisDocument cmisDocument = new CmisDocument();
              cmisDocument.setAttachmentId(id);
              cmisDocument.setUploadStatus(uploadStatus);
              dbQuery.saveUploadStatusToAttachment(
                  attachmentEntity.get(), persistenceService, cmisDocument);
              totalProcessed++;
            } else {
              logger.warn(
                  "[SDM] CREATE: Attachment in composition '{}' has no ID — skipping uploadStatus persistence",
                  attachmentCompositionName);
            }
          }
        } else {
          logger.debug(
              "No attachments in payload for composition '{}' during post-processing",
              attachmentCompositionName);
        }
      }
    }
    logger.info("END: Post-processing completed. Processed {} attachments", totalProcessed);
  }

  @Before
  @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES - 500)
  public void preserveUploadStatus(CdsCreateEventContext context, List<CdsData> data) {
    // Preserve uploadStatus before CDS removes readonly fields
    logger.debug(
        "[CREATE] preserveUploadStatus: entity={} dataSize={}",
        context.getTarget().getQualifiedName(),
        data.size());
    SDMUtils.preserveReadonlyFields(context.getTarget(), data);
    logger.debug(
        "[CREATE] preserveUploadStatus: SDM_READONLY_CONTEXT set on attachment maps via CdsDataProcessor");
  }

  /**
   * Captures uploadStatus from draft attachment rows into a ThreadLocal before CAP activates the
   * draft. CAP strips @readonly fields (including uploadStatus) when writing active rows, so we
   * must read the correct values while the draft rows still exist and re-apply them in @After.
   */
  @Before(event = DraftService.EVENT_DRAFT_SAVE)
  @HandlerOrder(HandlerOrder.EARLY + 500)
  public void captureUploadStatusBeforeDraftSave(DraftSaveEventContext context) {
    try {
      CdsEntity draftEntity = context.getTarget();
      String draftName = draftEntity.getQualifiedName();
      String activeName =
          draftName.endsWith("_drafts")
              ? draftName.substring(0, draftName.length() - 7)
              : draftName;

      CdsModel model = context.getModel();
      Optional<CdsEntity> activeRootOpt = model.findEntity(activeName);
      if (activeRootOpt.isEmpty()) return;

      Object parentId = extractParentId(context, draftEntity);
      if (parentId == null) {
        logger.warn("[DRAFT_SAVE] Could not extract parent ID for entity: {}", draftName);
        return;
      }

      // Walk the whole composition tree so attachments at any nesting depth (e.g.
      // Root -> Chapters -> attachments) are captured, not just direct compositions of the root.
      Map<String, List<CmisDocument>> statusMap = new HashMap<>();
      walkCompositionsForAttachments(model, activeRootOpt.get(), parentId, statusMap, 0);

      if (!statusMap.isEmpty()) {
        DRAFT_UPLOAD_STATUS_THREADLOCAL.set(statusMap);
        logger.debug(
            "[DRAFT_SAVE] Captured upload statuses for {} composition(s)", statusMap.size());
      }
    } catch (Exception e) {
      logger.error(
          "[DRAFT_SAVE] Failed to capture upload statuses before draft save: {}",
          e.getMessage(),
          e);
    }
  }

  /**
   * Restores uploadStatus values (captured in @Before) onto the newly written active attachment
   * rows. Without this, CAP applies the CDS default 'uploading' to all activated attachments.
   */
  @After(event = DraftService.EVENT_DRAFT_SAVE)
  @HandlerOrder(HandlerOrder.LATE)
  public void applyUploadStatusAfterDraftSave(DraftSaveEventContext context) {
    Map<String, List<CmisDocument>> statusMap = DRAFT_UPLOAD_STATUS_THREADLOCAL.get();
    if (statusMap == null || statusMap.isEmpty()) return;
    try {
      DRAFT_UPLOAD_STATUS_THREADLOCAL.remove();
      CdsModel model = context.getModel();
      for (Map.Entry<String, List<CmisDocument>> entry : statusMap.entrySet()) {
        Optional<CdsEntity> activeEntityOpt = model.findEntity(entry.getKey());
        if (activeEntityOpt.isEmpty()) {
          logger.warn("[DRAFT_SAVE] Active entity not found for composition: {}", entry.getKey());
          continue;
        }
        CdsEntity activeEntity = activeEntityOpt.get();
        for (CmisDocument doc : entry.getValue()) {
          dbQuery.saveUploadStatusToAttachment(activeEntity, persistenceService, doc);
          logger.info(
              "[DRAFT_SAVE] Restored uploadStatus='{}' for attachment ID={}",
              doc.getUploadStatus(),
              doc.getAttachmentId());
        }
      }
    } catch (Exception e) {
      logger.error(
          "[DRAFT_SAVE] Failed to apply upload statuses after draft save: {}", e.getMessage(), e);
    } finally {
      DRAFT_UPLOAD_STATUS_THREADLOCAL.remove();
    }
  }

  private Object extractParentId(DraftSaveEventContext context, CdsEntity draftEntity) {
    try {
      CqnSelect cqn = context.getCqn();
      if (cqn == null) return null;
      Map<String, Object> keys = CqnAnalyzer.create(context.getModel()).analyze(cqn).rootKeys();
      return draftEntity
          .elements()
          .filter(el -> el.isKey() && !"IsActiveEntity".equals(el.getName()))
          .map(el -> keys.get(el.getName()))
          .filter(v -> v != null)
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      logger.warn("[DRAFT_SAVE] Could not extract parent ID from CQN: {}", e.getMessage());
      return null;
    }
  }

  private void collectDraftUploadStatuses(
      CdsModel model,
      String compositionPath,
      Object parentId,
      Map<String, List<CmisDocument>> statusMap) {
    Optional<CdsEntity> draftEntityOpt = model.findEntity(compositionPath + "_drafts");
    if (draftEntityOpt.isEmpty()) return;

    CdsEntity draftEntity = draftEntityOpt.get();
    String upIdKey = SDMUtils.getUpIdKey(draftEntity);
    if (upIdKey == null || upIdKey.isEmpty()) return;

    Result rows =
        persistenceService.run(
            Select.from(draftEntity)
                .columns("ID", "uploadStatus")
                .where(a -> a.get(upIdKey).eq(parentId).and(a.get("IsActiveEntity").eq(false))));

    List<CmisDocument> docs = new ArrayList<>();
    for (Row row : rows) {
      String id = row.get("ID") != null ? row.get("ID").toString() : null;
      Object statusObj = row.get("uploadStatus");
      String uploadStatus = statusObj != null ? statusObj.toString() : null;
      if (id != null && uploadStatus != null) {
        CmisDocument doc = new CmisDocument();
        doc.setAttachmentId(id);
        doc.setUploadStatus(uploadStatus);
        docs.add(doc);
      }
    }

    if (!docs.isEmpty()) {
      // Merge, not overwrite: the same attachment entity (e.g. Root.Chapters.attachments) can be
      // reached via multiple parent rows (multiple chapters), each contributing its own docs.
      statusMap.computeIfAbsent(compositionPath, k -> new ArrayList<>()).addAll(docs);
      logger.debug(
          "[DRAFT_SAVE] Found {} attachment(s) to sync in composition '{}'",
          docs.size(),
          compositionPath);
    }
  }

  /**
   * Recursively walks the composition tree of {@code activeEntity}. For each composition that is an
   * attachment facet, captures the draft upload statuses keyed by the immediate parent's id. For
   * every other (intermediate) composition, resolves the child draft rows belonging to this parent
   * and recurses into each, so attachments at any depth are covered.
   */
  private void walkCompositionsForAttachments(
      CdsModel model,
      CdsEntity activeEntity,
      Object parentId,
      Map<String, List<CmisDocument>> statusMap,
      int depth) {
    if (depth > MAX_DRAFT_TREE_DEPTH) {
      logger.warn(
          "[DRAFT_SAVE] Max composition depth {} reached at entity: {}",
          MAX_DRAFT_TREE_DEPTH,
          activeEntity.getQualifiedName());
      return;
    }

    Set<String> attachmentCompositionPaths =
        AttachmentsHandlerUtils.getDirectAttachmentPathMapping(activeEntity).keySet();

    for (CdsElement comp : activeEntity.compositions().toList()) {
      if (!comp.getType().isAssociation()) continue;
      CdsAssociationType assocType = (CdsAssociationType) comp.getType();
      String childActiveName = assocType.getTarget().getQualifiedName().replaceAll("_drafts$", "");
      String childPath = activeEntity.getQualifiedName() + "." + comp.getName();

      if (attachmentCompositionPaths.contains(childPath)) {
        // Leaf: this composition holds attachments. up__ points at this entity instance (parentId).
        collectDraftUploadStatuses(model, childActiveName, parentId, statusMap);
      } else {
        recurseIntoChildComposition(
            model, activeEntity, childActiveName, parentId, statusMap, depth);
      }
    }
  }

  private void recurseIntoChildComposition(
      CdsModel model,
      CdsEntity parentActive,
      String childActiveName,
      Object parentId,
      Map<String, List<CmisDocument>> statusMap,
      int depth) {
    Optional<CdsEntity> childActiveOpt = model.findEntity(childActiveName);
    Optional<CdsEntity> childDraftOpt = model.findEntity(childActiveName + "_drafts");
    if (childActiveOpt.isEmpty() || childDraftOpt.isEmpty()) return;

    CdsEntity childDraft = childDraftOpt.get();
    CdsEntity childActive = childActiveOpt.get();

    String fkColumn = resolveParentFkColumn(childDraft, parentActive.getQualifiedName());
    if (fkColumn == null) {
      logger.debug(
          "[DRAFT_SAVE] No parent FK from {} back to {}, skipping recursion",
          childActiveName,
          parentActive.getQualifiedName());
      return;
    }
    String childKey = firstKeyName(childActive);

    Result rows =
        persistenceService.run(
            Select.from(childDraft)
                .columns(childKey)
                .where(c -> c.get(fkColumn).eq(parentId).and(c.get("IsActiveEntity").eq(false))));

    for (Row row : rows) {
      Object childId = row.get(childKey);
      if (childId != null) {
        walkCompositionsForAttachments(model, childActive, childId, statusMap, depth + 1);
      }
    }
  }

  /**
   * Resolves the foreign-key column on {@code childDraft} that references {@code parentActiveName}.
   * Prefers an explicit association whose target is the parent; falls back to the {@code up_}
   * convention.
   */
  private String resolveParentFkColumn(CdsEntity childDraft, String parentActiveName) {
    for (CdsElement assoc : childDraft.associations().toList()) {
      CdsAssociationType at = (CdsAssociationType) assoc.getType();
      String target = at.getTarget().getQualifiedName().replaceAll("_drafts$", "");
      if (target.equals(parentActiveName)) {
        List<String> fks = at.refs().map(ref -> assoc.getName() + "_" + ref.path()).toList();
        if (!fks.isEmpty()) return fks.get(0);
      }
    }
    String up = SDMUtils.getUpIdKey(childDraft);
    return (up != null && !up.isEmpty()) ? up : null;
  }

  private String firstKeyName(CdsEntity entity) {
    return entity
        .elements()
        .filter(el -> el.isKey() && !"IsActiveEntity".equals(el.getName()))
        .map(CdsElement::getName)
        .findFirst()
        .orElse("ID");
  }

  public void updateName(
      CdsCreateEventContext context,
      List<CdsData> data,
      Map<String, Map<String, String>> attachmentCompositionDetails)
      throws IOException {
    for (Map.Entry<String, Map<String, String>> entry : attachmentCompositionDetails.entrySet()) {
      String attachmentCompositionDefinition = entry.getKey();
      String attachmentCompositionName = entry.getValue().get("name");
      String parentTitle = entry.getValue().get("parentTitle");
      Map<String, String> propertyTitles = new HashMap<>();
      Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();
      String targetEntity = context.getTarget().getQualifiedName();
      Boolean isError = false;

      // Extract composition name (last part after the final ".")
      String compositionName = attachmentCompositionName;
      if (attachmentCompositionName != null && attachmentCompositionName.contains(".")) {
        String[] parts = attachmentCompositionName.split("\\.");
        compositionName = parts[parts.length - 1];
      }

      String contextInfo = AttachmentsHandlerUtils.getContextInfo(compositionName, parentTitle);

      Optional<CdsEntity> attachmentEntity =
          context.getModel().findEntity(attachmentCompositionDefinition);
      logger.debug(
          "[CREATE] updateName: processing composition={} entityFound={}",
          attachmentCompositionName,
          attachmentEntity.isPresent());
      isError =
          AttachmentsHandlerUtils.validateFileNames(
              context, data, attachmentCompositionName, contextInfo, attachmentEntity);
      if (isError) {
        logger.debug(
            "[CREATE] updateName: filename validation failed for composition={}, skipping SDM update",
            attachmentCompositionName);
      }
      if (!isError) {
        List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
        List<String> duplicateFileNameList = new ArrayList<>();
        List<String> filesNotFound = new ArrayList<>();
        List<String> filesWithUnsupportedProperties = new ArrayList<>();
        Map<String, String> badRequest = new HashMap<>();
        List<String> noSDMRoles = new ArrayList<>();
        for (Map<String, Object> entity : data) {
          List<Map<String, Object>> attachments =
              AttachmentsHandlerUtils.fetchAttachments(
                  targetEntity, entity, attachmentCompositionName);
          if (attachments == null || attachments.isEmpty()) {
            logger.info(
                "No attachments found for composition [{}] in entity [{}]. Skipping processing.",
                attachmentCompositionName,
                targetEntity);
            continue;
          }
          propertyTitles = SDMUtils.getPropertyTitles(attachmentEntity, attachments.get(0));
          secondaryPropertiesWithInvalidDefinitions =
              SDMUtils.getSecondaryPropertiesWithInvalidDefinition(
                  attachmentEntity, attachments.get(0));
          processEntity(
              context,
              entity,
              fileNameWithRestrictedCharacters,
              duplicateFileNameList,
              filesNotFound,
              filesWithUnsupportedProperties,
              badRequest,
              attachmentCompositionDefinition,
              attachmentEntity,
              secondaryPropertiesWithInvalidDefinitions,
              noSDMRoles,
              attachmentCompositionName);
          handleWarnings(
              context,
              fileNameWithRestrictedCharacters,
              duplicateFileNameList,
              filesNotFound,
              filesWithUnsupportedProperties,
              badRequest,
              propertyTitles,
              noSDMRoles,
              contextInfo);
        }
      }
    }
  }

  private void processEntity(
      CdsCreateEventContext context,
      Map<String, Object> entity,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      String composition,
      Optional<CdsEntity> attachmentEntity,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      String attachmentCompositionName)
      throws IOException {
    String targetEntity = context.getTarget().getQualifiedName();
    List<Map<String, Object>> attachments =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);
    List<String> scanFailedFiles = new ArrayList<>();
    List<String> uploadInProgressFiles = new ArrayList<>();

    if (attachments != null) {
      logger.debug(
          "[CREATE] processEntity: composition={} attachmentCount={}",
          attachmentCompositionName,
          attachments.size());
      for (Map<String, Object> attachment : attachments) {
        processAttachment(
            context,
            attachment,
            fileNameWithRestrictedCharacters,
            duplicateFileNameList,
            filesNotFound,
            filesWithUnsupportedProperties,
            badRequest,
            composition,
            attachmentEntity,
            secondaryPropertiesWithInvalidDefinitions,
            noSDMRoles,
            scanFailedFiles,
            uploadInProgressFiles);
      }

      // Throw exception if any files failed scan or upload in progress
      String errorMessage = buildErrorMessage(scanFailedFiles, uploadInProgressFiles);
      if (!errorMessage.isEmpty()) {
        logger.debug(
            "[CREATE] processEntity: blocking — scanFailed={} uploadInProgress={}",
            scanFailedFiles,
            uploadInProgressFiles);
        throw new ServiceException(errorMessage);
      }

      SecondaryPropertiesKey secondaryPropertiesKey =
          new SecondaryPropertiesKey(); // Emptying cache after attachments are updated in loop
      secondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
      CacheConfig.getSecondaryPropertiesCache().remove(secondaryPropertiesKey);
    }
  }

  private void processAttachment(
      CdsCreateEventContext context,
      Map<String, Object> attachment,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      String composition,
      Optional<CdsEntity> attachmentEntity,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      List<String> scanFailedFiles,
      List<String> uploadInProgressFiles)
      throws IOException {
    long startTime = System.currentTimeMillis();
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");
    logger.debug(
        "START: Process attachment - ID: {}, fileName: {}, objectId: {}",
        id,
        filenameInRequest,
        objectId);

    // Fetch original data from DB
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    String fileNameInDB = cmisDocument.getFileName();

    // Check upload status and collect problematic files
    if (checkUploadStatus(
        attachment, fileNameInDB, filenameInRequest, scanFailedFiles, uploadInProgressFiles)) {
      logger.debug("Upload status check failed, skipping further processing for ID: {}", id);
      return; // Skip further processing if upload status is problematic
    }

    // Fetch data from SDM
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    SDMAttachmentData sdmData = fetchSDMData(context, objectId, sdmCredentials);

    // Prepare and update attachment in SDM
    updateAndSendToSDM(
        context,
        attachment,
        id,
        objectId,
        filenameInRequest,
        descriptionInRequest,
        fileNameInDB,
        sdmData.fileNameInSDM,
        sdmData.descriptionInSDM,
        sdmCredentials,
        attachmentEntity,
        secondaryPropertiesWithInvalidDefinitions,
        noSDMRoles,
        duplicateFileNameList,
        filesNotFound,
        filesWithUnsupportedProperties,
        badRequest);
    logger.debug(
        "END: Process attachment - ID: {} completed in {} ms",
        id,
        (System.currentTimeMillis() - startTime));
  }

  private boolean checkUploadStatus(
      Map<String, Object> attachment,
      String fileNameInDB,
      String filenameInRequest,
      List<String> scanFailedFiles,
      List<String> uploadInProgressFiles) {
    Map<String, Object> readonlyData = (Map<String, Object>) attachment.get(SDM_READONLY_CONTEXT);
    if (readonlyData == null || readonlyData.get("uploadStatus") == null) {
      return false;
    }

    String uploadStatus = readonlyData.get("uploadStatus").toString();
    String fileName = fileNameInDB != null ? fileNameInDB : filenameInRequest;

    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS)) {
      logger.warn("Upload in progress for file: {}", fileName);
      uploadInProgressFiles.add(fileName);
      return true;
    }
    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_SCAN_FAILED)) {
      logger.warn("Scan failed for file: {}", fileName);
      scanFailedFiles.add(fileName);
      return true;
    }

    attachment.put("uploadStatus", uploadStatus);
    return false;
  }

  private SDMAttachmentData fetchSDMData(
      CdsCreateEventContext context, String objectId, SDMCredentials sdmCredentials)
      throws IOException {
    logger.debug("Fetching attachment data from SDM for objectId: {}", objectId);
    JSONObject sdmAttachmentData =
        AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
            sdmService, objectId, sdmCredentials, context.getUserInfo().isSystemUser());
    JSONObject succinctProperties = sdmAttachmentData.getJSONObject("succinctProperties");

    String fileNameInSDM = null;
    String descriptionInSDM = null;

    if (succinctProperties.has("cmis:name")) {
      fileNameInSDM = succinctProperties.getString("cmis:name");
    }
    if (succinctProperties.has("cmis:description")) {
      descriptionInSDM = succinctProperties.getString("cmis:description");
    }
    logger.debug(
        "Retrieved from SDM - fileName: {}, hasDescription: {}",
        fileNameInSDM,
        descriptionInSDM != null);

    return new SDMAttachmentData(fileNameInSDM, descriptionInSDM);
  }

  private void updateAndSendToSDM(
      CdsCreateEventContext context,
      Map<String, Object> attachment,
      String id,
      String objectId,
      String filenameInRequest,
      String descriptionInRequest,
      String fileNameInDB,
      String fileNameInSDM,
      String descriptionInSDM,
      SDMCredentials sdmCredentials,
      Optional<CdsEntity> attachmentEntity,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest)
      throws IOException {
    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
    Map<String, String> propertiesInDB =
        dbQuery.getPropertiesForID(
            attachmentEntity.get(), persistenceService, id, secondaryTypeProperties);

    logger.debug(
        "[CREATE] updateAndSendToSDM: ID={} objectId={} secondaryTypeProperties={} propertiesInDB={}",
        id,
        objectId,
        secondaryTypeProperties.keySet(),
        propertiesInDB.keySet());

    Map<String, String> updatedSecondaryProperties =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity,
            attachment,
            persistenceService,
            secondaryTypeProperties,
            propertiesInDB);
    CmisDocument cmisDocument =
        AttachmentsHandlerUtils.prepareCmisDocument(
            filenameInRequest, descriptionInRequest, objectId);

    AttachmentsHandlerUtils.updateFilenameProperty(
        fileNameInDB, filenameInRequest, fileNameInSDM, updatedSecondaryProperties);
    AttachmentsHandlerUtils.updateDescriptionProperty(
        descriptionInSDM,
        descriptionInRequest,
        descriptionInSDM,
        updatedSecondaryProperties,
        false);

    logger.debug(
        "[CREATE] updateAndSendToSDM: updatedSecondaryProperties={}", updatedSecondaryProperties);
    logger.debug(
        "Creating attachment in SDM - ID: {}, fileName: {}, properties count: {}",
        id,
        filenameInRequest,
        updatedSecondaryProperties.size());

    try {
      int responseCode =
          sdmService.updateAttachments(
              sdmCredentials,
              cmisDocument,
              updatedSecondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              context.getUserInfo().isSystemUser());

      logger.info("SDM update response code: {} for attachment ID: {}", responseCode, id);
      AttachmentsHandlerUtils.handleSDMUpdateResponse(
          responseCode,
          attachment,
          fileNameInSDM,
          filenameInRequest,
          propertiesInDB,
          secondaryTypeProperties,
          descriptionInSDM,
          noSDMRoles,
          duplicateFileNameList,
          filesNotFound);
    } catch (ServiceException e) {
      logger.error("Error updating attachment {} in SDM: {}", id, e.getMessage(), e);
      AttachmentsHandlerUtils.handleSDMServiceException(
          e,
          attachment,
          fileNameInSDM,
          filenameInRequest,
          propertiesInDB,
          secondaryTypeProperties,
          descriptionInSDM,
          filesWithUnsupportedProperties,
          badRequest);
    }
  }

  private void handleWarnings(
      CdsCreateEventContext context,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> propertyTitles,
      List<String> noSDMRoles,
      String contextInfo) {
    if (!fileNameWithRestrictedCharacters.isEmpty()) {
      logger.warn(
          "Files with restricted characters in filename: {}", fileNameWithRestrictedCharacters);
      context
          .getMessages()
          .warn(
              SDMErrorMessages.nameConstraintMessage(fileNameWithRestrictedCharacters)
                  + contextInfo);
    }
    if (!duplicateFileNameList.isEmpty()) {
      logger.warn("Duplicate filenames detected: {}", duplicateFileNameList);
      context
          .getMessages()
          .warn(
              String.format(SDMErrorMessages.duplicateFilenameFormat(duplicateFileNameList))
                  + contextInfo);
    }
    if (!filesNotFound.isEmpty()) {
      logger.warn("Files not found in SDM: {}", filesNotFound);
      context.getMessages().warn(SDMErrorMessages.fileNotFound(filesNotFound) + contextInfo);
    }
    if (!filesWithUnsupportedProperties.isEmpty()) {
      List<String> invalidPropertyNames = new ArrayList<>();
      Set<String> uniqueValues = new HashSet<>();
      for (String str : filesWithUnsupportedProperties) {
        String[] values = str.split(",");
        for (String value : values) {
          uniqueValues.add(value.trim());
        }
      }
      List<String> propertiesList = new ArrayList<>(uniqueValues);
      for (String file : propertiesList) {
        invalidPropertyNames.add(propertyTitles.get(file));
      }
      if (!invalidPropertyNames.isEmpty()) {
        logger.warn("Files with unsupported properties: {}", invalidPropertyNames);
        context
            .getMessages()
            .warn(
                SDMErrorMessages.unsupportedPropertiesMessage(invalidPropertyNames) + contextInfo);
      }
    }

    if (!badRequest.isEmpty()) {
      logger.warn("Bad request errors: {}", badRequest.keySet());
      context.getMessages().warn(SDMErrorMessages.badRequestMessage(badRequest) + contextInfo);
    }
    if (!noSDMRoles.isEmpty()) {
      logger.warn("No SDM roles for files: {}", noSDMRoles);
      context
          .getMessages()
          .warn(
              SDMErrorMessages.noSDMRolesMessage(
                      noSDMRoles, SDMUtils.getErrorMessage("EVENT_CREATE"))
                  + contextInfo);
    }
  }

  private String buildErrorMessage(
      List<String> scanFailedFiles, List<String> uploadInProgressFiles) {
    StringBuilder errorMessage = new StringBuilder();

    if (!scanFailedFiles.isEmpty()) {
      appendWithSpace(errorMessage);
      errorMessage.append(SDMErrorMessages.scanFailedFilesMessage(scanFailedFiles));
    }
    if (!uploadInProgressFiles.isEmpty()) {
      appendWithSpace(errorMessage);
      errorMessage.append(SDMErrorMessages.uploadInProgressFilesMessage(uploadInProgressFiles));
    }

    return errorMessage.toString();
  }

  private void appendWithSpace(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.append(" ");
    }
  }

  private void cleanupReadonlyContextsForAttachments(
      CdsCreateEventContext context,
      Map<String, Object> entityData,
      Map<String, Map<String, String>> attachmentCompositionDetails) {
    String targetEntity = context.getTarget().getQualifiedName();

    for (Map.Entry<String, Map<String, String>> entry : attachmentCompositionDetails.entrySet()) {
      String attachmentCompositionName = entry.getValue().get("name");

      logger.debug(
          "Cleaning up SDM_READONLY_CONTEXT for composition: {}", attachmentCompositionName);

      // Fetch attachments for this specific composition
      List<Map<String, Object>> attachments =
          AttachmentsHandlerUtils.fetchAttachments(
              targetEntity, entityData, attachmentCompositionName);

      if (attachments != null && !attachments.isEmpty()) {
        logger.debug(
            "Found {} attachments in composition: {}",
            attachments.size(),
            attachmentCompositionName);

        for (int i = 0; i < attachments.size(); i++) {
          Map<String, Object> attachment = attachments.get(i);
          if (attachment.containsKey(SDM_READONLY_CONTEXT)) {
            logger.debug(
                "Removing SDM_READONLY_CONTEXT from attachment [{}] in {}",
                i,
                attachmentCompositionName);
            attachment.remove(SDM_READONLY_CONTEXT);
          }
        }
      } else {
        logger.debug(
            "[SDM] CREATE: fetchAttachments returned no results for composition '{}' on entity '{}'. "
                + "This may indicate a deeply nested composition whose property name does not match the entity name. "
                + "Fallback recursive cleanup will handle SDM_READONLY_CONTEXT removal.",
            attachmentCompositionName,
            targetEntity);
      }
    }
    // Use CdsDataProcessor to mirror the exact traversal path used by preserveReadonlyFields.
    // This handles cases where CdsData stores composition data internally (e.g. during
    // draftActivate) in a way that plain Map.values() iteration cannot reach.
    SDMUtils.removeReadonlyFields(context.getTarget(), List.of(CdsData.create(entityData)));
    // Plain-map recursive fallback as secondary safety net for any remaining entries.
    removeReadonlyContextRecursively(entityData);
  }

  @SuppressWarnings("unchecked")
  private void removeReadonlyContextRecursively(Map<String, Object> data) {
    if (data == null) {
      return;
    }
    if (data.containsKey(SDM_READONLY_CONTEXT)) {
      logger.warn(
          "[SDM] CREATE: Fallback removed SDM_READONLY_CONTEXT from map with keys: {}. "
              + "This entry was not cleaned up by the composition-based path — "
              + "likely a deeply nested or mismatched composition name.",
          data.keySet());
      data.remove(SDM_READONLY_CONTEXT);
    }
    for (Object value : data.values()) {
      if (value instanceof List) {
        for (Object item : (List<?>) value) {
          if (item instanceof Map) {
            removeReadonlyContextRecursively((Map<String, Object>) item);
          }
        }
      }
    }
  }

  private static class SDMAttachmentData {
    final String fileNameInSDM;
    final String descriptionInSDM;

    SDMAttachmentData(String fileNameInSDM, String descriptionInSDM) {
      this.fileNameInSDM = fileNameInSDM;
      this.descriptionInSDM = descriptionInSDM;
    }
  }
}
