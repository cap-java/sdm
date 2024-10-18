package com.sap.cds.sdm.handler;

import static com.sap.cloud.security.xsuaa.client.OAuth2TokenServiceConstants.*;
import static com.sap.cloud.security.xsuaa.client.OAuth2TokenServiceConstants.ASSERTION;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.CacheKey;
import com.sap.cds.sdm.caching.TokenCacheKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import com.sap.cloud.security.xsuaa.http.HttpHeaders;
import com.sap.cloud.security.xsuaa.http.MediaType;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenHandler {
  private static final Logger logger = LoggerFactory.getLogger(TokenHandler.class);

  private static final ObjectMapper mapper = new ObjectMapper();

  private TokenHandler() {
    throw new IllegalStateException("TokenHandler class");
  }

  public static byte[] toBytes(String str) {
    return requireNonNull(str).getBytes(StandardCharsets.UTF_8);
  }

  public static String toString(byte[] bytes) {
    return new String(requireNonNull(bytes), StandardCharsets.UTF_8);
  }

  public static SDMCredentials getSDMCredentials() {
    List<ServiceBinding> allServiceBindings =
        DefaultServiceBindingAccessor.getInstance().getServiceBindings();
    // filter for a specific binding
    ServiceBinding sdmBinding =
        allServiceBindings.stream()
            .filter(binding -> "sdm".equalsIgnoreCase(binding.getServiceName().orElse(null)))
            .findFirst()
            .get();
    SDMCredentials sdmCredentials = new SDMCredentials();
    Map<String, Object> uaaCredentials = sdmBinding.getCredentials();
    Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");

    sdmCredentials.setBaseTokenUrl(uaa.get("url").toString());
    sdmCredentials.setUrl(sdmBinding.getCredentials().get("uri").toString());
    sdmCredentials.setClientId(uaa.get("clientid").toString());
    sdmCredentials.setClientSecret(uaa.get("clientsecret").toString());
    return sdmCredentials;
  }

  public static String getAccessToken(SDMCredentials sdmCredentials) throws IOException {
    // Fetch the token from Cache if present use it else generate and store
    String cachedToken = CacheConfig.getClientCredentialsTokenCache().get("clientCredentialsToken");
    if (cachedToken == null) {
      String userCredentials =
          sdmCredentials.getClientId() + ":" + sdmCredentials.getClientSecret();
      String authHeaderValue = "Basic " + Base64.encodeBase64String(toBytes(userCredentials));
      String bodyParams = "grant_type=client_credentials";
      byte[] postData = toBytes(bodyParams);
      String authurl = sdmCredentials.getBaseTokenUrl() + "/oauth/token";
      URL url = new URL(authurl);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("Authorization", authHeaderValue);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setRequestProperty("charset", "utf-8");
      conn.setRequestProperty("Content-Length", "" + postData.length);
      conn.setUseCaches(false);
      conn.setDoInput(true);
      conn.setDoOutput(true);
      try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
        os.write(postData);
      }
      String resp;
      try (DataInputStream is = new DataInputStream(conn.getInputStream());
          BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
        resp = br.lines().collect(Collectors.joining("\n"));
      }
      conn.disconnect();
      cachedToken = mapper.readValue(resp, JsonNode.class).get("access_token").asText();
      CacheConfig.getClientCredentialsTokenCache().put("clientCredentialsToken", cachedToken);
    }
    return cachedToken;
  }

  public static String getUserTokenFromAuthorities(
      String email, String subdomain, SDMCredentials sdmCredentials) throws IOException {
    // Fetch the token from Cache if present use it else generate and store
    String cachedToken = null;
    String userCredentials = sdmCredentials.getClientId() + ":" + sdmCredentials.getClientSecret();
    String authHeaderValue = "Basic " + Base64.encodeBase64String(toBytes(userCredentials));
    // Define the authorities (JSON) and URL encode it
    String authoritiesJson =
        "{\"az_attr\":{\"X-EcmUserEnc\":" + email + ",\"X-EcmAddPrincipals\":" + email + "}}";
    String encodedAuthorities =
        URLEncoder.encode(authoritiesJson, StandardCharsets.UTF_8.toString());

    // Create body parameters including the grant type and authorities
    String bodyParams = "grant_type=client_credentials&authorities=" + encodedAuthorities;
    byte[] postData = bodyParams.getBytes(StandardCharsets.UTF_8);

    // Create the URL for the token endpoint
    String authUrl = sdmCredentials.getBaseTokenUrl() + "/oauth/token";
    URL url = new URL(authUrl);

    // Open the connection and set the properties
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Authorization", authHeaderValue);
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setRequestProperty("charset", "utf-8");
    conn.setRequestProperty("Content-Length", String.valueOf(postData.length));
    conn.setUseCaches(false);
    conn.setDoInput(true);
    conn.setDoOutput(true);

    // Write the POST data to the output stream
    try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
      os.write(postData);
    }
    String resp;
    try (DataInputStream is = new DataInputStream(conn.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      resp = br.lines().collect(Collectors.joining("\n"));
    }
    conn.disconnect();
    cachedToken = mapper.readValue(resp, JsonNode.class).get("access_token").asText();
    TokenCacheKey cacheKey = new TokenCacheKey();
    cacheKey.setKey(email + "_" + subdomain);
    CacheConfig.getUserAuthoritiesTokenCache().put(cacheKey, cachedToken);
    return cachedToken;
  }

  public static String getDIToken(String token, SDMCredentials sdmCredentials) throws IOException {
    JsonObject payloadObj = getTokenFields(token);
    String email = payloadObj.get("email").getAsString();
    JsonObject tenantDetails = payloadObj.get("ext_attr").getAsJsonObject();
    String subdomain = tenantDetails.get("zdn").getAsString();
    String token_expiry = payloadObj.get("exp").getAsString();
    CacheKey cacheKey = new CacheKey();
    cacheKey.setKey(email + "_" + subdomain);
    cacheKey.setExpiration(token_expiry);
    String cachedToken = CacheConfig.getUserTokenCache().get(cacheKey);
    if (cachedToken == null) {
      cachedToken = generateDITokenFromTokenExchange(token, sdmCredentials, payloadObj);
    }
    return cachedToken;
  }

  public static String getDITokenUsingAuthorities(
      SDMCredentials sdmCredentials, String email, String subdomain) throws IOException {
    TokenCacheKey cacheKey = new TokenCacheKey();
    cacheKey.setKey(email + "_" + subdomain);
    String cachedToken = CacheConfig.getUserAuthoritiesTokenCache().get(cacheKey);
    if (cachedToken == null) {
      cachedToken = getUserTokenFromAuthorities(email, subdomain, sdmCredentials);
    }
    return cachedToken;
  }

  private static String generateDITokenFromTokenExchange(
      String token, SDMCredentials sdmCredentials, JsonObject payloadObj)
      throws OAuth2ServiceException {
    String cachedToken = null;
    CloseableHttpClient httpClient = null;
    try {
      httpClient = HttpClients.createDefault();
      if (sdmCredentials.getClientId() == null) {
        throw new IOException("No SDM binding found");
      }
      Map<String, String> parameters = fillTokenExchangeBody(token, sdmCredentials);
      HttpPost httpPost = new HttpPost(sdmCredentials.getBaseTokenUrl() + "/oauth/token");
      httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.value());
      httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED.value());
      httpPost.setHeader(SDMConstants.TENANT_ID, getTokenFields(token).get("zid").getAsString());

      List<BasicNameValuePair> basicNameValuePairs =
          parameters.entrySet().stream()
              .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
              .collect(Collectors.toList());
      httpPost.setEntity(new UrlEncodedFormEntity(basicNameValuePairs));
      HttpResponse response = httpClient.execute(httpPost);
      String responseBody = extractResponseBodyAsString(response);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        logger.error("Error fetching token with JWT bearer : " + responseBody);
      }
      Map<String, Object> accessTokenMap = new JSONObject(responseBody).toMap();
      cachedToken = String.valueOf(accessTokenMap.get("access_token"));
      String expiryTime = payloadObj.get("exp").getAsString();
      CacheKey cacheKey = new CacheKey();
      JsonObject tenantDetails = payloadObj.get("ext_attr").getAsJsonObject();
      String subdomain = tenantDetails.get("zdn").getAsString();
      cacheKey.setKey(payloadObj.get("email").getAsString() + "_" + subdomain);
      cacheKey.setExpiration(expiryTime);
      CacheConfig.getUserTokenCache().put(cacheKey, cachedToken);
    } catch (UnsupportedEncodingException e) {
      throw new OAuth2ServiceException("Unexpected error parsing URI: " + e.getMessage());
    } catch (ClientProtocolException e) {
      throw new OAuth2ServiceException(
          "Unexpected error while fetching client protocol: " + e.getMessage());
    } catch (IOException e) {
      logger.error("Error in POST request while fetching token with JWT bearer", e.getMessage());
    } finally {
      safeClose(httpClient);
    }
    return cachedToken;
  }

  public static JsonObject getTokenFields(String token) {
    String[] chunks = token.split("\\.");
    java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
    String payload = new String(decoder.decode(chunks[1]));
    JsonElement jelement = new JsonParser().parse(payload);
    return jelement.getAsJsonObject();
  }

  private static Map<String, String> fillTokenExchangeBody(String token, SDMCredentials sdmEnv) {
    Map<String, String> parameters = new HashMap<>();
    parameters.put(GRANT_TYPE, GRANT_TYPE_JWT_BEARER);
    parameters.put(CLIENT_ID, sdmEnv.getClientId());
    parameters.put(CLIENT_SECRET, sdmEnv.getClientSecret());
    parameters.put(ASSERTION, token);
    return parameters;
  }

  private static String extractResponseBodyAsString(HttpResponse response) throws IOException {
    // Ensure that InputStream and BufferedReader are automatically closed
    try (InputStream inputStream = response.getEntity().getContent();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
    }
  }

  private static void safeClose(CloseableHttpClient httpClient) {
    if (httpClient != null) {
      try {
        httpClient.close();
      } catch (IOException ex) {
        logger.error("Failed to close httpclient " + ex.getMessage());
      }
    }
  }
}
