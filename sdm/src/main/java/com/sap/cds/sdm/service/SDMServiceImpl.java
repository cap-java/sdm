package com.sap.cds.sdm.service;

import static com.sap.cds.sdm.constants.SDMConstants.NAMED_USER_FLOW;
import static com.sap.cds.sdm.constants.SDMConstants.TECHNICAL_USER_FLOW;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.caching.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
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
  public JSONObject createDocument(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken) {
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, NAMED_USER_FLOW);
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

  @Override
  public JSONObject editLink(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws ServiceException {

    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
    Map<String, String> finalResponse = new HashMap<>();

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    HttpPost uploadFile = new HttpPost(sdmUrl);

    String urlShortcut = "[InternetShortcut]\nURL=" + cmisDocument.getUrl();
    byte[] fileContent = urlShortcut.getBytes(StandardCharsets.UTF_8);

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    builder.addTextBody("cmisaction", "setContent", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getObjectId(), ContentType.TEXT_PLAIN);
    builder.addTextBody(
        "filename",
        cmisDocument.getFileName() != null ? cmisDocument.getFileName() + ".url" : "link.url",
        ContentType.TEXT_PLAIN);
    builder.addTextBody("charset", "UTF-8", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);

    builder.addBinaryBody(
        "media",
        fileContent,
        ContentType.create("application/internet-shortcut"),
        cmisDocument.getFileName() != null ? cmisDocument.getFileName() + ".url" : "link.url");

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
      throw new ServiceException(SDMConstants.ERROR_IN_SETTING_TIMEOUT_MESSAGE, e.getMessage());
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
      int responseCode = response.getStatusLine().getStatusCode();

      if (responseCode == 201 || responseCode == 200) {
        status = "success";
        JSONObject jsonResponse = new JSONObject(responseString);
        JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
        objectId = succinctProperties.getString("cmis:objectId");
      } else {
        if (responseCode == 409) {
          JSONObject jsonResponse = new JSONObject(responseString);
          String message = jsonResponse.getString("message");
          JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
          objectId = succinctProperties.getString("cmis:objectId");
          if ("Malware Service Exception: Virus found in the file!".equals(message)) {
            status = "virus";
          } else {
            status = "duplicate";
          }
        } else if ((responseCode == 403)
            && (responseString.equals("User does not have required scope"))) {
          status = "unauthorized";
        } else {
          JSONObject jsonResponse = new JSONObject(responseString);
          String message = jsonResponse.getString("message");
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
    } catch (Exception e) {
      throw new ServiceException(e.getMessage());
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
            .filter(
                key ->
                    !key.equals("filename")
                        && !key.equals("description")
                        && !validSecondaryProperties.contains(key))
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

    boolean isFilenameUpdated = secondaryProperties.containsKey("filename");
    boolean isDescriptionUpdated = secondaryProperties.containsKey("description");
    boolean isSecondaryPropertiesUpdated =
        secondaryProperties.size() > ((isFilenameUpdated ? 1 : 0) + (isDescriptionUpdated ? 1 : 0));
    if (isSecondaryPropertiesUpdated) {
      updateRequestBody.put(
          "propertyId[0]",
          "cmis:secondaryObjectTypeIds"); // Creating request body for update properties

      for (int index = 0; index < secondaryTypes.size(); index++) {
        updateRequestBody.put(
            "propertyValue[0][" + index + "]",
            secondaryTypes.get(index)); // Adding Secondary Types to the request body
      }
    }

    SDMUtils.prepareSecondaryProperties(
        updateRequestBody, secondaryProperties, isSecondaryPropertiesUpdated);

    // Only proceed with the update if there are properties to update
    if (updateRequestBody.isEmpty()) {
      return 200; // No updates needed, return success
    }

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
  public List<String> getObject(
      String objectId, SDMCredentials sdmCredentials, boolean isSystemUser) throws IOException {
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
        return Collections.emptyList();
      }
      String responseString = EntityUtils.toString(response.getEntity());
      JSONObject jsonObject = new JSONObject(responseString);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      String cmisName = succinctProperties.optString("cmis:name", "");
      String cmisDescription = succinctProperties.optString("cmis:description", "");
      return List.of(cmisName, cmisDescription);
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
          String errorMessage =
              context
                  .getCdsRuntime()
                  .getLocalizedMessage(
                      "SDM.File.fileNotFoundError", null, context.getParameterInfo().getLocale());
          if (errorMessage.equalsIgnoreCase(SDMConstants.FILE_NOT_FOUND_ERROR_MSG))
            throw new ServiceException(SDMConstants.FILE_NOT_FOUND_ERROR);
          throw new ServiceException(errorMessage);
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
  public String getLinkUrl(String objectId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("Fetching link URL - This is a :{} flow", grantType);
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + SDMConstants.REPOSITORY_ID
            + "/root?objectID="
            + objectId
            + "&cmisselector=content";

    HttpGet getContentRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getContentRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode != 200) {
        logger.warn("Failed to fetch link content for objectId {}: {}", objectId, responseCode);
        return null;
      }

      // Read the content which is in format: [InternetShortcut]\nURL=<actual-url>
      String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

      // Parse the URL from the content
      String[] lines = content.split("\n");
      for (String line : lines) {
        if (line.startsWith("URL=")) {
          String url = line.substring(4).trim();
          logger.info("Extracted link URL for objectId {}: {}", objectId, url);
          return url;
        }
      }

      logger.warn("Could not find URL in link content for objectId {}", objectId);
      return null;
    } catch (IOException e) {
      logger.error("Failed to fetch link URL for objectId {}: {}", objectId, e.getMessage(), e);
      throw new ServiceException("Failed to fetch link URL", e);
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
        // check if folderId exists for the repositoryId if not then make folderId null
        // else
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
        throw new ServiceException(SDMConstants.FAILED_TO_CREATE_FOLDER + ". " + responseBody);
      }
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.FAILED_TO_CREATE_FOLDER + " " + e.getMessage());
    }
  }

  @Override
  public RepoValue checkRepositoryType(String repositoryId, String tenant) {
    RepoKey repoKey = new RepoKey();
    repoKey.setSubdomain(tenant);
    repoKey.setRepoId(repositoryId);
    RepoValue repoValue = CacheConfig.getRepoCache().get(repoKey);
    if (repoValue == null) {
      SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
      JSONObject repoInfo = getRepositoryInfo(sdmCredentials);
      Map<String, RepoValue> repoValueMap = fetchRepositoryData(repoInfo, repositoryId);
      repoKey = new RepoKey();
      repoKey.setSubdomain(tenant);
      repoKey.setRepoId(repositoryId);
      RepoValue value = repoValueMap.get(repositoryId);
      CacheConfig.getRepoCache().put(repoKey, value);
      return repoValueMap.get(repositoryId);
    }
    return repoValue;
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

  public Map<String, RepoValue> fetchRepositoryData(JSONObject repoInfo, String repositoryId) {
    Map<String, RepoValue> repoValueMap = new HashMap<>();
    repoInfo = repoInfo.getJSONObject(repositoryId);
    JSONObject capabilities = repoInfo.getJSONObject("capabilities");
    String type = capabilities.getString("capabilityContentStreamUpdatability");
    RepoValue repoValue = new RepoValue();
    repoValue.setVersionEnabled("pwconly".equals(type) ? true : false);
    JSONArray extendedFeaturesArray = repoInfo.getJSONArray("extendedFeatures");
    // Iterate over the array and find the object with featureData
    for (int i = 0; i < extendedFeaturesArray.length(); i++) {
      JSONObject feature = extendedFeaturesArray.getJSONObject(i);
      if (feature.has("featureData")) {
        JSONObject featureData = feature.getJSONObject("featureData");
        // Fetch the 'virusScanner' value
        repoValue.setVirusScanEnabled(featureData.getBoolean("virusScanner"));
        // Fetch the disableVirusScannerForLargeFile
        repoValue.setDisableVirusScannerForLargeFile(
            featureData.getBoolean("disableVirusScannerForLargeFile"));
      }
    }
    repoValueMap.put(repositoryId, repoValue);
    return repoValueMap;
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
            "%sbrowser/%s?cmisselector=typeDefinition&typeID=%s"
                .formatted(sdmCredentials.getUrl(), repositoryId, value);
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
  public Map<String, String> copyAttachment(
      CmisDocument cmisDocument,
      SDMCredentials sdmCredentials,
      boolean isSystemUser,
      Set<String> customPropertiesInSDM)
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
        return processCopyAttachmentResponse(responseBody, customPropertiesInSDM);
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

  private String getRepositoryId(String jsonString) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode rootNode = objectMapper.readTree(jsonString);
      JsonNode repoInfosNode = rootNode.path("repoAndConnectionInfos");

      List<JsonNode> repoInfos = new ArrayList<>();
      if (repoInfosNode.isArray()) {
        repoInfosNode.forEach(repoInfos::add);
      } else if (!repoInfosNode.isMissingNode() && !repoInfosNode.isNull()) {
        repoInfos.add(repoInfosNode); // wrap single object in a list
      }

      for (JsonNode repoInfo : repoInfos) {
        JsonNode repository = repoInfo.path("repository");
        if (repository.path("externalId").asText().equals(SDMConstants.REPOSITORY_ID)) {
          return repository.path("id").asText();
        }
      }
    } catch (Exception e) {
      throw new ServiceException(SDMConstants.FAILED_TO_PARSE_REPOSITORY_RESPONSE, e);
    }
    return null;
  }

  @Override
  public JSONObject getChangeLog(
      String objectId, SDMCredentials sdmCredentials, boolean isSystemUser) throws IOException {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :" + grantType + " flow");
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
    String sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES + "/";
    HttpGet getRepos = new HttpGet(sdmUrl);
    String repoId = "";
    try (var response = (CloseableHttpResponse) httpClient.execute(getRepos)) {
      int responseCode = response.getStatusLine().getStatusCode();
      String responseString = EntityUtils.toString(response.getEntity());
      if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      } else if (responseCode != 200) {
        logger.info(SDMConstants.REPOSITORY_ERROR + " : " + responseString);
        throw new ServiceException(SDMConstants.REPOSITORY_ERROR + " : " + responseString);
      }
      repoId = getRepositoryId(responseString);
    } catch (IOException e) {
      logger.info(SDMConstants.REPOSITORY_ERROR + " : " + e.getMessage());
      throw new ServiceException(SDMConstants.REPOSITORY_ERROR, e);
    }
    sdmUrl =
        sdmUrl
            + (repoId == null ? SDMConstants.REPOSITORY_ID : repoId)
            + "/objects/"
            + objectId
            + "/changeLogs?includeAll=true";

    HttpGet getChangeLogRequest = new HttpGet(sdmUrl);
    try (var response = (CloseableHttpResponse) httpClient.execute(getChangeLogRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      String responseString = EntityUtils.toString(response.getEntity());
      if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      } else if (responseCode == 404) {
        throw new ServiceException(SDMConstants.FILE_NOT_FOUND_ERROR);
      } else if (responseCode != 200) {
        throw new ServiceException(SDMConstants.FETCH_CHANGELOG_ERROR);
      }
      return new JSONObject(responseString);
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.FETCH_CHANGELOG_ERROR, e);
    }
  }

  private Map<String, String> processCopyAttachmentResponse(
      String responseBody, Set<String> customPropertiesInSDM) {
    Map<String, String> resultMap = new HashMap<>();
    JSONObject jsonObject = new JSONObject(responseBody);
    JSONObject props = jsonObject.optJSONObject("succinctProperties");

    // Extract standard CMIS properties
    resultMap.put("cmis:name", extractProperty(props, jsonObject, "cmis:name"));
    resultMap.put(
        "cmis:contentStreamMimeType",
        extractProperty(props, jsonObject, "cmis:contentStreamMimeType"));
    resultMap.put("cmis:description", extractProperty(props, jsonObject, "cmis:description"));
    resultMap.put("cmis:objectId", extractProperty(props, jsonObject, "cmis:objectId"));

    // Extract custom properties from SDM response
    extractCustomProperties(props, customPropertiesInSDM, resultMap);

    return resultMap;
  }

  private String extractProperty(JSONObject props, JSONObject jsonObject, String propertyName) {
    return props != null ? props.optString(propertyName) : jsonObject.optString(propertyName);
  }

  private void extractCustomProperties(
      JSONObject props, Set<String> customPropertiesInSDM, Map<String, String> resultMap) {
    if (props != null && customPropertiesInSDM != null && !customPropertiesInSDM.isEmpty()) {
      for (String customProperty : customPropertiesInSDM) {
        if (props.has(customProperty)) {
          Object value = props.get(customProperty);
          resultMap.put(customProperty, value != null ? value.toString() : "");
        }
      }
    }
  }

  @Override
  public String moveAttachment(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;

    logger.info("Moving attachment - This is a :{} flow", grantType);

    try {
      // Use RxJava with retry logic for move operation
      return io.reactivex.Flowable.fromCallable(
              () -> {
                var httpClient =
                    tokenHandler.getHttpClient(binding, connectionPool, null, grantType);
                String sdmUrl =
                    sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";
                HttpPost moveFile = new HttpPost(sdmUrl);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();

                // Add form fields for move operation
                builder.addTextBody("cmisaction", "move", ContentType.TEXT_PLAIN);
                builder.addTextBody("objectId", cmisDocument.getObjectId(), ContentType.TEXT_PLAIN);
                builder.addTextBody(
                    "sourceFolderId", cmisDocument.getSourceFolderId(), ContentType.TEXT_PLAIN);
                builder.addTextBody(
                    "targetFolderId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
                builder.addTextBody("succinct", "true");
                HttpEntity multipart = builder.build();
                moveFile.setEntity(multipart);

                try (var response = (CloseableHttpResponse) httpClient.execute(moveFile)) {
                  // Handle response entity
                  HttpEntity entity = response.getEntity();
                  String responseBody =
                      entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

                  if (response.getStatusLine().getStatusCode() == 201
                      || response.getStatusLine().getStatusCode() == 200) {
                    // Return the SDM response JSON - caller can extract needed properties
                    return responseBody;
                  }

                  // On error, throw exception with error information
                  JSONObject errorJson = new JSONObject(responseBody);
                  String exceptionType = errorJson.optString("exception");
                  String errorMessage = errorJson.optString("message");
                  throw new ServiceException(exceptionType + " : " + errorMessage);
                }
              })
          .retryWhen(RetryUtils.retryLogic(5)) // Apply retry logic with 5 attempts
          .blockingFirst();
    } catch (Exception e) {
      logger.error("Failed to move attachment after retries: {}", e.getMessage(), e);
      throw new ServiceException(SDMConstants.FAILED_TO_MOVE_ATTACHMENT, e);
    }
  }
}
