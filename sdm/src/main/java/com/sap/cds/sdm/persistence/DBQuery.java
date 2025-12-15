package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
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
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsReadEventContext;
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
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId", "mimeType")
            .where(doc -> doc.get(upIdKey).eq(upID));
    return persistenceService.run(q);
  }

  public Result getAllAttachments(
      CdsReadEventContext context, PersistenceService persistenceService) {
    Optional<CdsEntity> attachmentDraftEntity =
        context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
    if (attachmentDraftEntity.isPresent()) {

      CqnSelect q =
          Select.from(attachmentDraftEntity.get())
              .columns(
                  "fileName",
                  "ID",
                  "IsActiveEntity",
                  "folderId",
                  "repositoryId",
                  "mimeType",
                  "uploadStatus");
      return persistenceService.run(q);
    } else {
      attachmentDraftEntity = context.getModel().findEntity(context.getTarget().getQualifiedName());
      CqnSelect q =
          Select.from(attachmentDraftEntity.get())
              .columns(
                  "fileName",
                  "ID",
                  "IsActiveEntity",
                  "folderId",
                  "repositoryId",
                  "mimeType",
                  "uploadStatus");
      return persistenceService.run(q);
    }
  }

  public CmisDocument getObjectIdForAttachmentID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns(
                "objectId",
                "folderId",
                "fileName",
                "mimeType",
                "contentId",
                "linkUrl",
                "uploadStatus")
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
      cmisDocument.setUploadStatus(
          row.get("uploadStatus") != null ? row.get("uploadStatus").toString() : null);
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

  public CmisDocument getAttachmentForID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String id) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "uploadStatus")
            .where(doc -> doc.get("ID").eq(id));
    Result result = persistenceService.run(q);
    CmisDocument cmisDocument = new CmisDocument();
    for (Row row : result.list()) {
      cmisDocument.setFileName(row.get("fileName").toString());
      cmisDocument.setUploadStatus(
          row.get("uploadStatus") != null ? row.get("uploadStatus").toString() : null);
    }
    return cmisDocument;
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
    updatedFields.put("uploadStatus", cmisDocument.getUploadStatus());
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

  public CmisDocument getuploadStatusForAttachment(
      String entity,
      PersistenceService persistenceService,
      String objectId,
      AttachmentReadEventContext context) {
    Optional<CdsEntity> attachmentEntity = context.getModel().findEntity(entity + "_drafts");
    CqnSelect q =
        Select.from(attachmentEntity.get())
            .columns("uploadStatus")
            .where(doc -> doc.get("objectId").eq(objectId));
    Result result = persistenceService.run(q);
    CmisDocument cmisDocument = new CmisDocument();
    boolean isAttachmentFound = false;
    for (Row row : result.list()) {
      cmisDocument.setUploadStatus(
          row.get("uploadStatus") != null ? row.get("uploadStatus").toString() : null);
      isAttachmentFound = true;
    }
    if (!isAttachmentFound) {
      attachmentEntity = context.getModel().findEntity(entity);
      q =
          Select.from(attachmentEntity.get())
              .columns("uploadStatus")
              .where(doc -> doc.get("objectId").eq(objectId));
      result = persistenceService.run(q);
      for (Row row : result.list()) {
        cmisDocument.setUploadStatus(
            row.get("uploadStatus") != null ? row.get("uploadStatus").toString() : null);
      }
    }
    return cmisDocument;
  }

  public List<CmisDocument> getAttachmentsWithVirusScanInProgress(
      CdsEntity attachmentEntity, PersistenceService persistenceService) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns(
                "ID",
                "objectId",
                "fileName",
                "folderId",
                "repositoryId",
                "mimeType",
                "uploadStatus")
            .where(doc -> doc.get("uploadStatus").eq(SDMConstants.VIRUS_SCAN_INPROGRESS));
    Result result = persistenceService.run(q);

    List<CmisDocument> attachments = new ArrayList<>();
    for (Row row : result.list()) {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setAttachmentId(row.get("ID") != null ? row.get("ID").toString() : null);
      cmisDocument.setObjectId(row.get("objectId") != null ? row.get("objectId").toString() : null);
      cmisDocument.setFileName(row.get("fileName") != null ? row.get("fileName").toString() : null);
      cmisDocument.setFolderId(row.get("folderId") != null ? row.get("folderId").toString() : null);
      cmisDocument.setRepositoryId(
          row.get("repositoryId") != null ? row.get("repositoryId").toString() : null);
      cmisDocument.setMimeType(row.get("mimeType") != null ? row.get("mimeType").toString() : null);
      cmisDocument.setUploadStatus(
          row.get("uploadStatus") != null ? row.get("uploadStatus").toString() : null);
      attachments.add(cmisDocument);
    }
    return attachments;
  }

  /**
   * Updates uploadStatus to 'SUCCESS' for all attachments where uploadStatus is null for a given
   * up__ID.
   *
   * @param attachmentEntity the attachment entity
   * @param persistenceService the persistence service
   * @param upID the up__ID to filter attachments
   * @param upIdKey the key name for up__ID field (e.g., "up__ID")
   */
  public void updateNullUploadStatusToSuccess(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String upID,
      String upIdKey) {

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data("uploadStatus", SDMConstants.UPLOAD_STATUS_SUCCESS)
            .where(doc -> doc.get(upIdKey).eq(upID).and(doc.get("uploadStatus").isNull()));

    persistenceService.run(updateQuery);
  }

  public void updateUploadStatusByScanStatus(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String objectId,
      SDMConstants.ScanStatus scanStatus) {
    String uploadStatus = mapScanStatusToUploadStatus(scanStatus);

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data("uploadStatus", uploadStatus)
            .where(doc -> doc.get("objectId").eq(objectId));

    persistenceService.run(updateQuery);
  }

  /**
   * Updates the criticality value for an attachment based on its upload status.
   *
   * @param persistenceService the persistence service
   * @param attachmentId the attachment ID
   * @param criticality the calculated criticality value
   */
  public void updateAttachmentCriticality(
      PersistenceService persistenceService,
      String attachmentId,
      int criticality,
      CdsReadEventContext context) {
    // Update the attachment criticality in the database for draft table
    Optional<CdsEntity> attachmentDraftEntity =
        context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
    CqnUpdate updateQuery =
        Update.entity(attachmentDraftEntity.get())
            .data("statusCriticality", criticality)
            .where(doc -> doc.get("ID").eq(attachmentId));

    persistenceService.run(updateQuery);
    Optional<CdsEntity> attachmentEntity =
        context.getModel().findEntity(context.getTarget().getQualifiedName());
    updateQuery =
        Update.entity(attachmentEntity.get())
            .data("statusCriticality", criticality)
            .where(doc -> doc.get("ID").eq(attachmentId));

    persistenceService.run(updateQuery);
  }

  private String mapScanStatusToUploadStatus(SDMConstants.ScanStatus scanStatus) {
    switch (scanStatus) {
      case QUARANTINED:
        return SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED;
      case PENDING:
        return SDMConstants.UPLOAD_STATUS_IN_PROGRESS;
      case SCANNING:
        return SDMConstants.VIRUS_SCAN_INPROGRESS;
      case FAILED:
        return SDMConstants.UPLOAD_STATUS_SCAN_FAILED;
      case CLEAN:
        return SDMConstants.UPLOAD_STATUS_SUCCESS;
      case BLANK:
      default:
        return SDMConstants.UPLOAD_STATUS_SUCCESS;
    }
  }
}
