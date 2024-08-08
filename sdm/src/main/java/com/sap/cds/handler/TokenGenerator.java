package com.sap.cds.handler;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.model.SDMCredentials;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;

public class TokenGenerator {
  private static Map<String, Object> uaaCredentials;
  private static final ObjectMapper mapper = new ObjectMapper();

  public static byte[] toBytes(String str) {
    return requireNonNull(str).getBytes(StandardCharsets.UTF_8);
  }

  public static String toString(byte[] bytes) {
    return new String(requireNonNull(bytes), StandardCharsets.UTF_8);
  }

  private static SDMCredentials getSDMCredentianls() {
    List<ServiceBinding> allServiceBindings =
        DefaultServiceBindingAccessor.getInstance().getServiceBindings();

    // filter for a specific binding
    ServiceBinding sdmBinding =
        allServiceBindings.stream()
            .filter(binding -> "sdm".equalsIgnoreCase(binding.getServiceName().orElse(null)))
            .findFirst()
            .get();
    SDMCredentials sdmCredentials = new SDMCredentials();
    uaaCredentials = sdmBinding.getCredentials();
    sdmCredentials.setBaseTokenUrl(uaaCredentials.get("url").toString());
    sdmCredentials.setUrl(sdmBinding.getCredentials().get("uri").toString());
    sdmCredentials.setClientId(uaaCredentials.get("clientid").toString());
    sdmCredentials.setClientSecret(uaaCredentials.get("clientsecret").toString());
    return sdmCredentials;
  }

  public static String getAccessToken() throws IOException, ProtocolException {
    SDMCredentials sdmCredentials = getSDMCredentianls();
    String userCredentials = sdmCredentials.getClientId() + ":" + sdmCredentials.getClientSecret();
    String authHeaderValue = "Basic " + Base64.encodeBase64String(toBytes(userCredentials));
    String bodyParams = "grant_type=client_credentials";
    byte[] postData = toBytes(bodyParams);
    URL url = new URL(sdmCredentials.getBaseTokenUrl());
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
    return mapper.readValue(resp, JsonNode.class).get("access_token").asText();
  }
}
