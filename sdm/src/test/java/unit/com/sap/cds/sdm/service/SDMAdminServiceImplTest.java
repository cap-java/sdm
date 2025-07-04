package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sap.cds.feature.mt.lib.subscription.ServiceBinding;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.Repository;
import com.sap.cds.sdm.model.RepositoryParams;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMAdminService;
import com.sap.cds.sdm.service.SDMAdminServiceImpl;
import com.sap.cds.services.ServiceException;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBindingAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("https://example.com/");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(tokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
        .thenReturn(httpClient);
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
    sdmCredentials.setUrl("https://example.com/");
    when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);
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
    assertEquals("Repository <repoid> Offboarded", result);
    verify(httpClient, atLeastOnce()).execute(any());
  }

  //  @Test
  //  public void testOffboardRepository_subdomainnull() throws Exception {
  //    String subdomain = null;
  //    SDMCredentials sdmCredentials = new SDMCredentials();
  //    sdmCredentials.setBaseTokenUrl("https://subdomain.example.com/oauth/token");
  //    sdmCredentials.setClientId("clientID");
  //    sdmCredentials.setClientSecret("clientSecret");
  //    sdmCredentials.setUrl("url");
  //    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
  //    CloseableHttpResponse mockGetResponse = mock(CloseableHttpResponse.class);
  //    CloseableHttpResponse mockDeleteResponse = mock(CloseableHttpResponse.class);
  //    HttpEntity mockGetEntity = mock(HttpEntity.class);
  //    HttpEntity mockDeleteEntity = mock(HttpEntity.class);
  //
  //    String json =
  //        """
  // {
  //  "repoAndConnectionInfos": [
  //    {
  //      "repository": {
  //        "externalId": "repoid",
  //        "id": "123"
  //      }
  //    }
  //  ]
  // }
  // """;
  //
  //    InputStream getInputStream = new
  // ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  //    InputStream deleteInputStream =
  //        new ByteArrayInputStream(
  //            "{\"message\":\"Repository with id:123
  // deleted\"}".getBytes(StandardCharsets.UTF_8));
  //
  //    when(mockGetResponse.getStatusLine()).thenReturn(statusLine);
  //    when(statusLine.getStatusCode()).thenReturn(200);
  //    when(mockGetResponse.getEntity()).thenReturn(mockGetEntity);
  //    when(mockGetEntity.getContent()).thenReturn(getInputStream);
  //
  //    when(mockDeleteResponse.getStatusLine()).thenReturn(statusLine);
  //    when(mockDeleteResponse.getEntity()).thenReturn(mockDeleteEntity);
  //    when(mockDeleteEntity.getContent()).thenReturn(deleteInputStream);
  //
  //    // Use class-level mocked httpClient here
  //    when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
  //    when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);
  //
  //    String result = sdmAdminService.offboardRepository(subdomain);
  //    assertNotNull(result);
  //    assertEquals("Repository <repoid> Offboarded", result);
  //    verify(httpClient, atLeastOnce()).execute(any());
  //  }

  @Test
  public void testOffboardRepository_subdomainnull() throws Exception {
    String subdomain = null;

    // Step 1: Create fake UAA credentials map
    Map<String, Object> uaa = new HashMap<>();
    uaa.put("url", "https://subdomain.example.com/oauth/token");
    uaa.put("clientid", "clientID");
    uaa.put("clientsecret", "clientSecret");

    Map<String, Object> uaaCredentials = new HashMap<>();
    uaaCredentials.put("uaa", uaa);
    uaaCredentials.put("uri", "url");

    // Step 2: Mock ServiceBinding to return credentials
    ServiceBinding mockBinding = mock(ServiceBinding.class);
    when(mockBinding.getCredentials()).thenReturn(uaaCredentials);

    // Step 3: Mock DefaultServiceBindingAccessor instance and static call
    List<com.sap.cloud.environment.servicebinding.api.ServiceBinding> serviceBindings =
        new ArrayList<>();
    serviceBindings.add((com.sap.cloud.environment.servicebinding.api.ServiceBinding) mockBinding);
    ServiceBindingAccessor mockAccessor = mock(ServiceBindingAccessor.class);
    when(mockAccessor.getServiceBindings()).thenReturn(serviceBindings);

    try (MockedStatic<DefaultServiceBindingAccessor> mockedStatic =
        Mockito.mockStatic(DefaultServiceBindingAccessor.class)) {
      mockedStatic.when(DefaultServiceBindingAccessor::getInstance).thenReturn(mockAccessor);

      // Step 4: HTTP mocks
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

      when(httpClient.execute(any(HttpGet.class))).thenReturn(mockGetResponse);
      when(httpClient.execute(any(HttpDelete.class))).thenReturn(mockDeleteResponse);

      // Step 5: Invoke and verify
      String result = sdmAdminService.offboardRepository(subdomain);

      assertNotNull(result);
      assertEquals("Repository <repoid> Offboarded", result);
      verify(httpClient, atLeastOnce()).execute(any());
    }
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
    assertEquals("Repository <repoid> Offboarded", result);
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

    assertTrue(exception.getMessage().contains("Error in offboarding"));
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

    assertTrue(exception.getMessage().contains("Error in offboarding"));
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

    assertTrue(
        exception
            .getMessage()
            .contains(
                "Unrecognized token 'repoid': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')"));
  }
}
