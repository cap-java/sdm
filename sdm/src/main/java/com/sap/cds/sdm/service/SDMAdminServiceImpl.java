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
import com.sap.cds.services.ServiceException;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import com.sap.cloud.sdk.cloudplatform.connectivity.OAuth2DestinationBuilder;
import com.sap.cloud.sdk.cloudplatform.connectivity.OnBehalfOf;
import com.sap.cloud.security.config.ClientCredentials;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

  @java.lang.Override
  public String onboardRepository(Repository repository)
      throws JsonProcessingException, UnsupportedEncodingException {
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    var httpClient =
        TokenHandler.getHttpClient(
            null, null, repository.getSubdomain(), "TECHNICAL_CREDENTIALS_FLOW");
    String sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES;
    HttpPost onboardingReq = new HttpPost(sdmUrl);
    ObjectMapper objectMapper = new ObjectMapper();
    RepositoryBody onboardRepository = new RepositoryBody();
    repository.setExternalId(REPOSITORY_ID);
    onboardRepository.setRepository(repository);
    String json = objectMapper.writeValueAsString(onboardRepository);
    StringEntity entity = new StringEntity(json);
    onboardingReq.setEntity(entity);
    // Set the content type of the request
    onboardingReq.setHeader("Content-Type", "application/json");
    try (var response = (CloseableHttpResponse) httpClient.execute(onboardingReq)) {
      String responseString = EntityUtils.toString(response.getEntity());
      System.out.println(
          "ON RES " + responseString + ":" + response.getStatusLine().getStatusCode());
      JsonObject jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
      String repositoryId = jsonObject.get("id").getAsString();
      return String.format(
          SDMConstants.ONBOARD_REPO_MESSAGE, repository.getDisplayName(), repositoryId);
    } catch (IOException e) {
      throw new ServiceException(
          String.format(SDMConstants.ONBOARD_REPO_ERROR_MESSAGE, repository.getDisplayName()),
          e.getMessage());
    }
  }

  @java.lang.Override
  public String offboardRepository(String subdomain) {
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    ClientCredentials clientCredentials =
        new ClientCredentials(sdmCredentials.getClientId(), sdmCredentials.getClientSecret());
    String baseTokenUrl = sdmCredentials.getBaseTokenUrl();
    if (subdomain != null && !subdomain.equals("")) {
      String providersubdomain =
          baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
      baseTokenUrl = baseTokenUrl.replace(providersubdomain, subdomain);
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
    HttpClient httpClient = factory.createHttpClient(destination);
    String sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES + "/";
    HttpGet getRepos = new HttpGet(sdmUrl);
    String repoId = "";
    try (var response = (CloseableHttpResponse) httpClient.execute(getRepos)) {
      repoId = getRepositoryId(EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      logger.error("Error in offboarding repository : " + e.getMessage());
      throw new ServiceException("Error in offboarding ", e.getMessage());
    }
    sdmUrl = sdmCredentials.getUrl() + SDMConstants.REST_V2_REPOSITORIES + "/" + repoId;
    HttpDelete offboardingReq = new HttpDelete(sdmUrl);
    // Set the content type of the request
    offboardingReq.setHeader("Content-Type", "application/json");
    try (var response = (CloseableHttpResponse) httpClient.execute(offboardingReq)) {
      logger.info("Repository <" + REPOSITORY_ID + "> Offboarded");
      return "Repository <" + REPOSITORY_ID + "> Offboarded";
    } catch (IOException e) {
      logger.error("Error in offboarding repository : " + e.getMessage());
      throw new ServiceException("Error in offboarding ", e.getMessage());
    }
  }

  private String getRepositoryId(String jsonString) {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode rootNode = objectMapper.readTree(jsonString);
      JsonNode repoInfos = rootNode.path("repoAndConnectionInfos");

      // Iterate through the array to find the correct externalId and retrieve the id
      for (JsonNode repoInfo : repoInfos) {
        JsonNode repository = repoInfo.path("repository");
        if (repository.path("externalId").asText().equals(SDMConstants.REPOSITORY_ID)) {
          return repository.path("id").asText();
        }
      }
    } catch (Exception e) {
      throw new ServiceException(String.format(e.getMessage()));
    }
    return null;
  }
}
