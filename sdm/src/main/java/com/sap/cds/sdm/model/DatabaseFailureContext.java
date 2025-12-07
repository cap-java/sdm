package com.sap.cds.sdm.model;

import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Helper class to hold database failure context information. */
public class DatabaseFailureContext {
  private final List<String> successfulObjectIds;
  private final String sourceFolderId;
  private final String targetFolderId;
  private final String repositoryId;
  private final SDMCredentials sdmCredentials;
  private final Boolean isSystemUser;
  private final AttachmentMoveEventContext context;
  private final List<Map<String, String>> failedAttachments;

  public DatabaseFailureContext(
      List<String> successfulObjectIds,
      String sourceFolderId,
      String targetFolderId,
      String repositoryId,
      SDMCredentials sdmCredentials,
      Boolean isSystemUser,
      AttachmentMoveEventContext context,
      List<Map<String, String>> failedAttachments) {
    this.successfulObjectIds = successfulObjectIds;
    this.sourceFolderId = sourceFolderId;
    this.targetFolderId = targetFolderId;
    this.repositoryId = repositoryId;
    this.sdmCredentials = sdmCredentials;
    this.isSystemUser = isSystemUser;
    this.context = context;
    this.failedAttachments = failedAttachments;
  }

  public List<String> getSuccessfulObjectIds() {
    return successfulObjectIds;
  }

  public String getSourceFolderId() {
    return sourceFolderId;
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

  public AttachmentMoveEventContext getContext() {
    return context;
  }

  public List<Map<String, String>> getFailedAttachments() {
    // Return an unmodifiable list with each map also wrapped as unmodifiable for deep immutability
    return Collections.unmodifiableList(
        failedAttachments.stream().map(Collections::unmodifiableMap).toList());
  }
}
