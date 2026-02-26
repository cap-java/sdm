package com.sap.cds.sdm.model;

import com.sap.cds.reflect.CdsEntity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Helper class to encapsulate validated attachment data for processing. */
public class ValidatedAttachmentData {
  private final String objectId;
  private final String fileName;
  private final String mimeType;
  private final String description;
  private final String movedObjectId;
  private final JSONObject succinctProperties;
  private final Map<String, String> entityAnnotations;
  private final CdsEntity targetEntity;
  private final List<String> successfulObjectIds;
  private final List<List<String>> movedAttachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final CmisDocument sourceCmisDocument;
  private final String createdBy;
  private final java.time.Instant creationDate;
  private final String lastModifiedBy;
  private final java.time.Instant lastModificationDate;

  public ValidatedAttachmentData(
      String objectId,
      String fileName,
      String mimeType,
      String description,
      String movedObjectId,
      JSONObject succinctProperties,
      Map<String, String> entityAnnotations,
      CdsEntity targetEntity,
      List<String> successfulObjectIds,
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      CmisDocument sourceCmisDocument,
      String createdBy,
      java.time.Instant creationDate,
      String lastModifiedBy,
      java.time.Instant lastModificationDate) {
    this.objectId = objectId;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.description = description;
    this.movedObjectId = movedObjectId;
    this.succinctProperties = succinctProperties;
    this.entityAnnotations = entityAnnotations;
    this.targetEntity = targetEntity;
    this.successfulObjectIds = successfulObjectIds;
    this.movedAttachmentsMetadata = movedAttachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
    this.sourceCmisDocument = sourceCmisDocument;
    this.createdBy = createdBy;
    this.creationDate = creationDate;
    this.lastModifiedBy = lastModifiedBy;
    this.lastModificationDate = lastModificationDate;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getDescription() {
    return description;
  }

  public String getMovedObjectId() {
    return movedObjectId;
  }

  public JSONObject getSuccinctProperties() {
    return succinctProperties;
  }

  public Map<String, String> getEntityAnnotations() {
    return entityAnnotations;
  }

  public CdsEntity getTargetEntity() {
    return targetEntity;
  }

  public List<String> getSuccessfulObjectIds() {
    return Collections.unmodifiableList(successfulObjectIds);
  }

  public List<List<String>> getMovedAttachmentsMetadata() {
    return Collections.unmodifiableList(movedAttachmentsMetadata);
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return Collections.unmodifiableList(populatedDocuments);
  }

  /**
   * Internal method for adding successful object IDs during processing. For internal use only - do
   * not expose to external callers.
   */
  public void addSuccessfulObjectId(String objectId) {
    successfulObjectIds.add(objectId);
  }

  /**
   * Internal method for adding moved attachments metadata during processing. For internal use only
   * - do not expose to external callers.
   */
  public void addMovedAttachmentMetadata(List<String> metadata) {
    movedAttachmentsMetadata.add(metadata);
  }

  /**
   * Internal method for adding populated documents during processing. For internal use only - do
   * not expose to external callers.
   */
  public void addPopulatedDocument(CmisDocument document) {
    populatedDocuments.add(document);
  }

  public CmisDocument getSourceCmisDocument() {
    return sourceCmisDocument;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public java.time.Instant getCreationDate() {
    return creationDate;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public java.time.Instant getLastModificationDate() {
    return lastModificationDate;
  }
}
