package com.sap.cds.sdm.service;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class SDMServiceImpl implements SDMService {

  @Override
  public JSONObject createDocument(CmisDocument cmisDocument, String jwtToken) {
    String failedDocument = "";
    String failedId = "";
    String accessToken;
    JSONObject result = new JSONObject();

    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    try {
      accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException();
    }

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    try {
      byte[] fileContent = IOUtils.toByteArray(cmisDocument.getContent());
      RequestBody fileBody =
          RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));

      RequestBody requestBody =
          new MultipartBody.Builder()
              .setType(MultipartBody.FORM)
              .addFormDataPart("cmisaction", "createDocument")
              .addFormDataPart(
                  "objectId", cmisDocument.getFolderId()) // Note: removed quotes from folderId
              .addFormDataPart("propertyId[0]", "cmis:name")
              .addFormDataPart("propertyValue[0]", cmisDocument.getFileName())
              .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
              .addFormDataPart("propertyValue[1]", "cmis:document")
              .addFormDataPart("succinct", "true")
              .addFormDataPart("filename", cmisDocument.getFileName(), fileBody)
              .build();

      Request request =
          new Request.Builder()
              .url(sdmUrl)
              .addHeader("Authorization", "Bearer " + accessToken)
              .post(requestBody)
              .build();

      try (Response response = client.newCall(request).execute()) { // Ensure resources are closed
        if (!response.isSuccessful()) {
          String responseBody = response.body().string();
          JSONObject jsonResponse = new JSONObject(responseBody);
          String message = jsonResponse.getString("message");
          failedDocument = cmisDocument.getFileName();
          failedId = cmisDocument.getAttachmentId();
          if (response.code() == 409) {
            result.put("duplicate", true);
            result.put("virus", false);
            result.put("id", failedId);
            result.put("failedDocument", failedDocument);
          } else if ("Malware Service Exception: Virus found in the file!".equals(message)) {
            result.put("duplicate", false);
            result.put("virus", true);
            result.put("id", failedId);
            result.put("failedDocument", failedDocument);
          } else {
            result.put("fail", true);
            result.put("id", failedId);
            result.put("failedDocument", failedDocument);
          }
        } else {
          String responseBody = response.body().string();
          JSONObject jsonResponse = new JSONObject(responseBody);
          JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
          result.put("url", succinctProperties.getString("cmis:objectId"));
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  @Override
  public void readDocument() {}

  @Override
  public void deleteDocument() {}

  @Override
  public String getFolderId(
      String jwtToken,
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String up__ID)
      throws IOException {
    String folderId = DBQuery.getAttachmentsForUP__ID(attachmentEntity, persistenceService, up__ID);

    if (folderId == null) {
      folderId = getFolderIdByPath(up__ID, jwtToken, SDMConstants.REPOSITORY_ID);
      if (folderId == null) {
        folderId = createFolder(up__ID, jwtToken, SDMConstants.REPOSITORY_ID);
        if (folderId != null) {
          JSONObject jsonObject = new JSONObject(folderId);
          JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
          folderId = succinctProperties.getString("cmis:objectId");
        }
      }
    }
    return folderId;
  }

  @Override
  public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root/"
            + parentId
            + "?cmisselector=object";
    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
      else {
        return response.body().string();
      }
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public String createFolder(String parentId, String jwtToken, String repositoryId)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";

    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("cmisaction", "createFolder")
            .addFormDataPart("propertyId[0]", "cmis:name")
            .addFormDataPart("propertyValue[0]", parentId)
            .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
            .addFormDataPart("propertyValue[1]", "cmis:folder")
            .addFormDataPart("succinct", "true")
            .build();

    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + accessToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Could not upload");
      return response.body().string();
    } catch (IOException e) {
      throw new IOException("Could not upload");
    }
  }

  @Override
  public String checkRepositoryType(String repositoryId) throws IOException {
    String type = CacheConfig.getVersionedRepoCache().get(repositoryId);
    Boolean isVersioned;
    if (type == null) {
      SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
      String token = TokenHandler.getAccessToken(sdmCredentials);
      JSONObject repoInfo = getRepositoryInfo(token, sdmCredentials);
      isVersioned = isRepositoryVersioned(repoInfo, repositoryId);
    } else {
      isVersioned = "Versioned".equals(type);
    }

    if (isVersioned) {
      CacheConfig.getVersionedRepoCache().put(repositoryId, "Versioned");
      return "Versioned";
    } else {
      CacheConfig.getVersionedRepoCache().put(repositoryId, "Non Versioned");
      return "Non Versioned";
    }
  }

  public JSONObject getRepositoryInfo(String token, SDMCredentials sdmCredentials)
      throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    OkHttpClient client = new OkHttpClient();
    String getRepoInfoUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=repositoryInfo";

    Request request =
        new Request.Builder()
            .url(getRepoInfoUrl)
            .addHeader("Authorization", "Bearer " + token)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to get repository info");
      }
      String responseBody = response.body().string();
      return new JSONObject(responseBody);
    } catch (IOException e) {
      throw new IOException("Failed to get repository info");
    }
  }

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId)
      throws IOException {
    repoInfo = repoInfo.getJSONObject(repositoryId);
    JSONObject capabilities = repoInfo.getJSONObject("capabilities");
    String type = capabilities.getString("capabilityContentStreamUpdatability");

    if ("pwconly".equals(type)) {
      type = "Versioned";
    } else {
      type = "Non Versioned";
    }

    // saveRepoToCache(repositoryId, repoInfo);
    return "Versioned".equals(type);
  }
}
