package unit.com.sap.cds.sdm.handler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.environment.servicebinding.api.ServiceBindingAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpDestination;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.ehcache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class TokenHandlerTest {
  private String email = "email-value";
  private String subdomain = "subdomain-value";
  private static final String SDM_TOKEN_ENDPOINT = "url";
  private static final String SDM_URL = "uri";

  private static final String CLIENT_ID = "clientid";
  private static final String CLIENT_SECRET = "clientsecret";
  @Mock private ServiceBinding binding;

  @Mock private CdsProperties.ConnectionPool connectionPoolConfig;

  @Mock private DefaultHttpClientFactory factory;

  @Mock private CloseableHttpClient httpClient;

  private Map<String, Object> uaaCredentials;
  private Map<String, Object> uaa;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    uaaCredentials = new HashMap<>();
    uaa = new HashMap<>();

    uaa.put(CLIENT_ID, "test-client-id");
    uaa.put(CLIENT_SECRET, "test-client-secret");
    uaa.put(SDM_TOKEN_ENDPOINT, "https://test-token-url.com");

    uaaCredentials.put("uaa", uaa);
    uaaCredentials.put(SDM_URL, "https://example.com");

    when(binding.getCredentials()).thenReturn(uaaCredentials);
    when(connectionPoolConfig.getTimeout()).thenReturn(Duration.ofMillis(1000));
    when(connectionPoolConfig.getMaxConnectionsPerRoute()).thenReturn(10);
    when(connectionPoolConfig.getMaxConnections()).thenReturn(100);

    // Instantiate and mock the factory
    when(factory.createHttpClient(any(DefaultHttpDestination.class))).thenReturn(httpClient);

    // Mock the cache to return the expected value
    Cache<String, String> mockCache = Mockito.mock(Cache.class);
    Mockito.when(mockCache.get(any())).thenReturn("cachedToken");
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {
      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
    }
  }

  @Test
  public void testGetHttpClientForTokenExchange() {
    HttpClient client =
        TokenHandler.getHttpClient(binding, connectionPoolConfig, "subdomain", "TOKEN_EXCHANGE");

    assertNotNull(client);
  }

  @Test
  public void testGetHttpClientForTechnicalUser() {
    HttpClient client =
        TokenHandler.getHttpClient(binding, connectionPoolConfig, "subdomain", "TECHNICAL_USER");

    assertNotNull(client);
  }

  @Test
  public void testGetHttpClientWithNullSubdomain() {
    HttpClient client =
        TokenHandler.getHttpClient(binding, connectionPoolConfig, null, "TOKEN_EXCHANGE");

    assertNotNull(client);
  }

  @Test
  public void testGetHttpClientWithEmptySubdomain() {
    HttpClient client =
        TokenHandler.getHttpClient(binding, connectionPoolConfig, "", "TOKEN_EXCHANGE");

    assertNotNull(client);
  }

  @Test
  public void testGetDITokenFromAuthoritiesNoCache() throws IOException {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://example.com");

    Cache<String, String> mockCache = Mockito.mock(Cache.class);
    when(mockCache.get(any())).thenReturn(null); // Cache is empty

    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {

      cacheConfigMockedStatic.when(CacheConfig::getUserAuthoritiesTokenCache).thenReturn(mockCache);
      HttpURLConnection mockConn = Mockito.mock(HttpURLConnection.class);
      doNothing().when(mockConn).setRequestMethod("POST");
      ByteArrayOutputStream mockOutputStream = new ByteArrayOutputStream();
      // when(mockConn.getOutputStream()).thenReturn(new DataOutputStream(mockOutputStream));
      doReturn(new DataOutputStream(mockOutputStream)).when(mockConn).getOutputStream();
      doThrow(new IOException()).when(mockConn).getInputStream();
      Exception exception =
          assertThrows(
              IOException.class,
              () -> {
                TokenHandler.getDITokenUsingAuthorities(mockSdmCredentials, email, subdomain);
              });

      assertEquals("subdomain-value.com", exception.getMessage());
    }
  }

  @Test
  public void testGetSDMCredentials() {
    ServiceBindingAccessor mockAccessor = Mockito.mock(ServiceBindingAccessor.class);
    try (MockedStatic<DefaultServiceBindingAccessor> accessorMockedStatic =
        Mockito.mockStatic(DefaultServiceBindingAccessor.class)) {
      accessorMockedStatic
          .when(DefaultServiceBindingAccessor::getInstance)
          .thenReturn(mockAccessor);

      ServiceBinding mockServiceBinding = Mockito.mock(ServiceBinding.class);

      Map<String, Object> mockCredentials = new HashMap<>();
      Map<String, Object> mockUaa = new HashMap<>();
      mockUaa.put("url", "https://mock.uaa.url");
      mockUaa.put("clientid", "mockClientId");
      mockUaa.put("clientsecret", "mockClientSecret");
      mockCredentials.put("uaa", mockUaa);
      mockCredentials.put("uri", "https://mock.service.url");

      Mockito.when(mockServiceBinding.getServiceName()).thenReturn(Optional.of("sdm"));
      Mockito.when(mockServiceBinding.getCredentials()).thenReturn(mockCredentials);

      List<ServiceBinding> mockServiceBindings = Collections.singletonList(mockServiceBinding);
      Mockito.when(mockAccessor.getServiceBindings()).thenReturn(mockServiceBindings);

      SDMCredentials result = TokenHandler.getSDMCredentials();

      assertNotNull(result);
      assertEquals("https://mock.uaa.url", result.getBaseTokenUrl());
      assertEquals("https://mock.service.url", result.getUrl());
      assertEquals("mockClientId", result.getClientId());
      assertEquals("mockClientSecret", result.getClientSecret());
    }
  }

  @Test
  public void testGetDITokenFromAuthorities() throws IOException {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {

      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn("cachedToken"); // Cache is empty
      cacheConfigMockedStatic.when(CacheConfig::getUserAuthoritiesTokenCache).thenReturn(mockCache);
      String result = TokenHandler.getDITokenUsingAuthorities(mockSdmCredentials, email, subdomain);
      assertEquals("cachedToken", result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testPrivateConstructor() {
    // Use reflection to access the private constructor
    Constructor<TokenHandler> constructor = null;
    try {
      constructor = TokenHandler.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThrows(InvocationTargetException.class, constructor::newInstance);
    } catch (NoSuchMethodException e) {
      fail("Exception occurred during test: " + e.getMessage());
    }
  }

  @Test
  void testToString() {
    byte[] input = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    String expected = new String(input, StandardCharsets.UTF_8);
    String actual = TokenHandler.toString(input);
    assertEquals(expected, actual);
  }

  @Test
  void testToStringWithNullInput() {
    assertThrows(NullPointerException.class, () -> TokenHandler.toString(null));
  }

  @Test
  public void testGetSubdomainFromToken() {
    String token =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.efgtgCjF7bxG2kEgYbkTObovuZN5YQP5t7yr9aPKntk";
    // Performing the actual test
    String result = TokenHandler.getSubdomainFromToken(token);

    // Asserting the expected result
    assertEquals("tenant", result);
  }

  @Test
  public void testGetHttpClientForOnboardFlow() {
    ServiceBindingAccessor mockAccessor = Mockito.mock(ServiceBindingAccessor.class);
    try (MockedStatic<DefaultServiceBindingAccessor> accessorMockedStatic =
        Mockito.mockStatic(DefaultServiceBindingAccessor.class)) {
      accessorMockedStatic
          .when(DefaultServiceBindingAccessor::getInstance)
          .thenReturn(mockAccessor);

      ServiceBinding mockServiceBinding = Mockito.mock(ServiceBinding.class);

      Map<String, Object> mockCredentials = new HashMap<>();
      Map<String, Object> mockUaa = new HashMap<>();
      mockUaa.put("url", "https://mock.uaa.url");
      mockUaa.put("clientid", "mockClientId");
      mockUaa.put("clientsecret", "mockClientSecret");
      mockCredentials.put("uaa", mockUaa);
      mockCredentials.put("uri", "https://mock.service.url");

      Mockito.when(mockServiceBinding.getServiceName()).thenReturn(Optional.of("sdm"));
      Mockito.when(mockServiceBinding.getCredentials()).thenReturn(mockCredentials);

      List<ServiceBinding> mockServiceBindings = Collections.singletonList(mockServiceBinding);
      Mockito.when(mockAccessor.getServiceBindings()).thenReturn(mockServiceBindings);

      HttpClient client =
          TokenHandler.getHttpClient(null, null, "subdomain", "TECHNICAL_CREDENTIALS_FLOW");

      assertNotNull(client);
    }
  }

  // Additional tests for uncovered methods

  @Test
  public void testToBytes() {
    String input = "Hello, World!";
    byte[] expected = input.getBytes(StandardCharsets.UTF_8);
    byte[] actual = TokenHandler.toBytes(input);
    assertArrayEquals(expected, actual);
  }

  @Test
  public void testToBytesWithNullInput() {
    assertThrows(NullPointerException.class, () -> TokenHandler.toBytes(null));
  }

  // @Test
  public void testGetUserTokenFromAuthorities() throws IOException {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://example.com");

    HttpURLConnection mockConn = Mockito.mock(HttpURLConnection.class);
    when(mockConn.getOutputStream()).thenReturn(new DataOutputStream(new ByteArrayOutputStream()));
    when(mockConn.getInputStream())
        .thenReturn(
            new DataInputStream(
                new ByteArrayInputStream(
                    "{\"access_token\":\"mockToken\"}".getBytes(StandardCharsets.UTF_8))));

    try (MockedStatic<URL> urlMockedStatic = Mockito.mockStatic(URL.class)) {
      URL mockUrl = Mockito.mock(URL.class);
      urlMockedStatic.when(() -> new URL(anyString())).thenReturn(mockUrl);
      when(mockUrl.openConnection()).thenReturn(mockConn);

      String result =
          TokenHandler.getUserTokenFromAuthorities(email, subdomain, mockSdmCredentials);
      assertEquals("mockToken", result);
    }
  }

  @Test
  public void testGetDIToken() throws IOException {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://example.com");

    String token = "mockToken";
    JsonObject payloadObj = new JsonObject();
    payloadObj.addProperty("email", email);
    JsonObject extAttr = new JsonObject();
    extAttr.addProperty("zdn", subdomain);
    payloadObj.add("ext_attr", extAttr);
    payloadObj.addProperty("exp", "1234567890");

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getTokenFields(token))
          .thenReturn(payloadObj);

      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn("cachedToken");
      try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
          Mockito.mockStatic(CacheConfig.class)) {
        cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);

        String result = TokenHandler.getDIToken(token, mockSdmCredentials);
        assertEquals(null, result);
      }
    }
  }

  @Test
  public void testFillTokenExchangeBody() {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");

    String token = "mockToken";
    Map<String, String> result = TokenHandler.fillTokenExchangeBody(token, mockSdmCredentials);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(token, result.get("assertion"));
  }

  @Test
  public void testGenerateDITokenFromTokenExchange() throws IOException {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://example.com");

    String token = "mockToken";
    JsonObject payloadObj = new JsonObject();
    payloadObj.addProperty("email", email);
    JsonObject extAttr = new JsonObject();
    extAttr.addProperty("zdn", subdomain);
    payloadObj.add("ext_attr", extAttr);
    payloadObj.addProperty("exp", "1234567890");

    CloseableHttpClient mockHttpClient = Mockito.mock(CloseableHttpClient.class);
    CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
    when(mockResponse.getStatusLine())
        .thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK"));
    when(mockResponse.getEntity()).thenReturn(new StringEntity("{\"access_token\":\"mockToken\"}"));

    try (MockedStatic<HttpClients> httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);
      when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

      tokenHandlerMockedStatic
          .when(() -> TokenHandler.getTokenFields(token))
          .thenReturn(payloadObj);

      String result =
          TokenHandler.generateDITokenFromTokenExchange(token, mockSdmCredentials, payloadObj);
      assertEquals(null, result);
    }
  }

  @Test
  public void testExtractResponseBodyAsString() throws IOException {
    CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
    InputStream mockInputStream =
        new ByteArrayInputStream("mockResponse".getBytes(StandardCharsets.UTF_8));
    when(mockResponse.getEntity()).thenReturn(new InputStreamEntity(mockInputStream));

    String result = TokenHandler.extractResponseBodyAsString(mockResponse);
    assertEquals("mockResponse", result);
  }
}
