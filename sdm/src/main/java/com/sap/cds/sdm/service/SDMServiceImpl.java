package com.sap.cds.sdm.service;

import com.google.gson.JsonObject;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.RepoKey;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.caching.SecondaryTypesKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class SDMServiceImpl implements SDMService {
  private final ServiceBinding binding;
  private final CdsProperties.ConnectionPool connectionPool;

  public SDMServiceImpl(ServiceBinding binding, CdsProperties.ConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
    this.binding = binding;
  }

  @Override
  public JSONObject createDocument(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken)
      throws IOException {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    Map<String, String> finalResponse = new HashMap<>();
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    HttpPost uploadFile = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addBinaryBody(
        "filename",
        cmisDocument.getContent(),
        ContentType.create(cmisDocument.getMimeType()),
        cmisDocument.getFileName());
    // Add additional form fields
    builder.addTextBody("cmisaction", "createDocument", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:name", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[1]", "cmis:document", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    HttpEntity multipart = builder.build();
    uploadFile.setEntity(multipart);
    executeHttpPost(httpClient, uploadFile, cmisDocument, finalResponse);
    return new JSONObject(finalResponse);
  }

  private void executeHttpPost(
      HttpClient httpClient,
      HttpPost uploadFile,
      CmisDocument cmisDocument,
      Map<String, String> finalResponse)
      throws ServiceException {
    try (var response = (CloseableHttpResponse) httpClient.execute(uploadFile)) {
      formResponse(cmisDocument, finalResponse, response);
    } catch (IOException e) {
      throw new ServiceException("Error in setting timeout", e.getMessage());
    }
  }

  private void formResponse(
      CmisDocument cmisDocument,
      Map<String, String> finalResponse,
      CloseableHttpResponse response) {
    String status = "success";
    String name = cmisDocument.getFileName();
    String id = cmisDocument.getAttachmentId();
    String objectId = "";
    String error = "";
    try {
      String responseString = EntityUtils.toString(response.getEntity());
      JSONObject jsonResponse = new JSONObject(responseString);
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode == 201 || responseCode == 200) {
        JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
        status = "success";
        objectId = succinctProperties.getString("cmis:objectId");
      } else {
        String message = jsonResponse.getString("message");
        if (responseCode == 409
            && "Malware Service Exception: Virus found in the file!".equals(message)) {
          status = "virus";
        } else if (responseCode == 409) {
          status = "duplicate";
        } else {
          status = "fail";
          error = message;
        }
      }
      // Construct the final response
      finalResponse.put("name", name);
      finalResponse.put("id", id);
      finalResponse.put("status", status);
      finalResponse.put("message", error);
      if (!objectId.isEmpty()) {
        finalResponse.put("objectId", objectId);
      }
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.getGenericError("upload"));
    }
  }

  @Override
  public int updateAttachments(
      String jwtToken,
      SDMCredentials sdmCredentials,
      CmisDocument cmisDocument,
      Map<String, String> secondaryProperties)
      throws ServiceException {

    Map<String, String> updatedMap = new HashMap<>();
    for (Map.Entry<String, String> entry : secondaryProperties.entrySet()) {
      updatedMap.put(entry.getKey().replace("___", ":"), entry.getValue());
    }

    secondaryProperties = updatedMap;

    String repositoryId = SDMConstants.REPOSITORY_ID;
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String objectId = cmisDocument.getObjectId();
    String fileName = cmisDocument.getFileName();

    List<String> secondaryTypes = getSecondaryTypes(repositoryId, jwtToken, sdmCredentials);
    List<String> validSecondaryProperties =
        getValidSecondaryProperties(secondaryTypes, subdomain, sdmCredentials, repositoryId);
    SecondaryTypesKey secondaryTypesKey = new SecondaryTypesKey();
    secondaryTypesKey.setRepositoryId(repositoryId);
    CacheConfig.getSecondaryTypesCache().put(secondaryTypesKey, secondaryTypes);
    SecondaryPropertiesKey secondaryPropertiesKey = new SecondaryPropertiesKey();
    secondaryPropertiesKey.setRepositoryId(repositoryId);
    CacheConfig.getSecondaryPropertiesCache().put(secondaryPropertiesKey, validSecondaryProperties);
    Set<String> keysToRemove =
        secondaryProperties.keySet().stream()
            .filter(key -> !key.equals("filename") && !validSecondaryProperties.contains(key))
            .collect(Collectors.toSet());
    if (!keysToRemove.isEmpty()) {
      String errorMessage = String.join(", ", keysToRemove);
      throw new ServiceException("Unsupported properties " + errorMessage);
    }
    String sdmUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "/root?objectId=" + objectId;

    HttpPost updateRequest = new HttpPost(sdmUrl);

    // Prepare the request body parts
    Map<String, String> updateRequestBody = new HashMap<>();
    updateRequestBody.put("cmisaction", "update");
    updateRequestBody.put("propertyId[0]", "cmis:secondaryObjectTypeIds");

    for (int index = 0; index < secondaryTypes.size(); index++) {
      updateRequestBody.put("propertyValue[0][" + index + "]", secondaryTypes.get(index));
    }

    SDMUtils.prepareSecondaryProperties(updateRequestBody, secondaryProperties, fileName);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    SDMUtils.assembleRequestBodySecondaryTypes(builder, updateRequestBody, objectId);

    // Set the multipart entity to the request
    updateRequest.setEntity(builder.build());

    try (var response = (CloseableHttpResponse) httpClient.execute(updateRequest)) {
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.COULD_NOT_UPDATE_THE_ATTACHMENT, e);
    }
  }

  @Override
  public String getObject(String jwtToken, String objectId, SDMCredentials sdmCredentials)
      throws IOException {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");

    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + SDMConstants.REPOSITORY_ID
            + "/root?cmisselector=object&objectId="
            + objectId
            + "&succinct=true";

    HttpGet getObjectRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getObjectRequest)) {
      if (response.getStatusLine().getStatusCode() != 200) {
        return null;
      }
      String responseString = EntityUtils.toString(response.getEntity());
      JSONObject jsonObject = new JSONObject(responseString);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      return succinctProperties.getString("cmis:name");
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.ATTACHMENT_NOT_FOUND, e);
    }
  }

  @Override
  public void readDocument(
      String objectId,
      String jwtToken,
      SDMCredentials sdmCredentials,
      AttachmentReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");

    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root?objectID="
            + objectId
            + "&cmisselector=content";

    HttpGet getContentRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getContentRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode != 200) {
        response.close();
        if (responseCode == 404) {
          throw new ServiceException(SDMConstants.FILE_NOT_FOUND_ERROR);
        }
        throw new ServiceException("Unexpected code");
      }
      byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
      try (InputStream inputStream = new ByteArrayInputStream(responseBody)) {
        context.getData().setContent(inputStream);
      }
    } catch (Exception e) {
      throw new ServiceException("Failed to set document stream in context");
    }
  }

  @Override
  public String getFolderId(
      Result result, PersistenceService persistenceService, String upID, String token) {

    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream()
            .map(map -> (Map<String, Object>) map)
            .collect(Collectors.toList());

    String folderId = null;
    String repositoryId = null;
    String repoId = SDMConstants.REPOSITORY_ID;
    for (Map<String, Object> attachment : resultList) {
      if (attachment.get("folderId") != null) {
        repositoryId = attachment.get("repositoryId").toString();
        // check if folderId exists for the repositoryId if not then make folderId null else
        // continue
        if (repoId.equalsIgnoreCase(repositoryId)) {
          folderId = attachment.get("folderId").toString();
          break;
        }
      }
    }

    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();

    if (folderId == null) {
      folderId = getFolderIdByPath(upID, SDMConstants.REPOSITORY_ID, sdmCredentials, token);
      if (folderId == null) {
        folderId = createFolder(upID, SDMConstants.REPOSITORY_ID, sdmCredentials, token);
        JSONObject jsonObject = new JSONObject(folderId);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        folderId = succinctProperties.getString("cmis:objectId");
      }
    }
    return folderId;
  }

  @Override
  public String getFolderIdByPath(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, String token) {
    String subdomain = TokenHandler.getSubdomainFromToken(token);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root/"
            + parentId
            + "?cmisselector=object";
    HttpPost getFolderRequest = new HttpPost(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getFolderRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode == 200) {
        return EntityUtils.toString(response.getEntity());
      } else if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      }
      return null;
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.getGenericError("upload"));
    }
  }

  @Override
  public String createFolder(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, String jwtToken) {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    HttpPost createFolderRequest = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", "createFolder", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:name", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", parentId, ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[1]", "cmis:folder", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    HttpEntity multipart = builder.build();
    createFolderRequest.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(createFolderRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      String responseBody = EntityUtils.toString(response.getEntity());
      if (responseCode == 201) return responseBody;
      else if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      } else {
        throw new ServiceException("Failed to create folder. " + responseBody);
      }
    } catch (IOException e) {
      throw new ServiceException("Failed to create folder " + e.getMessage());
    }
  }

  @Override
  public String checkRepositoryType(String jwttoken, String repositoryId) {
    RepoKey repoKey = new RepoKey();
    JsonObject payloadObj = TokenHandler.getTokenFields(jwttoken);
    JsonObject tenantDetails = payloadObj.get("ext_attr").getAsJsonObject();
    String subdomain = tenantDetails.get("zdn").getAsString();
    repoKey.setSubdomain(subdomain);
    repoKey.setRepoId(repositoryId);
    String type = CacheConfig.getVersionedRepoCache().get(repoKey);
    Boolean isVersioned;
    if (type == null) {
      SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
      JSONObject repoInfo = getRepositoryInfo(sdmCredentials, subdomain);
      isVersioned = isRepositoryVersioned(repoInfo, repositoryId);
    } else {
      isVersioned = "Versioned".equals(type);
    }

    if (Boolean.TRUE.equals(isVersioned)) {
      repoKey = new RepoKey();
      repoKey.setSubdomain(subdomain);
      repoKey.setRepoId(repositoryId);
      CacheConfig.getVersionedRepoCache().put(repoKey, "Versioned");
      return "Versioned";
    } else {
      repoKey = new RepoKey();
      repoKey.setSubdomain(subdomain);
      repoKey.setRepoId(repositoryId);
      CacheConfig.getVersionedRepoCache().put(repoKey, "Non Versioned");
      return "Non Versioned";
    }
  }

  public JSONObject getRepositoryInfo(SDMCredentials sdmCredentials, String subdomain) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    var httpClient =
        TokenHandler.getHttpClient(
            binding, connectionPool, subdomain, "TECHNICAL_CREDENTIALS_FLOW");

    String getRepoInfoUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=repositoryInfo";
    HttpGet getRepoInfoRequest = new HttpGet(getRepoInfoUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getRepoInfoRequest)) {
      if (response.getStatusLine().getStatusCode() != 200)
        throw new ServiceException(SDMConstants.REPOSITORY_ERROR);
      String responseString = EntityUtils.toString(response.getEntity());
      return new JSONObject(responseString);
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.REPOSITORY_ERROR);
    }
  }

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId) {
    repoInfo = repoInfo.getJSONObject(repositoryId);
    JSONObject capabilities = repoInfo.getJSONObject("capabilities");
    String type = capabilities.getString("capabilityContentStreamUpdatability");
    if ("pwconly".equals(type)) {
      type = "Versioned";
    } else {
      type = "Non Versioned";
    }

    return "Versioned".equals(type);
  }

  @Override
  public int deleteDocument(String cmisaction, String objectId, String userEmail, String subdomain)
      throws IOException {
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();

    HttpClient httpClient = HttpClients.createDefault();
    String accessToken =
        TokenHandler.getDITokenUsingAuthorities(sdmCredentials, userEmail, subdomain);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + SDMConstants.REPOSITORY_ID + "/root";
    HttpPost deleteDocumentRequest = new HttpPost(sdmUrl);
    deleteDocumentRequest.setHeader("Authorization", "Bearer " + accessToken);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", cmisaction, ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", objectId, ContentType.TEXT_PLAIN);
    HttpEntity multipart = builder.build();
    deleteDocumentRequest.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(deleteDocumentRequest)) {
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.getGenericError("delete"));
    }
  }

  @Override
  public List<String> getSecondaryTypes(
      String repositoryId, String jwtToken, SDMCredentials sdmCredentials) throws ServiceException {
    SecondaryTypesKey secondaryTypesKey = new SecondaryTypesKey();
    secondaryTypesKey.setRepositoryId(repositoryId);
    List<String> secondaryTypes = new ArrayList<>();
    secondaryTypes = CacheConfig.getSecondaryTypesCache().get(secondaryTypesKey);
    if (secondaryTypes == null) {
      String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
      var httpClient =
          TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
      String sdmUrl =
          sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=typeDescendants";
      HttpGet getTypesRequest = new HttpGet(sdmUrl);
      try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
        HttpEntity responseEntity = response.getEntity();
        List<String> result = new ArrayList<>();
        if (responseEntity != null) {
          String responseString = EntityUtils.toString(responseEntity, "UTF-8");
          JSONArray jsonArray = new JSONArray(responseString);
          JSONArray secondaryTypesJSON = new JSONArray();
          for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if (jsonObject.getJSONObject("type").getString("id").equals("cmis:secondary")) {
              secondaryTypesJSON = jsonObject.getJSONArray("children");
              break;
            }
          }
          SDMUtils.extractSecondaryTypeIds(secondaryTypesJSON, result);
        }
        return result;
      } catch (IOException e) {
        throw new ServiceException("Could not update the attachment", e);
      }
    }
    return secondaryTypes;
  }

  @Override
  public List<String> getValidSecondaryProperties(
      List<String> secondaryTypes,
      String subdomain,
      SDMCredentials sdmCredentials,
      String repositoryId) {
    SecondaryPropertiesKey secondaryPropertiesKey = new SecondaryPropertiesKey();
    secondaryPropertiesKey.setRepositoryId(repositoryId);
    List<String> validSecondaryProperties =
        CacheConfig.getSecondaryPropertiesCache().get(secondaryPropertiesKey);
    if (validSecondaryProperties == null) {
      validSecondaryProperties = new ArrayList<>();
      Iterator<String> iterator = secondaryTypes.iterator();
      Boolean isTypeValid = false;
      while (iterator.hasNext()) {
        String value = iterator.next();
        var httpClient =
            TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
        String sdmUrl =
            sdmCredentials.getUrl()
                + "browser/"
                + repositoryId
                + "?cmisselector=typeDefinition&typeID="
                + value;
        HttpGet getTypesRequest = new HttpGet(sdmUrl);
        try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
          HttpEntity responseEntity = response.getEntity();
          if (responseEntity != null) {
            isTypeValid = SDMUtils.checkMCM(responseEntity, validSecondaryProperties);
          }
          if (Boolean.FALSE.equals(isTypeValid)) {
            iterator.remove();
          }
        } catch (IOException e) {
          throw new ServiceException(SDMConstants.UPDATE_ATTACHMENT_ERROR, e);
        }
      }
    }

    return validSecondaryProperties;
  }
}
