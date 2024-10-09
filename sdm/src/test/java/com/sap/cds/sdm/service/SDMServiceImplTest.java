package com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class SDMServiceImplTest {
  private static final String REPO_ID = "repo";
  private SDMService SDMService;

  @BeforeEach
  public void setUp() {
    SDMService = new SDMServiceImpl();
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
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    String mockUrl = mockWebServer.url("/").toString();

    JSONObject capabilities = new JSONObject();
    capabilities.put("capabilityContentStreamUpdatability", "other");
    JSONObject repoInfo = new JSONObject();
    repoInfo.put("capabilities", capabilities);
    JSONObject root = new JSONObject();
    root.put(REPO_ID, repoInfo);

    mockWebServer.enqueue(
        new MockResponse().setBody(root.toString()).addHeader("Content-Type", "application/json"));

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl(mockUrl);
    String token = "token";

    JSONObject json = SDMService.getRepositoryInfo(token, sdmCredentials);

    JSONObject fetchedRepoInfo = json.getJSONObject(REPO_ID);
    JSONObject fetchedCapabilities = fetchedRepoInfo.getJSONObject("capabilities");
    assertEquals("other", fetchedCapabilities.getString("capabilityContentStreamUpdatability"));

    mockWebServer.shutdown();
  }

  @Test
  public void testGetRepositoryInfoFail() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    String mockUrl = mockWebServer.url("/").toString();

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
            .setBody(
                "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message in
            // the body
            .addHeader("Content-Type", "application/json"));

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl(mockUrl);
    String token = "token";

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              SDMService.getRepositoryInfo(token, sdmCredentials);
            });
    assertEquals("Failed to get repository info", exception.getMessage());

    mockWebServer.shutdown();
  }

  @Test
  public void testCheckRepositoryTypeCacheVersioned() throws IOException {
    String repositoryId = "repo";
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(repositoryId)).thenReturn("Versioned");
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      String result = SDMService.checkRepositoryType(repositoryId);
      assertEquals("Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeCacheNonVersioned() throws IOException {
    String repositoryId = "repo";
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(repositoryId)).thenReturn("Non Versioned");
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      String result = SDMService.checkRepositoryType(repositoryId);
      assertEquals("Non Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeNoCacheVersioned() throws IOException {
    String repositoryId = "repo";
    SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl());
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(repositoryId)).thenReturn(null);
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getAccessToken(mockSdmCredentials))
          .thenReturn("token");
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

      JSONObject capabilities = new JSONObject();
      capabilities.put(
          "capabilityContentStreamUpdatability",
          "pwconly"); // To match the expected output "Versioned"
      JSONObject repoInfo = new JSONObject();
      repoInfo.put("capabilities", capabilities);
      JSONObject mockRepoData = new JSONObject();
      mockRepoData.put(repositoryId, repoInfo);

      Mockito.doReturn(mockRepoData)
          .when(spySDMService)
          .getRepositoryInfo("token", mockSdmCredentials);

      String result = spySDMService.checkRepositoryType(repositoryId);
      assertEquals("Versioned", result);
    }
  }

  @Test
  public void testCheckRepositoryTypeNoCacheNonVersioned() throws IOException {
    String repositoryId = "repo";
    SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl());
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(repositoryId)).thenReturn(null);
      cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getAccessToken(mockSdmCredentials))
          .thenReturn("token");
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

      JSONObject capabilities = new JSONObject();
      capabilities.put(
          "capabilityContentStreamUpdatability",
          "notpwconly"); // To match the expected output "Versioned"
      JSONObject repoInfo = new JSONObject();
      repoInfo.put("capabilities", capabilities);
      JSONObject mockRepoData = new JSONObject();
      mockRepoData.put(repositoryId, repoInfo);

      Mockito.doReturn(mockRepoData)
          .when(spySDMService)
          .getRepositoryInfo("token", mockSdmCredentials);

      String result = spySDMService.checkRepositoryType(repositoryId);
      assertEquals("Non Versioned", result);
    }
  }

  @Test
  public void testCreateFolder() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse = "Folder ID";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(expectedResponse)
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getDIToken(jwtToken, sdmCredentials)).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      String actualResponse =
          sdmServiceImpl.createFolder(parentId, jwtToken, repositoryId, sdmCredentials);

      assertEquals(expectedResponse, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateFolderFail() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
              .setBody(
                  "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message in
              // the body
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getDIToken(jwtToken, sdmCredentials)).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      IOException exception =
          assertThrows(
              IOException.class,
              () -> {
                sdmServiceImpl.createFolder(parentId, jwtToken, repositoryId, sdmCredentials);
              });
      assertEquals("Could not upload", exception.getMessage());

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testGetFolderIdByPath() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String expectedResponse = "Folder ID";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(expectedResponse)
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getDIToken(jwtToken, sdmCredentials)).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      String actualResponse =
          sdmServiceImpl.getFolderIdByPath(parentId, jwtToken, repositoryId, sdmCredentials);

      assertEquals(expectedResponse, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testGetFolderIdByPathFail() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
              .setBody(
                  "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message in
              // the body
              .addHeader("Content-Type", "application/json"));
      String parentId = "123";
      String jwtToken = "jwt_token";
      String repositoryId = "repository_id";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getDIToken(jwtToken, sdmCredentials)).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      String folderId =
          sdmServiceImpl.getFolderIdByPath(parentId, jwtToken, repositoryId, sdmCredentials);
      assertNull(folderId, "Expected folderId to be null");

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateDocument() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"succinctProperties\": {\"cmis:objectId\": \"objectId\"}}";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(mockResponseBody)
              .addHeader("Content-Type", "application/json"));

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

      String jwtToken = "jwtToken";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("url", "objectId");
      expectedResponse.put("status", "success");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateDocumentFailDuplicate() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"message\": \"Duplicate document found\"}";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(mockResponseBody)
              .setResponseCode(409)
              .addHeader("Content-Type", "application/json"));

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

      String jwtToken = "jwtToken";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("status", "duplicate");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateDocumentFailVirus() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody =
          "{\"message\": \"Malware Service Exception: Virus found in the file!\"}";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(mockResponseBody)
              .setResponseCode(400) // Assuming 400 Bad Request or a similar client error code
              .addHeader("Content-Type", "application/json"));

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

      String jwtToken = "jwtToken";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("status", "virus");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  public void testCreateDocumentFailOther() throws IOException {
    MockWebServer mockWebServer = new MockWebServer();
    mockWebServer.start();
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      String mockResponseBody = "{\"message\": \"An unexpected error occurred\"}";
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(mockResponseBody)
              .setResponseCode(
                  500) // Assuming 500 Internal Server Error or another server error code
              .addHeader("Content-Type", "application/json"));

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

      String jwtToken = "jwtToken";
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");

      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      JSONObject actualResponse =
          sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

      JSONObject expectedResponse = new JSONObject();
      expectedResponse.put("name", "sample.pdf");
      expectedResponse.put("id", "attachmentId");
      expectedResponse.put("status", "fail");
      assertEquals(expectedResponse.toString(), actualResponse.toString());
    } finally {
      mockWebServer.shutdown();
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
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      int actualResponse =
          sdmServiceImpl.deleteDocument("deleteTree", "objectId", "email", "subdomain");

      assertEquals(200, actualResponse);

    } finally {
      mockWebServer.shutdown();
    }
  }

  @Test
  void testGetFolderId_FolderIdPresentInResult() throws IOException {
    PersistenceService persistenceService = mock(PersistenceService.class);
    Result result = mock(Result.class);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("folderId", "folder123");
    List<Map> resultList = Arrays.asList((Map) attachment);

    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      SDMCredentials sdmCredentials = new SDMCredentials();
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");
      String folderId = sdmServiceImpl.getFolderId(jwtToken, result, persistenceService, up__ID);
      assertEquals("folder123", folderId, "Expected folderId from result list");
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
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

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
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

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
          .thenThrow(new IOException("Could not delete the document"));

      // Since the exception is thrown before OkHttpClient is used, no need to mock httpClient
      // behavior.
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
      // Assert exception
      IOException thrown =
          assertThrows(
              IOException.class,
              () -> {
                // Call the method under test
                sdmServiceImpl.deleteDocument(cmisaction, objectId, userEmail, subdomain);
              });

      // Verify the exception message
      assertEquals("Could not delete the document", thrown.getMessage());
    }
  }

  @Test
  void testGetFolderId_GetFolderIdByPathReturns() throws IOException {
    Result result = mock(Result.class);
    PersistenceService persistenceService = mock(PersistenceService.class);

    List<Map> resultList = new ArrayList<>();
    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    SDMServiceImpl sdmServiceImpl = spy(new SDMServiceImpl());

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      doReturn("folderByPath123")
          .when(sdmServiceImpl)
          .getFolderIdByPath(anyString(), anyString(), anyString(), any(SDMCredentials.class));

      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("mockUrl");
      MockWebServer mockWebServer = new MockWebServer();
      mockWebServer.start();
      String mockUrl = mockWebServer.url("/").toString();
      sdmCredentials.setUrl(mockUrl);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");
      MockResponse mockResponse1 =
          new MockResponse().setResponseCode(200).setBody("folderByPath123");
      mockWebServer.enqueue(mockResponse1);
      String folderId = sdmServiceImpl.getFolderId(jwtToken, result, persistenceService, up__ID);
      assertEquals("folderByPath123", folderId, "Expected folderId from getFolderIdByPath");
    }
  }

  @Test
  void testGetFolderId_CreateFolderWhenFolderIdNull() throws IOException {
    // Mock the dependencies
    Result result = mock(Result.class);
    PersistenceService persistenceService = mock(PersistenceService.class);

    // Mock the result list as empty
    List<Map> resultList = new ArrayList<>();
    when(result.listOf(Map.class)).thenReturn((List) resultList);

    String jwtToken = "jwtToken";
    String up__ID = "up__ID";

    // Create a spy of the SDMServiceImpl to mock specific methods
    SDMServiceImpl sdmServiceImpl = spy(new SDMServiceImpl());

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      // Mock the getFolderIdByPath method to return null (so that it will try to create a folder)
      doReturn(null)
          .when(sdmServiceImpl)
          .getFolderIdByPath(anyString(), anyString(), anyString(), any(SDMCredentials.class));

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
          .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
          .thenReturn("mockAccessToken");

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
          .createFolder(anyString(), anyString(), anyString(), any(SDMCredentials.class));

      // Invoke the method
      String folderId = sdmServiceImpl.getFolderId(jwtToken, result, persistenceService, up__ID);

      // Assert the folder ID is the newly created one
      assertEquals("newFolderId123", folderId, "Expected newly created folderId");
    }
  }
}
