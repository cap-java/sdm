package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
    persistenceService.run(updateQuery);
  }

  public static List<CmisDocument> getAttachmentsForFolder(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String folderId) {
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    CqnSelect q =
        Select.from(attachmentEntity)
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
