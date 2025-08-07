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
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.ehcache.Cache;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMUpdateAttachmentsHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

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
    List<String> attachmentCompositions = getEntityCompositions(context);
    for (String composition : attachmentCompositions) {
      updateName(context, data, composition);
    }
  }

  public void updateName(CdsUpdateEventContext context, List<CdsData> data, String composition)
      throws IOException {
    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data, composition);
    if (!duplicateFilenames.isEmpty()) {
      context
          .getMessages()
          .error(
              String.format(
                  SDMConstants.DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE,
                  String.join(", ", duplicateFilenames)));
    } else {
      Optional<CdsEntity> attachmentEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + "." + composition);
      renameDocument(attachmentEntity, context, data, composition);
    }
  }

  private void renameDocument(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      List<CdsData> data,
      String composition)
      throws IOException {
    List<String> duplicateFileNameList = new ArrayList<>();
    Map<String, String> secondaryPropertiesWithInvalidDefinitions;
    List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
    List<String> filesNotFound = new ArrayList<>();
    List<String> filesWithUnsupportedProperties = new ArrayList<>();
    Map<String, String> badRequest = new HashMap<>();
    Map<String, String> propertyTitles = new HashMap<>();
    List<String> noSDMRoles = new ArrayList<>();
    List<String> fileWithWhiteSpace = new ArrayList<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get(composition);
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
            fileWithWhiteSpace);
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
        fileWithWhiteSpace);
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
      List<String> fileWithWhiteSpace)
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
          noSDMRoles,
          fileWithWhiteSpace);
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
      List<String> fileWithWhiteSpace)
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
        SDMUtils.isRestrictedCharactersInName(
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
      if (filenameInRequest == null || filenameInRequest.trim().length() == 0) {
        fileWithWhiteSpace.add(fileNameInDB);
        replacePropertiesInAttachment(
            attachment, fileNameInDB, propertiesInDB, secondaryTypeProperties);
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
            throw new ServiceException(SDMConstants.SDM_ROLES_ERROR_MESSAGE, null);
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
      List<String> fileWithWhiteSpace) {
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
      context.getMessages().warn(SDMConstants.noSDMRolesMessage(noSDMRoles, "update"));
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

  private List<String> getEntityCompositions(CdsUpdateEventContext context) {
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
