package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
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
import java.util.Iterator;
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
      context
          .getMessages()
          .error(
              String.format(
                  SDMConstants.DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE,
                  String.join(", ", duplicateFilenames)));
    } else {
      List<String> duplicateFileNameList = new ArrayList<>();
      for (Map<String, Object> entity : data) {
        List<Map<String, Object>> attachments =
            (List<Map<String, Object>>) entity.get("attachments");
        if (attachments != null) {
          Iterator<Map<String, Object>> iterator = attachments.iterator();
          while (iterator.hasNext()) {
            Map<String, Object> attachment = iterator.next();
            String filenameInRequest = (String) attachment.get("fileName");
            String objectId = (String) attachment.get("objectId");
            AuthenticationInfo authInfo = context.getAuthenticationInfo();
            JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
            String jwtToken = jwtTokenInfo.getToken();
            SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
            String fileNameInSDM = sdmService.getObject(jwtToken, objectId, sdmCredentials);

            if (fileNameInSDM != null && !fileNameInSDM.equals(filenameInRequest)) {
              CmisDocument cmisDocument = new CmisDocument();
              cmisDocument.setFileName(filenameInRequest);
              cmisDocument.setObjectId(objectId);
              int responseCode =
                  sdmService.renameAttachments(jwtToken, sdmCredentials, cmisDocument);
              if (responseCode == 409) {
                duplicateFileNameList.add(filenameInRequest);
                attachment.replace("fileName", fileNameInSDM);
              }
            }
          }
        }
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
}
