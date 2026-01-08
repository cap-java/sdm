package com.sap.cds.sdm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.Repository;
import com.sap.cds.sdm.model.RepositoryBody;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import com.sap.cloud.sdk.cloudplatform.connectivity.OAuth2DestinationBuilder;
import com.sap.cloud.sdk.cloudplatform.connectivity.OnBehalfOf;
import com.sap.cloud.security.config.ClientCredentials;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMAdminServiceImpl implements SDMAdminService {
  private static final Logger logger = LoggerFactory.getLogger(SDMAdminServiceImpl.class);
  private static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  private final TokenHandler tokenHandler = TokenHandler.getTokenHandlerInstance();

  @java.lang.Override
  public String onboardRepository(Repository repository)
      throws JsonProcessingException, UnsupportedEncodingException {
    if (repository == null) {
      logger.error("Repository object is null. Cannot proceed with onboarding.");
      throw new IllegalArgumentException("Repository object cannot be null.");
    }

    SDMCredentials sdmCredentials;
    try {
      sdmCredentials = tokenHandler.getSDMCredentials();
      if (sdmCredentials == null || sdmCredentials.getUrl() == null) {
        logger.error("SDM credentials are missing or invalid.");
        throw new ServiceException(SDMUtils.getErrorMessage("SDM_CREDENTIALS_MISSING_OR_INVALID"));
      }
    } catch (Exception e) {
      logger.error("Failed to retrieve SDM credentials: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_RETRIEVE_SDM_CREDENTIALS"), e);
    }

    HttpClient httpClient = null;
    try {
      httpClient =
          tokenHandler.getHttpClient(
              null, null, repository.getSubdomain(), "TECHNICAL_CREDENTIALS_FLOW");
      if (httpClient == null) {
        logger.error("Failed to create HTTP client.");
        throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_CREATE_HTTP_CLIENT"));
      }
    } catch (Exception e) {
      logger.error("Error while creating HTTP client: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("ERROR_WHILE_CREATING_HTTP_CLIENT"), e);
    }

    String sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES;
    HttpPost onboardingReq = new HttpPost(sdmUrl);
    ObjectMapper objectMapper = new ObjectMapper();
    RepositoryBody onboardRepository = new RepositoryBody();

    try {
      repository.setExternalId(REPOSITORY_ID);
      onboardRepository.setRepository(repository);
    } catch (Exception e) {
      logger.error("Failed to set repository details: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_SET_REPOSITORY_DETAILS"), e);
    }

    String json;
    try {
      json = objectMapper.writeValueAsString(onboardRepository);
    } catch (JsonProcessingException e) {
      logger.error("Failed to serialize repository object to JSON: " + e.getMessage());
      throw new ServiceException(
          SDMUtils.getErrorMessage("FAILED_TO_SERIALIZE_REPOSITORY_OBJECT_TO_JSON"), e);
    }

    StringEntity entity;
    try {
      entity = new StringEntity(json);
    } catch (UnsupportedEncodingException e) {
      logger.error("Failed to create StringEntity: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_CREATE_STRING_ENTITY"), e);
    }

    onboardingReq.setEntity(entity);
    onboardingReq.setHeader("Content-Type", "application/json");

    try (var response = (CloseableHttpResponse) httpClient.execute(onboardingReq)) {
      String responseString = EntityUtils.toString(response.getEntity());

      if ((responseString.contains(REPOSITORY_ID + " already exists"))
          && response.getStatusLine().getStatusCode() == 409) {
        return String.format(
            SDMUtils.getErrorMessage("REPOSITORY_ALREADY_EXIST"),
            repository.getDisplayName(),
            REPOSITORY_ID);
      }

      JsonObject jsonObject;
      jsonObject = JsonParser.parseString(responseString).getAsJsonObject();

      String repositoryId;
      if (jsonObject.has("id") && !jsonObject.get("id").isJsonNull()) {
        repositoryId = jsonObject.get("id").getAsString();
      } else {
        logger.error(
            String.format(
                    SDMUtils.getErrorMessage("ONBOARD_REPO_ERROR_MESSAGE"),
                    repository.getDisplayName())
                + " : "
                + responseString);
        throw new ServiceException(
            String.format(
                SDMUtils.getErrorMessage("ONBOARD_REPO_ERROR_MESSAGE"),
                repository.getDisplayName()),
            responseString);
      }

      return String.format(
          SDMUtils.getErrorMessage("ONBOARD_REPO_MESSAGE"),
          repository.getDisplayName(),
          repositoryId);
    } catch (Exception e) {
      logger.error(
          String.format(
                  SDMUtils.getErrorMessage("ONBOARD_REPO_ERROR_MESSAGE"),
                  repository.getDisplayName())
              + " : "
              + e.getMessage());
      throw new ServiceException(
          String.format(
              SDMUtils.getErrorMessage("ONBOARD_REPO_ERROR_MESSAGE"), repository.getDisplayName()),
          e);
    }
  }

  @java.lang.Override
  public String offboardRepository(String subdomain) {
    SDMCredentials sdmCredentials;
    try {
      sdmCredentials = tokenHandler.getSDMCredentials();
      if (sdmCredentials == null
          || sdmCredentials.getUrl() == null
          || sdmCredentials.getBaseTokenUrl() == null) {
        logger.error("SDM credentials are missing or invalid.");
        throw new ServiceException(SDMUtils.getErrorMessage("SDM_CREDENTIALS_MISSING_OR_INVALID"));
      }
    } catch (Exception e) {
      logger.error("Failed to retrieve SDM credentials: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_RETRIEVE_SDM_CREDENTIALS"), e);
    }

    ClientCredentials clientCredentials;
    try {
      clientCredentials =
          new ClientCredentials(sdmCredentials.getClientId(), sdmCredentials.getClientSecret());
      if (clientCredentials.getId() == null || clientCredentials.getSecret() == null) {
        logger.error("Client credentials are missing or invalid.");
        throw new ServiceException(
            SDMUtils.getErrorMessage("CLIENT_CREDENTIALS_MISSING_OR_INVALID"));
      }
    } catch (Exception e) {
      logger.error("Failed to create client credentials: " + e.getMessage());
      throw new ServiceException(
          SDMUtils.getErrorMessage("FAILED_TO_CREATE_CLIENT_CREDENTIALS"), e);
    }

    String baseTokenUrl = sdmCredentials.getBaseTokenUrl();
    if (subdomain != null && !subdomain.isEmpty()) {
      try {
        String providersubdomain =
            baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
        baseTokenUrl = baseTokenUrl.replace(providersubdomain, subdomain);
      } catch (Exception e) {
        logger.error("Failed to replace subdomain in base token URL: " + e.getMessage());
        throw new ServiceException(
            SDMUtils.getErrorMessage("FAILED_TO_REPLACE_SUBDOMAIN_IN_BASE_TOKEN_URL"), e);
      }
    }

    var destination =
        OAuth2DestinationBuilder.forTargetUrl(sdmCredentials.getUrl())
            .withTokenEndpoint(baseTokenUrl)
            .withClient(clientCredentials, OnBehalfOf.TECHNICAL_USER_PROVIDER)
            .property(SDMConstants.SDM_DESTINATION_KEY, SDMConstants.SDM_TOKEN_FETCH)
            .build();

    DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder builder =
        DefaultHttpClientFactory.builder();
    builder.timeoutMilliseconds(SDMConstants.TIMEOUT_MILLISECONDS);
    builder.maxConnectionsPerRoute(SDMConstants.MAX_CONNECTIONS_PER_ROUTE);
    builder.maxConnectionsTotal(SDMConstants.MAX_CONNECTIONS_TOTAL);
    DefaultHttpClientFactory factory = builder.build();
    HttpClient httpClient;
    try {
      httpClient = factory.createHttpClient(destination);
      if (httpClient == null) {
        logger.error("Failed to create HTTP client.");
        throw new ServiceException(SDMUtils.getErrorMessage("FAILED_TO_CREATE_HTTP_CLIENT"));
      }
    } catch (Exception e) {
      logger.error("Error while creating HTTP client: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("ERROR_WHILE_CREATING_HTTP_CLIENT"), e);
    }

    String sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES + "/";
    HttpGet getRepos = new HttpGet(sdmUrl);
    String repoId = "";
    try (var response = (CloseableHttpResponse) httpClient.execute(getRepos)) {
      String responseString = EntityUtils.toString(response.getEntity());
      repoId = getRepositoryId(responseString);
      if (repoId == null || repoId.isEmpty()) {
        logger.error("Repository ID not found");
        return "Repository with ID " + SDMConstants.REPOSITORY_ID + " not found.";
      }
    } catch (IOException e) {
      logger.error("Error while fetching repository ID: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("ERROR_WHILE_FETCHING_REPOSITORY_ID"), e);
    } catch (Exception e) {
      logger.error("Unexpected error while fetching repository ID: " + e.getMessage());
      throw new ServiceException(
          SDMUtils.getErrorMessage("UNEXPECTED_ERROR_WHILE_FETCHING_REPOSITORY_ID"), e);
    }

    sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES + "/" + repoId;
    HttpDelete offboardingReq = new HttpDelete(sdmUrl);
    offboardingReq.setHeader("Content-Type", "application/json");
    try (var response = (CloseableHttpResponse) httpClient.execute(offboardingReq)) {
      int statusCode = response.getStatusLine().getStatusCode();
      String responseString = EntityUtils.toString(response.getEntity());

      if (statusCode != 200) { // Failed to offboard
        if (statusCode == 404) { // Exception isn't thrown in case of missing repository
          logger.warn("Repository with ID " + SDMConstants.REPOSITORY_ID + " not found.");
          return "Repository with ID " + SDMConstants.REPOSITORY_ID + " not found.";
        }
        logger.error("Failed to offboard repository : " + responseString);
        throw new ServiceException(
            SDMUtils.getErrorMessage("FAILED_TO_OFFBOARD_REPOSITORY"), responseString);
      }

      logger.info("Repository " + repoId + " Offboarded");
      return "Repository " + repoId + " Offboarded";
    } catch (IOException e) {
      logger.error("Error while offboarding repository: " + e.getMessage());
      throw new ServiceException(SDMUtils.getErrorMessage("ERROR_WHILE_OFFBOARDING_REPOSITORY"), e);
    } catch (Exception e) {
      logger.error("Unexpected error while offboarding repository: " + e.getMessage());
      throw new ServiceException(
          SDMUtils.getErrorMessage("UNEXPECTED_ERROR_WHILE_OFFBOARDING_REPOSITORY"), e);
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
      throw new ServiceException(
          SDMUtils.getErrorMessage("FAILED_TO_PARSE_REPOSITORY_RESPONSE"), e);
    }
    return null;
  }
}
