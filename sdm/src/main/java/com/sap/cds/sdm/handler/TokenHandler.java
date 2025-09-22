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
    Map<String, Object> uaaCredentials = getUaaCredentials();
    Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");
    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setBaseTokenUrl(uaa.get("url").toString());
    sdmCredentials.setUrl(uaaCredentials.get("uri").toString());
    sdmCredentials.setClientId(uaa.get("clientid").toString());
    sdmCredentials.setClientSecret(uaa.get("clientsecret").toString());
    return sdmCredentials;
  }

  public Map<String, Object> getUaaCredentials() {
    List<ServiceBinding> allServiceBindings =
        DefaultServiceBindingAccessor.getInstance().getServiceBindings();
    ServiceBinding sdmBinding =
        allServiceBindings.stream()
            .filter(binding -> "sdm".equalsIgnoreCase(binding.getServiceName().orElse(null)))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("SDM binding not found"));
    return sdmBinding.getCredentials();
  }

  public HttpClient getHttpClient(
      ServiceBinding binding,
      CdsProperties.ConnectionPool connectionPoolConfig,
      String subdomain,
      String type) {

    Map<String, Object> uaaCredentials;
    if (binding != null && !binding.getCredentials().isEmpty()) {
      uaaCredentials = binding.getCredentials();
    } else {
      uaaCredentials = getUaaCredentials();
    }

    Map<String, Object> uaa = (Map<String, Object>) uaaCredentials.get("uaa");

    ClientCredentials clientCredentials =
        new ClientCredentials(uaa.get(CLIENT_ID).toString(), uaa.get(CLIENT_SECRET).toString());

    String baseTokenUrl = uaa.get(SDM_TOKEN_ENDPOINT).toString();
    if (subdomain != null && !subdomain.isEmpty()) {
      String providerSubdomain =
          baseTokenUrl.substring(baseTokenUrl.indexOf("/") + 2, baseTokenUrl.indexOf("."));
      baseTokenUrl = baseTokenUrl.replace(providerSubdomain, subdomain);
    }

    DefaultHttpDestination destination;
    if (NAMED_USER_FLOW.equals(type)) {
      destination =
          OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get(SDM_URL).toString())
              .withTokenEndpoint(baseTokenUrl)
              .withClient(clientCredentials, OnBehalfOf.NAMED_USER_CURRENT_TENANT)
              .property(
                  SDMConstants.SDM_DESTINATION_KEY, SDMConstants.SDM_TOKEN_EXCHANGE_DESTINATION)
              .build();
    } else {
      destination =
          OAuth2DestinationBuilder.forTargetUrl(uaaCredentials.get(SDM_URL).toString())
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
    } else {
      builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
      builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
      builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
    }

    return builder.build().createHttpClient(destination);
  }

  public HttpClient getHttpClientForAuthoritiesFlow(
      CdsProperties.ConnectionPool connectionPoolConfig, String user) {

    Optional<HttpDestination> destinations = getHttpDestination(user);
    if (destinations.isPresent()) {
      DefaultHttpClientFactory.DefaultHttpClientFactoryBuilder builder =
          DefaultHttpClientFactory.builder();

      if (connectionPoolConfig == null) {
        Duration timeout = Duration.ofSeconds(SDMConstants.CONNECTION_TIMEOUT);
        builder.timeoutMilliseconds((int) timeout.toMillis());
        builder.maxConnectionsPerRoute(SDMConstants.MAX_CONNECTIONS);
        builder.maxConnectionsTotal(SDMConstants.MAX_CONNECTIONS);
      } else {
        builder.timeoutMilliseconds((int) connectionPoolConfig.getTimeout().toMillis());
        builder.maxConnectionsPerRoute(connectionPoolConfig.getMaxConnectionsPerRoute());
        builder.maxConnectionsTotal(connectionPoolConfig.getMaxConnections());
      }

      return builder.build().createHttpClient(destinations.get());
    }
    return null;
  }

  private Optional<HttpDestination> getHttpDestination(String userName) {
    HttpDestination httpDestination;
    try {
      httpDestination =
          ServiceBindingDestinationLoader.defaultLoaderChain()
              .getDestination(getSDMDestinationOptions(userName));
    } catch (Exception exception) {
      logger.error("Error with fetching httpdestination " + exception.getCause());
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
