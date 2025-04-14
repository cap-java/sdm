package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class VersioningServiceImpl implements VersioningService {
  private final ServiceBinding binding;
  private final CdsProperties.ConnectionPool connectionPool;

  public VersioningServiceImpl(
      ServiceBinding binding, CdsProperties.ConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
    this.binding = binding;
  }

  @Override
  public String checkInDocument(
      String repositoryId, SDMCredentials sdmCredentials, String jwtToken, String objectId) {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    HttpPost createFolderRequest = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", "checkIn", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", objectId, ContentType.TEXT_PLAIN);
    builder.addTextBody("charset", "UTF-8", ContentType.TEXT_PLAIN);
    builder.addTextBody("major", "true", ContentType.TEXT_PLAIN);
    builder.addTextBody("checkInComment", "hello", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    HttpEntity multipart = builder.build();
    createFolderRequest.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(createFolderRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      String responseBody = EntityUtils.toString(response.getEntity());
      JSONObject checkOutResponse = new JSONObject(responseBody);
      if (responseCode == 201) {

        JSONObject succinctProperties = checkOutResponse.getJSONObject("succinctProperties");
        return succinctProperties.getString("cmis:objectId");
      } else if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      } else {
        throw new ServiceException("Failed to check out document. " + responseBody);
      }
    } catch (IOException e) {
      throw new ServiceException("Failed to check out document " + e.getMessage());
    }
  }

  @Override
  public String checkOutDocument(
      String repositoryId, SDMCredentials sdmCredentials, String jwtToken, String objectId) {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    HttpPost createFolderRequest = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", "checkOut", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", objectId, ContentType.TEXT_PLAIN);
    builder.addTextBody("includeAllowableActions", "true", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    HttpEntity multipart = builder.build();
    createFolderRequest.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(createFolderRequest)) {
      int responseCode = response.getStatusLine().getStatusCode();
      String responseBody = EntityUtils.toString(response.getEntity());
      JSONObject checkOutResponse = new JSONObject(responseBody);
      if (responseCode == 201) {

        JSONObject succinctProperties = checkOutResponse.getJSONObject("succinctProperties");
        return succinctProperties.getString("cmis:objectId");
      } else if (responseCode == 403) {
        throw new ServiceException(SDMConstants.USER_NOT_AUTHORISED_ERROR);
      } else {
        throw new ServiceException("Failed to check out document. " + responseBody);
      }
    } catch (IOException e) {
      throw new ServiceException("Failed to check out document " + e.getMessage());
    }
  }

  @Override
  public JSONObject cancelCheckOut() {
    return null;
  }

  @Override
  public int setContentStream(
      SDMCredentials sdmCredentials, String jwtToken, CmisDocument cmisDocument) {
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");
    Map<String, String> finalResponse = new HashMap<>();
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    HttpPost uploadFile = new HttpPost(sdmUrl);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    // Add additional form fields
    builder.addTextBody("cmisaction", "setContent", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getObjectId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("overwriteFlag", "true", ContentType.TEXT_PLAIN);
    builder.addTextBody("media", "binary", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    builder.addBinaryBody(
        "filename",
        cmisDocument.getContent(),
        ContentType.create(cmisDocument.getMimeType()),
        cmisDocument.getFileName());

    HttpEntity multipart = builder.build();
    uploadFile.setEntity(multipart);
    try (var response = (CloseableHttpResponse) httpClient.execute(uploadFile)) {
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      throw new ServiceException("Error in setting timeout", e.getMessage());
    }
  }
}
