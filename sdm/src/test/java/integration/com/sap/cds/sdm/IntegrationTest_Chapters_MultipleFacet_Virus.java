package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_Chapters_MultipleFacet_Virus {
  private static String token;
  private static String bookID;
  private static String chapterID;
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String clientId;
  private static String clientSecret;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String serviceName = "AdminService";
  private static String bookEntityName = "Books";
  private static String chapterEntityName = "Chapters";
  private static String entityName2 = "author";
  private static String srvpath = "AdminService";
  private static ApiInterface api;
  private static String[] attachmentID1 = new String[3];
  private static String[] attachmentID2 = new String[3];

  @BeforeAll
  static void setup() throws IOException {
    Properties credentialsProperties = Credentials.getCredentials();
    String tenancyModel = System.getProperty("tenancyModel");
    String tenant = System.getProperty("tenant");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");
    if (tenancyModel.equals("single")) {
      System.out.println("Running integration tests | Single tenant Scenario");
      clientId = credentialsProperties.getProperty("clientID");
      clientSecret = credentialsProperties.getProperty("clientSecret");
      appUrl = credentialsProperties.getProperty("appUrl");
      authUrl = credentialsProperties.getProperty("authUrl");
    } else if (tenancyModel.equals("multi")) {
      clientId = credentialsProperties.getProperty("clientIDMT");
      clientSecret = credentialsProperties.getProperty("clientSecretMT");
      appUrl = credentialsProperties.getProperty("appUrlMT");
      if (tenant.equals("TENANT1")) {
        System.out.println("Running integration tests | Multitenant Scenario | SDM DEV Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMT1");
      } else if (tenant.equals("TENANT2")) {
        System.out.println(
            "Running integration tests | Multitenant Scenario | Googleworkspace Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMT2");
      } else {
        throw new IllegalArgumentException("Invalid tenant specified: " + tenant);
      }
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
    Request request;

    String tokenFlowFlag = System.getProperty("tokenFlow");
    if (tokenFlowFlag.equals("namedUser")) {
      System.out.println("Named user token flow");
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
      System.out.println("Technical user token flow");
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
    if (response.code() != 200) {
      System.out.println("Token generation failed. Response code: " + response.code());
      String errorBody = response.body().string();
      System.out.println("Error body: " + errorBody);
    }
    token = new ObjectMapper().readTree(response.body().string()).get("access_token").asText();
    response.close();
    Map<String, String> config = new HashMap<>();
    config.put("Authorization", "Bearer " + token);
    if (tenancyModel.equals("multi")) {
      api = new ApiMT(config);
    } else if (tenancyModel.equals("single")) {
      config.put("serviceName", serviceName);
      api = new Api(config);
    } else {
      throw new IllegalArgumentException("Invalid tenancy model specified: " + tenancyModel);
    }
  }

  /**
   * Helper method to wait for attachment upload completion.
   *
   * @param chapterId The ID of the chapter containing the attachment
   * @param attachmentId The ID of the attachment to check
   * @param facetName The facet name for the attachment
   * @param timeoutSeconds Maximum time to wait in seconds
   * @return true if upload completed successfully, false if failed or timed out
   */
  private boolean waitForUploadCompletion(
      String chapterId, String attachmentId, String facetName, int timeoutSeconds) {
    int maxIterations = timeoutSeconds / 2;
    for (int i = 0; i < maxIterations; i++) {
      try {
        Map<String, Object> metadata =
            api.fetchMetadataDraft(appUrl, chapterEntityName, facetName, chapterId, attachmentId);
        String uploadStatus = (String) metadata.get("uploadStatus");

        if ("Success".equals(uploadStatus)) {
          return true;
        } else if ("Failed".equals(uploadStatus)) {
          System.err.println("Upload failed for attachment: " + attachmentId);
          return false;
        }

        Thread.sleep(2000);
      } catch (Exception e) {
        System.err.println(
            "Error checking upload status for attachment " + attachmentId + ": " + e.getMessage());
        return false;
      }
    }

    System.err.println("Upload timed out for attachment: " + attachmentId);
    return false;
  }

  @Test
  @Order(1)
  void testCreateBookChapterAndCheck() {
    System.out.println("Test (1) : Create book+chapter and check if they exist");
    boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      bookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID);
      if (!chapterResponse.equals("Could not create entity")) {
        chapterID = chapterResponse;

        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        if (response.equals("Saved")) {
          response = api.checkEntity(appUrl, bookEntityName, bookID);
          if (response.equals("Entity exists")) {
            response = api.checkEntity(appUrl, chapterEntityName, chapterID);
            if (response.equals("Entity exists")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not create book+chapter");
    }
  }

  @Test
  @Order(2)
  void testUpdateEmptyBookChapter() {
    System.out.println("Test (2) : Update an existing book+chapter");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
      if (response.equals("Saved")) {
        response = api.checkEntity(appUrl, chapterEntityName, chapterID);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not update book+chapter");
    }
  }

  @Test
  @Order(3)
  void testUploadSingleAttachmentPDF() throws IOException {
    System.out.println("Test (3) : Upload pdf in all facets on chapter");
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    assertEquals("Entity in draft mode", response, "Book should be in draft mode");

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID, srvpath, postData, file);
      assertEquals(
          "Attachment created", createResponse.get(0), "Upload should succeed for " + facet[i]);
      attachmentID1[i] = createResponse.get(1);

      response =
          api.readAttachmentDraft(appUrl, chapterEntityName, facet[i], chapterID, attachmentID1[i]);
      assertEquals("OK", response, "Attachment should be readable in draft for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    assertEquals("Saved", response, "Book save should succeed");

    for (int i = 0; i < facet.length; i++) {
      response =
          api.readAttachment(appUrl, chapterEntityName, facet[i], chapterID, attachmentID1[i]);
      assertEquals("OK", response, "Attachment should be readable after save for " + facet[i]);
    }
  }

  @Test
  @Order(4)
  void testUploadVirusFileInScannedRepo() throws IOException {
    System.out.println(
        "Test (4) : Upload EICAR virus file in all facets on chapter — expect virus scan to reject");

    String eicarFilePath = System.getProperty("eicar.file.path", "eicar.com.txt");
    File file = new File(eicarFilePath);
    if (!file.exists()) {
      fail("EICAR virus test file not found at: " + file.getAbsolutePath());
    }

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    assertEquals("Entity in draft mode", response, "Book should be in draft mode");

    for (int i = 0; i < facet.length; i++) {
      boolean testStatus = false;

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.contains("malware") || check.contains("potential malware")) {
        testStatus = true;
      } else if (check.equals("Attachment created")) {
        attachmentID2[i] = createResponse.get(1);
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        if (response.equals("Saved")) {
          boolean uploadSucceeded =
              waitForUploadCompletion(chapterID, attachmentID2[i], facet[i], 120);
          if (!uploadSucceeded) {
            testStatus = true;
          } else {
            fail(
                "Virus file should have been rejected by the virus scanner but upload succeeded for "
                    + facet[i]);
          }
        }
      }

      if (!testStatus) {
        fail("Could not verify virus file rejection for " + facet[i]);
      }
    }
  }
}
