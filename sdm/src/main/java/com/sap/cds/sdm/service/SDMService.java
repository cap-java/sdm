package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;
import org.json.JSONObject;
import com.sap.cds.sdm.model.SDMCredentials;

import java.io.IOException;
import java.util.List;

public interface SDMService {
    public JSONObject createDocument(CmisDocument cmisDocument, String jwtToken, SDMCredentials sdmCredentials) throws IOException;
    public String createFolder(String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials) throws IOException;
    public String getFolderId(String jwtToken, Result result, PersistenceService persistenceService, String up__ID) throws IOException;
    public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials) throws IOException;
    public String checkRepositoryType(String repositoryId)throws IOException;
    public JSONObject getRepositoryInfo(String token, SDMCredentials sdmCredentials) throws IOException ;
    public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId) throws IOException;
//    public void readDocument();
//    public void deleteDocument();
}
