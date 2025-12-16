package com.sap.cds.sdm.model;

/** Helper class to hold target folder information. */
public class TargetFolderInfo {
  private final String targetFolderId;
  private final Boolean targetFolderExists;

  public TargetFolderInfo(String targetFolderId, Boolean targetFolderExists) {
    this.targetFolderId = targetFolderId;
    this.targetFolderExists = targetFolderExists;
  }

  public String getTargetFolderId() {
    return targetFolderId;
  }

  public Boolean getTargetFolderExists() {
    return targetFolderExists;
  }
}
