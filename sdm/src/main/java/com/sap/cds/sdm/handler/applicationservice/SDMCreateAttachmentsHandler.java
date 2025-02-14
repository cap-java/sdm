package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateAttachmentsHandler implements EventHandler {

  private final SDMService sdmService;

  public SDMCreateAttachmentsHandler(SDMService sdmService) {
    this.sdmService = sdmService;
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void processBefore(CdsCreateEventContext context, List<CdsData> data) throws IOException {
    updateName(context, data);
  }

  public void updateName(CdsCreateEventContext context, List<CdsData> data) throws IOException {
    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data);
    if (!duplicateFilenames.isEmpty()) {
      handleDuplicateFilenames(context, duplicateFilenames);
    } else {
      List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
      List<String> duplicateFileNameList = new ArrayList<>();
      for (Map<String, Object> entity : data) {
        processEntity(context, entity, fileNameWithRestrictedCharacters, duplicateFileNameList);
      }
      handleWarnings(context, fileNameWithRestrictedCharacters, duplicateFileNameList);
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
      List<String> duplicateFileNameList)
      throws IOException {
    List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
    if (attachments != null) {
      for (Map<String, Object> attachment : attachments) {
        processAttachment(
            context, attachment, fileNameWithRestrictedCharacters, duplicateFileNameList);
      }
    }
  }

  private void processAttachment(
      CdsCreateEventContext context,
      Map<String, Object> attachment,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList)
      throws IOException {
    String filenameInRequest = (String) attachment.get("fileName");
    String objectId = (String) attachment.get("objectId");
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String fileNameInSDM = sdmService.getObject(jwtToken, objectId, sdmCredentials);

    if (fileNameInSDM != null && !fileNameInSDM.equals(filenameInRequest)) {
      if (Boolean.TRUE.equals(SDMUtils.isRestrictedCharactersInName(filenameInRequest))) {
        fileNameWithRestrictedCharacters.add(filenameInRequest);
        attachment.replace("fileName", fileNameInSDM);
      } else {
        CmisDocument cmisDocument = new CmisDocument();
        cmisDocument.setFileName(filenameInRequest);
        cmisDocument.setObjectId(objectId);
        Map<String, String> secondaryTypes = new HashMap<>();
        int responseCode =
            sdmService.renameAttachments(jwtToken, sdmCredentials, cmisDocument, secondaryTypes);
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
  }

  private void handleWarnings(
      CdsCreateEventContext context,
      List<String> fileNameWithRestrictedCharacters,
      List<String> duplicateFileNameList) {
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
