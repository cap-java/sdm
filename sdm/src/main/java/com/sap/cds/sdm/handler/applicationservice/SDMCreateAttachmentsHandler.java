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

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsCreateEventContext context, List<CdsData> data) throws IOException {
    logger.info("Target Entity : " + context.getTarget().getQualifiedName());

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
      // Remove uploadStatus from attachment data to prevent validation errors

    }
  }

  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void processAfter(CdsCreateEventContext context, List<CdsData> data) {
    // Update uploadStatus to Success after entity is persisted
    logger.info(
        "Post-processing attachments after persistence for entity: {}",
        context.getTarget().getQualifiedName());

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
            for (Map<String, Object> attachment : attachments) {
              String id = (String) attachment.get("ID");
              String uploadStatus = (String) attachment.get("uploadStatus");
              if (id != null) {
                CmisDocument cmisDocument = new CmisDocument();
                cmisDocument.setAttachmentId(id);
                cmisDocument.setUploadStatus(uploadStatus);
                // Update uploadStatus to Success in database if it was InProgress
                dbQuery.saveUploadStatusToAttachment(
                    attachmentEntity.get(), persistenceService, cmisDocument);
                logger.debug("Updated uploadStatus to Success for attachment ID: {}", id);
              }
            }
          }
        }
      }
    }
  }

  @Before
  @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES - 500)
  public void preserveUploadStatus(CdsCreateEventContext context, List<CdsData> data) {
    // Preserve uploadStatus before CDS removes readonly fields
    SDMUtils.preserveReadonlyFields(context.getTarget(), data);
  }

  @Before
  @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES - 400)
  public void cleanupReadonlyContextEarly(CdsCreateEventContext context, List<CdsData> data) {
    // Clean up readonly context immediately after preservation to prevent CQN validation errors
    SDMUtils.cleanupReadonlyContexts(data);
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
      isError =
          AttachmentsHandlerUtils.validateFileNames(
              context, data, attachmentCompositionName, contextInfo, attachmentEntity);
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
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");

    // Fetch original data from DB
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    String fileNameInDB = cmisDocument.getFileName();

    // Check upload status and collect problematic files
    if (checkUploadStatus(
        attachment, fileNameInDB, filenameInRequest, scanFailedFiles, uploadInProgressFiles)) {
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
  }

  private boolean checkUploadStatus(
      Map<String, Object> attachment,
      String fileNameInDB,
      String filenameInRequest,
      List<String> scanFailedFiles,
      List<String> uploadInProgressFiles) {
    // uploadStatus is already extracted from SDM_READONLY_CONTEXT during cleanup
    Object uploadStatusObj = attachment.get("uploadStatus");
    if (uploadStatusObj == null) {
      return false;
    }

    String uploadStatus = uploadStatusObj.toString();
    String fileName = fileNameInDB != null ? fileNameInDB : filenameInRequest;

    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_IN_PROGRESS)) {
      uploadInProgressFiles.add(fileName);
      return true;
    }
    if (uploadStatus.equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_SCAN_FAILED)) {
      scanFailedFiles.add(fileName);
      return true;
    }

    return false;
  }

  private SDMAttachmentData fetchSDMData(
      CdsCreateEventContext context, String objectId, SDMCredentials sdmCredentials)
      throws IOException {
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

    AttachmentsHandlerUtils.updateFilenameProperty(
        fileNameInDB, filenameInRequest, fileNameInSDM, updatedSecondaryProperties);
    AttachmentsHandlerUtils.updateDescriptionProperty(
        descriptionInSDM,
        descriptionInRequest,
        descriptionInSDM,
        updatedSecondaryProperties,
        false);

    logger.debug(
        "Creating attachment in SDM - ID: {}, properties count: {}",
        id,
        updatedSecondaryProperties.size());

    try {
      int responseCode =
          sdmService.updateAttachments(
              sdmCredentials,
              cmisDocument,
              updatedSecondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              context.getUserInfo().isSystemUser());

      logger.debug("SDM create response code: {} for attachment ID: {}", responseCode, id);
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
              String.format(SDMErrorMessages.duplicateFilenameFormat(duplicateFileNameList))
                  + contextInfo);
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

  private static class SDMAttachmentData {
    final String fileNameInSDM;
    final String descriptionInSDM;

    SDMAttachmentData(String fileNameInSDM, String descriptionInSDM) {
      this.fileNameInSDM = fileNameInSDM;
      this.descriptionInSDM = descriptionInSDM;
    }
  }
}
