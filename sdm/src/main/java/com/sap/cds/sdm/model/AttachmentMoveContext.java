package com.sap.cds.sdm.model;

import com.sap.cds.reflect.CdsEntity;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Helper class to hold attachment move context information. */
public class AttachmentMoveContext {
  private final String objectId;
  private final MoveAttachmentsRequest request;
  private final List<String> validSecondaryProperties;
  private final Map<String, String> entityAnnotations;
  private final CdsEntity targetEntity;
  private final AttachmentProcessingResults processingResults;
  private final List<Map<String, String>> failedAttachments;

  public AttachmentMoveContext(
      String objectId,
      MoveAttachmentsRequest request,
      List<String> validSecondaryProperties,
      Map<String, String> entityAnnotations,
      CdsEntity targetEntity,
      AttachmentProcessingResults processingResults,
      List<Map<String, String>> failedAttachments) {
    this.objectId = objectId;
    this.request = request;
    this.validSecondaryProperties = validSecondaryProperties;
    this.entityAnnotations = entityAnnotations;
    this.targetEntity = targetEntity;
    this.processingResults = processingResults;
    this.failedAttachments = failedAttachments;
  }

  public String getObjectId() {
    return objectId;
  }

  public MoveAttachmentsRequest getRequest() {
    return request;
  }

  public List<String> getValidSecondaryProperties() {
    return validSecondaryProperties;
  }

  public Map<String, String> getEntityAnnotations() {
    return entityAnnotations;
  }

  public CdsEntity getTargetEntity() {
    return targetEntity;
  }

  public AttachmentProcessingResults getProcessingResults() {
    return processingResults;
  }

  public List<Map<String, String>> getFailedAttachments() {
    return Collections.unmodifiableList(failedAttachments);
  }
}
