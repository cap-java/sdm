package com.sap.cds.sdm.model;

import java.util.List;

/** Helper class to hold attachment processing results. */
public class AttachmentProcessingResults {
  private final List<String> successfulObjectIds;
  private final List<List<String>> movedAttachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;

  public AttachmentProcessingResults(
      List<String> successfulObjectIds,
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments) {
    this.successfulObjectIds = successfulObjectIds;
    this.movedAttachmentsMetadata = movedAttachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
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
}
