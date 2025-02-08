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
}
