package com.sap.cds.sdm.handler.applicationservice;

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
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
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
  private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

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
  @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES)
  void processBeforeForDraft(CdsCreateEventContext context, List<CdsData> data) {
    // before the attachment's readonly fields are removed by the runtime, preserve them in a custom
    // field in data
    logger.info("Hellooo");
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void processBefore(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
    // Get comprehensive attachment composition details for each entity
    for (CdsData entityData : data) {
      Map<String, Map<String, String>> attachmentCompositionDetails =
          AttachmentsHandlerUtils.getAttachmentCompositionDetails(
              context.getModel(),
              context.getTarget(),
              persistenceService,
              context.getTarget().getQualifiedName(),
              entityData);
      logger.info("Attachment compositions present in CDS Model : " + attachmentCompositionDetails);

      updateName(context, data, attachmentCompositionDetails);
    }
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
    List<String> duplicateFileNameList = new ArrayList<>();
    Map<String, String> secondaryPropertiesWithInvalidDefinitions;
    List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
    List<String> filesNotFound = new ArrayList<>();
    List<String> filesWithUnsupportedProperties = new ArrayList<>();
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
            noSDMRoles);
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
      List<String> noSDMRoles)
      throws IOException {
    List<String> virusDetectedFiles = new ArrayList<>();
    List<String> virusScanInProgressFiles = new ArrayList<>();
    List<String> scanFailedFiles = new ArrayList<>();

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
          virusDetectedFiles,
          virusScanInProgressFiles,
          scanFailedFiles);
    }

    // Throw exception if any files failed virus scan or scan failed
    if (!virusDetectedFiles.isEmpty()
        || !virusScanInProgressFiles.isEmpty()
        || !scanFailedFiles.isEmpty()) {
      StringBuilder errorMessage = new StringBuilder();
      if (!virusDetectedFiles.isEmpty()) {
        errorMessage.append(SDMErrorMessages.virusDetectedFilesMessage(virusDetectedFiles));
      }
      if (!virusScanInProgressFiles.isEmpty()) {
        if (errorMessage.length() > 0) {
          errorMessage.append(" ");
        }
        errorMessage.append(
            SDMErrorMessages.virusScanInProgressFilesMessage(virusScanInProgressFiles));
      }
      if (!scanFailedFiles.isEmpty()) {
        if (errorMessage.length() > 0) {
          errorMessage.append(" ");
        }
        errorMessage.append(SDMErrorMessages.scanFailedFilesMessage(scanFailedFiles));
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
      List<String> virusDetectedFiles,
      List<String> virusScanInProgressFiles,
      List<String> scanFailedFiles)
      throws IOException {
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");

    logger.debug("Processing attachment update - ID: {}, objectId: {}", id, objectId);

    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
    String fileNameInDB;
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    fileNameInDB = cmisDocument.getFileName();

    // Collect files with virus-related upload statuses
    if (attachment.get("uploadStatus") != null) {
      String uploadStatus = attachment.get("uploadStatus").toString();
      if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED)) {
        virusDetectedFiles.add(fileNameInDB != null ? fileNameInDB : filenameInRequest);
        return; // Skip further processing for this attachment
      }
      if (uploadStatus.equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS)) {
        virusScanInProgressFiles.add(fileNameInDB != null ? fileNameInDB : filenameInRequest);
        return; // Skip further processing for this attachment
      }
      if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_SCAN_FAILED)) {
        scanFailedFiles.add(fileNameInDB != null ? fileNameInDB : filenameInRequest);
        return; // Skip further processing for this attachment
      }
    }

    // Fetch from SDM if not in DB
    String descriptionInDB = null;
    if (fileNameInDB == null || descriptionInRequest != null) {
      JSONObject sdmAttachmentData =
          AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
              sdmService, objectId, sdmCredentials, context.getUserInfo().isSystemUser());
      JSONObject succinctProperties = sdmAttachmentData.getJSONObject("succinctProperties");
      if (succinctProperties.has("cmis:name")) {
        fileNameInDB = succinctProperties.getString("cmis:name");
      }
      if (succinctProperties.has("cmis:description")) {
        descriptionInDB = succinctProperties.getString("cmis:description");
      }
    }

    Map<String, String> propertiesInDB =
        dbQuery.getPropertiesForID(
            attachmentEntity.get(), persistenceService, id, secondaryTypeProperties);
    // Prepare document and updated properties

    Map<String, String> updatedSecondaryProperties =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity,
            attachment,
            persistenceService,
            secondaryTypeProperties,
            propertiesInDB);
    cmisDocument =
        AttachmentsHandlerUtils.prepareCmisDocument(
            filenameInRequest, descriptionInRequest, objectId);

    // Update filename and description properties
    AttachmentsHandlerUtils.updateFilenameProperty(
        fileNameInDB, filenameInRequest, fileNameInDB, updatedSecondaryProperties);

    AttachmentsHandlerUtils.updateDescriptionProperty(
        null, descriptionInRequest, descriptionInDB, updatedSecondaryProperties, true);

    // Send update to SDM only if there are changes
    if (updatedSecondaryProperties.isEmpty()) {
      logger.debug("No changes detected for attachment ID: {}, skipping SDM update", id);
      return;
    }

    logger.debug(
        "Updating attachment in SDM - ID: {}, properties count: {}",
        id,
        updatedSecondaryProperties.size());

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

  private void handleWarnings(
      CdsUpdateEventContext context,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> propertyTitles,
      List<String> noSDMRoles,
      String contextInfo) {
    if (!fileNameWithRestrictedCharacters.isEmpty()) {
      context
          .getMessages()
          .warn(
              SDMErrorMessages.nameConstraintMessage(fileNameWithRestrictedCharacters)
                  + contextInfo);
    }
    if (!duplicateFileNameList.isEmpty()) {
      context
          .getMessages()
          .warn(
              String.format(
                  SDMErrorMessages.duplicateFilenameFormat(duplicateFileNameList), contextInfo));
    }
    if (!filesNotFound.isEmpty()) {
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
        context
            .getMessages()
            .warn(
                SDMErrorMessages.unsupportedPropertiesMessage(invalidPropertyNames) + contextInfo);
      }
    }
    if (!badRequest.isEmpty()) {
      context.getMessages().warn(SDMErrorMessages.badRequestMessage(badRequest) + contextInfo);
    }
    if (!noSDMRoles.isEmpty()) {
      context
          .getMessages()
          .warn(
              SDMErrorMessages.noSDMRolesMessage(
                      noSDMRoles, SDMUtils.getErrorMessage("EVENT_UPDATE"))
                  + contextInfo);
    }
  }
}
