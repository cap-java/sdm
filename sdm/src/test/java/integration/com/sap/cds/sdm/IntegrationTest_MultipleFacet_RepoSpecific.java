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
  private static String repo1;
  private static String repo2;
  private static String defaultRepositoryID;
  private static ApiInterface api;

  // Entity IDs used across tests
  private static String entityID1;
  private static String[] attachmentID1 = new String[3];
  private static String entityID_rename;

  @BeforeAll
  static void setup() throws IOException {
    Properties credentialsProperties = Credentials.getCredentials();
    String tenancyModel = System.getProperty("tenancyModel");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");
    repo1 = credentialsProperties.getProperty("repo1");
    repo2 = credentialsProperties.getProperty("repo2");
    defaultRepositoryID = credentialsProperties.getProperty("defaultRepositoryID");

    if (tenancyModel.equals("single")) {
      clientId = credentialsProperties.getProperty("clientID");
      clientSecret = credentialsProperties.getProperty("clientSecret");
      appUrl = credentialsProperties.getProperty("appUrl");
      authUrl = credentialsProperties.getProperty("authUrl");
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
    if (response.code() != 200) {
      System.out.println("Token generation failed. Response code: " + response.code());
      System.out.println("Error body: " + response.body().string());
    }
    token = new ObjectMapper().readTree(response.body().string()).get("access_token").asText();
    response.close();

    Map<String, String> config = new HashMap<>();
    config.put("Authorization", "Bearer " + token);
    config.put("serviceName", serviceName);
    api = new Api(config);
  }

  @Test
  @Order(1)
  void testSetupRepo1AndCreateAttachments() throws Exception {
    System.out.println(
        "Test (1) : Setup — switch to repo1 ("
            + repo1
            + "), create entity with attachments in all facets");

    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

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
      assertEquals("OK", response, "Attachment should be readable under repo1 for " + facet[i]);

      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, facet[i], entityID1);
      assertEquals(
          1, attachments.size(), "Entity should have 1 attachment under repo1 for " + facet[i]);
    }
  }

  @Test
  @Order(2)
  void testSwitchToRepo2AttachmentsNotVisible() throws Exception {
    System.out.println(
        "Test (2) : Switch to repo2, verify attachments from repo1 are not visible in any facet");

    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    String response = api.checkEntity(appUrl, entityName, entityID1);
    assertEquals("Entity exists", response, "Entity should still be visible after repo switch");

    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, facet[i], entityID1);
      assertEquals(
          0,
          attachments.size(),
          "Entity should have 0 attachments after switching to repo2 for " + facet[i]);
    }
  }

  @Test
  @Order(3)
  void testDuplicateAttachmentCreateAcrossRepos() throws Exception {
    System.out.println(
        "Test (3) : Create attachment with same name under repo2 in all facets — should succeed");

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
        "Test (4) : Create new entity with sample.pdf in repo1, switch to repo2, upload"
            + " sample.txt, rename to sample.pdf in all facets — should succeed");

    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

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
    assertEquals("Saved", response, "Entity save should succeed under repo1");

    exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

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
  void testRevertToDefaultRepository() throws Exception {
    System.out.println("Test (5) : Revert REPOSITORY_ID to default repository");
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");
  }
}
