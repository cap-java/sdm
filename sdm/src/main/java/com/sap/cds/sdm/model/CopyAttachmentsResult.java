package com.sap.cds.sdm.model;

import java.util.List;
import java.util.Map;

/** Result class for copyAttachmentsToSDM method. */
public class CopyAttachmentsResult {
  private final List<Map<String, String>> attachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;

  public CopyAttachmentsResult(
      List<Map<String, String>> attachmentsMetadata, List<CmisDocument> populatedDocuments) {
    this.attachmentsMetadata = attachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
  }

  public List<Map<String, String>> getAttachmentsMetadata() {
    return attachmentsMetadata;
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return populatedDocuments;
  }
}
