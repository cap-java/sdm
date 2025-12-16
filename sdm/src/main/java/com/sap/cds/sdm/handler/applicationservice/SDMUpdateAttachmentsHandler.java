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
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
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
      String contextInfo =
          "\n\nTable: "
              + compositionName
              + "\nPage: "
              + (parentTitle != null ? parentTitle : "Unknown");

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
          noSDMRoles);
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
      List<String> noSDMRoles)
      throws IOException {
    String id = (String) attachment.get("ID");
    String filenameInRequest = (String) attachment.get("fileName");
    String descriptionInRequest = (String) attachment.get("note");
    String objectId = (String) attachment.get("objectId");

    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
    String fileNameInDB;
    Optional<CdsEntity> attachmentDraftEntity =
        context.getModel().findEntity(attachmentEntity.get().getQualifiedName() + "_drafts");
    CmisDocument cmisDocument =
        dbQuery.getAttachmentForID(
            attachmentEntity.get(), persistenceService, id, attachmentDraftEntity.get());
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    fileNameInDB = cmisDocument.getFileName();
    System.out.println("Upload status in create handler" + cmisDocument.getUploadStatus());
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument
            .getUploadStatus()
            .equalsIgnoreCase(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED))
      throw new ServiceException("Virus Detected in this file kindly delete it.");
    if (cmisDocument.getUploadStatus() != null
        && cmisDocument.getUploadStatus().equalsIgnoreCase(SDMConstants.VIRUS_SCAN_INPROGRESS))
      throw new ServiceException(
          "Virus Scanning is in Progress. Refresh the page to see the effect");

    // Fetch from SDM if not in DB
    String descriptionInDB = null;
    if (fileNameInDB == null) {
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

    // Extract note (description) from DB if it exists
    if (propertiesInDB != null && propertiesInDB.containsKey("note")) {
      descriptionInDB = propertiesInDB.get("note");
    }

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
        descriptionInDB, descriptionInRequest, updatedSecondaryProperties);

    // Send update to SDM only if there are changes
    if (updatedSecondaryProperties.isEmpty()) {
      return;
    }

    try {
      int responseCode =
          sdmService.updateAttachments(
              tokenHandler.getSDMCredentials(),
              cmisDocument,
              updatedSecondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              context.getUserInfo().isSystemUser());
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
    } catch (ServiceException e) {
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
          .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedCharacters) + contextInfo);
    }
    if (!duplicateFileNameList.isEmpty()) {
      context
          .getMessages()
          .warn(
              String.format(
                  SDMConstants.duplicateFilenameFormat(duplicateFileNameList), contextInfo));
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
          .warn(SDMConstants.noSDMRolesMessage(noSDMRoles, "update") + contextInfo);
    }
  }
}
