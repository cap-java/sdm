package com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.persistence.DBQuery.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
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
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMUpdateAttachmentsHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMUpdateAttachmentsHandler(PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void processBefore(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
    updateName(context, data);
  }

  public void updateName(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data);
    if (!duplicateFilenames.isEmpty()) {
      context
          .getMessages()
          .error(
              String.format(
                  SDMConstants.DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE,
                  String.join(", ", duplicateFilenames)));
    } else {
      Optional<CdsEntity> attachmentEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + ".attachments");
      renameDocument(attachmentEntity, context, data);
    }
  }

  private void renameDocument(
      Optional<CdsEntity> attachmentEntity, CdsUpdateEventContext context, List<CdsData> data)
      throws IOException {
    List<String> duplicateFileNameList = new ArrayList<>();
    List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
      if (attachments != null) {
        processAttachments(
            attachmentEntity,
            context,
            attachments,
            duplicateFileNameList,
            fileNameWithRestrictedCharacters);
      }
    }
    handleWarnings(context, duplicateFileNameList, fileNameWithRestrictedCharacters);
  }

  private void processAttachments(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      List<Map<String, Object>> attachments,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters)
      throws IOException {
    Iterator<Map<String, Object>> iterator = attachments.iterator();
    while (iterator.hasNext()) {
      Map<String, Object> attachment = iterator.next();
      processAttachment(
          attachmentEntity,
          context,
          attachment,
          duplicateFileNameList,
          fileNameWithRestrictedCharacters);
    }
  }

  public void processAttachment(
      Optional<CdsEntity> attachmentEntity,
      CdsUpdateEventContext context,
      Map<String, Object> attachment,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters)
      throws IOException {
    String id = (String) attachment.get("ID"); // Ensure appropriate cast to String
    List<String> keysList = new ArrayList<>(attachment.keySet());
    List<String> secondaryTypeProperties = new ArrayList<>();
    secondaryTypeProperties.add("fileName");
    if (attachmentEntity.isPresent()) {
      CdsEntity entity = attachmentEntity.get();
      for (String key : keysList) {
        if ("DRAFT_READONLY_CONTEXT".equals(key)) {
          continue;
        }
        CdsElement element = entity.getElement(key);
        if (element != null) {
          Optional<CdsAnnotation<Object>> annotation =
              element.findAnnotation("@AdditionalProperty");
          if (annotation.isPresent()) {
            secondaryTypeProperties.add(element.getName());
          }
        }
      }
    } else {
      throw new ServiceException("Entity not found");
    }
    Map<String, Object> propertiesMap = new HashMap<>();
    for (String property : secondaryTypeProperties) {
      Object value = attachment.get(property);
      propertiesMap.put(property, value);
    }
    System.out.println("Properties Map : " + propertiesMap);
    String filenameInRequest = (String) attachment.get("fileName");
    String objectId = (String) attachment.get("objectId");
    List<String> propertiesInDB = new ArrayList<>();
    propertiesInDB =
        DBQuery.getpropertiesForID(
            attachmentEntity.get(), persistenceService, id, secondaryTypeProperties);
    Map<String, String> updatedSecondaryProperties = new HashMap<>();
    for (String property : secondaryTypeProperties) {
      String valueInDB = propertiesInDB.get(secondaryTypeProperties.indexOf(property));
      Object valueInMap = propertiesMap.get(property);
      if ("cmis___rm_holdIds".equals(property) && valueInMap != null && valueInDB != null) {
        throw new ServiceException(
            "The properties could not be modified because of an active hold. Please set the value of 'hold' to empty and try again.",
            null);
      }
      if ("cmis___rm_holdIds".equals(property) && valueInMap == null && valueInDB == null) {
        continue;
      }
      if (valueInMap != valueInDB) {
        if (valueInMap != null) {
          updatedSecondaryProperties.put(property, valueInMap.toString());
        } else {
          updatedSecondaryProperties.put(property, null);
        }
      }
    }
    System.out.println("Updated Secondary Properties : " + updatedSecondaryProperties);

    if (Boolean.TRUE.equals(SDMUtils.isRestrictedCharactersInName(filenameInRequest))) {
      fileNameWithRestrictedCharacters.add(filenameInRequest);
      return;
    }
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName(filenameInRequest);
    cmisDocument.setObjectId(objectId);
    String fileNameInDB =
        DBQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
    String fileNameInSDM = getFileNameInSDM(context, fileNameInDB, objectId);
    if (fileNameInSDM != null && !fileNameInSDM.equals(filenameInRequest)) {
      if (Boolean.TRUE.equals(SDMUtils.isRestrictedCharactersInName(filenameInRequest))) {
        fileNameWithRestrictedCharacters.add(filenameInRequest);
        attachment.replace("fileName", fileNameInSDM);
        return;
      }
      int responseCode =
          sdmService.renameAttachments(
              context.getAuthenticationInfo().as(JwtTokenAuthenticationInfo.class).getToken(),
              TokenHandler.getSDMCredentials(),
              cmisDocument,
              updatedSecondaryProperties);
      switch (responseCode) {
        case 403:
          // SDM Roles for user are missing
          throw new ServiceException(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG, null);

        case 409:
          duplicateFileNameList.add(filenameInRequest);
          attachment.replace("fileName", fileNameInSDM);
          break;

        case 200:
        case 201:
          // Success cases, do nothing
          break;

        default:
          throw new ServiceException(SDMConstants.SDM_ROLES_ERROR_MESSAGE, null);
      }
    }
  }

  private String getFileNameInSDM(
      CdsUpdateEventContext context, String fileNameInDB, String objectId) throws IOException {
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    if (Objects.isNull(fileNameInDB)) {
      return sdmService.getObject(jwtToken, objectId, sdmCredentials);
    } else {
      return fileNameInDB;
    }
  }

  private void handleWarnings(
      CdsUpdateEventContext context,
      List<String> duplicateFileNameList,
      List<String> fileNameWithRestrictedCharacters) {
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
  }
}
