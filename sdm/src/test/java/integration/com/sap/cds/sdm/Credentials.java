package integration.com.sap.cds.sdm;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Credentials {

  public static Properties getCredentials() {
    Properties properties = new Properties();
    try (FileInputStream input = new FileInputStream("src/test/resources/credentials.properties")) {
      properties.load(input);
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return properties;
  }

  public static Properties getCredentials(String tenant) {
    Properties properties = getCredentials();
    if (tenant == null) {
      return properties;
    }
    String suffix = mapTenantToSuffix(tenant);

    properties.setProperty("authUrlMT", properties.getProperty("authUrlMT" + suffix));
    properties.setProperty(
        "consumerSubaccountIdMT", properties.getProperty("consumerSubaccountIdMT" + suffix));
    properties.setProperty(
        "consumerSubdomainMT", properties.getProperty("consumerSubdomainMT" + suffix));

    return properties;
  }

  private static String mapTenantToSuffix(String tenant) {
    if ("TENANT1".equals(tenant)) {
      return "1";
    } else if ("TENANT2".equals(tenant)) {
      return "2";
    }
    throw new IllegalArgumentException("Unknown tenant: " + tenant);
  }
}
