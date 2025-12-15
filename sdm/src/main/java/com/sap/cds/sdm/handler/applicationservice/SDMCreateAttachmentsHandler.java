package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.constants.SDMConstants;
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
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
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
  @HandlerOrder(HandlerOrder.EARLY)
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
    }
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
      String contextInfo =
          "\n\nTable: "
              + compositionName
              + "\nPage: "
              + (parentTitle != null ? parentTitle : "Unknown");

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
            noSDMRoles);
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
      List<String> noSDMRoles)
      throws IOException {
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");

    // Fetch original data from DB and SDM
    String fileNameInDB;
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    fileNameInDB = cmisDocument.getFileName();
    System.out.println("Upload status in create handler" + cmisDocument.getUploadStatus());
    if (cmisDocument.getUploadStatus() != null
        && !cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_SUCCESS)) {
      if (cmisDocument
          .getUploadStatus()
          .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED))
        throw new ServiceException("Virus Detected in this file kindly delete it.");
      if (cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS))
        throw new ServiceException("Virus Scanning is in Progress.");
    }
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    String fileNameInSDM = null, descriptionInSDM = null;
    JSONObject sdmAttachmentData =
        AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
            sdmService, objectId, sdmCredentials, context.getUserInfo().isSystemUser());
    JSONObject succinctProperties = sdmAttachmentData.getJSONObject("succinctProperties");
    if (succinctProperties.has("cmis:name")) {
      fileNameInSDM = succinctProperties.getString("cmis:name");
    }
    if (succinctProperties.has("cmis:description")) {
      descriptionInSDM = succinctProperties.getString("cmis:description");
    }

    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
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
        fileNameInDB, filenameInRequest, updatedSecondaryProperties);
    AttachmentsHandlerUtils.updateDescriptionProperty(
        descriptionInSDM, descriptionInRequest, updatedSecondaryProperties);

    // Send update to SDM and handle response
    try {
      int responseCode =
          sdmService.updateAttachments(
              sdmCredentials,
              cmisDocument,
              updatedSecondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              context.getUserInfo().isSystemUser());
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
          .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedCharacters) + contextInfo);
    }
    if (!duplicateFileNameList.isEmpty()) {
      context
          .getMessages()
          .warn(
              String.format(SDMConstants.duplicateFilenameFormat(duplicateFileNameList))
                  + contextInfo);
    }
    if (!filesNotFound.isEmpty()) {
      context.getMessages().warn(SDMConstants.fileNotFound(filesNotFound) + contextInfo);
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
            .warn(SDMConstants.unsupportedPropertiesMessage(invalidPropertyNames) + contextInfo);
      }
    }

    if (!badRequest.isEmpty()) {
      context.getMessages().warn(SDMConstants.badRequestMessage(badRequest) + contextInfo);
    }
    if (!noSDMRoles.isEmpty()) {
      context
          .getMessages()
          .warn(SDMConstants.noSDMRolesMessage(noSDMRoles, "create") + contextInfo);
    }
  }
}
