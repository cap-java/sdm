package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBQuery {

  private static DBQuery dbQueryInstance = new DBQuery();
  private static final Logger logger = LoggerFactory.getLogger(DBQuery.class);

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
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId", "mimeType")
            .where(doc -> doc.get(upIdKey).eq(upID));
    return persistenceService.run(q);
  }

  public CmisDocument getObjectIdForAttachmentID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("objectId", "folderId", "fileName", "mimeType", "contentId", "linkUrl")
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
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

  public CmisDocument getAttachmentForObjectID(
      PersistenceService persistenceService, String id, AttachmentCopyEventContext context) {

    // Use the new API to resolve the target attachment entity
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    CdsModel model = context.getModel();

    // Find the parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMConstants.PARENT_ENTITY_NOT_FOUND_ERROR, parentEntity));
    }

    // Find the composition element in the parent entity
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          String.format(SDMConstants.COMPOSITION_NOT_FOUND_ERROR, compositionName, parentEntity));
    }

    // Get the target entity of the composition
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    // Find the target attachment entity
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMConstants.TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR, targetEntityName));
    }

    // Search in active entity first
    CqnSelect q =
        Select.from(attachmentEntity.get())
            .columns("linkUrl", "type")
            .where(doc -> doc.get("objectId").eq(id));
    Result result = persistenceService.run(q);
    Optional<Row> res = result.first();

    CmisDocument cmisDocument = new CmisDocument();
    if (res.isPresent()) {
      Row row = res.get();
      cmisDocument.setType(row.get("type") != null ? row.get("type").toString() : null);
      cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
    } else {
      // Check in draft table as well
      Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
      if (attachmentDraftEntity.isPresent()) {
        q =
            Select.from(attachmentDraftEntity.get())
                .columns("linkUrl", "type")
                .where(doc -> doc.get("objectId").eq(id));
        result = persistenceService.run(q);
        res = result.first();
        if (res.isPresent()) {
          Row row = res.get();
          cmisDocument.setType(row.get("type") != null ? row.get("type").toString() : null);
          cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
        }
      }
    }
    return cmisDocument;
  }

  public CmisDocument getAttachmentForObjectID(
      PersistenceService persistenceService, String id, AttachmentMoveEventContext context) {
    // Use the new API to resolve the target attachment entity
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    CdsModel model = context.getModel();

    // Find the parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMConstants.PARENT_ENTITY_NOT_FOUND_ERROR, parentEntity));
    }

    // Find the composition element in the parent entity
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          String.format(SDMConstants.COMPOSITION_NOT_FOUND_ERROR, compositionName, parentEntity));
    }

    // Get the target entity of the composition
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    // Find the target attachment entity
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMConstants.TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR, targetEntityName));
    }

    // Search in active entity first
    CqnSelect q =
        Select.from(attachmentEntity.get())
            .columns("linkUrl", "type")
            .where(doc -> doc.get("objectId").eq(id));
    Result result = persistenceService.run(q);
    Optional<Row> res = result.first();

    CmisDocument cmisDocument = new CmisDocument();
    if (res.isPresent()) {
      Row row = res.get();
      cmisDocument.setType(row.get("type") != null ? row.get("type").toString() : null);
      cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
    } else {
      // Check in draft table as well
      Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
      if (attachmentDraftEntity.isPresent()) {
        q =
            Select.from(attachmentDraftEntity.get())
                .columns("linkUrl", "type")
                .where(doc -> doc.get("objectId").eq(id));
        result = persistenceService.run(q);
        res = result.first();
        if (res.isPresent()) {
          Row row = res.get();
          cmisDocument.setType(row.get("type") != null ? row.get("type").toString() : null);
          cmisDocument.setUrl(row.get("linkUrl") != null ? row.get("linkUrl").toString() : null);
        }
      }
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
    updatedFields.put("type", "sap-icon://document");
    updatedFields.put("mimeType", cmisDocument.getMimeType());
    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
    persistenceService.run(updateQuery);
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
      List<String> properties) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns(properties.toArray(new String[0]))
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    Map<String, String> propertyValueMap = new HashMap<>();

    for (String property : properties) {
      Object value = result.rowCount() > 0 ? result.list().get(0).get(property) : null;
      propertyValueMap.put(property, value != null ? value.toString() : null);
    }

    return propertyValueMap;
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

  /**
   * Deletes attachment metadata from the source entity (both draft and non-draft tables) for the
   * given list of object IDs. This is used to clean up source entity after successful moves.
   *
   * @param persistenceService The persistence service to execute the delete
   * @param objectIds The list of object IDs to delete
   * @param context The move event context containing source entity information
   * @return The number of records deleted
   */
  public int deleteAttachmentsByObjectIds(
      PersistenceService persistenceService,
      List<String> objectIds,
      AttachmentMoveEventContext context) {
    if (objectIds == null || objectIds.isEmpty()) {
      return 0;
    }

    String sourceParentEntity = context.getSourceParentEntity();
    String sourceCompositionName = context.getSourceCompositionName();

    // If source entity info is not provided, skip cleanup
    if (sourceParentEntity == null || sourceCompositionName == null) {
      logger.warn(
          "Source entity information not provided. Skipping metadata cleanup for {} attachments.",
          objectIds.size());
      return 0;
    }

    CdsModel model = context.getModel();

    // Find the source parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(sourceParentEntity);
    if (optionalParentEntity.isEmpty()) {
      logger.error(
          "Unable to find source parent entity: {}. Skipping cleanup.", sourceParentEntity);
      return 0;
    }

    // Find the composition element in the source parent entity
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(sourceCompositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      logger.error(
          "Unable to find composition '{}' in source entity: {}. Skipping cleanup.",
          sourceCompositionName,
          sourceParentEntity);
      return 0;
    }

    // Get the target entity of the composition
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    int deletedCount = 0;

    // Try deleting from draft table first
    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      var deleteQuery =
          Delete.from(attachmentDraftEntity.get())
              .where(doc -> doc.get("objectId").in(objectIds.toArray()));
      Result result = persistenceService.run(deleteQuery);
      deletedCount += result.rowCount();
      logger.info(
          "Deleted {} attachment records from draft table '{}' for objectIds: {}",
          result.rowCount(),
          targetEntityName + "_drafts",
          objectIds);
    }

    // Try deleting from non-draft table
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isPresent()) {
      var deleteQuery =
          Delete.from(attachmentEntity.get())
              .where(doc -> doc.get("objectId").in(objectIds.toArray()));
      Result result = persistenceService.run(deleteQuery);
      deletedCount += result.rowCount();
      logger.info(
          "Deleted {} attachment records from table '{}' for objectIds: {}",
          result.rowCount(),
          targetEntityName,
          objectIds);
    }

    if (deletedCount == 0) {
      logger.warn(
          "No attachment metadata found in source entity '{}' for objectIds: {}. This may indicate"
              + " the records were already cleaned up.",
          targetEntityName,
          objectIds);
    }

    return deletedCount;
  }
}
