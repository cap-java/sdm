package com.sap.cds.sdm.service;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import org.json.JSONObject;

public interface SDMService {
  public JSONObject createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException;

  public String createFolder(String parentId, String jwtToken, String repositoryId)
      throws IOException;

  public String getFolderId(
      String jwtToken,
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String up__ID)
      throws IOException;

  public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId)
      throws IOException;

  public String checkRepositoryType(String repositoryId) throws IOException;

  public JSONObject getRepositoryInfo(String token, SDMCredentials sdmCredentials)
      throws IOException;

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId) throws IOException;

  public void readDocument();

  public void deleteDocument();
}
