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
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
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

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateAttachmentsHandler implements EventHandler {

  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMCreateAttachmentsHandler(PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
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
    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data, composition);
    if (!duplicateFilenames.isEmpty()) {
      handleDuplicateFilenames(context, duplicateFilenames);
    } else {
      List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
      List<String> duplicateFileNameList = new ArrayList<>();
      List<String> filesNotFound = new ArrayList<>();
      List<String> filesWithUnsupportedProperties = new ArrayList<>();
      Map<String, String> badRequest = new HashMap<>();
      for (Map<String, Object> entity : data) {
        processEntity(
            context,
            entity,
            fileNameWithRestrictedCharacters,
            duplicateFileNameList,
            filesNotFound,
            filesWithUnsupportedProperties,
            badRequest,
            composition);
      }
      handleWarnings(
          context,
          fileNameWithRestrictedCharacters,
          duplicateFileNameList,
          filesNotFound,
          filesWithUnsupportedProperties,
          badRequest);
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
      String composition)
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
            composition);
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
      String composition)
      throws IOException {
    String id = (String) attachment.get("ID"); // Ensure appropriate cast to String
    Optional<CdsEntity> attachmentEntity =
        context.getModel().findEntity(context.getTarget().getQualifiedName() + "." + composition);
    String fileNameInDB;
    fileNameInDB = DBQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    String filenameInRequest = (String) attachment.get("fileName");
    String objectId = (String) attachment.get("objectId");
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String fileNameInSDM = sdmService.getObject(jwtToken, objectId, sdmCredentials);

    List<String> secondaryTypeProperties =
        SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);
    Map<String, String> propertiesInDB;
    propertiesInDB =
        DBQuery.getPropertiesForID(
            attachmentEntity.get(), persistenceService, id, secondaryTypeProperties);
    Map<String, Object> propertiesMap = new HashMap<>();
    // For each property get the value
    if (!secondaryTypeProperties.isEmpty()) {
      for (String property : secondaryTypeProperties) {
        Object value = attachment.get(property);
        propertiesMap.put(property, value);
      }
    }
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
      replacePropertiesInAttachment(attachment, fileNameInSDM, propertiesInDB);
    } else {
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
      try {
        JSONObject finalResponse =
            sdmService.updateAttachments(
                context.getAuthenticationInfo().as(JwtTokenAuthenticationInfo.class).getToken(),
                TokenHandler.getSDMCredentials(),
                cmisDocument,
                updatedSecondaryProperties);
        int responseCode = finalResponse.getInt("status");
        switch (responseCode) {
          case 403:
            // SDM Roles for user are missing
            throw new ServiceException(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG, null);

          case 409:
            duplicateFileNameList.add(filenameInRequest);
            replacePropertiesInAttachment(attachment, fileNameInSDM, propertiesInDB);
            break;
          case 404:
            filesNotFound.add(filenameInRequest);
            replacePropertiesInAttachment(attachment, filenameInRequest, propertiesInDB);
            break;
          case 200:
          case 201:
            // Success cases, do nothing
            DBQuery.updateObjectId(
                attachmentEntity.get(),
                persistenceService,
                finalResponse.get("objectId").toString(),
                id);
            break;

          default:
            throw new ServiceException(SDMConstants.SDM_ROLES_ERROR_MESSAGE, null);
        }
      } catch (ServiceException e) {
        if (e.getMessage().startsWith(SDMConstants.UNSUPPORTED_PROPERTIES)) {
          String unsupportedDetails =
              e.getMessage().substring(SDMConstants.UNSUPPORTED_PROPERTIES.length()).trim();
          filesWithUnsupportedProperties.add(unsupportedDetails);
          replacePropertiesInAttachment(attachment, fileNameInSDM, propertiesInDB);
        } else {
          badRequest.put(filenameInRequest, e.getMessage());
          replacePropertiesInAttachment(attachment, filenameInRequest, propertiesInDB);
        }
      }
    }
  }

  private void replacePropertiesInAttachment(
      Map<String, Object> attachment, String fileName, Map<String, String> propertiesInDB) {
    if (propertiesInDB != null) {
      for (Map.Entry<String, String> entry : propertiesInDB.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        attachment.replace(key, value);
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
      Map<String, String> badRequest) {
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
      Set<String> uniqueValues = new HashSet<>();
      for (String str : filesWithUnsupportedProperties) {
        String[] values = str.split(",");
        for (String value : values) {
          uniqueValues.add(value.trim());
        }
      }
      List<String> propertiesList = new ArrayList<>(uniqueValues);
      context.getMessages().warn(SDMConstants.unsupportedPropertiesMessage(propertiesList));
    }
    if (!badRequest.isEmpty()) {
      context.getMessages().warn(SDMConstants.badRequestMessage(badRequest));
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
