package com.sap.cds.sdm.service;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

public class SDMServiceImplTest {
  private static final String REPO_ID = "repo";
  private SDMService SDMService;

  @BeforeEach
  public void setUp() {
    SDMService = new SDMServiceImpl();
  }

  // @Test
  // public void testIsRepositoryVersioned_Versioned() throws IOException {
  //   // Mocked JSON structure for a versioned repository
  //   JSONObject capabilities = new JSONObject();
  //   capabilities.put("capabilityContentStreamUpdatability", "pwconly");

  //   JSONObject repoInfo = new JSONObject();
  //   repoInfo.put("capabilities", capabilities);

  //   JSONObject root = new JSONObject();
  //   root.put(REPO_ID, repoInfo);

  //   // Call the method and verify the result
  //   boolean isVersioned = SDMService.isRepositoryVersioned(root, REPO_ID);
  //   assertTrue(isVersioned);
  // }

  // @Test
  // public void testIsRepositoryVersioned_NonVersioned() throws IOException {
  //   // Mocked JSON structure for a non-versioned repository
  //   JSONObject capabilities = new JSONObject();
  //   capabilities.put("capabilityContentStreamUpdatability", "other");

  //   JSONObject repoInfo = new JSONObject();
  //   repoInfo.put("capabilities", capabilities);

  //   JSONObject root = new JSONObject();
  //   root.put(REPO_ID, repoInfo);

  //   // Call the method and verify the result
  //   boolean isVersioned = SDMService.isRepositoryVersioned(root, REPO_ID);
  //   assertFalse(isVersioned);
  // }

  // @Test
  // public void testGetRepositoryInfo() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   String mockUrl = mockWebServer.url("/").toString();

  //   JSONObject capabilities = new JSONObject();
  //   capabilities.put("capabilityContentStreamUpdatability", "other");
  //   JSONObject repoInfo = new JSONObject();
  //   repoInfo.put("capabilities", capabilities);
  //   JSONObject root = new JSONObject();
  //   root.put(REPO_ID, repoInfo);

  //   mockWebServer.enqueue(
  //       new MockResponse().setBody(root.toString()).addHeader("Content-Type",
  // "application/json"));

  //   SDMCredentials sdmCredentials = new SDMCredentials();
  //   sdmCredentials.setUrl(mockUrl);
  //   String token = "token";

  //   JSONObject json = SDMService.getRepositoryInfo(token, sdmCredentials);

  //   JSONObject fetchedRepoInfo = json.getJSONObject(REPO_ID);
  //   JSONObject fetchedCapabilities = fetchedRepoInfo.getJSONObject("capabilities");
  //   assertEquals("other", fetchedCapabilities.getString("capabilityContentStreamUpdatability"));

  //   mockWebServer.shutdown();
  // }

  // @Test
  // public void testGetRepositoryInfoFail() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   String mockUrl = mockWebServer.url("/").toString();

  //   mockWebServer.enqueue(
  //       new MockResponse()
  //           .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
  //           .setBody(
  //               "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message in
  //           // the body
  //           .addHeader("Content-Type", "application/json"));

  //   SDMCredentials sdmCredentials = new SDMCredentials();
  //   sdmCredentials.setUrl(mockUrl);
  //   String token = "token";

  //   IOException exception =
  //       assertThrows(
  //           IOException.class,
  //           () -> {
  //             SDMService.getRepositoryInfo(token, sdmCredentials);
  //           });
  //   assertEquals("Failed to get repository info", exception.getMessage());

  //   mockWebServer.shutdown();
  // }

  // @Test
  // public void testCheckRepositoryTypeCacheVersioned() throws IOException {
  //   String repositoryId = "repo";
  //   try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
  //       Mockito.mockStatic(CacheConfig.class)) {
  //     Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //     Mockito.when(mockCache.get(repositoryId)).thenReturn("Versioned");
  //     cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
  //     String result = SDMService.checkRepositoryType(repositoryId);
  //     assertEquals("Versioned", result);
  //   }
  // }

  // @Test
  // public void testCheckRepositoryTypeCacheNonVersioned() throws IOException {
  //   String repositoryId = "repo";
  //   try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
  //       Mockito.mockStatic(CacheConfig.class)) {
  //     Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //     Mockito.when(mockCache.get(repositoryId)).thenReturn("Non Versioned");
  //     cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
  //     String result = SDMService.checkRepositoryType(repositoryId);
  //     assertEquals("Non Versioned", result);
  //   }
  // }

