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
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.OrderConstants;
import java.io.IOException;
import java.util.*;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMUpdateAttachmentsHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;
  private static final Logger logger = LoggerFactory.getLogger(SDMUpdateAttachmentsHandler.class);

  public SDMUpdateAttachmentsHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  @Before
  @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES - 500)
  public void preserveUploadStatus(CdsUpdateEventContext context, List<CdsData> data) {
    logger.debug(
        "Preserving uploadStatus field before CDS processing for entity: {}",
        context.getTarget().getQualifiedName());
    // Preserve uploadStatus before CDS removes readonly fields
    SDMUtils.preserveReadonlyFields(context.getTarget(), data);
  }

  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void processAfter(CdsUpdateEventContext context, List<CdsData> data) {
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

        if (attachmentEntity.isPresent()) {
          String targetEntity = context.getTarget().getQualifiedName();
          List<Map<String, Object>> attachments =
              AttachmentsHandlerUtils.fetchAttachments(
                  targetEntity, entityData, attachmentCompositionName);

          if (attachments != null) {
            logger.debug(
                "Processing {} attachments for composition: {}",
                attachments.size(),
                attachmentCompositionName);
            for (Map<String, Object> attachment : attachments) {
              String id = (String) attachment.get("ID");
              String uploadStatus = (String) attachment.get("uploadStatus");
              if (id != null) {
                CmisDocument cmisDocument = new CmisDocument();
                cmisDocument.setAttachmentId(id);
                cmisDocument.setUploadStatus(uploadStatus);
                // Update uploadStatus to Success in database if it was InProgress
                logger.debug("Saving uploadStatus: {} for attachment ID: {}", uploadStatus, id);
                dbQuery.saveUploadStatusToAttachment(
                    attachmentEntity.get(), persistenceService, cmisDocument);
                totalProcessed++;
              }
            }
          }
        }
      }
    }
    logger.info("END: Post-processing completed. Updated {} attachments", totalProcessed);
  }

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
    logger.info(
        "START: Process attachments before persistence for entity: {}",
        context.getTarget().getQualifiedName());
    logger.debug("Number of entities to update: {}", data.size());

    // Get comprehensive attachment composition details for each entity
    for (CdsData entityData : data) {
      Map<String, Map<String, String>> attachmentCompositionDetails =
          AttachmentsHandlerUtils.getAttachmentCompositionDetails(
              context.getModel(),
              context.getTarget(),
              persistenceService,
              context.getTarget().getQualifiedName(),
              entityData);
      logger.debug("Attachment compositions present: {}", attachmentCompositionDetails.keySet());

      updateName(context, data, attachmentCompositionDetails);

      // Remove uploadStatus from attachment data to prevent validation errors
      cleanupReadonlyContextsForAttachments(context, entityData, attachmentCompositionDetails);
    }
    logger.info("END: Process attachments before persistence");
  }

  public void updateName(
      CdsUpdateEventContext context,
      List<CdsData> data,
      Map<String, Map<String, String>> attachmentCompositionDetails)
      throws IOException {
    for (Map.Entry<String, Map<String, String>> entry : attachmentCompositionDetails.entrySet()) {
      String attachmentCompositionDefinition = entry.getKey();
      String attachmentCompositionName = entry.getValue().get("name");
      String parentTitle = entry.getValue().get("parentTitle");
      Boolean isError = false;

      // Extract composition name (last part after the final ".")
      String compositionName = attachmentCompositionName;
      if (attachmentCompositionName != null && attachmentCompositionName.contains(".")) {
        String[] parts = attachmentCompositionName.split("\\.");
        compositionName = parts[parts.length - 1];
      }

      String contextInfo = AttachmentsHandlerUtils.getContextInfo(compositionName, parentTitle);

      Optional<CdsEntity> attachmentEntity = Optional.empty();
      if (context.getModel() != null) {
        attachmentEntity = context.getModel().findEntity(attachmentCompositionDefinition);
      }
      isError =
          AttachmentsHandlerUtils.validateFileNames(
              context, data, attachmentCompositionName, contextInfo, attachmentEntity);
      if (!isError) {
        renameDocument(
            attachmentEntity,
            context,
            data,
            attachmentCompositionDefinition,
            attachmentCompositionName,
            contextInfo);
      }
    }
  }

  private void renameDocument(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      List<CdsData> data,
      String attachmentCompositionDefinition,
      String attachmentCompositionName,
      String contextInfo)
      throws IOException {
    logger.debug("Renaming documents for composition: {}", attachmentCompositionName);
    List<String> duplicateFileNameList = new ArrayList<>();
    Map<String, String> secondaryPropertiesWithInvalidDefinitions;
    List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
    List<String> filesNotFound = new ArrayList<>();
    List<String> filesWithUnsupportedProperties = new ArrayList<>();
    List<String> extensionChangedFiles = new ArrayList<>();
    Map<String, String> badRequest = new HashMap<>();
    Map<String, String> propertyTitles = new HashMap<>();
    List<String> noSDMRoles = new ArrayList<>();
    String targetEntity = context.getTarget().getQualifiedName();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments =
          AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

      if (attachments != null && !attachments.isEmpty()) {
        propertyTitles = SDMUtils.getPropertyTitles(attachmentEntity, attachments.get(0));
      } else {
        propertyTitles = null;
      }
      if (attachments != null && !attachments.isEmpty()) {
        secondaryPropertiesWithInvalidDefinitions =
            SDMUtils.getSecondaryPropertiesWithInvalidDefinition(
                attachmentEntity, attachments.get(0));
      } else {
        // Handle the case where attachments is null or empty
        secondaryPropertiesWithInvalidDefinitions = null;
      }
      if ((attachments != null) && !attachments.isEmpty()) {
        processAttachments(
            attachmentEntity,
            context,
            attachments,
            duplicateFileNameList,
            fileNameWithRestrictedCharacters,
            filesNotFound,
            filesWithUnsupportedProperties,
            badRequest,
            secondaryPropertiesWithInvalidDefinitions,
            noSDMRoles,
            extensionChangedFiles);
      }
    }
    handleWarnings(
        context,
        duplicateFileNameList,
        fileNameWithRestrictedCharacters,
        filesNotFound,
        filesWithUnsupportedProperties,
        badRequest,
        propertyTitles,
        noSDMRoles,
        extensionChangedFiles,
        contextInfo);
  }

  private void processAttachments(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      List<Map<String, Object>> attachments,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      List<String> extensionChangedFiles)
      throws IOException {
    logger.debug("Processing {} attachments for update", attachments.size());
    List<String> scanFailedFiles = new ArrayList<>();
    List<String> uploadInProgressFiles = new ArrayList<>();

    Iterator<Map<String, Object>> iterator = attachments.iterator();
    while (iterator.hasNext()) {
      Map<String, Object> attachment = iterator.next();
      processAttachment(
          attachmentEntity,
          context,
          attachment,
          duplicateFileNameList,
          fileNameWithRestrictedCharacters,
          filesNotFound,
          filesWithUnsupportedProperties,
          badRequest,
          secondaryPropertiesWithInvalidDefinitions,
          noSDMRoles,
          scanFailedFiles,
          uploadInProgressFiles,
          extensionChangedFiles);
    }

    // Throw exception if any files failed scan or upload in progress
    if (!scanFailedFiles.isEmpty() || !uploadInProgressFiles.isEmpty()) {
      logger.warn(
          "Blocking update due to scan failures: {}, uploads in progress: {}",
          scanFailedFiles.size(),
          uploadInProgressFiles.size());
      StringBuilder errorMessage = new StringBuilder();
      if (!scanFailedFiles.isEmpty()) {
        if (errorMessage.length() > 0) {
          errorMessage.append(" ");
        }
        errorMessage.append(SDMErrorMessages.scanFailedFilesMessage(scanFailedFiles));
      }
      if (!uploadInProgressFiles.isEmpty()) {
        if (errorMessage.length() > 0) {
          errorMessage.append(" ");
        }
        errorMessage.append(SDMErrorMessages.uploadInProgressFilesMessage(uploadInProgressFiles));
      }
      throw new ServiceException(errorMessage.toString());
    }
    SecondaryPropertiesKey secondaryPropertiesKey = new SecondaryPropertiesKey();
    secondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    Cache<SecondaryPropertiesKey, ?> cache = CacheConfig.getSecondaryPropertiesCache();
    if (cache != null) {
      cache.remove(secondaryPropertiesKey); // Emptying cache after attachments are updated in loop
    }
  }

  public void processAttachment(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      Map<String, Object> attachment,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      List<String> scanFailedFiles,
      List<String> uploadInProgressFiles,
      List<String> extensionChangedFiles)
      throws IOException {
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");

    logger.debug("Processing attachment update - ID: {}, objectId: {}", id, objectId);

    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    String fileNameInDB = cmisDocument.getFileName();

    // Check for upload status issues
    if (handleUploadStatusCheck(
        attachment, fileNameInDB, filenameInRequest, scanFailedFiles, uploadInProgressFiles)) {
      return;
    }

    // Fetch file details from SDM if needed
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    AttachmentDetails details =
        fetchAttachmentDetails(
            fileNameInDB,
            descriptionInRequest,
            objectId,
            sdmCredentials,
            context.getUserInfo().isSystemUser());

    Map<String, String> propertiesInDB =
        dbQuery.getPropertiesForID(
            attachmentEntity.get(), persistenceService, id, secondaryTypeProperties);

    int extensionWarningsBefore = extensionChangedFiles.size();
    Map<String, String> updatedSecondaryProperties =
        prepareUpdatedProperties(
            attachmentEntity,
            attachment,
            filenameInRequest,
            descriptionInRequest,
            details.fileNameInDB,
            details.descriptionInDB,
            secondaryTypeProperties,
            propertiesInDB,
            extensionChangedFiles);

    // If extension change was detected, revert filename to original
    if (extensionChangedFiles.size() > extensionWarningsBefore) {
      attachment.put("fileName", details.fileNameInDB);
    }

    if (updatedSecondaryProperties.isEmpty()) {
      logger.debug("No changes detected for attachment ID: {}, skipping SDM update", id);
      return;
    }

    updateAttachmentInSDM(
        context,
        attachment,
        id,
        filenameInRequest,
        descriptionInRequest,
        objectId,
        details.fileNameInDB,
        details.descriptionInDB,
        propertiesInDB,
        secondaryTypeProperties,
        updatedSecondaryProperties,
        secondaryPropertiesWithInvalidDefinitions,
        noSDMRoles,
        duplicateFileNameList,
        filesNotFound,
        filesWithUnsupportedProperties,
        badRequest);
  }

  private boolean handleUploadStatusCheck(
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

    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_SCAN_FAILED)) {
      logger.warn("Scan failed for file: {}", fileName);
      scanFailedFiles.add(fileName);
      return true;
    }
    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS)) {
      logger.warn("Upload in progress for file: {}", fileName);
      uploadInProgressFiles.add(fileName);
      return true;
    }

    attachment.put("uploadStatus", uploadStatus);
    return false;
  }

  private AttachmentDetails fetchAttachmentDetails(
      String fileNameInDB,
      String descriptionInRequest,
      String objectId,
      SDMCredentials sdmCredentials,
      boolean isSystemUser)
      throws IOException {
    logger.debug("Fetching attachment details from SDM for objectId: {}", objectId);
    String finalFileNameInDB = fileNameInDB;
    String descriptionInDB = null;

    if (fileNameInDB == null || descriptionInRequest != null) {
      JSONObject sdmAttachmentData =
          AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
              sdmService, objectId, sdmCredentials, isSystemUser);
      JSONObject succinctProperties = sdmAttachmentData.getJSONObject("succinctProperties");

      if (succinctProperties.has("cmis:name")) {
        finalFileNameInDB = succinctProperties.getString("cmis:name");
      }
      if (succinctProperties.has("cmis:description")) {
        descriptionInDB = succinctProperties.getString("cmis:description");
      }
      logger.debug(
          "Retrieved from SDM - fileName: {}, hasDescription: {}",
          finalFileNameInDB,
          descriptionInDB != null);
    }

    return new AttachmentDetails(finalFileNameInDB, descriptionInDB);
  }

  private Map<String, String> prepareUpdatedProperties(
      Optional<CdsEntity> attachmentEntity,
      Map<String, Object> attachment,
      String filenameInRequest,
      String descriptionInRequest,
      String fileNameInDB,
      String descriptionInDB,
      Map<String, String> secondaryTypeProperties,
      Map<String, String> propertiesInDB,
      List<String> extensionChangedFiles) {
    Map<String, String> updatedSecondaryProperties =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity,
            attachment,
            persistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    AttachmentsHandlerUtils.updateFilenameProperty(
        fileNameInDB,
        filenameInRequest,
        fileNameInDB,
        updatedSecondaryProperties,
        extensionChangedFiles);

    AttachmentsHandlerUtils.updateDescriptionProperty(
        null, descriptionInRequest, descriptionInDB, updatedSecondaryProperties, true);

    return updatedSecondaryProperties;
  }

  private void updateAttachmentInSDM(
      CdsUpdateEventContext context,
      Map<String, Object> attachment,
      String id,
      String filenameInRequest,
      String descriptionInRequest,
      String objectId,
      String fileNameInDB,
      String descriptionInDB,
      Map<String, String> propertiesInDB,
      Map<String, String> secondaryTypeProperties,
      Map<String, String> updatedSecondaryProperties,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      List<String> noSDMRoles,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest) {
    logger.debug(
        "Updating attachment in SDM - ID: {}, properties count: {}",
        id,
        updatedSecondaryProperties.size());

    CmisDocument cmisDocument =
        AttachmentsHandlerUtils.prepareCmisDocument(
            filenameInRequest, descriptionInRequest, objectId);

    try {
      int responseCode =
          sdmService.updateAttachments(
              tokenHandler.getSDMCredentials(),
              cmisDocument,
              updatedSecondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              context.getUserInfo().isSystemUser());

      logger.debug("SDM update response code: {} for attachment ID: {}", responseCode, id);

      AttachmentsHandlerUtils.handleSDMUpdateResponse(
          responseCode,
          attachment,
          fileNameInDB,
          filenameInRequest,
          propertiesInDB,
          secondaryTypeProperties,
          descriptionInDB,
          noSDMRoles,
          duplicateFileNameList,
          filesNotFound);

      logger.info(
          "Successfully updated attachment in SDM - ID: {}, fileName: {}", id, filenameInRequest);
    } catch (ServiceException e) {
      logger.error("Failed to update attachment in SDM - ID: {}, error: {}", id, e.getMessage());
      AttachmentsHandlerUtils.handleSDMServiceException(
          e,
          attachment,
          fileNameInDB,
          filenameInRequest,
          propertiesInDB,
          secondaryTypeProperties,
          descriptionInDB,
          filesWithUnsupportedProperties,
          badRequest);
    }
  }

  private static class AttachmentDetails {
    final String fileNameInDB;
    final String descriptionInDB;

    AttachmentDetails(String fileNameInDB, String descriptionInDB) {
      this.fileNameInDB = fileNameInDB;
      this.descriptionInDB = descriptionInDB;
    }
  }

  private void handleWarnings(
      CdsUpdateEventContext context,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> propertyTitles,
      List<String> noSDMRoles,
      List<String> extensionChangedFiles,
      String contextInfo) {
    if (!extensionChangedFiles.isEmpty()) {
      logger.warn("File extension change attempted for files: {}", extensionChangedFiles);
      for (String warningMessage : extensionChangedFiles) {
        context.getMessages().warn(warningMessage);
      }
    }
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
              String.format(
                  SDMErrorMessages.duplicateFilenameFormat(duplicateFileNameList), contextInfo));
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
                      noSDMRoles, SDMUtils.getErrorMessage("EVENT_UPDATE"))
                  + contextInfo);
    }
  }

  private void cleanupReadonlyContextsForAttachments(
      CdsUpdateEventContext context,
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
        logger.debug("No attachments found for composition: {}", attachmentCompositionName);
      }
    }
  }
}
