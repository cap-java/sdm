package com.sap.cds.sdm.service;

import com.google.gson.JsonObject;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.RepoKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
  public int renameAttachments(
      String jwtToken,
      SDMCredentials sdmCredentials,
      CmisDocument cmisDocument,
      Map<String, String> updatedSecondaryProperties) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    System.out.println("Updated Secondary Properties check : " + updatedSecondaryProperties);
    List<String> secondaryTypes = new ArrayList();
    // secondaryTypes.add("abc:bo");
    // secondaryTypes.add("abcbo");
    secondaryTypes = getSecondaryTypes(repositoryId, jwtToken, sdmCredentials);
    System.out.println("Secondary Types 123check: " + secondaryTypes);
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String objectId = cmisDocument.getObjectId();
    String sdmUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "/root?objectId=" + objectId;
    System.out.println("sdmUrl : " + sdmUrl);
    // String fileName = cmisDocument.getFileName();

    HttpPost updateRequest = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", "update", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:secondaryObjectTypeIds", ContentType.TEXT_PLAIN);
    int index = 0;
    for (String type : secondaryTypes) {
      String propertyValueKey = "propertyValue[0][" + index + "]";
      builder.addTextBody(propertyValueKey, type, ContentType.TEXT_PLAIN);
      index++;
    }
    Iterator<Map.Entry<String, String>> iterator = updatedSecondaryProperties.entrySet().iterator();
    if (!updatedSecondaryProperties.isEmpty()) {
      String firstKey = updatedSecondaryProperties.keySet().iterator().next();
      if ("fileName".equals(firstKey)) {
        builder.addTextBody("propertyId[1]", "cmis:name", ContentType.TEXT_PLAIN);
        builder.addTextBody(
            "propertyValue[1]",
            updatedSecondaryProperties.entrySet().iterator().next().getValue(),
            ContentType.TEXT_PLAIN);

        if (iterator.hasNext()) {
          iterator.next(); // Skip the first entry
        }
        index = 2;
      } else {
        index = 1;
      }
    }

    while (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      String updatedKey = "propertyId[" + index + "]";
      String updatedValue = entry.getKey().replace("___", ":");
      builder.addTextBody(updatedKey, updatedValue, ContentType.TEXT_PLAIN);

      String valueKey = "propertyValue[" + index + "]";
      builder.addTextBody(valueKey, entry.getValue(), ContentType.TEXT_PLAIN);

      index++;
    }
    System.out.println("builder : " + builder);
    HttpEntity multipart = builder.build();
    System.out.println("multipart : " + multipart);
    updateRequest.setEntity(multipart);

    // Print the request details
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      multipart.writeTo(baos);
      String requestContent = baos.toString("UTF-8");
      System.out.println("Request content: " + requestContent);
    } catch (IOException e) {
      e.printStackTrace();
    }
    try (var response = (CloseableHttpResponse) httpClient.execute(updateRequest)) {
      System.out.println("Response Status: " + response.getStatusLine());
      HttpEntity responseEntity = response.getEntity();
      if (responseEntity != null) {
        String responseString = EntityUtils.toString(responseEntity, "UTF-8");
        System.out.println("Response content: " + responseString);
      }
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.COULD_NOT_UPDATE_THE_ATTACHMENT, e);
    }
  }

  @Override
  public List<String> getSecondaryTypes(
      String repositoryId, String jwtToken, SDMCredentials sdmCredentials) {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String sdmUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=typeDescendants";
    HttpGet getTypesRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
      System.out.println("Response Status: " + response.getStatusLine());
      HttpEntity responseEntity = response.getEntity();
      List<String> result = new ArrayList<>();
      if (responseEntity != null) {
        String responseString = EntityUtils.toString(responseEntity, "UTF-8");
        System.out.println("Response content: " + responseString);
        JSONArray jsonArray = new JSONArray(responseString);
        JSONArray secondaryTypesJSON = new JSONArray();
        for (int i = 0; i < jsonArray.length(); i++) {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          if (jsonObject.getJSONObject("type").getString("id").equals("cmis:secondary")) {
            secondaryTypesJSON = jsonObject.getJSONArray("children");
            break;
          }
        }
        extractTypeIds(secondaryTypesJSON, result);
      }

      return result;
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.COULD_NOT_UPDATE_THE_ATTACHMENT, e);
    }
    // SecondaryTypeKey secondaryTypeKey = new SecondaryTypeKey();
    // secondaryTypeKey.setRepoId(repositoryId);
    // List<String> secondaryTypes =
    //     CacheConfig.getSecondaryTypePropertiesCache().get(secondaryTypeKey);
    // if (secondaryTypes == null) {
    //   String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    //   var httpClient =
    //       TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    //   String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    //   HttpPost getTypesRequest = new HttpPost(sdmUrl);
    //   MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    //   // Add additional form fields
    //   builder.addTextBody("cmisselector", "typeDescendants", ContentType.TEXT_PLAIN);
    //   HttpEntity multipart = builder.build();
    //   getTypesRequest.setEntity(multipart);
    //   try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
    //     if (response.getStatusLine().getStatusCode() == 200) {
    //       secondaryTypes.add(response.toString());
    //     }
    //   } catch (IOException e) {
    //     throw new ServiceException(SDMConstants.COULD_NOT_UPDATE_THE_ATTACHMENT, e);
    //   }
    // }

    // return secondaryTypes;
  }

  public static void extractTypeIds(JSONArray jsonArray, List<String> result) {
    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject jsonObject = jsonArray.getJSONObject(i);

      // Extract and store the type ID if it exists
      if (jsonObject.has("type") && jsonObject.getJSONObject("type").has("id")) {
        System.out.println("Found a type : " + jsonObject.getJSONObject("type").getString("id"));
        result.add(jsonObject.getJSONObject("type").getString("id"));
      }

      // If this object has children, recursively process them
      if (jsonObject.has("children")) {
        JSONArray children = jsonObject.getJSONArray("children");
        extractTypeIds(children, result);
      }
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
    for (Map<String, Object> attachment : resultList) {
      if (attachment.get("folderId") != null) {
        folderId = attachment.get("folderId").toString();
        repositoryId = attachment.get("repositoryId").toString();
      }
    }
    String repoId = SDMConstants.REPOSITORY_ID;
    // check if folderId exists for the repositoryId if not then make folderId null else continue
    if (!repoId.equalsIgnoreCase(repositoryId)) {
      folderId = null;
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
}
