package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;

public class DBQuery {

  private static DBQuery dbQueryInstance = new DBQuery();

  private DBQuery() {
    // Singleton pattern
  }

  public static DBQuery getDBQueryInstance() {
    if (dbQueryInstance == null) {
      dbQueryInstance = new DBQuery();
    }
    return dbQueryInstance;
  }

  public Result getAttachmentsForUPID(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String upID,
      String upIdKey) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId")
            .where(doc -> doc.get(upIdKey).eq(upID));
    return persistenceService.run(q);
  }

  public  static CmisDocument getObjectIdForAttachmentID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("objectId", "folderId", "fileName", "mimeType", "contentId", "linkUrl")
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    System.out.println("Result" + result.rowCount());
    Optional<Row> res = result.first();
    CmisDocument cmisDocument = new CmisDocument();
    if (res.isPresent()) {
      Row row = res.get();
      cmisDocument.setObjectId(row.get("objectId").toString());
      cmisDocument.setFileName(row.get("fileName").toString());
      cmisDocument.setFolderId(row.get("folderId").toString());
      cmisDocument.setMimeType(row.get("mimeType").toString());
      cmisDocument.setContentId(
          row.get("contentId") != null ? row.get("contentId").toString() : null);
      cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
    }
    return cmisDocument;
  }

  public Result getAttachmentsForUPIDAndRepository(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String upID,
      String upIdKey) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId")
            .where(
                doc ->
                    doc.get(upIdKey)
                        .eq(upID)
                        .and(doc.get("repositoryId").eq(SDMConstants.REPOSITORY_ID)));
    return persistenceService.run(q);
  }

  public String getAttachmentForID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity).columns("fileName").where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    return result.rowCount() == 0 ? null : result.list().get(0).get("fileName").toString();
  }

  public void addAttachmentToDraft(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      CmisDocument cmisDocument) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    String icon = getIconforMimeType(cmisDocument.getMimeType());
    updatedFields.put("type", icon);

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
    persistenceService.run(updateQuery);
  }

  private static String getIconforMimeType(String mimeType) {
    String type = "sap-icon://document";
    if ((mimeType.contains("vnd.ms-excel")
        || mimeType.contains("vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
      type = "sap-icon://excel-attachment";
    else if ((mimeType.contains("image"))) {
      type = "sap-icon://attachment-photo";
    } else if ((mimeType.contains("text"))) {
      type = "sap-icon://attachment-text-file";
    } else if ((mimeType.contains("pdf"))) {
      type = "sap-icon://pdf-attachment";
    } else if ((mimeType.contains("powerpoint")) || (mimeType.contains("presentation"))) {
      type = "sap-icon://ppt-attachment";
    } else if ((mimeType.contains("video"))) {
      type = "sap-icon://attachment-video";
    } else if ((mimeType.contains("audio"))) {
      type = "sap-icon://attachment-audio";
    } else if ((mimeType.contains("zip"))) {
      type = " sap-icon://attachment-zip-file";
    } else if ((mimeType.contains("html"))) {
      type = "sap-icon://attachment-html";
    }
    return type;
  }

  public List<CmisDocument> getAttachmentsForFolder(
      String entity,
      PersistenceService persistenceService,
      String folderId,
      AttachmentMarkAsDeletedEventContext context) {
    Optional<CdsEntity> attachmentEntity = context.getModel().findEntity(entity + "_drafts");
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    CqnSelect q =
        Select.from(attachmentEntity.get())
            .columns("fileName", "IsActiveEntity", "ID", "folderId", "repositoryId", "objectId")
            .where(doc -> doc.get("folderId").eq(folderId));
    Result result = persistenceService.run(q);
    for (Row row : result.list()) {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFolderId(row.get("folderId").toString());
      cmisDocument.setRepositoryId(row.get("repositoryId").toString());
      cmisDocument.setFileName(row.get("fileName").toString());
      cmisDocument.setAttachmentId(row.get("ID").toString());
      cmisDocument.setObjectId(row.get("objectId").toString());
      cmisDocuments.add(cmisDocument);
    }
    if (cmisDocuments.isEmpty()) {
      attachmentEntity = context.getModel().findEntity(entity);
      q =
          Select.from(attachmentEntity.get())
              .columns("fileName", "IsActiveEntity", "ID", "folderId", "repositoryId", "objectId")
              .where(doc -> doc.get("folderId").eq(folderId));
      result = persistenceService.run(q);
      for (Row row : result.list()) {
        CmisDocument cmisDocument = new CmisDocument();
        cmisDocument.setFolderId(row.get("folderId").toString());
        cmisDocument.setRepositoryId(row.get("repositoryId").toString());
        cmisDocument.setFileName(row.get("fileName").toString());
        cmisDocument.setAttachmentId(row.get("ID").toString());
        cmisDocument.setObjectId(row.get("objectId").toString());
        cmisDocuments.add(cmisDocument);
      }
    }
    return cmisDocuments;
  }



  public Map<String, String> getPropertiesForID(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String id,
      Map<String, String> properties) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns(properties.keySet().toArray(new String[0]))
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    Map<String, String> propertyValueMap = new HashMap<>();

    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String property = entry.getKey();
      String mapKey = entry.getValue();
      Object value = result.rowCount() > 0 ? result.list().get(0).get(property) : null;
      propertyValueMap.put(mapKey, value != null ? value.toString() : null);
    }
    return propertyValueMap;
  }
}
