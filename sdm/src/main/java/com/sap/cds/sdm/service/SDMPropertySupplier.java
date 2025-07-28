package com.sap.cds.sdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cloud.sdk.cloudplatform.connectivity.DefaultOAuth2PropertySupplier;
import com.sap.cloud.sdk.cloudplatform.connectivity.OAuth2Options;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;
import io.reactivex.annotations.NonNull;
import java.net.URI;
import org.jetbrains.annotations.NotNull;

public class SDMPropertySupplier extends DefaultOAuth2PropertySupplier {

  public SDMPropertySupplier(ServiceBindingDestinationOptions options) {
    super(options);
  }

  @NonNull
  @Override
  public URI getServiceUri() {
    return getCredentialOrThrow(URI.class, "endpoints", "ecmservice", "url");
  }

  @NotNull
  @Override
  public OAuth2Options getOAuth2Options() {
    var builder = OAuth2Options.builder();
    var user = this.options.getOption(SDMUser.class);
    if (!user.isEmpty()) {
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
    return builder.build();
  }
}
