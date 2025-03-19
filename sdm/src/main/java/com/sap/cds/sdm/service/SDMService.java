package com.sap.cds.sdm.service;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public interface SDMService {
  public JSONObject createDocument(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken) throws IOException;

  public String createFolder(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, String jwtToken)
      throws IOException;

  public String getFolderId(
      Result result, PersistenceService persistenceService, String upID, String jwtToken)
      throws IOException;

  public String getFolderIdByPath(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, String jwtToken)
      throws IOException;

  public String checkRepositoryType(String jwtToken, String repositoryId) throws IOException;

  public JSONObject getRepositoryInfo(SDMCredentials sdmCredentials, String subdomain)
      throws IOException;

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId) throws IOException;

  public int deleteDocument(String cmisaction, String objectId, String userEmail, String subdomain)
      throws IOException;

  public void readDocument(
      String objectId,
      String jwtToken,
      SDMCredentials sdmCredentials,
      AttachmentReadEventContext context)
      throws IOException;

  public int updateAttachments(
      String jwtToken,
      SDMCredentials sdmCredentials,
      CmisDocument cmisDocument,
      Map<String, String> secondaryProperties)
      throws IOException;

  public String getObject(String jwtToken, String objectId, SDMCredentials sdmCredentials)
      throws IOException;

  public List<String> getSecondaryTypes(
      String repositoryId, String jwtToken, SDMCredentials sdmCredentials) throws IOException;

  public List<String> getValidSecondaryProperties(
      List<String> secondaryTypes,
      String subdomain,
      SDMCredentials sdmCredentials,
      String repositoryId)
      throws IOException;
}
