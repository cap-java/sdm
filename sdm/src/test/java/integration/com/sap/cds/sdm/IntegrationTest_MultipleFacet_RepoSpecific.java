package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import integration.com.sap.cds.sdm.utils.ShellScriptRunner;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for "Displaying Attachments Specific To Repository" with multiple facets.
 * Verifies that attachments created under one repository are not visible when the application
 * switches to a different repository, and that duplicate file names across repositories are allowed
 * for all facets (attachments, references, footnotes).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_MultipleFacet_RepoSpecific {

  private static final String UPDATE_ENV_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-update-env.sh";

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
  private static String defaultRepositoryID;
  private static String virusScanRepositoryID;
  private static ApiInterface api;

  // Entity IDs used across tests
  private static String entityID1;
  private static String[] attachmentID1 = new String[3];
  private static String entityID_rename;

  @BeforeAll
  static void setup() throws IOException {
    Properties credentialsProperties = Credentials.getCredentials();
    tenancyModel = System.getProperty("tenancyModel");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");
    defaultRepositoryID = credentialsProperties.getProperty("defaultRepositoryID");
    virusScanRepositoryID = credentialsProperties.getProperty("virusScanRepositoryID");

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
      defaultRepositoryID = credentialsProperties.getProperty("defaultRepositoryIDMT");
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

  private static int runUpdateEnv(String value) throws Exception {
    if (tenancyModel.equals("multi")) {
      return ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--app", "bookshop-mt-srv", "--value", value);
    }
    return ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", value);
  }

  @Test
  @Order(1)
  void testSetupRepo1AndCreateAttachments() throws Exception {
    System.out.println(
        "Test (1) : Setup — switch to defaultRepositoryID ("
            + defaultRepositoryID
            + "), create entity with attachments in all facets");

    int exitCode = runUpdateEnv(defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for defaultRepositoryID");

    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Entity creation should succeed");
    entityID1 = response;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID1);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facet[i], entityID1, srvpath, postData, file);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Attachment creation should succeed for " + facet[i]);
      attachmentID1[i] = createResponse.get(1);
    }

    response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID1);
    assertEquals("Saved", response, "Entity save should succeed");

    for (int i = 0; i < facet.length; i++) {
      response = api.readAttachment(appUrl, entityName, facet[i], entityID1, attachmentID1[i]);
      assertEquals(
          "OK",
          response,
          "Attachment should be readable under defaultRepositoryID for " + facet[i]);

      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, facet[i], entityID1);
      assertEquals(
          1,
          attachments.size(),
          "Entity should have 1 attachment under defaultRepositoryID for " + facet[i]);
    }
  }

  @Test
  @Order(2)
  void testSwitchToRepo2AttachmentsNotVisible() throws Exception {
    System.out.println(
        "Test (2) : Switch to virusScanRepositoryID, verify attachments from defaultRepositoryID are not visible in any facet");

    int exitCode = runUpdateEnv(virusScanRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for virusScanRepositoryID");

    String response = api.checkEntity(appUrl, entityName, entityID1);
    assertEquals("Entity exists", response, "Entity should still be visible after repo switch");

    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, facet[i], entityID1);
      assertEquals(
          0,
          attachments.size(),
          "Entity should have 0 attachments after switching to virusScanRepositoryID for "
              + facet[i]);
    }
  }

  @Test
  @Order(3)
  void testDuplicateAttachmentCreateAcrossRepos() throws Exception {
    System.out.println(
        "Test (3) : Create attachment with same name under virusScanRepositoryID in all facets — should succeed");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID1);
    assertEquals("Entity in draft mode", response, "Edit entity should succeed");

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID1);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facet[i], entityID1, srvpath, postData, file);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Duplicate attachment across repos should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID1);
    assertEquals("Saved", response, "Entity save should succeed");
  }

  @Test
  @Order(4)
  void testDuplicateAttachmentRenameAcrossRepos() throws Exception {
    System.out.println(
        "Test (4) : Create new entity with sample.pdf in defaultRepositoryID, switch to virusScanRepositoryID, upload"
            + " sample.txt, rename to sample.pdf in all facets — should succeed");

    int exitCode = runUpdateEnv(defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for defaultRepositoryID");

    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Entity creation should succeed");
    entityID_rename = response;

    ClassLoader classLoader = getClass().getClassLoader();
    File pdfFile = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID_rename);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, entityName, facet[i], entityID_rename, srvpath, postData, pdfFile);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Attachment creation should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID_rename);
    assertEquals("Saved", response, "Entity save should succeed under defaultRepositoryID");

    exitCode = runUpdateEnv(virusScanRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for virusScanRepositoryID");

    response = api.editEntityDraft(appUrl, entityName, srvpath, entityID_rename);
    assertEquals("Entity in draft mode", response, "Edit entity should succeed");

    File txtFile = new File(classLoader.getResource("sample.txt").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID_rename);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, entityName, facet[i], entityID_rename, srvpath, postData, txtFile);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Upload sample.txt should succeed for " + facet[i]);
      String attachmentID2 = createResponse.get(1);

      response =
          api.renameAttachment(
              appUrl, entityName, facet[i], entityID_rename, attachmentID2, "sample.pdf");
      assertEquals(
          "Renamed",
          response,
          "Renaming to duplicate name across repos should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID_rename);
    assertEquals("Saved", response, "Entity save after rename should succeed");
  }

  @Test
  @Order(5)
  void testCreateAttachment_NonExistentRepo_FailsWithRepoInfoError() throws Exception {
    String fakeRepoId = "non-existent-repo-" + UUID.randomUUID();
    System.out.println(
        "Test (5) : Switch to non-existent repo ("
            + fakeRepoId
            + ") and attempt attachment creation — expect failure");

    // Switch to a random non-existent repository ID
    int exitCode = runUpdateEnv(fakeRepoId);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");

    // Create an entity (draft creation should still succeed)
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Entity creation should succeed");
    String entityId = response;

    // Upload an attachment to the first facet
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityId);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(appUrl, entityName, facet[0], entityId, srvpath, postData, file);

    // Attachment content upload should fail because the repo doesn't exist
    String status = createResponse.get(0);
    assertNotEquals(
        "Attachment created", status, "Attachment should fail with non-existent repository");
    assertTrue(
        status.toLowerCase().contains("repository")
            || status.toLowerCase().contains("error")
            || status.toLowerCase().contains("not found")
            || status.toLowerCase().contains("failed"),
        "Error should indicate repository issue. Got: " + status);
    System.out.println("Expected error received: " + status);
  }

  @Test
  @Order(6)
  void testRevertToDefaultRepository() throws Exception {
    System.out.println("Test (6) : Revert REPOSITORY_ID to default repository");
    int exitCode = runUpdateEnv(defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");
  }
}
