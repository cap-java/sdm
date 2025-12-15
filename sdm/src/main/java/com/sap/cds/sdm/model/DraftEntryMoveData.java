package com.sap.cds.sdm.model;

import java.util.List;

/** Helper class to encapsulate draft entry creation parameters for move operations. */
public class DraftEntryMoveData {
  private final List<List<String>> movedAttachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final String parentEntity;
  private final String compositionName;
  private final String upID;
  private final String upIdKey;
  private final String repositoryId;
  private final String folderId;

  public DraftEntryMoveData(
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      String parentEntity,
      String compositionName,
      String upID,
      String upIdKey,
      String repositoryId,
      String folderId) {
    this.movedAttachmentsMetadata = movedAttachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
    this.parentEntity = parentEntity;
    this.compositionName = compositionName;
    this.upID = upID;
    this.upIdKey = upIdKey;
    this.repositoryId = repositoryId;
    this.folderId = folderId;
  }

  public List<List<String>> getMovedAttachmentsMetadata() {
    return movedAttachmentsMetadata;
  }

  public List<CmisDocument> getPopulatedDocuments() {
    return populatedDocuments;
  }

  public String getParentEntity() {
    return parentEntity;
  }

  public String getCompositionName() {
    return compositionName;
  }

  public String getUpID() {
    return upID;
  }

  public String getUpIdKey() {
    return upIdKey;
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public String getFolderId() {
    return folderId;
  }
}
