package com.sap.cds.sdm.model;

import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import java.util.List;

/** Request object encapsulating all parameters for database update and source cleanup. */
public class DatabaseUpdateRequest {
  private final List<List<String>> movedAttachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final String parentEntity;
  private final String compositionName;
  private final String upID;
  private final String upIdKey;
  private final String repositoryId;
  private final String folderId;
  private final List<String> successfulObjectIds;
  private final AttachmentMoveEventContext context;

  public DatabaseUpdateRequest(
      List<List<String>> movedAttachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      String parentEntity,
      String compositionName,
      String upID,
      String upIdKey,
      String repositoryId,
      String folderId,
      List<String> successfulObjectIds,
      AttachmentMoveEventContext context) {
    this.movedAttachmentsMetadata = movedAttachmentsMetadata;
    this.populatedDocuments = populatedDocuments;
    this.parentEntity = parentEntity;
    this.compositionName = compositionName;
    this.upID = upID;
    this.upIdKey = upIdKey;
    this.repositoryId = repositoryId;
    this.folderId = folderId;
    this.successfulObjectIds = successfulObjectIds;
    this.context = context;
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

  public List<String> getSuccessfulObjectIds() {
    return successfulObjectIds;
  }

  public AttachmentMoveEventContext getContext() {
    return context;
  }
}
