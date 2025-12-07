package com.sap.cds.sdm.model;

import com.sap.cds.reflect.CdsEntity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
      CmisDocument sourceCmisDocument) {
    this.objectId = objectId;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.description = description;
    this.movedObjectId = movedObjectId;
    this.succinctProperties = succinctProperties;
    this.entityAnnotations = entityAnnotations;
    this.targetEntity = targetEntity;
    this.successfulObjectIds = List.copyOf(successfulObjectIds);
    // Deep immutability: wrap outer list and each inner list as unmodifiable
    this.movedAttachmentsMetadata =
        Collections.unmodifiableList(
            movedAttachmentsMetadata.stream()
                .map(Collections::unmodifiableList)
                .collect(Collectors.toList()));
    this.populatedDocuments = List.copyOf(populatedDocuments);
    this.sourceCmisDocument = sourceCmisDocument;
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
    return successfulObjectIds;
  }

  public List<List<String>> getMovedAttachmentsMetadata() {
    return movedAttachmentsMetadata;
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return populatedDocuments;
  }

  public CmisDocument getSourceCmisDocument() {
    return sourceCmisDocument;
  }
}