  // @Test
  // public void testCheckRepositoryTypeNoCacheVersioned() throws IOException {
  //   String repositoryId = "repo";
  //   SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl());
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //           Mockito.mockStatic(TokenHandler.class);
  //       MockedStatic<CacheConfig> cacheConfigMockedStatic =
  // Mockito.mockStatic(CacheConfig.class)) {
  //     Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //     Mockito.when(mockCache.get(repositoryId)).thenReturn(null);
  //     cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
  //     SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getAccessToken(mockSdmCredentials))
  //         .thenReturn("token");
  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

  //     JSONObject capabilities = new JSONObject();
  //     capabilities.put(
  //         "capabilityContentStreamUpdatability",
  //         "pwconly"); // To match the expected output "Versioned"
  //     JSONObject repoInfo = new JSONObject();
  //     repoInfo.put("capabilities", capabilities);
  //     JSONObject mockRepoData = new JSONObject();
  //     mockRepoData.put(repositoryId, repoInfo);

  //     Mockito.doReturn(mockRepoData)
  //         .when(spySDMService)
  //         .getRepositoryInfo("token", mockSdmCredentials);

  //     String result = spySDMService.checkRepositoryType(repositoryId);
  //     assertEquals("Versioned", result);
  //   }
  // }

  // @Test
  // public void testCheckRepositoryTypeNoCacheNonVersioned() throws IOException {
  //   String repositoryId = "repo";
  //   SDMServiceImpl spySDMService = Mockito.spy(new SDMServiceImpl());
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //           Mockito.mockStatic(TokenHandler.class);
  //       MockedStatic<CacheConfig> cacheConfigMockedStatic =
  // Mockito.mockStatic(CacheConfig.class)) {
  //     Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //     Mockito.when(mockCache.get(repositoryId)).thenReturn(null);
  //     cacheConfigMockedStatic.when(CacheConfig::getVersionedRepoCache).thenReturn(mockCache);
  //     SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getAccessToken(mockSdmCredentials))
  //         .thenReturn("token");
  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

  //     JSONObject capabilities = new JSONObject();
  //     capabilities.put(
  //         "capabilityContentStreamUpdatability",
  //         "notpwconly"); // To match the expected output "Versioned"
  //     JSONObject repoInfo = new JSONObject();
  //     repoInfo.put("capabilities", capabilities);
  //     JSONObject mockRepoData = new JSONObject();
  //     mockRepoData.put(repositoryId, repoInfo);

  //     Mockito.doReturn(mockRepoData)
  //         .when(spySDMService)
  //         .getRepositoryInfo("token", mockSdmCredentials);

  //     String result = spySDMService.checkRepositoryType(repositoryId);
  //     assertEquals("Non Versioned", result);
  //   }
  // }

  // @Test
  // public void testCreateFolder() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String expectedResponse = "Folder ID";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(expectedResponse)
  //             .addHeader("Content-Type", "application/json"));
  //     String parentId = "123";
  //     String jwtToken = "jwt_token";
  //     String repositoryId = "repository_id";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);
  //     Mockito.when(TokenHandler.getDIToken(jwtToken,
  // sdmCredentials)).thenReturn("mockAccessToken");
  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

  //     String actualResponse =
  //         sdmServiceImpl.createFolder(parentId, jwtToken, repositoryId, sdmCredentials);

  //     assertEquals(expectedResponse, actualResponse);

  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testCreateFolderFail() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
  //             .setBody(
  //                 "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message
  // in
  //             // the body
  //             .addHeader("Content-Type", "application/json"));
  //     String parentId = "123";
  //     String jwtToken = "jwt_token";
  //     String repositoryId = "repository_id";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);
  //     Mockito.when(TokenHandler.getDIToken(jwtToken,
  // sdmCredentials)).thenReturn("mockAccessToken");
  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

  //     IOException exception =
  //         assertThrows(
  //             IOException.class,
  //             () -> {
  //               sdmServiceImpl.createFolder(parentId, jwtToken, repositoryId, sdmCredentials);
  //             });
  //     assertEquals("Could not upload", exception.getMessage());

  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testGetFolderIdByPath() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String expectedResponse = "Folder ID";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(expectedResponse)
  //             .addHeader("Content-Type", "application/json"));
  //     String parentId = "123";
  //     String jwtToken = "jwt_token";
  //     String repositoryId = "repository_id";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);
  //     Mockito.when(TokenHandler.getDIToken(jwtToken,
  // sdmCredentials)).thenReturn("mockAccessToken");
  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

  //     String actualResponse =
  //         sdmServiceImpl.getFolderIdByPath(parentId, jwtToken, repositoryId, sdmCredentials);

  //     assertEquals(expectedResponse, actualResponse);

  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testGetFolderIdByPathFail() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setResponseCode(500) // Set HTTP status code to 500 for an internal server error
  //             .setBody(
  //                 "{\"error\":\"Internal Server Error\"}") // Optional: Provide an error message
  // in
  //             // the body
  //             .addHeader("Content-Type", "application/json"));
  //     String parentId = "123";
  //     String jwtToken = "jwt_token";
  //     String repositoryId = "repository_id";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);
  //     Mockito.when(TokenHandler.getDIToken(jwtToken,
  // sdmCredentials)).thenReturn("mockAccessToken");
  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

