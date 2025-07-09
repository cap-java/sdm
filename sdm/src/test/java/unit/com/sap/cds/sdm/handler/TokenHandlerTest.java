package unit.com.sap.cds.sdm.handler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.environment.servicebinding.api.ServiceBindingAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpDestination;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
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
