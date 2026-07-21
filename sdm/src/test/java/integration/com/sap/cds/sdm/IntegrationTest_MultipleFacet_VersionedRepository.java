package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

class IntegrationTest_MultipleFacet_VersionedRepository {

  private static String token;
  private static String clientId;
  private static String clientSecret;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String serviceName = "AdminService";
  private static String entityName = "Books";
  private static String entityName2 = "author";
  private static String srvpath = "AdminService";
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String tenancyModel;
  private static ApiInterface api;
  private static String entityID;

  @BeforeAll
  static void setup() throws IOException {
    Properties credentialsProperties = Credentials.getCredentials();
    tenancyModel = System.getProperty("tenancyModel");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");

    if (tenancyModel.equals("single")) {
      clientId = credentialsProperties.getProperty("clientID");
      clientSecret = credentialsProperties.getProperty("clientSecret");
      appUrl = credentialsProperties.getProperty("appUrl");
      authUrl = credentialsProperties.getProperty("authUrl");
    } else if (tenancyModel.equals("multi")) {
      clientId = credentialsProperties.getProperty("clientIDMT");
      clientSecret = credentialsProperties.getProperty("clientSecretMT");
      appUrl = credentialsProperties.getProperty("appUrlMT");
      authUrl = credentialsProperties.getProperty("authUrlMT1");
    } else {
      throw new IllegalArgumentException("Invalid tenancy model specified: " + tenancyModel);
    }

    String credentials = clientId + ":" + clientSecret;
    String basicAuth =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    MediaType mediaType = MediaType.parse("text/plain");
    RequestBody body = RequestBody.create(mediaType, "");

    String tokenFlowFlag = System.getProperty("tokenFlow");
    Request request;
    if (tokenFlowFlag.equals("namedUser")) {
      request =
          new Request.Builder()
              .url(
                  authUrl
                      + "/oauth/token?grant_type=password&username="
                      + username
                      + "&password="
                      + password)
              .method("POST", body)
              .addHeader("Authorization", basicAuth)
              .build();
    } else if (tokenFlowFlag.equals("technicalUser")) {
      request =
          new Request.Builder()
              .url(authUrl + "/oauth/token?grant_type=client_credentials")
              .method("POST", body)
              .addHeader("Authorization", basicAuth)
              .build();
    } else {
      throw new IllegalArgumentException("Invalid token flow specified: " + tokenFlowFlag);
    }

    Response response = client.newCall(request).execute();
    String responseBody = response.body().string();
    response.close();
    if (response.code() != 200) {
      System.out.println("Token generation failed. Response code: " + response.code());
      System.out.println("Error body: " + responseBody);
      fail("Token generation failed with response code: " + response.code());
    }
    token = new ObjectMapper().readTree(responseBody).get("access_token").asText();

    Map<String, String> config = new HashMap<>();
    config.put("Authorization", "Bearer " + token);
    if (tenancyModel.equals("multi")) {
      api = new ApiMT(config);
    } else {
      config.put("serviceName", serviceName);
      api = new Api(config);
    }
  }

  @Test
  void testCreateEntityAndUploadAttachmentShouldFail() throws IOException {
    System.out.println(
        "Test (2) : Create entity and upload attachments on versioned repository in all facets — expect error");
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Entity creation should succeed");
    entityID = response;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facet[i], entityID, srvpath, postData, file);
      String check = createResponse.get(0);

      if (check.equals("Attachment created")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        assertNotEquals(
            "Saved", response, "Save should fail on versioned repository for " + facet[i]);
        System.out.println("Save failed as expected for " + facet[i] + ": " + response);
      } else {
        System.out.println("Operation failed as expected for " + facet[i] + ": " + check);
        assertTrue(
            check.contains("error") || check.contains("Error"),
            "Response should contain an error message for " + facet[i]);
      }
    }
  }
}
