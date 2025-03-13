package com.sap.cds.sdm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Repository {
  private String displayName;
  private String externalId;
  private String description;
  private String repositoryId;
  private String subdomain;
  private Boolean isVersionEnabled;
  private Boolean isVirusScanEnabled;
  private Boolean skipVirusScanForLargeFile;
  private Boolean isClientCacheEnabled;
  private Boolean isEncryptionEnabled;
  private Boolean isThumbnailEnabled;
  private Boolean isContentBridgeEnabled;
  private String hashAlgorithms;
  private List<RepositoryParams> repositoryParams;
}
