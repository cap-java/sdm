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
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
import java.util.ArrayList;
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
          String.format(SDMUtils.getErrorMessage("PARENT_ENTITY_NOT_FOUND_ERROR"), parentEntity));
    }

    // Find the composition element in the parent entity
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          String.format(
              SDMUtils.getErrorMessage("COMPOSITION_NOT_FOUND_ERROR"),
              compositionName,
              parentEntity));
    }

    // Get the target entity of the composition
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    // Find the target attachment entity
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(
              SDMUtils.getErrorMessage("TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR"),
              targetEntityName));
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

  /**
   * Retrieves valid secondary properties for the target attachment entity. Used to determine which
   * properties from SDM should be persisted to the database.
   *
   * @param context The move event context containing target entity information
   * @return Map of DB field name to SDM property name for properties annotated
   *     with @SDM.Attachments.AdditionalProperty
   */
  public Map<String, String> getValidSecondaryPropertiesForMove(
      AttachmentMoveEventContext context) {
    // Use target entity to determine which secondary properties are valid
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    CdsModel model = context.getModel();

    // Find the parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMUtils.getErrorMessage("PARENT_ENTITY_NOT_FOUND_ERROR"), parentEntity));
    }

    // Find the composition element in the parent entity
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          String.format(
              SDMUtils.getErrorMessage("COMPOSITION_NOT_FOUND_ERROR"),
              compositionName,
              parentEntity));
    }

    // Get the target entity of the composition
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    // Find the target attachment entity (check both draft and non-draft)
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isEmpty()) {
      // Try with _drafts suffix
      attachmentEntity = model.findEntity(targetEntityName + "_drafts");
      if (attachmentEntity.isEmpty()) {
        throw new ServiceException(
            String.format(
                SDMUtils.getErrorMessage("TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR"),
                targetEntityName));
      }
    }

    // Manually iterate over all elements to find those with @SDM.Attachments.AdditionalProperty
    // annotation
    Map<String, String> secondaryProperties = new HashMap<>();
    CdsEntity entity = attachmentEntity.get();

    entity
        .elements()
        .forEach(
            element -> {
              // Check for @SDM.Attachments.AdditionalProperty annotation
              Optional<com.sap.cds.reflect.CdsAnnotation<Object>> annotation =
                  element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
              Optional<com.sap.cds.reflect.CdsAnnotation<Object>> nameAnnotation =
                  element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME);

              if (annotation.isPresent()) {
                // Old annotation style: use element name as SDM property name
                secondaryProperties.put(element.getName(), element.getName());
                logger.debug(
                    "Found secondary property (old style): DB field '{}' -> SDM property '{}'",
                    element.getName(),
                    element.getName());
              } else if (nameAnnotation.isPresent()) {
                // New annotation style: use specified SDM property name
                String sdmPropertyName = nameAnnotation.get().getValue().toString();
                secondaryProperties.put(element.getName(), sdmPropertyName);
                logger.debug(
                    "Found secondary property (new style): DB field '{}' -> SDM property '{}'",
                    element.getName(),
                    sdmPropertyName);
              }
            });

    logger.info(
        "Resolved {} secondary properties from target entity '{}': {}",
        secondaryProperties.size(),
        targetEntityName,
        secondaryProperties);

    return secondaryProperties;
  }

  /**
   * Retrieves valid secondary properties and the target entity for the move operation.
   *
   * @param context The move event context containing target entity information
   * @return Object array with [0] = Map of DB field to SDM property, [1] = CdsEntity
   */
  public Object[] getValidSecondaryPropertiesWithEntity(AttachmentMoveEventContext context) {
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    CdsModel model = context.getModel();

    // Find the parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException(
          String.format(SDMUtils.getErrorMessage("PARENT_ENTITY_NOT_FOUND_ERROR"), parentEntity));
    }

    // Find the composition element
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          String.format(
              SDMUtils.getErrorMessage("COMPOSITION_NOT_FOUND_ERROR"),
              compositionName,
              parentEntity));
    }

    // Get the target entity
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    // Find the target attachment entity
    Optional<CdsEntity> attachmentEntity = model.findEntity(targetEntityName);
    if (attachmentEntity.isEmpty()) {
      attachmentEntity = model.findEntity(targetEntityName + "_drafts");
      if (attachmentEntity.isEmpty()) {
        throw new ServiceException(
            String.format(
                SDMUtils.getErrorMessage("TARGET_ATTACHMENT_ENTITY_NOT_FOUND_ERROR"),
                targetEntityName));
      }
    }

    CdsEntity entity = attachmentEntity.get();

    // Get secondary properties annotations
    Map<String, String> secondaryProperties = new HashMap<>();
    entity
        .elements()
        .forEach(
            element -> {
              Optional<com.sap.cds.reflect.CdsAnnotation<Object>> annotation =
                  element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY);
              Optional<com.sap.cds.reflect.CdsAnnotation<Object>> nameAnnotation =
                  element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME);

              if (annotation.isPresent()) {
                secondaryProperties.put(element.getName(), element.getName());
              } else if (nameAnnotation.isPresent()) {
                String sdmPropertyName = nameAnnotation.get().getValue().toString();
                secondaryProperties.put(element.getName(), sdmPropertyName);
              }
            });

    logger.info(
        "Resolved {} secondary properties from target entity '{}'",
        secondaryProperties.size(),
        targetEntityName);

    return new Object[] {secondaryProperties, entity};
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
    logger.info("[SDM-DELETION] Checking for remaining attachments in folder: {}", folderId);

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
      logger.info("[SDM-DELETION] No attachments in draft table. Checking active table");
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

    if (cmisDocuments.isEmpty()) {
      logger.info("[SDM-DELETION] No remaining attachments found. Folder is ready for deletion");
    } else {
      logger.info(
          "[SDM-DELETION] Found {} remaining attachment(s). Will delete single object only",
          cmisDocuments.size());
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
   * @param sourceUpId The up__ID of the source entity to filter deletions (critical when source and
   *     target are same entity type)
   * @param context The move event context containing source entity information
   * @return The number of records deleted
   */
  public long deleteAttachmentsByObjectIds(
      PersistenceService persistenceService,
      List<String> objectIds,
      String sourceUpId,
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

    // Get the target entity of the composition (this is the SOURCE attachment entity)
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String sourceAttachmentEntityName = assocType.getTarget().getQualifiedName();

    long deletedCount = 0;

    // Resolve the up__ID key name
    String upIdKey = resolveUpIdKey(model, sourceParentEntity, sourceCompositionName);
    if (upIdKey == null) {
      logger.error(
          "Unable to resolve up__ID key for source entity: {}. Skipping cleanup.",
          sourceParentEntity);
      return 0;
    }

    // Try deleting from draft table first
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(sourceAttachmentEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      var deleteQuery =
          Delete.from(attachmentDraftEntity.get())
              .where(
                  doc ->
                      doc.get("objectId")
                          .in(objectIds.toArray())
                          .and(doc.get(upIdKey).eq(sourceUpId)));
      Result result = persistenceService.run(deleteQuery);
      deletedCount += result.rowCount();
      logger.info(
          "Deleted {} attachment records from SOURCE draft table '{}' for objectIds: {}",
          result.rowCount(),
          sourceAttachmentEntityName + "_drafts",
          objectIds);
    }

    // Try deleting from non-draft table
    Optional<CdsEntity> attachmentEntity = model.findEntity(sourceAttachmentEntityName);
    if (attachmentEntity.isPresent()) {
      var deleteQuery =
          Delete.from(attachmentEntity.get())
              .where(
                  doc ->
                      doc.get("objectId")
                          .in(objectIds.toArray())
                          .and(doc.get(upIdKey).eq(sourceUpId)));
      Result result = persistenceService.run(deleteQuery);
      deletedCount += result.rowCount();
      logger.info(
          "Deleted {} attachment records from SOURCE table '{}' for objectIds: {}",
          result.rowCount(),
          sourceAttachmentEntityName,
          objectIds);
    }

    if (deletedCount == 0) {
      logger.warn(
          "No attachment metadata found in source entity '{}' for objectIds: {}. This may indicate"
              + " the records were already cleaned up.",
          sourceAttachmentEntityName,
          objectIds);
    }

    return deletedCount;
  }

  /**
   * Queries the source entity's up__ID from the database using the given objectIds. This is used to
   * identify which source entity instance owns the attachments before cleanup.
   *
   * @param persistenceService The persistence service to execute the query
   * @param objectIds The list of object IDs to query
   * @param context The move event context containing source entity information
   * @return The up__ID of the source entity, or null if not found
   */
  public String getSourceUpIdForObjectIds(
      PersistenceService persistenceService,
      List<String> objectIds,
      AttachmentMoveEventContext context) {
    if (objectIds == null || objectIds.isEmpty()) {
      return null;
    }

    String sourceParentEntity = context.getSourceParentEntity();
    String sourceCompositionName = context.getSourceCompositionName();

    if (sourceParentEntity == null || sourceCompositionName == null) {
      return null;
    }

    CdsModel model = context.getModel();

    // Find the source parent entity
    Optional<CdsEntity> optionalParentEntity = model.findEntity(sourceParentEntity);
    if (optionalParentEntity.isEmpty()) {
      return null;
    }

    // Find the composition element
    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(sourceCompositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      return null;
    }

    // Get the source attachment entity
    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String sourceAttachmentEntityName = assocType.getTarget().getQualifiedName();

    // Resolve the up__ID key name
    String upIdKey = resolveUpIdKey(model, sourceParentEntity, sourceCompositionName);
    if (upIdKey == null) {
      return null;
    }

    // Query from draft table first (most likely to have the records)
    Optional<CdsEntity> attachmentDraftEntity =
        model.findEntity(sourceAttachmentEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      CqnSelect q =
          Select.from(attachmentDraftEntity.get())
              .columns(upIdKey)
              .where(doc -> doc.get("objectId").eq(objectIds.get(0)));
      Result result = persistenceService.run(q);
      Optional<Row> res = result.first();
      if (res.isPresent()) {
        Object upIdValue = res.get().get(upIdKey);
        return upIdValue != null ? upIdValue.toString() : null;
      }
    }

    // Try non-draft table
    Optional<CdsEntity> attachmentEntity = model.findEntity(sourceAttachmentEntityName);
    if (attachmentEntity.isPresent()) {
      CqnSelect q =
          Select.from(attachmentEntity.get())
              .columns(upIdKey)
              .where(doc -> doc.get("objectId").eq(objectIds.get(0)));
      Result result = persistenceService.run(q);
      Optional<Row> res = result.first();
      if (res.isPresent()) {
        Object upIdValue = res.get().get(upIdKey);
        return upIdValue != null ? upIdValue.toString() : null;
      }
    }

    return null;
  }

  /**
   * Resolves the up__ID key name for the given entity and composition.
   *
   * @param model The CDS model
   * @param parentEntity The qualified name of the parent entity
   * @param compositionName The name of the composition
   * @return The up__ID key name, or null if not found
   */
  private String resolveUpIdKey(CdsModel model, String parentEntity, String compositionName) {
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      return null;
    }

    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      return null;
    }

    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
      if (upAssociation.isPresent()) {
        CdsElement association = upAssociation.get();
        CdsAssociationType upAssocType = association.getType();
        List<String> fkElements = upAssocType.refs().map(ref -> "up__" + ref.path()).toList();
        if (!fkElements.isEmpty()) {
          return fkElements.get(0);
        }
      }
    }

    return null;
  }
}
