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
  private DBQuery() {
    // Doesn't do anything
  }

  public static Result getAttachmentsForUPID(
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

  public static String getAttachmentForID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity).columns("fileName").where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    if (result.rowCount() == 0) {
      return null;
    }
    return result.rowCount() == 0 ? null : result.list().get(0).get("fileName").toString();
  }

  public static CmisDocument getObjectIdForAttachmentID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("objectId", "folderId", "fileName", "mimeType", "linkUrl", "contentId")
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
      cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
      cmisDocument.setContentId(row.get("contentId").toString());
    }
    return cmisDocument;
  }

  public static String getUrlForObjectId(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity).columns("linkUrl").where(doc -> doc.get("objectId").eq(id));
    Result result = persistenceService.run(q);
    System.out.println("Result" + result.rowCount());
    Optional<Row> res = result.first();
    CmisDocument cmisDocument = new CmisDocument();
    if (res.isPresent()) {
      Row row = res.get();
      if (row.get("linkUrl") != null) return row.get("linkUrl").toString();
      else return null;
    }
    return null;
  }

  public static void addAttachmentToDraft(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      CmisDocument cmisDocument) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("objectId", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");
    // updatedFields.put("versionSeriesId", cmisDocument.getVersionSeriesId());
    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
    persistenceService.run(updateQuery);
  }

  public static void updateObjectId(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      CmisDocument cmisDocument,
      String subdomain,
      String attachmentId) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put(
        "contentId",
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + attachmentEntity
            + ":"
            + subdomain);

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(attachmentId));
    persistenceService.run(updateQuery);
  }

  public static void updatePWCObjectIdForCheckIn(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String versionSeriesId) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();

    updatedFields.put("PWC_objectId", null);
    updatedFields.put("isLatestVersion", false);
    updatedFields.put("attachmentStatus", "CHECKED_IN");
    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("versionSeriesId").eq(versionSeriesId));
    persistenceService.run(updateQuery);
  }

  public static void updatePWCObjectIdForCheckOut(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String objectId,
      String ID) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();

    updatedFields.put("PWC_objectId", objectId);
    updatedFields.put("attachmentStatus", "CHECKED_OUT");
    CqnUpdate updateQuery =
        Update.entity(attachmentEntity).data(updatedFields).where(doc -> doc.get("ID").eq(ID));
    persistenceService.run(updateQuery);
  }

  public static void updatePWCObjectIdForCancelCheckOut(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String attachmentId) {
    Map<String, Object> updatedFields = new HashMap<>();

    updatedFields.put("PWC_objectId", null);
    updatedFields.put("attachmentStatus", "CANCEL_CHECKED_OUT");
    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("versionSeriesId").eq(attachmentId));
    persistenceService.run(updateQuery);
  }

  public static List<CmisDocument> getAttachmentsForFolder(
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

  public static Map<String, String> getPropertiesForID(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String id,
      List<String> properties) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns(properties.toArray(new String[0]))
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    Map<String, String> propertyValueMap = new HashMap<>();

    // Ensure all keys from the properties list are included in the map
    for (String property : properties) {
      Object value = result.rowCount() > 0 ? result.list().get(0).get(property) : null;
      propertyValueMap.put(property, value != null ? value.toString() : null);
    }

    return propertyValueMap;
  }
}
