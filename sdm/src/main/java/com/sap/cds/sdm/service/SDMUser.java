package com.sap.cds.sdm.service;

import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;

public class SDMUser implements ServiceBindingDestinationOptions.OptionsEnhancer<String> {

  private final String value;

  /** Set the user. */
  public static SDMUser of(String value) {
    return new SDMUser(value);
  }

  private SDMUser(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }
}
