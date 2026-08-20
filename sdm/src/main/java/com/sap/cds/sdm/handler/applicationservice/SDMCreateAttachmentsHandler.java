package com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.constants.SDMConstants.SDM_READONLY_CONTEXT;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsEntity;
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

    logger.debug("Processing attachment creation - ID: {}, objectId: {}", id, objectId);

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

    List<String> extensionChangedFiles = new ArrayList<>();
    AttachmentsHandlerUtils.updateFilenameProperty(
        fileNameInDB,
        filenameInRequest,
        fileNameInSDM,
        updatedSecondaryProperties,
        extensionChangedFiles);

    // If extension change was detected, revert filename to original and warn
    if (!extensionChangedFiles.isEmpty()) {
      attachment.put("fileName", fileNameInSDM);
      for (String warningMessage : extensionChangedFiles) {
        context.getMessages().warn(warningMessage);
      }
    }

    AttachmentsHandlerUtils.updateDescriptionProperty(
        descriptionInSDM,
        descriptionInRequest,
        descriptionInSDM,
        updatedSecondaryProperties,
        false);

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
