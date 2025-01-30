package com.sap.cds.sdm.handler;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.TokenCacheKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpDestination;
import com.sap.cloud.sdk.cloudplatform.connectivity.OAuth2DestinationBuilder;
import com.sap.cloud.sdk.cloudplatform.connectivity.OnBehalfOf;
import com.sap.cloud.security.config.ClientCredentials;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;

public class TokenHandler {

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

  private static final String SDM_TOKEN_ENDPOINT = "url";
  private static final String SDM_URL = "uri";
  private static final String CLIENT_ID = "clientid";
  private static final String CLIENT_SECRET = "clientsecret";

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
    String baseTokenUrl = sdmCredentials.getBaseTokenUrl();
    if (subdomain != null && !subdomain.equals("")) {
      String providersubdomain =
          baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
      baseTokenUrl = baseTokenUrl.replace(providersubdomain, subdomain);
    }
    // Create the URL for the token endpoint
    String authUrl = baseTokenUrl + "/oauth/token";
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

  public static JsonObject getTokenFields(String token) {
    String[] chunks = token.split("\\.");
    java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
    String payload = new String(decoder.decode(chunks[1]));
    JsonElement jelement = new JsonParser().parse(payload);
    return jelement.getAsJsonObject();
  }

  public static HttpClient getHttpClient(
      ServiceBinding binding,
      CdsProperties.ConnectionPool connectionPoolConfig,
      String subdomain,
      String type) {
    if (!binding.getCredentials().isEmpty()) {
      Map<String, Object> uaaCredentials = binding.getCredentials();
      Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");
      ClientCredentials clientCredentials =
          new ClientCredentials(uaa.get(CLIENT_ID).toString(), uaa.get(CLIENT_SECRET).toString());
      String baseTokenUrl = uaa.get(SDM_TOKEN_ENDPOINT).toString();
      if (subdomain != null && !subdomain.isEmpty()) {
        String providersubdomain =
            baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
        baseTokenUrl = baseTokenUrl.replace(providersubdomain, subdomain);
      }

      DefaultHttpDestination destination;
      if (type.equals("TOKEN_EXCHANGE")) {
        destination =
            OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get(SDM_URL).toString())
                .withTokenEndpoint(baseTokenUrl)
                .withClient(clientCredentials, OnBehalfOf.NAMED_USER_CURRENT_TENANT)
                .property("name", SDMConstants.SDM_TOKEN_EXCHANGE_DESTINATION)
                .build();
      } else {
        destination =
            OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get(SDM_URL).toString())
                .withTokenEndpoint(baseTokenUrl)
                .withClient(clientCredentials, OnBehalfOf.TECHNICAL_USER_CURRENT_TENANT)
                .property("name", SDMConstants.SDM_TECHNICAL_CREDENTIALS_FLOW_DESTINATION)
                .build();
      }
      //      ApacheHttpClient5Factory customFactory = new ApacheHttpClient5FactoryBuilder()
      //              .timeout(Duration.ofMinutes(connectionPoolConfig.getTimeout().toMinutes()))
      //              .maxConnectionsTotal(connectionPoolConfig.getMaxConnectionsPerRoute())
      //              .maxConnectionsPerRoute(connectionPoolConfig.getMaxConnections())
      //              .build();

      DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder builder =
          DefaultHttpClientFactory.builder();
      builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
      builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
      builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
      DefaultHttpClientFactory factory = builder.build();

      return factory.createHttpClient(destination);
    }
    return null;
  }

  public static String getSubdomainFromToken(String token) {
    JsonObject payloadObj = TokenHandler.getTokenFields(token);
    JsonObject tenantDetails = payloadObj.get("ext_attr").getAsJsonObject();
    return tenantDetails.get("zdn").getAsString();
  }
}
