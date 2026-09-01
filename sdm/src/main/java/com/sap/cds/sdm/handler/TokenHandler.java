package com.sap.cds.sdm.handler;

import static com.sap.cds.sdm.constants.SDMConstants.NAMED_USER_FLOW;
import static com.sap.cloud.sdk.cloudplatform.connectivity.OnBehalfOf.TECHNICAL_USER_CURRENT_TENANT;
import static java.util.Objects.requireNonNull;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMPropertySupplier;
import com.sap.cds.sdm.service.SDMUser;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.environment.servicebinding.api.ServiceIdentifier;
import com.sap.cloud.sdk.cloudplatform.connectivity.*;
import com.sap.cloud.security.config.ClientCredentials;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenHandler {
  private static final String SDM_TOKEN_ENDPOINT = "url";
  private static final String SDM_URL = "uri";
  private static final String CLIENT_ID = "clientid";
  private static final String CLIENT_SECRET = "clientsecret";

  private static TokenHandler instance;

  private TokenHandler() {}

  private static final Logger logger = LoggerFactory.getLogger(TokenHandler.class);

  public static TokenHandler getTokenHandlerInstance() {
    if (instance == null) {
      instance = new TokenHandler();
    }
    return instance;
  }

  static {
    OAuth2ServiceBindingDestinationLoader.registerPropertySupplier(
        ServiceIdentifier.of("sdm"), SDMPropertySupplier::new);
  }

  public byte[] toBytes(String str) {
    return requireNonNull(str).getBytes(StandardCharsets.UTF_8);
  }

  public String toString(byte[] bytes) {
    return new String(requireNonNull(bytes), StandardCharsets.UTF_8);
  }

  public SDMCredentials getSDMCredentials() {
    logger.debug("START: getSDMCredentials - loading SDM credentials from service binding");
    Map<String, Object> uaaCredentials = getUaaCredentials();
    Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl(uaa.get("url").toString());
    sdmCredentials.setUrl(uaaCredentials.get("uri").toString());
    sdmCredentials.setClientId(uaa.get("clientid").toString());
    sdmCredentials.setClientSecret(uaa.get("clientsecret").toString());
    logger.debug("END: getSDMCredentials - SDM URL: {}", sdmCredentials.getUrl());
    return sdmCredentials;
  }

  public Map<String, Object> getUaaCredentials() {
    logger.debug("START: getUaaCredentials - scanning service bindings for 'sdm' tag");
    List<ServiceBinding> allServiceBindings =
        DefaultServiceBindingAccessor.getInstance().getServiceBindings();
    logger.debug("Total service bindings found: {}", allServiceBindings.size());
    ServiceBinding sdmBinding =
        allServiceBindings.stream()
            .filter(binding -> binding.getTags().contains("sdm"))
            .findFirst()
            .orElseThrow(() -> {
              logger.error("No service binding with 'sdm' tag found among {} bindings", allServiceBindings.size());
              return new IllegalStateException("SDM binding not found");
            });
    logger.debug("END: getUaaCredentials - SDM binding found");
    return sdmBinding.getCredentials();
  }

  public HttpClient getHttpClient(
      ServiceBinding binding,
      CdsProperties.ConnectionPool connectionPoolConfig,
      String subdomain,
      String type) {

    logger.debug("START: getHttpClient - type: {}, subdomain: {}, connectionPoolConfig: {}",
        type, subdomain, connectionPoolConfig != null ? "configured" : "null(using defaults)");

    Map<String, Object> uaaCredentials;
    if (binding != null && !binding.getCredentials().isEmpty()) {
      logger.debug("getHttpClient - using credentials from provided ServiceBinding");
      uaaCredentials = binding.getCredentials();
    } else {
      logger.debug("getHttpClient - binding not provided or empty, fetching credentials from service binding registry");
      uaaCredentials = getUaaCredentials();
    }

    Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");

    ClientCredentials clientCredentials =
        new ClientCredentials(uaa.get(CLIENT_ID).toString(), uaa.get(CLIENT_SECRET).toString());
    logger.debug("getHttpClient - clientId: {}", uaa.get(CLIENT_ID).toString());

    String baseTokenUrl = uaa.get(SDM_TOKEN_ENDPOINT).toString();
    logger.debug("getHttpClient - base token URL: {}", baseTokenUrl);
    if (subdomain != null && !subdomain.isEmpty()) {
      String providerSubdomain =
          baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
      baseTokenUrl = baseTokenUrl.replace(providerSubdomain, subdomain);
      logger.debug("getHttpClient - token URL adjusted for subdomain '{}': {}", subdomain, baseTokenUrl);
    }

    String sdmTargetUrl = uaaCredentials.get(SDM_URL).toString();
    logger.debug("getHttpClient - SDM target URL: {}", sdmTargetUrl);

    DefaultHttpDestination destination;
    if (NAMED_USER_FLOW.equals(type)) {
      logger.debug("getHttpClient - building NAMED_USER (token exchange) destination");
      destination =
          OAuth2DestinationBuilder.forTargetUrl(sdmTargetUrl)
              .withTokenEndpoint(baseTokenUrl)
              .withClient(clientCredentials, OnBehalfOf.NAMED_USER_CURRENT_TENANT)
              .property(
                  SDMConstants.SDM_DESTINATION_KEY, SDMConstants.SDM_TOKEN_EXCHANGE_DESTINATION)
              .build();
    } else {
      logger.debug("getHttpClient - building TECHNICAL_USER (client credentials) destination");
      destination =
          OAuth2DestinationBuilder.forTargetUrl(sdmTargetUrl)
              .withTokenEndpoint(baseTokenUrl)
              .withClient(clientCredentials, OnBehalfOf.TECHNICAL_USER_CURRENT_TENANT)
              .property(
                  SDMConstants.SDM_DESTINATION_KEY,
                  SDMConstants.SDM_TECHNICAL_CREDENTIALS_FLOW_DESTINATION)
              .build();
    }

    DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder builder =
        DefaultHttpClientFactory.builder();

    if (connectionPoolConfig == null) {
      Duration timeout = Duration.ofSeconds(SDMConstants.CONNECTION_TIMEOUT);
      builder.timeoutMilliseconds((int) timeout.toMillis());
      builder.maxConnectionsPerRoute(SDMConstants.MAX_CONNECTIONS);
      builder.maxConnectionsTotal(SDMConstants.MAX_CONNECTIONS);
      logger.debug("getHttpClient - using default connection pool: timeout={}ms, maxConnPerRoute={}, maxConnTotal={}",
          timeout.toMillis(), SDMConstants.MAX_CONNECTIONS, SDMConstants.MAX_CONNECTIONS);
    } else {
      builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
      builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
      builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
      logger.debug("getHttpClient - using configured connection pool: timeout={}ms, maxConnPerRoute={}, maxConnTotal={}",
          connectionPoolConfig.getTimeout().toMillis(),
          connectionPoolConfig.getMaxConnectionsPerRoute(),
          connectionPoolConfig.getMaxConnections());
    }

    HttpClient httpClient = builder.build().createHttpClient(destination);
    logger.debug("END: getHttpClient - HttpClient created for type: {}", type);
    return httpClient;
  }

  public HttpClient getHttpClientForAuthoritiesFlow(
      CdsProperties.ConnectionPool connectionPoolConfig, String user) {

    logger.debug("START: getHttpClientForAuthoritiesFlow - user: {}", user);
    Optional<HttpDestination> destinations = getHttpDestination(user);
    if (destinations.isPresent()) {
      logger.debug("getHttpClientForAuthoritiesFlow - HttpDestination resolved for user: {}", user);
      DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder builder =
          DefaultHttpClientFactory.builder();

      if (connectionPoolConfig == null) {
        Duration timeout = Duration.ofSeconds(SDMConstants.CONNECTION_TIMEOUT);
        builder.timeoutMilliseconds((int) timeout.toMillis());
        builder.maxConnectionsPerRoute(SDMConstants.MAX_CONNECTIONS);
        builder.maxConnectionsTotal(SDMConstants.MAX_CONNECTIONS);
        logger.debug("getHttpClientForAuthoritiesFlow - using default pool: timeout={}ms", timeout.toMillis());
      } else {
        builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
        builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
        builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
        logger.debug("getHttpClientForAuthoritiesFlow - using configured pool: timeout={}ms",
            connectionPoolConfig.getTimeout().toMillis());
      }

      HttpClient httpClient = builder.build().createHttpClient(destinations.get());
      logger.debug("END: getHttpClientForAuthoritiesFlow - HttpClient created for user: {}", user);
      return httpClient;
    }
    logger.warn("getHttpClientForAuthoritiesFlow - no HttpDestination found for user: {}, returning null", user);
    return null;
  }

  private Optional<HttpDestination> getHttpDestination(String userName) {
    logger.debug("START: getHttpDestination - resolving destination for user: {}", userName);
    HttpDestination httpDestination;
    try {
      httpDestination =
          ServiceBindingDestinationLoader.defaultLoaderChain()
              .getDestination(getSDMDestinationOptions(userName));
      logger.debug("END: getHttpDestination - destination resolved for user: {}", userName);
    } catch (Exception exception) {
      logger.error("Error with fetching httpdestination for user {}: {}", userName, exception.getCause());
      httpDestination = null;
    }
    return Optional.ofNullable(httpDestination);
  }

  public static ServiceBindingDestinationOptions getSDMDestinationOptions(String userName) {
    return ServiceBindingDestinationOptions.forService(ServiceIdentifier.of("sdm"))
        .onBehalfOf(TECHNICAL_USER_CURRENT_TENANT)
        .withOption(SDMUser.of(userName))
        .build();
  }
}