  //     IOException exception =
  //         assertThrows(
  //             IOException.class,
  //             () -> {
  //               sdmServiceImpl.getFolderIdByPath(parentId, jwtToken, repositoryId,
  // sdmCredentials);
  //             });
  //     assertEquals("Could not upload", exception.getMessage());

  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testCreateDocument() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String mockResponseBody = "{\"succinctProperties\": {\"cmis:objectId\": \"objectId\"}}";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(mockResponseBody)
  //             .addHeader("Content-Type", "application/json"));

  //     CmisDocument cmisDocument = new CmisDocument();
  //     cmisDocument.setFileName("sample.pdf");
  //     cmisDocument.setAttachmentId("attachmentId");
  //     String content = "sample.pdf content";
  //     InputStream contentStream =
  //         new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  //     cmisDocument.setContent(contentStream);
  //     cmisDocument.setParentId("parentId");
  //     cmisDocument.setRepositoryId("repositoryId");
  //     cmisDocument.setFolderId("folderId");

  //     String jwtToken = "jwtToken";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);

  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
  //         .thenReturn("mockAccessToken");

  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
  //     JSONObject actualResponse =
  //         sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

  //     JSONObject expectedResponse = new JSONObject();
  //     expectedResponse.put("url", "objectId");
  //     assertEquals(expectedResponse.toString(), actualResponse.toString());
  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testCreateDocumentFailDuplicate() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String mockResponseBody = "{\"message\": \"Duplicate document found\"}";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(mockResponseBody)
  //             .setResponseCode(409)
  //             .addHeader("Content-Type", "application/json"));

  //     CmisDocument cmisDocument = new CmisDocument();
  //     cmisDocument.setFileName("sample.pdf");
  //     cmisDocument.setAttachmentId("attachmentId");
  //     String content = "sample.pdf content";
  //     InputStream contentStream =
  //         new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  //     cmisDocument.setContent(contentStream);
  //     cmisDocument.setParentId("parentId");
  //     cmisDocument.setRepositoryId("repositoryId");
  //     cmisDocument.setFolderId("folderId");

  //     String jwtToken = "jwtToken";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);

  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
  //         .thenReturn("mockAccessToken");

  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
  //     JSONObject actualResponse =
  //         sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

  //     JSONObject expectedResponse = new JSONObject();
  //     expectedResponse.put("duplicate", true);
  //     expectedResponse.put("virus", false);
  //     expectedResponse.put(
  //         "id",
  //         "attachmentId"); // Replace "attachmentId" with the actual attachment ID used in the
  // test
  //     expectedResponse.put(
  //         "failedDocument",
  //         "sample.pdf"); // Replace "sample.pdf" with the actual document name used in the test
  //     assertEquals(expectedResponse.toString(), actualResponse.toString());
  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testCreateDocumentFailVirus() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String mockResponseBody =
  //         "{\"message\": \"Malware Service Exception: Virus found in the file!\"}";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(mockResponseBody)
  //             .setResponseCode(400) // Assuming 400 Bad Request or a similar client error code
  //             .addHeader("Content-Type", "application/json"));

  //     CmisDocument cmisDocument = new CmisDocument();
  //     cmisDocument.setFileName("sample.pdf");
  //     cmisDocument.setAttachmentId("attachmentId");
  //     String content = "sample.pdf content";
  //     InputStream contentStream =
  //         new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  //     cmisDocument.setContent(contentStream);
  //     cmisDocument.setParentId("parentId");
  //     cmisDocument.setRepositoryId("repositoryId");
  //     cmisDocument.setFolderId("folderId");

  //     String jwtToken = "jwtToken";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);

  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
  //         .thenReturn("mockAccessToken");

  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
  //     JSONObject actualResponse =
  //         sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

