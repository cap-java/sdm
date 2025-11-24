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

      isError =
          AttachmentsHandlerUtils.validateFileNames(
              context, data, attachmentCompositionName, contextInfo);
      if (!isError) {
        Optional<CdsEntity> attachmentEntity =
            context.getModel().findEntity(attachmentCompositionDefinition);
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
    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(
            attachmentEntity,
            attachment); // Fetching the secondary type properties from the attachment entity
    String fileNameInDB;
    fileNameInDB = dbQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    if (fileNameInDB
        == null) { // On entity UPDATE, fetch original attachment name from SDM to revert property
      // values if needed.
      String objectId = (String) attachment.get("objectId");
      SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
      fileNameInDB =
          sdmService.getObject(objectId, sdmCredentials, context.getUserInfo().isSystemUser());
    }
    Map<String, String> propertiesInDB;
    propertiesInDB =
        dbQuery.getPropertiesForID(
            attachmentEntity.get(),
            persistenceService,
            id,
            secondaryTypeProperties); // Fetching the values of the properties from the DB

    Map<String, String> updatedSecondaryProperties =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity,
            attachment,
            persistenceService,
            secondaryTypeProperties,
            propertiesInDB);
    String filenameInRequest = (String) attachment.get("fileName");

    String objectId = (String) attachment.get("objectId");
    if (Boolean.TRUE.equals(
        SDMUtils.hasRestrictedCharactersInName(
            filenameInRequest))) { // Check if the filename contains restricted characters and stop
      // further processing if it does (Request not sent to SDM)
      fileNameWithRestrictedCharacters.add(filenameInRequest);
      replacePropertiesInAttachment(
          attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
      return;
    }
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName(filenameInRequest);
    cmisDocument.setObjectId(objectId);
    if (fileNameInDB == null) {
      if (filenameInRequest != null) {
        updatedSecondaryProperties.put("filename", filenameInRequest);
      } else {
        throw new ServiceException("Filename cannot be empty");
      }
    } else {
      if (filenameInRequest == null) {
        throw new ServiceException("Filename cannot be empty");
      } else if (!fileNameInDB.equals(filenameInRequest)) {
        updatedSecondaryProperties.put("filename", filenameInRequest);
      }
    }
    if (!updatedSecondaryProperties.isEmpty()) {
      try {
        int responseCode =
            sdmService.updateAttachments(
                tokenHandler.getSDMCredentials(),
                cmisDocument,
                updatedSecondaryProperties,
                secondaryPropertiesWithInvalidDefinitions,
                context.getUserInfo().isSystemUser());
        switch (responseCode) {
          case 403:
            // SDM Roles for user are missing
            noSDMRoles.add(fileNameInDB);
            replacePropertiesInAttachment(
                attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
            break;
          case 409:
            duplicateFileNameList.add(filenameInRequest);
            replacePropertiesInAttachment(
                attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
            break;
          case 404:
            filesNotFound.add(fileNameInDB);
            replacePropertiesInAttachment(
                attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
            break;
          case 200:
          case 201:
            // Success cases, do nothing
            break;

          default:
            throw new ServiceException(SDMConstants.SDM_ROLES_ERROR_MESSAGE, (Object[]) null);
        }
      } catch (ServiceException e) {
        // This exception is thrown when there are unsupported properties in the request
        if (e.getMessage().startsWith(SDMConstants.UNSUPPORTED_PROPERTIES)) {
          String unsupportedDetails =
              e.getMessage().substring(SDMConstants.UNSUPPORTED_PROPERTIES.length()).trim();
          filesWithUnsupportedProperties.add(unsupportedDetails);
          replacePropertiesInAttachment(
              attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
        } else {
          badRequest.put(fileNameInDB, e.getMessage());
          replacePropertiesInAttachment(
              attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
        }
      }
    }
  }

  private void replacePropertiesInAttachment(
      Map<String, Object> attachment,
      String fileName,
      Map<String, String> propertiesInDB,
      Map<String, String> secondaryTypeProperties) {
    if (propertiesInDB != null) {
      for (Map.Entry<String, String> entry : propertiesInDB.entrySet()) {
        String dbKey = entry.getKey();
        String dbValue = entry.getValue();

        // Find the key in secondaryTypeProperties where the value matches dbKey
        String secondaryKey =
            secondaryTypeProperties.entrySet().stream()
                .filter(e -> e.getValue().equals(dbKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (secondaryKey != null) {
          attachment.replace(secondaryKey, dbValue);
        }
      }
    }
    attachment.replace("fileName", fileName);
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
          .warn(SDMConstants.duplicateFilenameFormat(duplicateFileNameList).formatted(contextInfo));
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
