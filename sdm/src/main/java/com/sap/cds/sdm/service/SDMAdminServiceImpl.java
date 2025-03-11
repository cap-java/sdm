package com.sap.cds.sdm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.Repository;
import com.sap.cds.sdm.model.RepositoryBody;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

public class SDMAdminServiceImpl implements SDMAdminService {
  @java.lang.Override
  public String onboardRepository(Repository repository)
      throws JsonProcessingException, UnsupportedEncodingException {
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    var httpClient =
        TokenHandler.getHttpClient(
            null, null, repository.getSubdomain(), "TECHNICAL_CREDENTIALS_FLOW");
    String sdmUrl = sdmCredentials.getUrl() + "rest/v2/repositories";
    HttpPost onboardingReq = new HttpPost(sdmUrl);
    ObjectMapper objectMapper = new ObjectMapper();
    RepositoryBody onboardRepository = new RepositoryBody();
    onboardRepository.setRepository(repository);
    String json = objectMapper.writeValueAsString(onboardRepository);
    StringEntity entity = new StringEntity(json);
    onboardingReq.setEntity(entity);
    // Set the content type of the request
    onboardingReq.setHeader("Content-Type", "application/json");
    try (var response = (CloseableHttpResponse) httpClient.execute(onboardingReq)) {
      String responseString = EntityUtils.toString(response.getEntity());
      JsonObject jsonObject = JsonParser.parseString(responseString).getAsJsonObject();
      String repositoryId = jsonObject.get("id").getAsString();
      return String.format(
          SDMConstants.ONBOARD_REPO_MESSAGE, repository.getDisplayName(), repositoryId);
    } catch (IOException e) {
      throw new ServiceException(
          String.format(SDMConstants.ONBOARD_REPO__ERROR_MESSAGE, repository.getDisplayName()),
          e.getMessage());
    }
  }

  @java.lang.Override
  public String offboardRepository(String subdomain) {

    // This is yet to be implemented
    return null;
  }

  @java.lang.Override
  public String restoreRepository(String subdomain) {

    // This is yet to be implemented
    return null;
  }
}
