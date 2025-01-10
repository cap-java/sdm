package com.sap.cds.sdm.service.client.httpclient;

import org.apache.http.client.HttpClient;

/** Factory for creating a {@link HttpClient} for the malware scan service. */
public interface HttpClientProviderFactory {

  HttpClient getHttpClient();

  /**
   * Returns {@code true}, if a binding to the malware scan service is available.
   *
   * @return {@code true} if a binding to the malware scan service is available.
   */
  boolean isServiceBound();
}
