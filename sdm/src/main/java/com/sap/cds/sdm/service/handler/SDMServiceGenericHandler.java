package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final RegisterService attachmentService;
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;

  public SDMServiceGenericHandler(
      RegisterService attachmentService,
      PersistenceService persistenceService,
      SDMService sdmService,
      TokenHandler tokenHandler) {
    this.attachmentService = attachmentService;
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.tokenHandler = tokenHandler;
  }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    String upID = context.get("up__ID").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();
    var copyEventInput =
        new CopyAttachmentInput(upID, context.getTarget().getQualifiedName(), objectIds);
    attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
    context.setCompleted();
  }

  @On(event = "download")
  public InputStream downloadAttachments(EventContext context) throws IOException {
    System.out.println("In download ");
    CdsModel cdsModel = context.getModel();
    CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    Optional<CdsEntity> attachmentDraftEntity =
        cdsModel.findEntity(context.get("Parameter2") + "_drafts");
    DBQuery dbQueryInstance = DBQuery.getDBQueryInstance();
    List<String> IDs = (List<String>) context.get("Parameter1");
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(byteArrayOutputStream)) {
      for (int i = 0; i < IDs.size(); i++) {
        CmisDocument cmisDocument =
            dbQueryInstance.getObjectIdForAttachmentID(
                attachmentDraftEntity.get(), persistenceService, IDs.get(i));
        SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
        // use the objectId and call the getContentStream and then add it to zip
        InputStream inputStream =
            sdmService.getContent(
                cmisDocument.getObjectId(), sdmCredentials, context.getUserInfo().isSystemUser());
        int lastIndex = cmisDocument.getFileName().lastIndexOf('.');
        addStreamToZip(
            zos,
            inputStream,
                cmisDocument.getFileName().substring(0, lastIndex) + (i + 1) + "." + cmisDocument.getFileName().substring(lastIndex + 1));
      }
    }
    return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
  }

  private static void addStreamToZip(ZipOutputStream zos, InputStream inputStream, String entryName)
      throws IOException {
    ZipEntry zipEntry = new ZipEntry(entryName);
    zos.putNextEntry(zipEntry);
    byte[] buffer = new byte[4096]; // Efficient buffer size
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      zos.write(buffer, 0, bytesRead);
    }

    zos.closeEntry();
  }
}
