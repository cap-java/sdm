package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
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

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateAttachmentsHandler implements EventHandler {

  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

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
    List<String> attachmentCompositions = getEntityCompositions(context);
    for (String composition : attachmentCompositions) {
      updateName(context, data, composition);
    }
  }

  public void updateName(CdsCreateEventContext context, List<CdsData> data, String composition)
      throws IOException {
    Map<String, String> propertyTitles = new HashMap<>();
    Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();
    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data, composition);
    if (!duplicateFilenames.isEmpty()) {
      handleDuplicateFilenames(context, duplicateFilenames);
    } else {
      List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
      List<String> duplicateFileNameList = new ArrayList<>();
      List<String> filesNotFound = new ArrayList<>();
      List<String> filesWithUnsupportedProperties = new ArrayList<>();
      Map<String, String> badRequest = new HashMap<>();
      List<String> fileWithWhiteSpace = new ArrayList<>();
      List<String> noSDMRoles = new ArrayList<>();
      for (Map<String, Object> entity : data) {
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get(composition);
        Optional<CdsEntity> attachmentEntity =
            context
                .getModel()
                .findEntity(context.getTarget().getQualifiedName() + "." + composition);
        if (attachments != null && !attachments.isEmpty()) {
          propertyTitles = SDMUtils.getPropertyTitles(attachmentEntity, attachments.get(0));
          secondaryPropertiesWithInvalidDefinitions =
              SDMUtils.getSecondaryPropertiesWithInvalidDefinition(
                  attachmentEntity, attachments.get(0));
        }
        processEntity(
            context,
            entity,
            fileNameWithRestrictedCharacters,
            duplicateFileNameList,
            filesNotFound,
            filesWithUnsupportedProperties,
            badRequest,
            composition,
            attachmentEntity,
            secondaryPropertiesWithInvalidDefinitions,
            noSDMRoles,
            fileWithWhiteSpace);
        handleWarnings(
            context,
            fileNameWithRestrictedCharacters,
            duplicateFileNameList,
            filesNotFound,
            filesWithUnsupportedProperties,
            badRequest,
            propertyTitles,
            fileWithWhiteSpace,
            noSDMRoles);
      }
    }
  }

  private void handleDuplicateFilenames(
      CdsCreateEventContext context, Set<String> duplicateFilenames) {
    context
        .getMessages()
        .error(
            String.format(
                SDMConstants.DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE,
                String.join(", ", duplicateFilenames)));
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
      List<String> fileWithWhiteSpace)
      throws IOException {
    List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get(composition);
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
            fileWithWhiteSpace);
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
      List<String> fileWithWhiteSpace,
      List<String> noSDMRoles)
      throws IOException {
    String id = (String) attachment.get("ID");
    String fileNameInDB;
    fileNameInDB =
        dbQuery.getAttachmentForID(
            attachmentEntity.get(),
            persistenceService,
            id); // Fetching the name of the file from DB
    String filenameInRequest =
        (String) attachment.get("fileName"); // Fetching the name of the file from request
    String objectId = (String) attachment.get("objectId");
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    String fileNameInSDM =
        sdmService.getObject(
            objectId,
            sdmCredentials,
            context
                .getUserInfo()
                .isSystemUser()); // Fetch original filename from SDM since it's null in attachments
    // table until save; needed to revert UI-modified names on error.

    Map<String, String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(
            attachmentEntity,
            attachment); // Fetching the secondary type properties from the attachment entity
    Map<String, String> propertiesInDB;
    propertiesInDB =
        dbQuery.getPropertiesForID(
            attachmentEntity.get(),
            persistenceService,
            id,
            secondaryTypeProperties); // Fetching the values of the properties from the DB

    // Get the updated secondary properties
    Map<String, String> updatedSecondaryProperties =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity,
            attachment,
            persistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    if (Boolean.TRUE.equals(SDMUtils.isRestrictedCharactersInName(filenameInRequest))) {
      fileNameWithRestrictedCharacters.add(filenameInRequest);
      replacePropertiesInAttachment(
          attachment,
          fileNameInSDM,
          propertiesInDB,
          secondaryTypeProperties); // In this case we immediately stop the processing (Request
      // isn't sent to SDM)
    } else {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName(filenameInRequest);
      cmisDocument.setObjectId(objectId);
      if (fileNameInDB
          == null) { // If the file name in DB is null, it means that the file is being created for
        // the first time
        if (filenameInRequest != null) {
          updatedSecondaryProperties.put("filename", filenameInRequest);
        } else {
          throw new ServiceException("Filename cannot be empty");
        }
      } else {
        if (filenameInRequest == null || filenameInRequest.trim().length() == 0) {
          fileWithWhiteSpace.add(fileNameInDB);
          replacePropertiesInAttachment(
              attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);

        } else if (!fileNameInDB.equals(
            filenameInRequest)) { // If the file name in DB is not equal to the file name in
          // request, it means that the file name has been modified
          updatedSecondaryProperties.put("filename", filenameInRequest);
        }
      }
      try {
        int responseCode =
            sdmService.updateAttachments(
                sdmCredentials,
                cmisDocument,
                updatedSecondaryProperties,
                secondaryPropertiesWithInvalidDefinitions,
                context.getUserInfo().isSystemUser());
        switch (responseCode) {
          case 403:
            // SDM Roles for user are missing
            noSDMRoles.add(fileNameInSDM);
            replacePropertiesInAttachment(
                attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties);
            break;
          case 409:
            duplicateFileNameList.add(filenameInRequest);
            replacePropertiesInAttachment(
                attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties);
            break;
          case 404:
            filesNotFound.add(filenameInRequest);
            replacePropertiesInAttachment(
                attachment, filenameInRequest, propertiesInDB, secondaryTypeProperties);
            break;
          case 200:
          case 201:
            // Success cases, do nothing
            break;

          default:
            throw new ServiceException(SDMConstants.SDM_ROLES_ERROR_MESSAGE, null);
        }
      } catch (ServiceException e) {
        // This exception is thrown when there are unsupported properties in the request
        if (e.getMessage().startsWith(SDMConstants.UNSUPPORTED_PROPERTIES)) {
          String unsupportedDetails =
              e.getMessage().substring(SDMConstants.UNSUPPORTED_PROPERTIES.length()).trim();
          filesWithUnsupportedProperties.add(unsupportedDetails);
          replacePropertiesInAttachment(
              attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties);
        } else {
          badRequest.put(filenameInRequest, e.getMessage());
          replacePropertiesInAttachment(
              attachment, filenameInRequest, propertiesInDB, secondaryTypeProperties);
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
      CdsCreateEventContext context,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList,
      List<String> filesNotFound,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest,
      Map<String, String> propertyTitles,
      List<String> fileWithWhiteSpace,
      List<String> noSDMRoles) {
    if (!fileNameWithRestrictedCharacters.isEmpty()) {
      context
          .getMessages()
          .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedCharacters, "Rename"));
    }
    if (!duplicateFileNameList.isEmpty()) {
      context
          .getMessages()
          .warn(
              String.format(
                  SDMConstants.FILES_RENAME_WARNING_MESSAGE,
                  String.join(", ", duplicateFileNameList)));
    }
    if (!filesNotFound.isEmpty()) {
      context.getMessages().warn(SDMConstants.fileNotFound(filesNotFound));
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
        context.getMessages().warn(SDMConstants.unsupportedPropertiesMessage(invalidPropertyNames));
      }
    }

    if (!badRequest.isEmpty()) {
      context.getMessages().warn(SDMConstants.badRequestMessage(badRequest));
    }
    if (!noSDMRoles.isEmpty()) {
      context.getMessages().warn(SDMConstants.noSDMRolesMessage(noSDMRoles, "create"));
    }
    if (!fileWithWhiteSpace.isEmpty()) {
      context
          .getMessages()
          .warn(
              String.format(
                  SDMConstants.FILENAME_WHITESPACE_WARNING_MESSAGE,
                  String.join(", ", fileWithWhiteSpace)));
    }
  }

  private List<String> getEntityCompositions(CdsCreateEventContext context) {
    List<CdsElement> compositions = context.getTarget().compositions().toList();
    List<String> attachmentsCompositionList = new ArrayList<>();
    for (CdsElement cdsElement : compositions) {
      if (cdsElement != null) {
        CdsAssociationType cdsAssociationType = cdsElement.getType();
        String targetAspect =
            cdsAssociationType.getTargetAspect().isPresent()
                ? cdsAssociationType.getTargetAspect().get().getQualifiedName()
                : null;
        if (targetAspect != null && targetAspect.equalsIgnoreCase("sap.attachments.Attachments")) {
          attachmentsCompositionList.add(cdsElement.getName());
        }
      }
    }
    return attachmentsCompositionList;
  }
}
