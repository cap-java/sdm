package com.sap.cds.sdm.service.client.httpclient;

import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.*;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder;
import com.sap.cloud.security.config.ClientCredentials;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.http.client.HttpClient;

public final class SDMClientProviderFactory implements HttpClientProviderFactory {
  private static final String SCAN_ENDPOINT = "/oauth/token?grant_type=client_credentials";
  private static final String VALUE_URL = "url";
  private static final String VALUE_USERNAME = "clientid";
  private static final String VALUE_PASSWORD = "clientsecret";

  private final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();

  public SDMClientProviderFactory(
      ServiceBinding binding, CdsProperties.ConnectionPool connectionPoolConfig) {
    if (Objects.isNull(binding)) {
      httpClients.put("sdm-destination-token-exchange", null);
      httpClients.put("sdm-destination-technical-user", null);

    } else {

      Map<String, Object> uaaCredentials = binding.getCredentials();
      Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");
      System.out.println("Cred " + uaaCredentials);
      System.out.println("UAA " + uaa);
      ClientCredentials clientCredentials =
          new ClientCredentials(
              uaa.get(VALUE_USERNAME).toString(), uaa.get(VALUE_PASSWORD).toString());
      System.out.println("UAA " + uaaCredentials.get("uri"));
      var destination =
          OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get("uri").toString())
              .withTokenEndpoint(uaa.get("url").toString())
              .withClient(clientCredentials, OnBehalfOf.NAMED_USER_CURRENT_TENANT)
              .property("name", "sdm-destination-token-exchange")
              .build();

      DefaultHttpClientFactoryBuilder builder = DefaultHttpClientFactory.builder();
      builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
      builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
      builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
      DefaultHttpClientFactory factory = builder.build();

      HttpClient httpClient = factory.createHttpClient(destination);
      httpClients.put("sdm-destination-token-exchange", httpClient);
      destination =
          OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get("uri").toString())
              .withTokenEndpoint(uaa.get("url").toString())
              .withClient(clientCredentials, OnBehalfOf.TECHNICAL_USER_CURRENT_TENANT)
              .property("name", "sdm-destination-technical-user")
              .build();

      builder = DefaultHttpClientFactory.builder();
      builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
      builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
      builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
      factory = builder.build();

      httpClient = factory.createHttpClient(destination);
      httpClients.put("sdm-destination-technical-user", httpClient);
    }
  }

  public HttpClient getHttpClient(String destinationName) {
    return this.httpClients.get(destinationName);
  }

  public boolean isServiceBound(String destinationName) {
    return this.httpClients.containsKey(destinationName);
  }

  @Override
  public HttpClient getHttpClient() {
    throw new UnsupportedOperationException("Use getHttpClient(String bindingName) instead.");
  }

  @Override
  public boolean isServiceBound() {
    throw new UnsupportedOperationException("Use isServiceBound(String bindingName) instead.");
  }
}
