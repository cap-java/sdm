package com.sap.cds.sdm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoValue {
  private String virusScanEnabled;
  private String versionEnabled;
  private Boolean disableVirusScannerForLargeFile;
}
