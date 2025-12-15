package com.sap.cds.sdm.model;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates the result of a batch move operation in SDM. Contains metadata for successfully
 * moved attachments and tracks failures.
 */
public class MoveAttachmentsResult {
  private final List<List<String>> movedAttachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final List<Map<String, String>> failedAttachments;
  private final List<String> successfulObjectIds;

  public MoveAttachmentsResult(
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      List<Map<String, String>> failedAttachments,
      List<String> successfulObjectIds) {
    this.movedAttachmentsMetadata = movedAttachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
    this.failedAttachments = failedAttachments;
    this.successfulObjectIds = successfulObjectIds;
  }

  public List<List<String>> getMovedAttachmentsMetadata() {
    return movedAttachmentsMetadata;
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return populatedDocuments;
  }

  public List<Map<String, String>> getFailedAttachments() {
    return failedAttachments;
  }

  public List<String> getSuccessfulObjectIds() {
    return successfulObjectIds;
  }
}
