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
  private String email = "email-value";
  private String subdomain = "subdomain-value";

  @Test
  public void testGetDIToken() throws IOException {
    JsonObject expected = new JsonObject();
    expected.addProperty(
        "email", "john.doe@example.com"); // Correct the property name as expected in the method
    expected.addProperty(
        "exp", "1234567890"); // Correct the property name as expected in the method
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    expected.add("ext_attr", jsonObject);
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
        Mockito.mockStatic(CacheConfig.class)) {

      Cache<String, String> mockCache = Mockito.mock(Cache.class);
      Mockito.when(mockCache.get(any())).thenReturn("cachedToken"); // Cache is empty
      cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
      String result =
          TokenHandler.getDIToken(
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.efgtgCjF7bxG2kEgYbkTObovuZN5YQP5t7yr9aPKntk",
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
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    mockPayload.add("ext_attr", jsonObject);
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
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJ6aWQiOiJ0ZW5hbnQtaWQtdmFsdWUiLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.MHwowSANGLEUQojz65Y7EVFC_bvojDL8guXA5kjuKuw",
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
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    mockPayload.add("ext_attr", jsonObject);
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
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.efgtgCjF7bxG2kEgYbkTObovuZN5YQP5t7yr9aPKntk",
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
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    mockPayload.add("ext_attr", jsonObject);
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
              "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJ6aWQiOiJ0ZW5hbnQtaWQtdmFsdWUiLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.MHwowSANGLEUQojz65Y7EVFC_bvojDL8guXA5kjuKuw",
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

  //  @Test
  //  public void testGetDITokenFromAuthoritiesNoCache() throws IOException {
  //    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //    when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
  //    when(mockSdmCredentials.getClientSecret()).thenReturn("mockClientSecret");
  //    when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");
  //
  //    Cache<String, String> mockCache = Mockito.mock(Cache.class);
  //    when(mockCache.get(any())).thenReturn(null); // Cache is empty
  //
  //    try (MockedStatic<CacheConfig> cacheConfigMockedStatic =
  //                 Mockito.mockStatic(CacheConfig.class)) {
  //
  // cacheConfigMockedStatic.when(CacheConfig::getUserAuthoritiesTokenCache).thenReturn(mockCache);
  //      HttpURLConnection mockConn = Mockito.mock(HttpURLConnection.class);
  //      try (MockedConstruction<URL> mockedUrl =
  //                   Mockito.mockConstruction(
  //                           URL.class,
  //                           (mock, context) -> {
  //                             when(mock.openConnection()).thenReturn(mockConn);
  //                           })) {
  //        doNothing().when(mockConn).setRequestMethod("POST");
  //        ByteArrayOutputStream mockOutputStream = new ByteArrayOutputStream();
  //        doReturn(new DataOutputStream(mockOutputStream)).when(mockConn).getOutputStream();
  //        doReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()))
  //                .when(mockConn)
  //                .getInputStream();
  //        doReturn(HttpURLConnection.HTTP_OK).when(mockConn).getResponseCode();
  //        ObjectMapper mockMapper = Mockito.mock(ObjectMapper.class);
  //        JsonNode mockJsonNode = Mockito.mock(JsonNode.class);
  //        when(mockMapper.readValue(any(String.class),
  // eq(JsonNode.class))).thenReturn(mockJsonNode);
  //        when(mockJsonNode.get("access_token")).thenReturn(mockJsonNode);
  //        when(mockJsonNode.asText()).thenReturn("mockedToken");
  //        String result =
  //                TokenHandler.getDITokenUsingAuthorities(mockSdmCredentials, email, subdomain);
  //        assertEquals("mockedToken", result);
  //        verify(mockCache).put("clientCredentialsToken", "mockedToken");
  //      }
  //    }
  // }
}
