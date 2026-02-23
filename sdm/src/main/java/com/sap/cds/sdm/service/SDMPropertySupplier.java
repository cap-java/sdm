package com.sap.cds.sdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultOAuth2PropertySupplier;
import com.sap.cloud.sdk.cloudplatform.connectivity.OAuth2Options;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;
import io.reactivex.annotations.NonNull;
import java.net.URI;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDMPropertySupplier extends DefaultOAuth2PropertySupplier {
  private static final Logger logger = LoggerFactory.getLogger(SDMPropertySupplier.class);

  public SDMPropertySupplier(ServiceBindingDestinationOptions options) {
    super(options);
  }

  @NonNull
  @Override
  public URI getServiceUri() {
    logger.debug("START: getServiceUri");
    URI uri = getCredentialOrThrow(URI.class, "endpoints", "ecmservice", "url");
    logger.debug("END: getServiceUri - returning: {}", uri);
    return uri;
  }

  @NotNull
  @Override
  public OAuth2Options getOAuth2Options() {
    logger.debug("START: getOAuth2Options");
    var builder = OAuth2Options.builder();
    var user = this.options.getOption(SDMUser.class);
    if (!user.isEmpty()) {
      logger.debug("User option present, adding X-EcmUserEnc and X-EcmAddPrincipals attributes");
      var objectMapper = new ObjectMapper();
      var azAttrNode = objectMapper.createObjectNode();
      // add X-EcmUserEnc attribute
      azAttrNode.put("X-EcmUserEnc", user.get());

      // add X-EcmAddPrincipals attribute
      azAttrNode.put("X-EcmAddPrincipals", user.get());
      var authoritiesNode = objectMapper.createObjectNode();
      authoritiesNode.set("az_attr", azAttrNode);
      builder.withTokenRetrievalParameter("authorities", authoritiesNode.toString());
    }
    logger.debug("END: getOAuth2Options");
    return builder.build();
  }
}
