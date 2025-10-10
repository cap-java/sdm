package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.Repository;
import com.sap.cds.sdm.model.RepositoryParams;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMAdminService;
import com.sap.cds.sdm.service.SDMAdminServiceImpl;
import com.sap.cds.services.ServiceException;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class SDMAdminServiceImplTest {

  @Mock private CloseableHttpClient httpClient;
  @Mock private CloseableHttpResponse httpResponse;
  @Mock private SDMCredentials mockCredentials;

  @Mock
  private DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder mockHttpClientFactoryBuilder;

  @Mock private DefaultHttpClientFactory mockHttpClientFactory;

  StatusLine statusLine;
  HttpEntity entity;

  private SDMAdminService sdmAdminService;

  private MockedStatic<TokenHandler> tokenHandlerMockedStatic;
  private MockedStatic<HttpClients> httpClientsMockedStatic;
  private MockedStatic<DefaultHttpClientFactory> defaultHttpClientFactoryMockedStatic;
  @Mock private TokenHandler tokenHandler;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    tokenHandler = mock(TokenHandler.class);
    tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
    tokenHandlerMockedStatic.when(TokenHandler::getTokenHandlerInstance).thenReturn(tokenHandler);

    sdmAdminService = new SDMAdminServiceImpl();
    statusLine = mock(StatusLine.class);
    entity = mock(HttpEntity.class);

    // Mock HttpClients static method
    httpClientsMockedStatic = mockStatic(HttpClients.class);
    httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(httpClient);

    // Mock DefaultHttpClientFactory static builder
    defaultHttpClientFactoryMockedStatic = mockStatic(DefaultHttpClientFactory.class);
    defaultHttpClientFactoryMockedStatic
        .when(DefaultHttpClientFactory::builder)
        .thenReturn(mockHttpClientFactoryBuilder);

    // Setup the builder method chain
    when(mockHttpClientFactoryBuilder.timeoutMilliseconds(anyInt()))
        .thenReturn(mockHttpClientFactoryBuilder);
    when(mockHttpClientFactoryBuilder.maxConnectionsPerRoute(anyInt()))
        .thenReturn(mockHttpClientFactoryBuilder);
    when(mockHttpClientFactoryBuilder.maxConnectionsTotal(anyInt()))
        .thenReturn(mockHttpClientFactoryBuilder);
    when(mockHttpClientFactoryBuilder.build()).thenReturn(mockHttpClientFactory);

    // Finally, mock createHttpClient
    when(mockHttpClientFactory.createHttpClient(any())).thenReturn(httpClient);
  }

  @AfterEach
  public void tearDown() {
    if (tokenHandlerMockedStatic != null) {
      tokenHandlerMockedStatic.close();
    }
    if (httpClientsMockedStatic != null) {
      httpClientsMockedStatic.close();
    }
    if (defaultHttpClientFactoryMockedStatic != null) {
      defaultHttpClientFactoryMockedStatic.close();
    }
  }

  @Test
  public void testOnboardRepository_success()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
    when(mockSdmCredentials.getUrl()).thenReturn("https://example.com/");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://auth.example.com/");
    when(mockSdmCredentials.getClientId()).thenReturn("client-id");
    when(mockSdmCredentials.getClientSecret()).thenReturn("client-secret");

    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");
    repository.setDescription("TestRepository");
    repository.setExternalId("TEST_REPO");
    repository.setIsVirusScanEnabled(true);
    repository.setIsVersionEnabled(true);
    repository.setHashAlgorithms("SHA-256");
    RepositoryParams repositoryParam = new RepositoryParams();
    repositoryParam.setParamName("fileExtensions");
    JsonObject fileExtensionsValue = new JsonObject();
    fileExtensionsValue.addProperty("type", "allow");
    fileExtensionsValue.add(
        "list", new Gson().toJsonTree(new String[] {"png", "pdf", "jpg", "txt"}));

    // Convert the nested JSON object to a JSON string
    String jsonParamValue = fileExtensionsValue.toString();

    // Create the outer JSON object
    JsonObject repositoryParamVal = new JsonObject();
    repositoryParamVal.addProperty("paramName", "fileExtensions");
    repositoryParamVal.addProperty("paramValue", jsonParamValue);

    // Serialize the entire object
    String finalJson = new Gson().toJson(repositoryParam);
    repositoryParam.setParamValue(finalJson);
    List<RepositoryParams> repositoryParams = new ArrayList<>();
    repositoryParams.add(repositoryParam);
    repository.setRepositoryParams(repositoryParams);
    JSONObject root = new JSONObject();
    root.put("id", "TEST_REPO");
    InputStream inputStream = new ByteArrayInputStream(root.toString().getBytes());
    when(entity.getContent()).thenReturn(inputStream);
    when(httpClient.execute(any())).thenReturn(httpResponse);

    when(httpResponse.getEntity()).thenReturn(entity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(httpResponse.getStatusLine().getStatusCode()).thenReturn(200);

    // Act
    String result = sdmAdminService.onboardRepository(repository);

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("TestRepository"));
    assertTrue(result.contains("TEST_REPO"));

    verify(httpClient).execute(any());
  }

  @Test
  public void testOnboardRepository_failure()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    // Arrange
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");
    repository.setDescription("TestRepository");
    when(httpClient.execute(any())).thenThrow(new IOException("Http error"));

    // Act & Assert
    assertThrows(
        ServiceException.class,
        () -> {
          sdmAdminService.onboardRepository(repository);
        });

    verify(httpClient).execute(any());
  }

  @Test
  public void testOffboardRepository_success() throws Exception {
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);

    String json =
        """
{
  "repoAndConnectionInfos": [
    {
      "repository": {
        "externalId": "repoid",
        "id": "123"
      }
    }
  ]
}
""";

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"message\":\"Repository with id:123 deleted\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    // Use class-level mocked httpClient here
    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    String result = sdmAdminService.offboardRepository(subdomain);
    assertNotNull(result);
    assertEquals("Repository 123 Offboarded", result);
    verify(httpClient, atLeastOnce()).execute(any());
  }

  @Test
  public void testOffboardRepository_subdomainnull() throws Exception {
    String subdomain = null;
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);

    String json =
        """
{
  "repoAndConnectionInfos": [
    {
      "repository": {
        "externalId": "some-other-id",
        "id": "123"
      }
    }
  ]
}
""";

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"message\":\"Repository with id:123 deleted\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    // Use class-level mocked httpClient here
    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    String result = sdmAdminService.offboardRepository(subdomain);
    assertNotNull(result);
    assertEquals("Repository with ID repoid not found.", result);
    verify(httpClient, atLeastOnce()).execute(any());
  }

  @Test
  public void testOffboardRepository_subdomainempty() throws Exception {
    String subdomain = "";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);

    String json =
        """
{
  "repoAndConnectionInfos": [
    {
      "repository": {
        "externalId": "some-other-id",
        "id": "123"
      }
    }
  ]
}
""";

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"message\":\"Repository with id:123 deleted\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    // Use class-level mocked httpClient here
    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    String result = sdmAdminService.offboardRepository(subdomain);
    assertNotNull(result);
    assertEquals("Repository with ID repoid not found.", result);
    verify(httpClient, atLeastOnce()).execute(any());
  }

  @Test
  public void testOffboardRepository_getRequestFails_throwsException() throws Exception {
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");

    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Simulate GET request failure
    when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("GET failed"));

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              sdmAdminService.offboardRepository(subdomain);
            });
    assertTrue(exception.getMessage().contains("Error while fetching repository ID."));
  }

  @Test
  public void testOffboardRepository_deleteRequestFails_throwsException() throws Exception {
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");

    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);

    String json =
        """
        {
          "repoAndConnectionInfos": [
            {
              "repository": {
                "externalId": "repoid",
                "id": "123"
              }
            }
          ]
        }
        """;

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    // GET works fine
    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);

    // DELETE throws exception
    when(httpClient.execute(any(HttpDelete.class))).thenThrow(new IOException("DELETE failed"));

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              sdmAdminService.offboardRepository(subdomain);
            });

    assertTrue(exception.getMessage().contains("Error while offboarding repository."));
  }

  @Test
  public void testOffboardRepository_invalidRepo_throwsException() throws Exception {
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);

    String json = "repoid";

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"message\":\"Repository with id:123 deleted\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    // Use class-level mocked httpClient here
    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              sdmAdminService.offboardRepository(subdomain);
            });

    assertTrue(exception.getMessage().contains("Unexpected error while fetching repository ID."));
  }

  @Test
  public void testOnboardRepository_nullRepository() {
    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              sdmAdminService.onboardRepository(null);
            });

    assertEquals("Repository object cannot be null.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_nullCredentials() throws Exception {
    // Arrange
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepo");

    when(tokenHandler.getSDMCredentials()).thenReturn(null);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_nullCredentialsUrl() throws Exception {
    // Arrange
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepo");

    SDMCredentials nullUrlCredentials = new SDMCredentials();
    nullUrlCredentials.setUrl(null);
    when(tokenHandler.getSDMCredentials()).thenReturn(nullUrlCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_credentialsException() throws Exception {
    // Arrange
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepo");

    when(tokenHandler.getSDMCredentials()).thenThrow(new RuntimeException("Credentials error"));

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_nullHttpClient() throws Exception {
    // Arrange
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepo");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("https://example.com/");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(null);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Error while creating HTTP client.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_httpClientException() throws Exception {
    // Arrange
    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepo");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("https://example.com/");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenThrow(new RuntimeException("HTTP client error"));

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Error while creating HTTP client.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_repositoryAlreadyExists()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    // Arrange
    SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
    when(mockSdmCredentials.getUrl()).thenReturn("https://example.com/");
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");

    // Mock response for repository already exists (409 status)
    // The implementation checks for REPOSITORY_ID which comes from env var, so use "repoid" if not
    // set
    String responseBody = "repoid already exists";
    InputStream inputStream = new ByteArrayInputStream(responseBody.getBytes());
    when(entity.getContent()).thenReturn(inputStream);
    when(httpClient.execute(any())).thenReturn(httpResponse);
    when(httpResponse.getEntity()).thenReturn(entity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(httpResponse.getStatusLine().getStatusCode()).thenReturn(409);

    // Act
    String result = sdmAdminService.onboardRepository(repository);

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("TestRepository"));
    assertTrue(result.contains("already exists"));
  }

  @Test
  public void testOnboardRepository_responseWithoutId()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    // Arrange
    SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
    when(mockSdmCredentials.getUrl()).thenReturn("https://example.com/");
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");

    // Mock response without ID field
    JSONObject root = new JSONObject();
    root.put("message", "Created");
    InputStream inputStream = new ByteArrayInputStream(root.toString().getBytes());
    when(entity.getContent()).thenReturn(inputStream);
    when(httpClient.execute(any())).thenReturn(httpResponse);
    when(httpResponse.getEntity()).thenReturn(entity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(httpResponse.getStatusLine().getStatusCode()).thenReturn(200);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Error in onboarding repository with name TestRepository", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_nullCredentials() {
    // Arrange
    when(tokenHandler.getSDMCredentials()).thenReturn(null);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_nullCredentialsUrl() {
    // Arrange
    SDMCredentials badCredentials = new SDMCredentials();
    badCredentials.setUrl(null);
    badCredentials.setBaseTokenUrl("https://test.com");
    when(tokenHandler.getSDMCredentials()).thenReturn(badCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_nullBaseTokenUrl() {
    // Arrange
    SDMCredentials badCredentials = new SDMCredentials();
    badCredentials.setUrl("https://test.com");
    badCredentials.setBaseTokenUrl(null);
    when(tokenHandler.getSDMCredentials()).thenReturn(badCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_credentialsException() {
    // Arrange
    when(tokenHandler.getSDMCredentials()).thenThrow(new RuntimeException("Creds error"));

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to retrieve SDM credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_nullClientId() {
    // Arrange
    SDMCredentials badCredentials = new SDMCredentials();
    badCredentials.setUrl("https://test.com");
    badCredentials.setBaseTokenUrl("https://token.com");
    badCredentials.setClientId(null);
    badCredentials.setClientSecret("secret");
    when(tokenHandler.getSDMCredentials()).thenReturn(badCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to create client credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_nullClientSecret() {
    // Arrange
    SDMCredentials badCredentials = new SDMCredentials();
    badCredentials.setUrl("https://test.com");
    badCredentials.setBaseTokenUrl("https://token.com");
    badCredentials.setClientId("client");
    badCredentials.setClientSecret(null);
    when(tokenHandler.getSDMCredentials()).thenReturn(badCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to create client credentials.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_httpClientCreationException() {
    // Arrange
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock the factory to return null HttpClient
    when(mockHttpClientFactory.createHttpClient(any())).thenReturn(null);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Error while creating HTTP client.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_httpClientFactoryException() {
    // Arrange
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock the factory to throw exception
    when(mockHttpClientFactory.createHttpClient(any()))
        .thenThrow(new RuntimeException("Factory error"));

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Error while creating HTTP client.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_subdomainReplacementException() {
    // Arrange
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("invalid-url"); // Invalid URL format
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository("subdomain");
            });

    assertEquals("Failed to replace subdomain in base token URL.", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_repositoryDetailsException() throws Exception {
    // Arrange - Test coverage for setting repository details exception
    Repository repository = mock(Repository.class);
    when(repository.getSubdomain()).thenReturn("testSubdomain");
    when(repository.getDisplayName()).thenReturn("TestRepo");
    // Mock repository to throw exception when setExternalId is called
    doThrow(new RuntimeException("Repository error")).when(repository).setExternalId(any());

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("https://example.com/");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Failed to set repository details.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_emptyRepositoryId() throws Exception {
    // Arrange - Test coverage for empty repository ID scenario
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);

    // Mock response with empty repository ID
    String json = """
        {
          "repoAndConnectionInfos": []
        }
        """;

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);

    // Act
    String result = sdmAdminService.offboardRepository(subdomain);

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("not found"));
  }

  @Test
  public void testGetRepositoryId_nonArrayResponse() throws Exception {
    // Arrange - Test coverage for non-array repoAndConnectionInfos
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);

    // Mock response with single object (not array) for repoAndConnectionInfos
    String json =
        """
        {
          "repoAndConnectionInfos": {
            "repository": {
              "externalId": "repoid",
              "id": "123"
            }
          }
        }
        """;

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream("Success".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    // Act
    String result = sdmAdminService.offboardRepository(subdomain);

    // Assert
    assertNotNull(result);
    assertTrue(result.contains("123 Offboarded"));
  }

  @Test
  public void testOnboardRepository_conflictStatusWithoutMessage() throws Exception {
    // Arrange - Test coverage for 409 status but without the expected message format
    SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
    when(mockSdmCredentials.getUrl()).thenReturn("https://example.com/");
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");

    // Mock response with 409 status but different message format
    String responseBody = "Some other error message";
    InputStream inputStream = new ByteArrayInputStream(responseBody.getBytes());
    when(entity.getContent()).thenReturn(inputStream);
    when(httpClient.execute(any())).thenReturn(httpResponse);
    when(httpResponse.getEntity()).thenReturn(entity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(httpResponse.getStatusLine().getStatusCode()).thenReturn(409);

    // Act - Should not take the "already exists" path
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Error in onboarding repository with name TestRepository", exception.getMessage());
  }

  @Test
  public void testOnboardRepository_responseWithNullId() throws Exception {
    // Arrange - Test coverage for JSON response where id field is null
    SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
    when(mockSdmCredentials.getUrl()).thenReturn("https://example.com/");
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    Repository repository = new Repository();
    repository.setSubdomain("testSubdomain");
    repository.setDisplayName("TestRepository");

    // Mock response with null id field
    JSONObject root = new JSONObject();
    root.put("id", JSONObject.NULL);
    InputStream inputStream = new ByteArrayInputStream(root.toString().getBytes());
    when(entity.getContent()).thenReturn(inputStream);
    when(httpClient.execute(any())).thenReturn(httpResponse);
    when(httpResponse.getEntity()).thenReturn(entity);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(httpResponse.getStatusLine().getStatusCode()).thenReturn(200);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.onboardRepository(repository);
            });

    assertEquals("Error in onboarding repository with name TestRepository", exception.getMessage());
  }

  @Test
  public void testGetRepositoryId_parseException() throws Exception {
    // Arrange - Test coverage for JSON parsing exception in getRepositoryId
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);

    // Mock response with invalid JSON
    String invalidJson = "{ invalid json }";
    InputStream getInputStream =
        new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository(subdomain);
            });

    assertEquals("Unexpected error while fetching repository ID.", exception.getMessage());
  }

  @Test
  public void testOffboardRepository_404StatusCode() throws Exception {
    // Arrange
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);
    StatusLine getStatusLine = mock(StatusLine.class);
    StatusLine deleteStatusLine = mock(StatusLine.class);

    String json =
        """
        {
          "repoAndConnectionInfos": [
            {
              "repository": {
                "externalId": "repoid",
                "id": "123"
              }
            }
          ]
        }
        """;

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"message\":\"Repository not found\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(getStatusLine);
    when(getStatusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(deleteStatusLine);
    when(deleteStatusLine.getStatusCode()).thenReturn(404);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    // Act
    String result = sdmAdminService.offboardRepository(subdomain);

    // Assert
    assertNotNull(result);
    assertEquals("Repository with ID repoid not found.", result);
  }

  @Test
  public void testOffboardRepository_500StatusCode() throws Exception {
    // Arrange
    String subdomain = "subdomain";
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
    sdmCredentials.setClientId("clientID");
    sdmCredentials.setClientSecret("clientSecret");
    sdmCredentials.setUrl("url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
    HttpEntity mockGetEntity = mock(HttpEntity.class);
    HttpEntity mockDeleteEntity = mock(HttpEntity.class);
    StatusLine getStatusLine = mock(StatusLine.class);
    StatusLine deleteStatusLine = mock(StatusLine.class);

    String json =
        """
        {
          "repoAndConnectionInfos": [
            {
              "repository": {
                "externalId": "repoid",
                "id": "123"
              }
            }
          ]
        }
        """;

    InputStream getInputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    InputStream deleteInputStream =
        new ByteArrayInputStream(
            "{\"error\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8));

    when(mockGetResponse.getStatusLine()).thenReturn(getStatusLine);
    when(getStatusLine.getStatusCode()).thenReturn(200);
    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
    when(mockGetEntity.getContent()).thenReturn(getInputStream);

    when(mockDeleteResponse.getStatusLine()).thenReturn(deleteStatusLine);
    when(deleteStatusLine.getStatusCode()).thenReturn(500);
    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);

    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmAdminService.offboardRepository(subdomain);
            });

    assertEquals("Unexpected error while offboarding repository.", exception.getMessage());
  }

  @Test
  public void testConstructorInitialization() {
    // Act
    SDMAdminService newService = new SDMAdminServiceImpl();

    // Assert
    assertNotNull(newService);
  }
}
