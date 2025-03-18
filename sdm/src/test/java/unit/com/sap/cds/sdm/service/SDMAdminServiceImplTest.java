package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class SDMAdminServiceImplTest {

  private SDMAdminService sdmAdminService;

  @Mock private CloseableHttpClient httpClient;

  @Mock private CloseableHttpResponse httpResponse;
  StatusLine statusLine;
  HttpEntity entity;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmAdminService = new SDMAdminServiceImpl();
    statusLine = mock(StatusLine.class);
    entity = mock(HttpEntity.class);
  }

  @Test
  public void testOnboardRepository_success()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class); ) {
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("https://example.com/");
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSDMCredentials())
          .thenReturn(sdmCredentials);
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
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
  }

  @Test
  public void testOnboardRepository_failure()
      throws UnsupportedEncodingException, JsonProcessingException, IOException {
    // Arrange
    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class); ) {
      SDMCredentials sdmCredentials = new SDMCredentials();
      sdmCredentials.setUrl("https://example.com/");
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getSDMCredentials())
          .thenReturn(sdmCredentials);
      tokenHandlerMockedStatic
          .when(
              () ->
                  TokenHandler.getHttpClient(any(), any(), any(), eq("TECHNICAL_CREDENTIALS_FLOW")))
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
  }

  @Test
  public void testOffboardRepository_returnsNull() {
    // Arrange
    String subdomain = "testSubdomain";

    // Act
    String result = sdmAdminService.offboardRepository(subdomain);

    // Assert
    assertNull(result, "Expected offboardRepository to return null");
  }

  @Test
  public void testRestoreRepository_returnsNull() {
    // Arrange
    String subdomain = "testSubdomain";

    // Act
    String result = sdmAdminService.restoreRepository(subdomain);

    // Assert
    assertNull(result, "Expected restoreRepository to return null");
  }
}