  //     JSONObject expectedResponse = new JSONObject();
  //     expectedResponse.put("duplicate", false);
  //     expectedResponse.put("virus", true);
  //     expectedResponse.put(
  //         "id",
  //         "attachmentId"); // Replace "attachmentId" with the actual attachment ID used in the
  // test
  //     expectedResponse.put(
  //         "failedDocument",
  //         "sample.pdf"); // Replace "sample.pdf" with the actual document name used in the test
  //     assertEquals(expectedResponse.toString(), actualResponse.toString());
  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }

  // @Test
  // public void testCreateDocumentFailOther() throws IOException {
  //   MockWebServer mockWebServer = new MockWebServer();
  //   mockWebServer.start();
  //   try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //       Mockito.mockStatic(TokenHandler.class)) {
  //     String mockResponseBody = "{\"message\": \"An unexpected error occurred\"}";
  //     mockWebServer.enqueue(
  //         new MockResponse()
  //             .setBody(mockResponseBody)
  //             .setResponseCode(
  //                 500) // Assuming 500 Internal Server Error or another server error code
  //             .addHeader("Content-Type", "application/json"));

  //     CmisDocument cmisDocument = new CmisDocument();
  //     cmisDocument.setFileName("sample.pdf");
  //     cmisDocument.setAttachmentId("attachmentId");
  //     String content = "sample.pdf content";
  //     InputStream contentStream =
  //         new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  //     cmisDocument.setContent(contentStream);
  //     cmisDocument.setParentId("parentId");
  //     cmisDocument.setRepositoryId("repositoryId");
  //     cmisDocument.setFolderId("folderId");

  //     String jwtToken = "jwtToken";
  //     String mockUrl = mockWebServer.url("/").toString();
  //     SDMCredentials sdmCredentials = new SDMCredentials();
  //     sdmCredentials.setUrl(mockUrl);

  //     tokenHandlerMockedStatic
  //         .when(() -> TokenHandler.getDIToken(jwtToken, sdmCredentials))
  //         .thenReturn("mockAccessToken");

  //     SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
  //     JSONObject actualResponse =
  //         sdmServiceImpl.createDocument(cmisDocument, jwtToken, sdmCredentials);

  //     JSONObject expectedResponse = new JSONObject();
  //     expectedResponse.put("fail", true);
  //     expectedResponse.put(
  //         "id",
  //         "attachmentId"); // Replace "attachmentId" with the actual attachment ID used in the
  // test
  //     expectedResponse.put(
  //         "failedDocument",
  //         "sample.pdf"); // Replace "sample.pdf" with the actual document name used in the test
  //     assertEquals(expectedResponse.toString(), actualResponse.toString());
  //   } finally {
  //     mockWebServer.shutdown();
  //   }
  // }


   @Test
   public void testDeleteFolder() throws IOException {
     MockWebServer mockWebServer = new MockWebServer();
     mockWebServer.start();
     try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
         Mockito.mockStatic(TokenHandler.class)) {
       String expectedResponse = "200";
       mockWebServer.enqueue(
           new MockResponse()
                   .setResponseCode(200)
               .addHeader("Content-Type", "application/json"));
       String mockUrl = mockWebServer.url("/").toString();
       SDMCredentials sdmCredentials = new SDMCredentials();
       sdmCredentials.setUrl(mockUrl);
       Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

       Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials,"email","subdomain")).thenReturn("mockAccessToken");
       SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

       int actualResponse =
           sdmServiceImpl.deleteDocument("deleteTree", "objectId","email","subdomain");

       assertEquals(200, actualResponse);

     } finally {
       mockWebServer.shutdown();
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
              new MockResponse()
                      .setResponseCode(200)
                      .addHeader("Content-Type", "application/json"));
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials,"email","subdomain")).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      int actualResponse =
              sdmServiceImpl.deleteDocument("delete", "objectId","email","subdomain");

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
      String mockResponseBody =
                     "{\"message\": \"Object Not Found\"}";
             mockWebServer.enqueue(
                      new MockResponse()
                         .setBody(mockResponseBody)
                          .setResponseCode(404) // Assuming 400 Bad Request or a similar client error code
                          .addHeader("Content-Type", "application/json"));
      String mockUrl = mockWebServer.url("/").toString();
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl(mockUrl);
      Mockito.when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      Mockito.when(TokenHandler.getDITokenUsingAuthorities(sdmCredentials,"email","subdomain")).thenReturn("mockAccessToken");
      SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();

      int actualResponse =
              sdmServiceImpl.deleteDocument("delete", "ewdwe","email","subdomain");

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
      try (MockedStatic<TokenHandler> tokenHandlerMockedStatic = Mockito.mockStatic(TokenHandler.class)) {
        tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(sdmCredentials);
        tokenHandlerMockedStatic.when(() -> TokenHandler.getDITokenUsingAuthorities(sdmCredentials, userEmail, subdomain))
                .thenThrow(new IOException("Could not delete the document"));

        // Since the exception is thrown before OkHttpClient is used, no need to mock httpClient behavior.
        SDMServiceImpl sdmServiceImpl = new SDMServiceImpl();
        // Assert exception
        IOException thrown = assertThrows(IOException.class, () -> {
          // Call the method under test
          sdmServiceImpl.deleteDocument(cmisaction, objectId, userEmail, subdomain);
        });

        // Verify the exception message
        assertEquals("Could not delete the document", thrown.getMessage());
      }
    }
  }

