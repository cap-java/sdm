package com.sap.cds.sdm.service;

import static com.sap.cds.sdm.constants.SDMConstants.NAMED_USER_FLOW;
import static com.sap.cds.sdm.constants.SDMConstants.TECHNICAL_USER_FLOW;

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
import com.sap.cloud.sdk.cloudplatform.connectivity.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMServiceImpl implements SDMService {
  private final ServiceBinding binding;
  private final CdsProperties.ConnectionPool connectionPool;
  private static final Logger logger = LoggerFactory.getLogger(SDMServiceImpl.class);
  private final TokenHandler tokenHandler;

  public SDMServiceImpl(
      ServiceBinding binding,
      CdsProperties.ConnectionPool connectionPool,
      TokenHandler tokenHandler) {
    this.connectionPool = connectionPool;
    this.binding = binding;
    this.tokenHandler = tokenHandler;
  }

  @Override
  public JSONObject createDocument(CmisDocument cmisDocument, SDMCredentials sdmCredentials) {
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, NAMED_USER_FLOW);
    Map<String, String> finalResponse = new HashMap<>();
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    HttpPost uploadFile = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody("cmisaction", "createDocument", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:name", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[1]", "cmis:document", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);

    if (cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")) {
      builder.addTextBody("propertyId[2]", "cmis:secondaryObjectTypeIds", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyValue[2]", "sap:createLink", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyId[3]", "sap:linkRepositoryId", ContentType.TEXT_PLAIN);
      builder.addTextBody(
          "propertyValue[3]", cmisDocument.getRepositoryId(), ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyId[4]", "sap:linkExternalURL", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyValue[4]", cmisDocument.getUrl(), ContentType.TEXT_PLAIN);

    } else {
      builder.addBinaryBody(
          "filename",
          cmisDocument.getContent(),
          ContentType.create(cmisDocument.getMimeType()),
          cmisDocument.getFileName());
    }

    HttpEntity multipart = builder.build();
    uploadFile.setEntity(multipart);
    executeHttpPost(httpClient, uploadFile, cmisDocument, finalResponse);
    return new JSONObject(finalResponse);
  }

  @Override
  public JSONObject editLink(CmisDocument cmisDocument, SDMCredentials sdmCredentials) {
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, NAMED_USER_FLOW);
    Map<String, String> finalResponse = new HashMap<>();
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + cmisDocument.getRepositoryId()
            + "/root?objectId="
            + cmisDocument.getObjectId();

    HttpPost uploadFile = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    builder.addTextBody("cmisaction", "update", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "sap:linkExternalURL", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", cmisDocument.getUrl(), ContentType.TEXT_PLAIN);
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
        } else if (responseCode == 403) {
          status = "unauthorized";
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
      SDMCredentials sdmCredentials,
      CmisDocument cmisDocument,
      Map<String, String> secondaryProperties,
      Map<String, String> secondaryPropertiesWithInvalidDefinitions,
      boolean isSystemUser)
      throws ServiceException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
    String objectId = cmisDocument.getObjectId();
    String fileName = cmisDocument.getFileName();
    List<String> secondaryTypes;
    try {
      secondaryTypes =
          getSecondaryTypes(
              repositoryId,
              sdmCredentials,
              isSystemUser); // Fetching the secondary types from the SDM repository
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      if (errorMessage != null && errorMessage.length() >= 3) {
        return (Integer.parseInt(errorMessage.substring(0, 3)));
      } else {
        return 500;
      }
    }
    List<String> validSecondaryProperties;
    try {
      validSecondaryProperties =
          getValidSecondaryProperties(secondaryTypes, sdmCredentials, repositoryId, isSystemUser);
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      if (errorMessage != null && errorMessage.length() >= 3) {
        return (Integer.parseInt(errorMessage.substring(0, 3)));
      } else {
        return 500;
      }
    }
    SecondaryTypesKey secondaryTypesKey = new SecondaryTypesKey();
    secondaryTypesKey.setRepositoryId(repositoryId);
    CacheConfig.getSecondaryTypesCache()
        .put(
            secondaryTypesKey,
            secondaryTypes); // Setting the secondary types we just fetched in the cache
    SecondaryPropertiesKey secondaryPropertiesKey = new SecondaryPropertiesKey();
    secondaryPropertiesKey.setRepositoryId(repositoryId);
    CacheConfig.getSecondaryPropertiesCache()
        .put(
            secondaryPropertiesKey,
            validSecondaryProperties); // Setting the valid secondary properties we just fetched in
    // the cache. This will stay in cache until the current list
    // of attachments in draft table are saved. Then it will be
    // removed as the properties can be updated from the backend
    // by the time new attachments are added to the draft

    Set<String> keysToRemove =
        secondaryProperties.keySet().stream()
            .filter(key -> !key.equals("filename") && !validSecondaryProperties.contains(key))
            .collect(
                Collectors
                    .toSet()); // Adding the properties which are unsupported to a list so that
    // exeception can be thrown
    Set<String> keysMap1 = secondaryProperties.keySet();
    for (Map.Entry<String, String> entry :
        secondaryPropertiesWithInvalidDefinitions
            .entrySet()) { // Adding the properties which are defined incorrectly to a list so that
      // exeception can be thrown
      if (keysMap1.contains(entry.getValue())) {
        keysToRemove.add(entry.getValue());
      }
    }
    if (!keysToRemove.isEmpty()) {
      String errorMessage = String.join(", ", keysToRemove);
      throw new ServiceException(
          SDMConstants.UNSUPPORTED_PROPERTIES
              + " "
              + errorMessage); // Some invalid/unsupported properties were present and were updated.
      // So processing is stopped (Request is not sent to SDM) and
      // exception is thrown
    }

    String sdmUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "/root?objectId=" + objectId;
    HttpPost updateRequest = new HttpPost(sdmUrl);

    // Prepare the request body parts
    Map<String, String> updateRequestBody = new HashMap<>();
    updateRequestBody.put("cmisaction", "update");
    updateRequestBody.put(
        "propertyId[0]",
        "cmis:secondaryObjectTypeIds"); // Creating request body for update properties

    for (int index = 0; index < secondaryTypes.size(); index++) {
      updateRequestBody.put(
          "propertyValue[0][" + index + "]",
          secondaryTypes.get(index)); // Adding Secondary Types to the request body
    }

    SDMUtils.prepareSecondaryProperties(updateRequestBody, secondaryProperties, fileName);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    SDMUtils.assembleRequestBodySecondaryTypes(
        builder, updateRequestBody, objectId); // Adding Secondary Properties to the request body

    // Set the multipart entity to the request
    updateRequest.setEntity(builder.build());

    try (var response = (CloseableHttpResponse) httpClient.execute(updateRequest)) {
      if (response.getStatusLine().getStatusCode() == 400) {
        String responseString = EntityUtils.toString(response.getEntity());
        JSONObject jsonResponse = new JSONObject(responseString);
        String message = jsonResponse.getString("message");
        throw new ServiceException(message);
      }
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.COULD_NOT_UPDATE_THE_ATTACHMENT, e);
    }
  }

  @Override
  public String getObject(String objectId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

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
      String objectId, SDMCredentials sdmCredentials, AttachmentReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    String grantType = context.getUserInfo().isSystemUser() ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

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
      Result result,
      PersistenceService persistenceService,
      String folderName,
      boolean isSystemUser) {

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

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();

    if (folderId == null) {
      folderId =
          getFolderIdByPath(folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
      if (folderId == null) {
        folderId =
            createFolder(folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
        JSONObject jsonObject = new JSONObject(folderId);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        folderId = succinctProperties.getString("cmis:objectId");
      }
    }
    return folderId;
  }

  @Override
  public String getFolderIdByPath(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser) {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    String folderId = null;
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root/"
            + parentId
            + "?cmisselector=object";
    HttpGet getFolderRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getFolderRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode == 200) {
        JSONObject jsonObject = new JSONObject(EntityUtils.toString(response.getEntity()));
        folderId =
            jsonObject
                .getJSONObject("properties")
                .getJSONObject("cmis:objectId")
                .getString("value");
      } else if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      }
      return folderId;
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.getGenericError("upload"));
    }
  }

  @Override
  public String createFolder(
      String parentId, String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser) {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
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
  public String checkRepositoryType(String repositoryId, String tenant) {
    RepoKey repoKey = new RepoKey();
    repoKey.setSubdomain(tenant);
    repoKey.setRepoId(repositoryId);
    String type = CacheConfig.getVersionedRepoCache().get(repoKey);
    Boolean isVersioned;
    if (type == null) {
      SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
      JSONObject repoInfo = getRepositoryInfo(sdmCredentials);
      isVersioned = isRepositoryVersioned(repoInfo, repositoryId);
    } else {
      isVersioned = "Versioned".equals(type);
    }

    if (Boolean.TRUE.equals(isVersioned)) {
      repoKey = new RepoKey();
      repoKey.setSubdomain(tenant);
      repoKey.setRepoId(repositoryId);
      CacheConfig.getVersionedRepoCache().put(repoKey, "Versioned");
      return "Versioned";
    } else {
      repoKey = new RepoKey();
      repoKey.setSubdomain(tenant);
      repoKey.setRepoId(repositoryId);
      CacheConfig.getVersionedRepoCache().put(repoKey, "Non Versioned");
      return "Non Versioned";
    }
  }

  public JSONObject getRepositoryInfo(SDMCredentials sdmCredentials) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, TECHNICAL_USER_FLOW);

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
  public int deleteDocument(String cmisaction, String objectId, String user) {
    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    HttpClient httpClient;
    if (user.equals(SDMConstants.SYSTEM_USER)) {
      httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, TECHNICAL_USER_FLOW);
    } else {
      httpClient = tokenHandler.getHttpClientForAuthoritiesFlow(connectionPool, user);
    }

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + SDMConstants.REPOSITORY_ID + "/root";
    HttpPost deleteDocumentRequest = new HttpPost(sdmUrl);
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
      String repositoryId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws ServiceException {
    SecondaryTypesKey secondaryTypesKey = new SecondaryTypesKey();
    secondaryTypesKey.setRepositoryId(repositoryId);
    List<String> secondaryTypes = new ArrayList<>();
    secondaryTypes = CacheConfig.getSecondaryTypesCache().get(secondaryTypesKey);
    if (secondaryTypes == null) {
      String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
      logger.info("This is a :" + grantType + " flow");
      var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
      String sdmUrl =
          sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=typeDescendants";
      HttpGet getTypesRequest = new HttpGet(sdmUrl);
      try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
          String reasonPhrase = response.getStatusLine().getReasonPhrase();
          throw new ServiceException(statusCode + " : " + reasonPhrase);
        }
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
      SDMCredentials sdmCredentials,
      String repositoryId,
      boolean isSystemUser) {
    SecondaryPropertiesKey secondaryPropertiesKey = new SecondaryPropertiesKey();
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    secondaryPropertiesKey.setRepositoryId(repositoryId);
    List<String> validSecondaryProperties =
        CacheConfig.getSecondaryPropertiesCache().get(secondaryPropertiesKey);
    if (validSecondaryProperties == null) {
      validSecondaryProperties = new ArrayList<>();
      Iterator<String> iterator = secondaryTypes.iterator();
      var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
      while (iterator.hasNext()) {
        String value = iterator.next();
        String sdmUrl =
            String.format(
                "%sbrowser/%s?cmisselector=typeDefinition&typeID=%s",
                sdmCredentials.getUrl(), repositoryId, value);
        HttpGet getTypesRequest = new HttpGet(sdmUrl);
        try (var response = (CloseableHttpResponse) httpClient.execute(getTypesRequest)) {
          int statusCode = response.getStatusLine().getStatusCode();
          if (statusCode != 200) {
            String reasonPhrase = response.getStatusLine().getReasonPhrase();
            throw new ServiceException(statusCode + " : " + reasonPhrase);
          }
          HttpEntity responseEntity = response.getEntity();
          if (responseEntity != null
              && Boolean.FALSE.equals(
                  SDMUtils.checkMCM(responseEntity, validSecondaryProperties))) {
            iterator.remove();
          }
        } catch (IOException e) {
          throw new ServiceException(SDMConstants.UPDATE_ATTACHMENT_ERROR, e);
        }
      }
    }

    return validSecondaryProperties;
  }

  @Override
  public List<String> copyAttachment(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;

    logger.info("This is a :{} flow", grantType);
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";
    HttpPost uploadFile = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    // Add additional form fields
    builder.addTextBody("cmisaction", "createDocumentFromSource", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("sourceId", cmisDocument.getObjectId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true");
    HttpEntity multipart = builder.build();
    uploadFile.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(uploadFile)) {

      // Handle response entity
      HttpEntity entity = response.getEntity();
      String responseBody =
          entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

      if (response.getStatusLine().getStatusCode() == 201) {
        // Process successful response

        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject props = jsonObject.getJSONObject("succinctProperties");
        String fileName = props.optString("cmis:contentStreamFileName");
        String mimeType = props.optString("cmis:contentStreamMimeType");
        String objectId = props.optString("cmis:objectId");
        return List.of(fileName, mimeType, objectId);
      }

      // On error, throw exception with error information
      JSONObject errorJson = new JSONObject(responseBody);
      String exceptionType = errorJson.optString("exception");
      String errorMessage = errorJson.optString("message");
      throw new ServiceException(exceptionType + " : " + errorMessage);
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.FAILED_TO_COPY_ATTACHMENT, e);
    }
  }
}
