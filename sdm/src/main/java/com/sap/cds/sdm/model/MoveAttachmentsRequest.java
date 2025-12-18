package com.sap.cds.sdm.model;

import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import java.util.List;

/**
 * Parameter object for moveAttachmentsInSDM method to reduce parameter count and improve code
 * maintainability.
 */
public class MoveAttachmentsRequest {
  private final AttachmentMoveEventContext context;
  private final String sourceFolderId;
  private final List<String> objectIds;
  private final String targetFolderId;
  private final String repositoryId;
  private final SDMCredentials sdmCredentials;
  private final Boolean isSystemUser;
  private final boolean targetFolderExists;

  private MoveAttachmentsRequest(Builder builder) {
    this.context = builder.context;
    this.sourceFolderId = builder.sourceFolderId;
    this.objectIds = builder.objectIds;
    this.targetFolderId = builder.targetFolderId;
    this.repositoryId = builder.repositoryId;
    this.sdmCredentials = builder.sdmCredentials;
    this.isSystemUser = builder.isSystemUser;
    this.targetFolderExists = builder.targetFolderExists;
  }

  // Getters
  public AttachmentMoveEventContext getContext() {
    return context;
  }

  public String getSourceFolderId() {
    return sourceFolderId;
  }

  public List<String> getObjectIds() {
    return objectIds;
  }

  public String getTargetFolderId() {
    return targetFolderId;
  }

  public String getRepositoryId() {
    return repositoryId;
  }

  public SDMCredentials getSdmCredentials() {
    return sdmCredentials;
  }

  public Boolean getIsSystemUser() {
    return isSystemUser;
  }

  public boolean isTargetFolderExists() {
    return targetFolderExists;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private AttachmentMoveEventContext context;
    private String sourceFolderId;
    private List<String> objectIds;
    private String targetFolderId;
    private String repositoryId;
    private SDMCredentials sdmCredentials;
    private Boolean isSystemUser;
    private boolean targetFolderExists;

    public Builder context(AttachmentMoveEventContext context) {
      this.context = context;
      return this;
    }

    public Builder sourceFolderId(String sourceFolderId) {
      this.sourceFolderId = sourceFolderId;
      return this;
    }

    public Builder objectIds(List<String> objectIds) {
      this.objectIds = objectIds;
      return this;
    }

    public Builder targetFolderId(String targetFolderId) {
      this.targetFolderId = targetFolderId;
      return this;
    }

    public Builder repositoryId(String repositoryId) {
      this.repositoryId = repositoryId;
      return this;
    }

    public Builder sdmCredentials(SDMCredentials sdmCredentials) {
      this.sdmCredentials = sdmCredentials;
      return this;
    }

    public Builder isSystemUser(Boolean isSystemUser) {
      this.isSystemUser = isSystemUser;
      return this;
    }

    public Builder targetFolderExists(boolean targetFolderExists) {
      this.targetFolderExists = targetFolderExists;
      return this;
    }

    public MoveAttachmentsRequest build() {
      return new MoveAttachmentsRequest(this);
    }
  }
}
