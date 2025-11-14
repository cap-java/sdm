package com.sap.cds.sdm.model;

import java.util.List;
import java.util.Map;

/**
 * Parameter object for createDraftEntries method to reduce parameter count and improve code
 * maintainability.
 */
public class CreateDraftEntriesRequest {
  private final List<List<String>> attachmentsMetadata;
  private final List<CmisDocument> populatedDocuments;
  private final String parentEntity;
  private final String compositionName;
  private final String upID;
  private final String upIdKey;
  private final String repositoryId;
  private final String folderId;
  private final Map<String, String> customPropertyValues;

  private CreateDraftEntriesRequest(Builder builder) {
    this.attachmentsMetadata = builder.attachmentsMetadata;
    this.populatedDocuments = builder.populatedDocuments;
    this.parentEntity = builder.parentEntity;
    this.compositionName = builder.compositionName;
    this.upID = builder.upID;
    this.upIdKey = builder.upIdKey;
    this.repositoryId = builder.repositoryId;
    this.folderId = builder.folderId;
    this.customPropertyValues = builder.customPropertyValues;
  }

  // Getters
  public List<List<String>> getAttachmentsMetadata() {
    return attachmentsMetadata;
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

  public Map<String, String> getCustomPropertyValues() {
    return customPropertyValues;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private List<List<String>> attachmentsMetadata;
    private List<CmisDocument> populatedDocuments;
    private String parentEntity;
    private String compositionName;
    private String upID;
    private String upIdKey;
    private String repositoryId;
    private String folderId;
    private Map<String, String> customPropertyValues;

    public Builder attachmentsMetadata(List<List<String>> attachmentsMetadata) {
      this.attachmentsMetadata = attachmentsMetadata;
      return this;
    }

    public Builder populatedDocuments(List<CmisDocument> populatedDocuments) {
      this.populatedDocuments = populatedDocuments;
      return this;
    }

    public Builder parentEntity(String parentEntity) {
      this.parentEntity = parentEntity;
      return this;
    }

    public Builder compositionName(String compositionName) {
      this.compositionName = compositionName;
      return this;
    }

    public Builder upID(String upID) {
      this.upID = upID;
      return this;
    }

    public Builder upIdKey(String upIdKey) {
      this.upIdKey = upIdKey;
      return this;
    }

    public Builder repositoryId(String repositoryId) {
      this.repositoryId = repositoryId;
      return this;
    }

    public Builder folderId(String folderId) {
      this.folderId = folderId;
      return this;
    }

    public Builder customPropertyValues(Map<String, String> customPropertyValues) {
      this.customPropertyValues = customPropertyValues;
      return this;
    }

    public CreateDraftEntriesRequest build() {
      return new CreateDraftEntriesRequest(this);
    }
  }
}
