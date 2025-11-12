package com.sap.cds.sdm.model;

import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import java.util.List;

/**
 * Parameter object for copyAttachmentsToSDM method to reduce parameter count and improve code
 * maintainability.
 */
public class CopyAttachmentsRequest {
  private final AttachmentCopyEventContext context;
  private final List<String> objectIds;
  private final String folderId;
  private final String repositoryId;
  private final SDMCredentials sdmCredentials;
  private final Boolean isSystemUser;
  private final boolean folderExists;

  private CopyAttachmentsRequest(Builder builder) {
    this.context = builder.context;
    this.objectIds = builder.objectIds;
    this.folderId = builder.folderId;
    this.repositoryId = builder.repositoryId;
    this.sdmCredentials = builder.sdmCredentials;
    this.isSystemUser = builder.isSystemUser;
    this.folderExists = builder.folderExists;
  }

  // Getters
  public AttachmentCopyEventContext getContext() {
    return context;
  }

  public List<String> getObjectIds() {
    return objectIds;
  }

  public String getFolderId() {
    return folderId;
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

  public boolean isFolderExists() {
    return folderExists;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private AttachmentCopyEventContext context;
    private List<String> objectIds;
    private String folderId;
    private String repositoryId;
    private SDMCredentials sdmCredentials;
    private Boolean isSystemUser;
    private boolean folderExists;

    public Builder context(AttachmentCopyEventContext context) {
      this.context = context;
      return this;
    }

    public Builder objectIds(List<String> objectIds) {
      this.objectIds = objectIds;
      return this;
    }

    public Builder folderId(String folderId) {
      this.folderId = folderId;
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

    public Builder folderExists(boolean folderExists) {
      this.folderExists = folderExists;
      return this;
    }

    public CopyAttachmentsRequest build() {
      return new CopyAttachmentsRequest(this);
    }
  }
}
