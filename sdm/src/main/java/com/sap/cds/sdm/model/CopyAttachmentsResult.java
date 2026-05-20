package com.sap.cds.sdm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Result class for copyAttachmentsToSDM method. */
public class CopyAttachmentsResult {
  private final List<Map<String, String>> attachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final List<Map<String, String>> failedAttachments;

  public CopyAttachmentsResult(
      List<Map<String, String>> attachmentsMetadata, List<CmisDocument> populatedDocuments) {
    this(attachmentsMetadata, populatedDocuments, new ArrayList<>());
  }

  public CopyAttachmentsResult(
      List<Map<String, String>> attachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      List<Map<String, String>> failedAttachments) {
    this.attachmentsMetadata = attachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
    this.failedAttachments = failedAttachments;
  }

  public List<Map<String, String>> getAttachmentsMetadata() {
    return attachmentsMetadata;
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return populatedDocuments;
  }

  public List<Map<String, String>> getFailedAttachments() {
    return failedAttachments;
  }
}
