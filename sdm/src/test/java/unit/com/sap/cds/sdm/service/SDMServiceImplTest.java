package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.RepoKey;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.*;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class SDMServiceImplTest {
  private static final String REPO_ID = "repo";
  private SDMService SDMService;
  JsonObject expected;
  RepoKey repoKey;
  @Mock ServiceBinding binding;
  @Mock CdsProperties.ConnectionPool connectionPool;
  String subdomain = "SUBDOMAIN";

  private CloseableHttpClient httpClient;
  private CloseableHttpResponse response;

  StatusLine statusLine;
  HttpEntity entity;

  @BeforeEach
  public void setUp() {
    httpClient = mock(CloseableHttpClient.class);
    response = mock(CloseableHttpResponse.class);
    statusLine = mock(StatusLine.class);
    entity = mock(HttpEntity.class);
    SDMService = new SDMServiceImpl(binding, connectionPool);
    repoKey = new RepoKey();
    expected = new JsonObject();
    expected.addProperty(
        "email", "john.doe@example.com"); // Correct the property name as expected in the method
    expected.addProperty(
        "exp", "1234567890"); // Correct the property name as expected in the method
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    expected.add("ext_attr", jsonObject);
    repoKey.setRepoId("repo");
    repoKey.setSubdomain("tenant");
  }

  @Test
  public void testIsRepositoryVersioned_Versioned() throws IOException {
    // Mocked JSON structure for a versioned repository
    JSONObject capabilities = new JSONObject();
    capabilities.put("capabilityContentStreamUpdatability", "pwconly");

    JSONObject repoInfo = new JSONObject();
    repoInfo.put("capabilities", capabilities);

    JSONObject root = new JSONObject();
    root.put(REPO_ID, repoInfo);

    // Call the method and verify the result
    boolean isVersioned = SDMService.isRepositoryVersioned(root, REPO_ID);
    assertTrue(isVersioned);
  }

  @Test
  public void testIsRepositoryVersioned_NonVersioned() throws IOException {
    // Mocked JSON structure for a non-versioned repository
    JSONObject capabilities = new JSONObject();
    capabilities.put("capabilityContentStreamUpdatability", "other");

    JSONObject repoInfo = new JSONObject();
    repoInfo.put("capabilities", capabilities);

    JSONObject root = new JSONObject();
    root.put(REPO_ID, repoInfo);

    // Call the method and verify the result
    boolean isVersioned = SDMService.isRepositoryVersioned(root, REPO_ID);
    assertFalse(isVersioned);
  }

  @Test
  public void testGetRepositoryInfo() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class); ) {
      JSONObject capabilities = new JSONObject();
      capabilities.put("capabilityContentStreamUpdatability", "other");
      JSONObject repoInfo = new JSONObject();
      repoInfo.put("capabilities", capabilities);
      JSONObject root = new JSONObject();
      root.put(REPO_ID, repoInfo);
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
          .thenReturn(httpClient);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when((response.getEntity())).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(root.toString().getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("test");
      com.sap.cds.sdm.service.SDMService sdmService = new SDMServiceImpl(binding, connectionPool);
      JSONObject json = sdmService.getRepositoryInfo(sdmCredentials, subdomain);

      JSONObject fetchedRepoInfo = json.getJSONObject(REPO_ID);
      JSONObject fetchedCapabilities = fetchedRepoInfo.getJSONObject("capabilities");
      assertEquals("other", fetchedCapabilities.getString("capabilityContentStreamUpdatability"));
    }
  }

  @Test
  public void testGetRepositoryInfoFail() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class); ) {
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("test");
      String token = "token";
      com.sap.cds.sdm.service.SDMService sdmService = new SDMServiceImpl(binding, connectionPool);
      SDMCredentials mockSdmCredentials = new SDMCredentials();
      mockSdmCredentials.setUrl("test");
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
          .thenReturn(httpClient);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmService.getRepositoryInfo(sdmCredentials, subdomain);
              });
      assertEquals("Failed to get repository info.", exception.getMessage());
    }
  }

  @Test
  public void testGetRepositoryInfoThrowsServiceExceptionOnHttpClientError() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<HttpClients> httpClientsMockedStatic = mockStatic(HttpClients.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);

      // Mock TokenHandler methods
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), any()))
          .thenReturn(mockHttpClient);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockSdmCredentials.getUrl()).thenReturn("http://example.com/");

      // Simulate IOException during HTTP call
      when(mockHttpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Network error"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Assert that ServiceException is thrown
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> sdmServiceImpl.getRepositoryInfo(mockSdmCredentials, "test-subdomain"));

      assertEquals(SDMConstants.REPOSITORY_ERROR, exception.getMessage());
    }
  }

  @Test
  public void testCheckRepositoryTypeCacheVersioned() throws IOException {
    String repositoryId = "repo";
    String token = "token";
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class); ) {
      Cache<RepoKey, String> mockCache = Mockito.mock(Cache.class);
      tokenHandlerMockedStatic.when(() -> TokenHandler.getTokenFields(token)).thenReturn(expected);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      Mockito.when(mockCache.get(repoKey)).thenReturn("Versioned");
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      String result = SDMService.checkRepositoryType(token, repositoryId);
      assertEquals("Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeCacheNonVersioned() throws IOException {
    String repositoryId = "repo";
    String token = "token";
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class); ) {
      Cache<RepoKey, String> mockCache = Mockito.mock(Cache.class);
      SDMCredentials mockSdmCredentials = new SDMCredentials();
      mockSdmCredentials.setUrl("test");
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
          .thenReturn(httpClient);
      tokenHandlerMockedStatic.when(() -> TokenHandler.getTokenFields(token)).thenReturn(expected);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSDMCredentials())
          .thenReturn(mockSdmCredentials);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when((response.getEntity())).thenReturn(entity);
      Mockito.when(mockCache.get(repoKey)).thenReturn("Non Versioned");
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      String result = SDMService.checkRepositoryType(token, repositoryId);
      assertEquals("Non Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeNoCacheVersioned() throws IOException {
    String repositoryId = "repo";
    String token = "token";
    SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl(binding, connectionPool));
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
      Cache<RepoKey, String> mockCache = Mockito.mock(Cache.class);
      tokenHandlerMockedStatic.when(() -> TokenHandler.getTokenFields(token)).thenReturn(expected);
      Mockito.when(mockCache.get(repoKey)).thenReturn(null);
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      SDMCredentials mockSdmCredentials = new SDMCredentials();
      mockSdmCredentials.setUrl("test");
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
          .thenReturn(httpClient);
      HttpGet getRepoInfoRequest =
          new HttpGet(
              mockSdmCredentials.getUrl()
                  + "browser/"
                  + repositoryId
                  + "?cmisselector=repositoryInfo");
      tokenHandlerMockedStatic.when(() -> TokenHandler.getTokenFields(token)).thenReturn(expected);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSDMCredentials())
          .thenReturn(mockSdmCredentials);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when((response.getEntity())).thenReturn(entity);
      JSONObject capabilities = new JSONObject();
      capabilities.put(
          "capabilityContentStreamUpdatability",
          "pwconly"); // To match the expected output "Versioned"
      JSONObject repoInfo = new JSONObject();
      repoInfo.put("capabilities", capabilities);
      JSONObject mockRepoData = new JSONObject();
      mockRepoData.put(repositoryId, repoInfo);
      InputStream inputStream = new ByteArrayInputStream(mockRepoData.toString().getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      String result = spySDMService.checkRepositoryType(token, repositoryId);
      assertEquals("Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeNoCacheNonVersioned() throws IOException {
    String repositoryId = "repo";
    String token = "token";
    SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl(binding, connectionPool));
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {

      Cache<RepoKey, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(repoKey)).thenReturn(null);
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      SDMCredentials mockSdmCredentials = new SDMCredentials();
      mockSdmCredentials.setUrl("test");
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
          .thenReturn(httpClient);
      HttpGet getRepoInfoRequest =
          new HttpGet(
              mockSdmCredentials.getUrl()
                  + "browser/"
                  + repositoryId
                  + "?cmisselector=repositoryInfo");
      tokenHandlerMockedStatic.when(() -> TokenHandler.getTokenFields(token)).thenReturn(expected);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSDMCredentials())
          .thenReturn(mockSdmCredentials);
      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);

      JSONObject capabilities = new JSONObject();
      capabilities.put(
          "capabilityContentStreamUpdatability",
          "notpwconly"); // To match the expected output "Versioned"
      JSONObject repoInfo = new JSONObject();
      repoInfo.put("capabilities", capabilities);
      JSONObject mockRepoData = new JSONObject();
      mockRepoData.put(repositoryId, repoInfo);

      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockRepoData.toString().getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      String result = spySDMService.checkRepositoryType(token, repositoryId);
      assertEquals("Non Versioned", result);
    }
  }

  @Test
  public void testCreateFolder() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse = "Folder ID";

      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(201);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(expectedResponse.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      String actualResponse =
          sdmServiceImpl.createFolder(parentId, repositoryId, sdmCredentials, jwtToken);

      assertEquals(expectedResponse, actualResponse);
    }
  }

  @Test
  public void testCreateFolderFail() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream(
              "Failed to create folder. Could not upload  the document".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.createFolder(parentId, repositoryId, sdmCredentials, jwtToken);
              });
      assertEquals(
          "Failed to create folder. Failed to create folder. Could not upload  the document",
          exception.getMessage());
    }
  }

  @Test
  public void testCreateFolderThrowsServiceExceptionOnHttpClientError() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<HttpClients> httpClientsMockedStatic = mockStatic(HttpClients.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);

      // Mock TokenHandler methods
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), any()))
          .thenReturn(mockHttpClient);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSubdomainFromToken(any()))
          .thenReturn("test-subdomain");

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockSdmCredentials.getUrl()).thenReturn("http://example.com/");

      // Simulate IOException during HTTP call
      when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new IOException("Network error"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Assert that ServiceException is thrown
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () ->
                  sdmServiceImpl.createFolder(
                      "parentId", "repositoryId", mockSdmCredentials, "jwtToken"));

      assertTrue(exception.getMessage().contains("Failed to create folder Network error"));
    }
  }

  @Test
  public void testCreateFolderFailResponseCode403() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(403) // Set HTTP status code to 403
              .setBody("{\"error\":" + SDMConstants.USER_NOT_AUTHORISED_ERROR + "\"}")
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(403);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream(SDMConstants.USER_NOT_AUTHORISED_ERROR.getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.createFolder(parentId, repositoryId, sdmCredentials, jwtToken);
              });
      assertEquals(SDMConstants.USER_NOT_AUTHORISED_ERROR, exception.getMessage());

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testGetFolderIdByPath() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse =
          "{"
              + "\"properties\": {"
              + "\"cmis:objectId\": {"
              + "\"id\": \"cmis:objectId\","
              + "\"localName\": \"cmis:objectId\","
              + "\"displayName\": \"cmis:objectId\","
              + "\"queryName\": \"cmis:objectId\","
              + "\"type\": \"id\","
              + "\"cardinality\": \"single\","
              + "\"value\": \"ExpectedFolderId\""
              + "}}"
              + "}";

      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when(response.getEntity()).thenReturn(entity);

      InputStream inputStream = new ByteArrayInputStream(expectedResponse.getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      // when(EntityUtils.toString(entity)).thenReturn(expectedResponse);
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      String actualResponse =
          sdmServiceImpl.getFolderIdByPath(parentId, repositoryId, sdmCredentials, jwtToken);

      assertEquals("ExpectedFolderId", actualResponse);
    }
  }

  @Test
  public void testGetFolderIdByPathFail() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream("Internal Server".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      String folderId =
          sdmServiceImpl.getFolderIdByPath(parentId, repositoryId, sdmCredentials, jwtToken);
      assertNull(folderId, "Expected folderId to be null");
    }
  }

  @Test
  public void testGetFolderIdByPathThrowsServiceExceptionOnHttpClientError() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<HttpClients> httpClientsMockedStatic = mockStatic(HttpClients.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);

      // Mock TokenHandler methods
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), any()))
          .thenReturn(mockHttpClient);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSubdomainFromToken(any()))
          .thenReturn("test-subdomain");

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockSdmCredentials.getUrl()).thenReturn("http://example.com/");

      // Simulate IOException during HTTP call
      when(mockHttpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Network error"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Assert that ServiceException is thrown
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () ->
                  sdmServiceImpl.getFolderIdByPath(
                      "parentId", "repositoryId", mockSdmCredentials, "jwtToken"));

      assertTrue(exception.getMessage().contains(SDMConstants.getGenericError("upload")));
    }
  }

  @Test
  public void testGetFolderIdByPathFailResponseCode403() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(403) // Set HTTP status code to 403 for an internal server error
              .setBody("{\"error\":" + SDMConstants.USER_NOT_AUTHORISED_ERROR + "\"}")
              // the body
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(403);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream(
              "Failed to create folder. Could not upload  the document".getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.getFolderIdByPath(parentId, repositoryId, sdmCredentials, jwtToken);
              });
      assertEquals(SDMConstants.USER_NOT_AUTHORISED_ERROR, exception.getMessage());

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateDocument() throws IOException {

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"succinctProperties\": {\"cmis:objectId\": \"objectId\"}}";

      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");

      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(201);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("objectId", "objectId");
      expectedResponse.put("message", "");
      expectedResponse.put("status", "success");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    }
  }

  @Test
  public void testCreateDocumentFailDuplicate() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"message\": \"Duplicate document found\"}";

      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");

      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(409);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("message", "");
      expectedResponse.put("status", "duplicate");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    }
  }

  @Test
  public void testCreateDocumentFailVirus() throws IOException {

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody =
          "{\"message\": \"Malware Service Exception: Virus found in the file!\"}";

      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");

      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(409);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("message", "");
      expectedResponse.put("status", "virus");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    }
  }

  @Test
  public void testCreateDocumentFailOther() throws IOException {

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"message\": \"An unexpected error occurred\"}";
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");

      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("message", "An unexpected error occurred");
      expectedResponse.put("status", "fail");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    }
  }

  @Test
  public void testCreateDocumentFailRequestError() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");
      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream("{\"message\":\"Error in setting timeout\"}".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      try {
        sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken);
      } catch (ServiceException e) {
        // Expected exception to be thrown
        assertEquals("Error in setting timeout", e.getMessage());
      }
    }
  }

  @Test
  public void testDeleteFolder() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse = "200";
      mockWebServer.enqueue(
          new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json"));
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials, "email", "subdomain"))
          .thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      int actualResponse =
          sdmServiceImpl.deleteDocument("deleteTree", "objectId", "email", "subdomain");

      assertEquals(200, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testGetFolderId_FolderIdPresentInResult() throws IOException {
    PersistenceService persistenceService = mock(PersistenceService.class);
    Result result = mock(Result.class);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("folderId", "newFolderId123");
    attachment.put("repositoryId", "repoId");
    List<Map> resultList = Arrays.asList((Map) attachment);

    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);
      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);

      // Mock the method `getFolderIdByPath`
      SDMServiceImpl spyService = spy(sdmServiceImpl);
      doReturn(null)
          .when(spyService)
          .getFolderIdByPath(anyString(), anyString(), any(SDMCredentials.class), anyString());

      // Mock the method `createFolder`
      doReturn("{\"succinctProperties\":{\"cmis:objectId\":\"newFolderId123\"}}")
          .when(spyService)
          .createFolder(anyString(), anyString(), any(SDMCredentials.class), anyString());

      String folderId = spyService.getFolderId(result, persistenceService, up__ID, jwtToken);
      assertEquals("newFolderId123", folderId, "Expected folderId from result list");
    }
  }

  @Test
  public void testDeleteDocument() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse = "200";
      mockWebServer.enqueue(
          new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json"));
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials, "email", "subdomain"))
          .thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      int actualResponse =
          sdmServiceImpl.deleteDocument("delete", "objectId", "email", "subdomain");

      assertEquals(200, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testDeleteDocumentObjectNotFound() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"message\": \"Object Not Found\"}";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(mockResponseBody)
              .setResponseCode(404) // Assuming 400 Bad Request or a similar client error code
              .addHeader("Content-Type", "application/json"));
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials, "email", "subdomain"))
          .thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      int actualResponse = sdmServiceImpl.deleteDocument("delete", "ewdwe", "email", "subdomain");

      assertEquals(404, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testGetDITokenUsingAuthoritiesThrowsIOException() {
    String cmisaction = "someAction";
    String objectId = "someObjectId";
    String userEmail = "user@example.com";
    String subdomain = "testSubdomain";

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("https://example.com/");

    // Mocking static methods
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDITokenUsingAuthorities(sdmCredentials, userEmail, subdomain))
          .thenThrow(new IOException("Could not delete the document."));

      // Since the exception is thrown before OkHttpClient is used, no need to mock httpClient
      // behavior.
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);
      // Assert exception
      IOException thrown =
          assertThrows(
              IOException.class,
              () -> {
                // Call the method under test
                sdmServiceImpl.deleteDocument(cmisaction, objectId, userEmail, subdomain);
              });

      // Verify the exception message
      assertEquals("Could not delete the document.", thrown.getMessage());
    }
  }

  @Test
  public void testGetFolderId_GetFolderIdByPathReturns() throws IOException {
    Result result = mock(Result.class);
    PersistenceService persistenceService = mock(PersistenceService.class);

    List<Map> resultList = new ArrayList<>();
    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    SDMServiceImpl sdmServiceImpl = spy(new SDMServiceImpl(binding, connectionPool));

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      doReturn("folderByPath123")
          .when(sdmServiceImpl)
          .getFolderIdByPath(anyString(), anyString(), any(SDMCredentials.class), anyString());

      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("mockUrl");
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();
      String mockUrl = mockWebServer.url("/").toString();
      sdmCredentials.setUrl(mockUrl);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);
      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);

      MockResponse mockResponse1 =
          new MockResponse().setResponseCode(200).setBody("folderByPath123");
      mockWebServer.enqueue(mockResponse1);
      String folderId = sdmServiceImpl.getFolderId(result, persistenceService, up__ID, jwtToken);
      assertEquals("folderByPath123", folderId, "Expected folderId from getFolderIdByPath");
    }
  }

  @Test
  public void testGetFolderId_CreateFolderWhenFolderIdNull() throws IOException {
    // Mock the dependencies
    Result result = mock(Result.class);
    PersistenceService persistenceService = mock(PersistenceService.class);

    // Mock the result list as empty
    List<Map> resultList = new ArrayList<>();
    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    // Create a spy of the SDMServiceImpl to mock specific methods
    SDMServiceImpl sdmServiceImpl = spy(new SDMServiceImpl(binding, connectionPool));

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      // Mock the getFolderIdByPath method to return null (so that it will try to create a folder)
      doReturn(null)
          .when(sdmServiceImpl)
          .getFolderIdByPath(anyString(), anyString(), any(SDMCredentials.class), anyString());

      // Mock the TokenHandler static method and SDMCredentials instantiation
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("mockUrl");

      // Use MockWebServer to set the URL for SDMCredentials
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();
      String mockUrl = mockWebServer.url("/").toString();
      sdmCredentials.setUrl(mockUrl);

      // Mock the static method to return a valid SDMCredentials instance
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);

      // Mock the token retrieval as well
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);
      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);

      // Mock the createFolder method to return a folder ID when invoked
      JSONObject jsonObject = new JSONObject();
      JSONObject succinctProperties = new JSONObject();
      succinctProperties.put("cmis:objectId", "newFolderId123");
      jsonObject.put("succinctProperties", succinctProperties);

      // Enqueue the mock response on the MockWebServer
      MockResponse mockResponse1 =
          new MockResponse().setResponseCode(200).setBody("newFolderId123");
      mockWebServer.enqueue(mockResponse1);

      doReturn(jsonObject.toString())
          .when(sdmServiceImpl)
          .createFolder(anyString(), anyString(), any(SDMCredentials.class), anyString());

      // Invoke the method
      String folderId = sdmServiceImpl.getFolderId(result, persistenceService, up__ID, jwtToken);

      // Assert the folder ID is the newly created one
      assertEquals("newFolderId123", folderId, "Expected newly created folderId");
    }
  }

  @Test
  public void testReadDocument_Success() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedContent = "This is a document content.";
      String objectId = "testObjectId";
      String jwtToken = "testJwtToken";
      String repositoryId = "repository_id";
      SDMCredentials sdmCredentials = new SDMCredentials();
      AttachmentReadEventContext mockContext = mock(AttachmentReadEventContext.class);
      MediaData mockData = mock(MediaData.class);
      when(mockContext.getData()).thenReturn(mockData);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream("{\"message\":\"Server error\"}".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      sdmServiceImpl.readDocument(objectId, jwtToken, sdmCredentials, mockContext);

      verify(mockData).setContent(any(InputStream.class));
    }
  }

  @Test
  public void testReadDocument_UnsuccessfulResponse() throws IOException {

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String objectId = "testObjectId";
      String jwtToken = "testJwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();
      AttachmentReadEventContext mockContext = mock(AttachmentReadEventContext.class);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream =
          new ByteArrayInputStream("{\"message\":\"Server error\"}".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.readDocument(objectId, jwtToken, sdmCredentials, mockContext);
              });

      // Check if the exception message contains the expected first part
      String expectedMessagePart1 = "Failed to set document stream in context";
      assertTrue(exception.getMessage().contains(expectedMessagePart1));
    }
  }

  @Test
  public void testReadDocument_ExceptionWhileSettingContent() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedContent = "This is a document content.";
      String objectId = "testObjectId";
      String jwtToken = "testJwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();
      AttachmentReadEventContext mockContext = mock(AttachmentReadEventContext.class);
      MediaData mockData = mock(MediaData.class);
      when(mockContext.getData()).thenReturn(mockData);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(expectedContent.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      doThrow(new RuntimeException("Failed to set document stream in context"))
          .when(mockData)
          .setContent(any(InputStream.class));

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.readDocument(objectId, jwtToken, sdmCredentials, mockContext);
              });
      assertEquals("Failed to set document stream in context", exception.getMessage());
    }
  }

  // @Test
  // public void testRenameAttachments_Success() throws IOException {
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class)) {
  //     String jwtToken = "jwt_token";
  //     CmisDocument cmisDocument = new CmisDocument();
  //     cmisDocument.setFileName("newFileName");
  //     cmisDocument.setObjectId("objectId");
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     secondaryProperties.put("property1", "value1");
  //     secondaryProperties.put("property2", "value2");

  //     SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
  //         .thenReturn(httpClient);

  //     when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
  //     when(response.getStatusLine()).thenReturn(statusLine);
  //     when(statusLine.getStatusCode()).thenReturn(200);
  //     when(response.getEntity()).thenReturn(entity);
  //     InputStream inputStream = new ByteArrayInputStream("".getBytes());
  //     when(entity.getContent()).thenReturn(inputStream);

  //     String jsonResponseTypes =
  //         "[{"
  //             + "\"type\": {\"id\": \"cmis:secondary\"},"
  //             + "\"children\": ["
  //             + "{\"type\": {\"id\": \"Type:1\"}},"
  //             + "{\"type\": {\"id\": \"Type:2\"}},"
  //             + "{\"type\": {\"id\": \"Type:3\"}, \"children\": [{\"type\": {\"id\":
  // \"Type:3child\"}}]}"
  //             + "]}]";

  //     String jsonResponseProperties =
  //         "{"
  //             + "\"id\": \"type:1\","
  //             + "\"propertyDefinitions\": {"
  //             + "\"property1\": {"
  //             + "\"id\": \"property1\","
  //             + "\"mcm:miscellaneous\": {\"isPartOfTable\": \"true\"}"
  //             + "},"
  //             + "\"property2\": {"
  //             + "\"id\": \"property2\","
  //             + "\"mcm:miscellaneous\": {\"isPartOfTable\": \"true\"}"
  //             + "}"
  //             + "}}";

  //     inputStream = new ByteArrayInputStream(jsonResponseTypes.getBytes(StandardCharsets.UTF_8));
  //     InputStream inputStream2 =
  //         new ByteArrayInputStream(jsonResponseProperties.getBytes(StandardCharsets.UTF_8));

  //     when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
  //     when(response.getStatusLine()).thenReturn(statusLine);
  //     when(statusLine.getStatusCode()).thenReturn(200);
  //     when(response.getEntity()).thenReturn(entity);
  //     when(entity.getContent()).thenReturn(inputStream, inputStream2);

  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

  //     int responseCode =
  //         sdmServiceImpl.updateAttachments(
  //             jwtToken, mockSdmCredentials, cmisDocument, secondaryProperties);

  //     // Verify the response code
  //     assertEquals(200, responseCode);
  //   }
  // }

  @Test
  public void testRenameAttachments_getTypesFail() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {

      String jwtToken = "jwt_token";
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("newFileName");
      cmisDocument.setObjectId("objectId");
      Map<String, String> secondaryProperties = new HashMap<>();
      secondaryProperties.put("property1", "value1");
      secondaryProperties.put("property2", "value2");
      Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();

      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream("".getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("IOException"));

      // Mock CacheConfig to return null
      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryTypesCache).thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(null);

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Verify the response code
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.updateAttachments(
                    jwtToken,
                    mockSdmCredentials,
                    cmisDocument,
                    secondaryProperties,
                    secondaryPropertiesWithInvalidDefinitions);
              });

      assertTrue(exception.getMessage().contains("Could not update the attachment"));
    }
  }

  @Test
  public void testDeleteDocumentThrowsServiceExceptionOnHttpClientError() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<HttpClients> httpClientsMockedStatic = mockStatic(HttpClients.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDITokenUsingAuthorities(any(), any(), any()))
          .thenReturn("dummyToken");

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockSdmCredentials.getUrl()).thenReturn("http://example.com/");
      httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);

      // Simulate IOException during HTTP call
      when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new IOException("IOException"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Ensure ServiceException is thrown
      assertThrows(
          ServiceException.class,
          () ->
              sdmServiceImpl.deleteDocument("delete", "123", "user@example.com", "test-subdomain"));
    }
  }

  @Test
  public void testGetSecondaryTypesWithCache() throws IOException {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      String jwtToken = "jwt_token";
      String repositoryId = "repoId";
      List<String> secondaryTypesCached =
          Arrays.asList("Type:1", "Type:2", "Type:3", "Type:3child");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryTypesCache).thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(secondaryTypesCached);

      // Verify the response code
      List<String> secondaryTypes =
          sdmServiceImpl.getSecondaryTypes(repositoryId, jwtToken, mockSdmCredentials);

      assertEquals(secondaryTypesCached.size(), secondaryTypes.size());
    }
  }

  @Test
  public void testValidSecondaryPropertiesFail() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      String repositoryId = "repoId";
      String subdomain = "subdomain";
      List<String> secondaryTypes = Arrays.asList("Type:1", "Type:2", "Type:3", "Type:3child");
      Cache<SecondaryPropertiesKey, List<String>> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn(null);

      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream("".getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("IOException"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Verify the response code
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.getValidSecondaryProperties(
                    secondaryTypes, subdomain, mockSdmCredentials, repositoryId);
              });

      assertTrue(exception.getMessage().contains("Could not update the attachment"));
    }
  }

  @Test
  public void testValidSecondaryPropertiesFailEmptyResponse() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      String jwtToken = "jwt_token";
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("newFileName");
      cmisDocument.setObjectId("objectId");
      Map<String, String> secondaryProperties = new HashMap<>();
      secondaryProperties.put("property1", "value1");
      secondaryProperties.put("property2", "value2");

      List<String> secondaryTypesCached = new ArrayList<>();
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      Cache<SecondaryPropertiesKey, List<String>> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn(secondaryTypesCached);

      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryTypesCache).thenReturn(mockCache);

      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream("".getBytes());
      when(entity.getContent()).thenReturn(inputStream);
      Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();

      String jsonResponseTypes =
          "[{"
              + "\"type\": {\"id\": \"cmis:secondary\"},"
              + "\"children\": ["
              + "{\"type\": {\"id\": \"Type:1\"}},"
              + "{\"type\": {\"id\": \"Type:2\"}},"
              + "{\"type\": {\"id\": \"Type:3\"}, \"children\": [{\"type\": {\"id\":\"Type:3child\"}}]}"
              + "]}]";

      String jsonResponseProperties =
          "{"
              + "\"id\": \"type:1\","
              + "\"propertyDefinitions\": {"
              + "\"property1\": {"
              + "\"id\": \"property1\","
              + "\"mcm:miscellaneous\": {\"isPartOfTable\": \"true\"}"
              + "},"
              + "\"property2\": {"
              + "\"id\": \"property2\","
              + "\"mcm:miscellaneous\": {\"isPartOfTable\": \"true\"}"
              + "}"
              + "}}";

      inputStream = new ByteArrayInputStream(jsonResponseTypes.getBytes(StandardCharsets.UTF_8));
      InputStream inputStream2 =
          new ByteArrayInputStream(jsonResponseProperties.getBytes(StandardCharsets.UTF_8));

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when(response.getEntity()).thenReturn(null);
      when(entity.getContent()).thenReturn(inputStream, inputStream2);

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                sdmServiceImpl.updateAttachments(
                    jwtToken,
                    mockSdmCredentials,
                    cmisDocument,
                    secondaryProperties,
                    secondaryPropertiesWithInvalidDefinitions);
              });
    }
  }

  @Test
  public void testGetObject_Success() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"succinctProperties\": {\"cmis:name\":\"desiredObjectName\"}}";
      String jwtToken = "jwt_token";
      String objectId = "objectId";
      SDMServiceImpl sdmServiceImpl = Mockito.spy(new SDMServiceImpl(binding, connectionPool));
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(200);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream(mockResponseBody.getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      String objectName = sdmServiceImpl.getObject(jwtToken, objectId, sdmCredentials);
      assertEquals("desiredObjectName", objectName);
    }
  }

  @Test
  public void testGetObject_Failure() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String jwtToken = "jwt_token";
      String objectId = "objectId";
      SDMServiceImpl sdmServiceImpl = Mockito.spy(new SDMServiceImpl(binding, connectionPool));
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpGet.class))).thenReturn(response);
      when(response.getStatusLine()).thenReturn(statusLine);
      when(statusLine.getStatusCode()).thenReturn(500);
      when(response.getEntity()).thenReturn(entity);
      InputStream inputStream = new ByteArrayInputStream("".getBytes());
      when(entity.getContent()).thenReturn(inputStream);

      String objectName = sdmServiceImpl.getObject(jwtToken, objectId, sdmCredentials);
      assertNull(objectName);
    }
  }

  @Test
  public void testGetObjectThrowsServiceExceptionOnIOException() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
        MockedStatic<HttpClients> httpClientsMockedStatic = mockStatic(HttpClients.class)) {
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);

      // Mock TokenHandler methods
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), any()))
          .thenReturn(mockHttpClient);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSubdomainFromToken(any()))
          .thenReturn("test-subdomain");

      when(mockSdmCredentials.getUrl()).thenReturn("http://example.com/");

      // Simulate IOException during HTTP call
      when(mockHttpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Network error"));

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      // Assert that ServiceException is thrown
      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> sdmServiceImpl.getObject("jwtToken", "objectId", mockSdmCredentials));

      assertEquals(SDMConstants.ATTACHMENT_NOT_FOUND, exception.getMessage());
      assertTrue(exception.getCause() instanceof IOException);
    }
  }

  @Test
  public void createDocument_ExceptionTest() throws IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFileName("sample.pdf");
      cmisDocument.setAttachmentId("attachmentId");
      String content = "sample.pdf content";
      InputStream contentStream =
          new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      cmisDocument.setContent(contentStream);
      cmisDocument.setParentId("parentId");
      cmisDocument.setRepositoryId("repositoryId");
      cmisDocument.setFolderId("folderId");
      cmisDocument.setMimeType("application/pdf");

      String jwtToken = "jwtToken";
      SDMCredentials sdmCredentials = new SDMCredentials();

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getHttpClient(any(), any(), any(), eq("TOKEN_EXCHANGE")))
          .thenReturn(httpClient);

      when(httpClient.execute(any(HttpPost.class))).thenThrow(new IOException("Error"));
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl(binding, connectionPool);

      assertThrows(
          ServiceException.class,
          () -> sdmServiceImpl.createDocument(cmisDocument, sdmCredentials, jwtToken));
    }
  }
}
