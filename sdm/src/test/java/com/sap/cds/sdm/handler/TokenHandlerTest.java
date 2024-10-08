package com.sap.cds.sdm.handler;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.environment.servicebinding.api.ServiceBindingAccessor;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.ehcache.Cache;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class TokenHandlerTest {
  //    @Test
  //    public void testGetTokenFields() {
  //        JsonObject expected = new JsonObject();
  //        expected.addProperty("sub", "1234567890");
  //        expected.addProperty("name", "John Doe");
  //        expected.addProperty("iat", 1516239022);
  //        JsonObject result =
  // TokenHandler.getTokenFields("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
  //        assertEquals(expected, result);
  //    }
  //
  //    @Test
  //    public void testToBytes() {
  //        String input = "Hello, World!";
  //        byte[] expected = "Hello, World!".getBytes(StandardCharsets.UTF_8);
  //        byte[] result = TokenHandler.toBytes(input);
  //        assertArrayEquals(expected, result);
  //    }
  //
  //    @Test
  //    public void testToString() {
  //        byte[] input = "Hello, World!".getBytes(StandardCharsets.UTF_8);
  //        String expected = "Hello, World!";
  //        String result = TokenHandler.toString(input);
  //        assertEquals(expected, result);
  //    }
  //
  //    @Test
  //    public void testGetAccessTokenCache(){
  //        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //        try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
  // Mockito.mockStatic(CacheConfig.class)) {
  //            Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //            Mockito.when(mockCache.get("clientCredentialsToken")).thenReturn("cachedToken");
  //
  // cacheConfigMockedStatic.when(CacheConfig::getClientCredentialsTokenCache).thenReturn(mockCache);
  //            String cachedToken = TokenHandler.getAccessToken(mockSdmCredentials);
  //            assertEquals(cachedToken, "cachedToken");
  //        } catch (ProtocolException e) {
  //            throw new RuntimeException(e);
  //        } catch (IOException e) {
  //            throw new RuntimeException(e);
  //        }
  //    }
  //
  ////    @Test
  ////    public void testGetAccessTokenWhenCacheIsEmpty() throws IOException, ProtocolException {
  ////        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  ////        Mockito.when(mockSdmCredentials.getClientId()).thenReturn("client_id");
  ////        Mockito.when(mockSdmCredentials.getClientSecret()).thenReturn("client_secret");
  ////        Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("http://example.com");
  ////
  ////        try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
  // Mockito.mockStatic(CacheConfig.class)) {
  ////            Cache<String, String> mockCache = Mockito.mock(Cache.class);
  ////            Mockito.when(mockCache.get("clientCredentialsToken")).thenReturn(null); // Cache
  // is empty
  ////
  // cacheConfigMockedStatic.when(CacheConfig::getClientCredentialsTokenCache).thenReturn(mockCache);
  ////
  ////            // Mock Base64 Encoder
  ////            Base64.Encoder encoder = Base64.getEncoder();
  ////            MockedStatic<Base64> mockedBase64 = Mockito.mockStatic(Base64.class);
  ////            mockedBase64.when(Base64::getEncoder).thenReturn(encoder);
  ////
  ////            HttpURLConnection mockConnection = Mockito.mock(HttpURLConnection.class);
  ////
  ////            try (MockedConstruction<URL> mockedUrl = Mockito.mockConstruction(URL.class,
  // (mock, context) -> {
  ////                Mockito.when(mock.openConnection()).thenReturn(mockConnection);
  ////            })) {
  ////                Mockito.doNothing().when(mockConnection).setRequestMethod("POST");
  ////
  // Mockito.doNothing().when(mockConnection).setRequestProperty(Mockito.anyString(),
  // Mockito.anyString());
  ////
  // Mockito.when(mockConnection.getOutputStream()).thenReturn(Mockito.mock(DataOutputStream.class));
  ////
  ////                // Mock the InputStream and BufferedReader correctly
  ////                InputStream mockInputStream = new
  // ByteArrayInputStream("{\"access_token\":\"newGeneratedToken\"}".getBytes(StandardCharsets.UTF_8));
  ////                Mockito.when(mockConnection.getInputStream()).thenReturn(mockInputStream);
  ////
  ////                // Use doNothing() for void methods
  ////                Mockito.doNothing().when(mockCache).put(Mockito.eq("clientCredentialsToken"),
  // Mockito.eq("newGeneratedToken"));
  ////
  ////                String resultToken = TokenHandler.getAccessToken(mockSdmCredentials);
  ////                assertEquals("newGeneratedToken", resultToken);
  ////            }
  ////        }
  ////    }
  //
  @Test
  public void testGetDIToken() {
    JsonObject expected = new JsonObject();
    expected.addProperty(
        "email", "john.doe@example.com"); // Correct the property name as expected in the method
    expected.addProperty(
        "exp", "1234567890"); // Correct the property name as expected in the method

    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {

      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn("cachedToken"); // Cache is empty
      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
      String result =
          TokenHandler.getDIToken(
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
              mockSdmCredentials);
      assertEquals("cachedToken", result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testGetDITokenNoCache() throws IOException {
    JsonObject mockPayload = new JsonObject();
    mockPayload.addProperty("email", "john.doe@example.com");
    mockPayload.addProperty("exp", "1234567890");
    mockPayload.addProperty("zid", "tenant-id-value");

    CloseableHttpClient mockHttpClient = Mockito.mock(CloseableHttpClient.class);
    CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
    HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
    StatusLine mockStatusLine = Mockito.mock(StatusLine.class);

    Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    Mockito.when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    Mockito.when(mockEntity.getContent())
        .thenReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()));
    Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);

    Mockito.when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    Mockito.when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

    try (MockedStatic<HttpClients> httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic =
            Mockito.mockStatic(CacheConfig.class); ) {
      httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn(null); // Cache is empty

      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
      String result =
          TokenHandler.getDIToken(
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwiZXhwIjoiMTIzNDU2Nzg5MCIsInppZCI6InRlbmFudC1pZC12YWx1ZSJ9.qcb3jez9HAjWYC7A-BC51MNgSpbBDMHERNsRg64_LV0",
              mockSdmCredentials);
      assertEquals("mockedToken", result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testGetDITokenNoCacheNoSDMBinding() throws IOException {
    JsonObject mockPayload = new JsonObject();
    mockPayload.addProperty("email", "john.doe@example.com");
    mockPayload.addProperty("exp", "1234567890");
    mockPayload.addProperty("zid", "tenant-id-value");

    CloseableHttpClient mockHttpClient = Mockito.mock(CloseableHttpClient.class);
    CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
    HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
    StatusLine mockStatusLine = Mockito.mock(StatusLine.class);

    Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    Mockito.when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    Mockito.when(mockEntity.getContent())
        .thenReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()));
    Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);

    Mockito.when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    Mockito.when(mockSdmCredentials.getClientId()).thenReturn(null);
    Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

    try (MockedStatic<HttpClients> httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic =
            Mockito.mockStatic(CacheConfig.class); ) {
      httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn(null); // Cache is empty

      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
      String result =
          TokenHandler.getDIToken(
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwiZXhwIjoiMTIzNDU2Nzg5MCIsInppZCI6InRlbmFudC1pZC12YWx1ZSJ9.qcb3jez9HAjWYC7A-BC51MNgSpbBDMHERNsRg64_LV0",
              mockSdmCredentials);
      assertEquals(null, result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testGetDITokenNoCacheStatusCodeError() throws IOException {
    JsonObject mockPayload = new JsonObject();
    mockPayload.addProperty("email", "john.doe@example.com");
    mockPayload.addProperty("exp", "1234567890");
    mockPayload.addProperty("zid", "tenant-id-value");

    CloseableHttpClient mockHttpClient = Mockito.mock(CloseableHttpClient.class);
    CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
    HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
    StatusLine mockStatusLine = Mockito.mock(StatusLine.class);

    Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
    Mockito.when(mockStatusLine.getStatusCode()).thenReturn(123);
    Mockito.when(mockEntity.getContent())
        .thenReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()));
    Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);

    Mockito.when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    Mockito.when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

    try (MockedStatic<HttpClients> httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic =
            Mockito.mockStatic(CacheConfig.class); ) {
      httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn(null); // Cache is empty

      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
      String result =
          TokenHandler.getDIToken(
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwiZXhwIjoiMTIzNDU2Nzg5MCIsInppZCI6InRlbmFudC1pZC12YWx1ZSJ9.qcb3jez9HAjWYC7A-BC51MNgSpbBDMHERNsRg64_LV0",
              mockSdmCredentials);
      assertEquals("mockedToken", result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
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
  public void testGetAccessToken() {
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {
      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn("cachedToken"); // Cache is empty
      cacheConfigMockedStatic
          .when(CacheConfig::getClientCredentialsTokenCache)
          .thenReturn(mockCache);
      String result = TokenHandler.getAccessToken(mockSdmCredentials);
      assertEquals("cachedToken", result); // Adjust based on the expected result
    } catch (OAuth2ServiceException e) {
      throw new RuntimeException(e);
    } catch (ProtocolException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testGetAccessTokenNoCache() throws IOException {
    // Mock SDMCredentials
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

    Cache<String, String> mockCache = Mockito.mock(Cache.class);
    when(mockCache.get(any())).thenReturn(null); // Cache is empty

    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {
      cacheConfigMockedStatic
          .when(CacheConfig::getClientCredentialsTokenCache)
          .thenReturn(mockCache);
      HttpURLConnection mockConn = Mockito.mock(HttpURLConnection.class);
      try (MockedConstruction<URL> mockedUrl =
          Mockito.mockConstruction(
              URL.class,
              (mock, context) -> {
                when(mock.openConnection()).thenReturn(mockConn);
              })) {
        doNothing().when(mockConn).setRequestMethod("POST");
        ByteArrayOutputStream mockOutputStream = new ByteArrayOutputStream();
        doReturn(new DataOutputStream(mockOutputStream)).when(mockConn).getOutputStream();
        doReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()))
            .when(mockConn)
            .getInputStream();
        doReturn(HttpURLConnection.HTTP_OK).when(mockConn).getResponseCode();
        ObjectMapper mockMapper = Mockito.mock(ObjectMapper.class);
        JsonNode mockJsonNode = Mockito.mock(JsonNode.class);
        when(mockMapper.readValue(any(String.class), eq(JsonNode.class))).thenReturn(mockJsonNode);
        when(mockJsonNode.get("access_token")).thenReturn(mockJsonNode);
        when(mockJsonNode.asText()).thenReturn("mockedToken");
        String result = TokenHandler.getAccessToken(mockSdmCredentials);
        assertEquals("mockedToken", result);
        verify(mockCache).put("clientCredentialsToken", "mockedToken");
      }
    }
  }
}
