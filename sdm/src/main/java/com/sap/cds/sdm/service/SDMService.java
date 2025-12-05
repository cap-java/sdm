package com.sap.cds.sdm.service;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public interface SDMService {
  public JSONObject createDocument(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken) throws IOException;

  public String createFolder(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException;

  public String getFolderId(
      Result result, PersistenceService persistenceService, String upID, boolean isSystemUser)
      throws IOException;

  public String getFolderIdByPath(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException;

  public RepoValue checkRepositoryType(String repositoryId, String tenant) throws IOException;

  public JSONObject getRepositoryInfo(SDMCredentials sdmCredentials) throws IOException;

  public int deleteDocument(String cmisaction, String objectId, String user) throws IOException;

  public void readDocument(
      String objectId, SDMCredentials sdmCredentials, AttachmentReadEventContext context)
      throws IOException;

  public int updateAttachments(
      SDMCredentials sdmCredentials,
      CmisDocument cmisDocument,
      Map<String, String> secondaryProperties,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      boolean isSystemUser)
      throws ServiceException;

  public List<String> getObject(
      String objectId, SDMCredentials sdmCredentials, boolean isSystemUser) throws IOException;

  public List<String> getSecondaryTypes(
      String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser) throws IOException;

  public List<String> getValidSecondaryProperties(
      List<String> secondaryTypes,
      SDMCredentials sdmCredentials,
      String repositoryId,
      boolean isSystemUser)
      throws IOException;

  public Map<String, String> copyAttachment(
      CmisDocument cmisDocument,
      SDMCredentials sdmCredentials,
      boolean isSystemUser,
      Set<String> customPropertiesInSDM)
      throws IOException;

  public String moveAttachment(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException;

  public JSONObject editLink(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException;
}
