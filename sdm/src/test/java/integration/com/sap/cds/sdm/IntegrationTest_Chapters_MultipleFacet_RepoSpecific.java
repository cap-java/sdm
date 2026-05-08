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
 * Integration tests for "Displaying Attachments Specific To Repository" with chapter-level multiple
 * facets. Verifies that attachments created under one repository are not visible when the
 * application switches to a different repository, and that duplicate file names across repositories
 * are allowed for all facets (attachments, references, footnotes) on chapter entities.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_Chapters_MultipleFacet_RepoSpecific {

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
  private static String bookEntityName = "Books";
  private static String chapterEntityName = "Chapters";
  private static String entityName2 = "author";
  private static String srvpath = "AdminService";
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String repo1;
  private static String repo2;
  private static String defaultRepositoryID;
  private static ApiInterface api;

  // Entity IDs used across tests
  private static String bookID1;
  private static String chapterID1;
  private static String[] attachmentID1 = new String[3];
  private static String bookID_rename;
  private static String chapterID_rename;

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
    } else if (tenancyModel.equals("multi")) {
      clientId = credentialsProperties.getProperty("clientIDMT");
      clientSecret = credentialsProperties.getProperty("clientSecretMT");
      appUrl = credentialsProperties.getProperty("appUrlMT");
      authUrl = credentialsProperties.getProperty("authUrlMTSDC");
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
  @Order(1)
  void testSetupRepo1AndCreateAttachments() throws Exception {
    System.out.println(
        "Test (1) : Setup — switch to repo1 ("
            + repo1
            + "), create book+chapter with attachments in all facets");

    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create book
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID1 = response;

    // Create chapter inside the book
    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID1);
    assertNotEquals("Could not create entity", chapterResponse, "Chapter creation should succeed");
    chapterID1 = chapterResponse;

    // Upload attachment in each facet on the chapter
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID1);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID1, srvpath, postData, file);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Attachment creation should succeed for " + facet[i]);
      attachmentID1[i] = createResponse.get(1);
    }

    // Save via book
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Saved", response, "Book save should succeed");

    // Verify attachments are readable
    for (int i = 0; i < facet.length; i++) {
      response =
          api.readAttachment(appUrl, chapterEntityName, facet[i], chapterID1, attachmentID1[i]);
      assertEquals("OK", response, "Attachment should be readable under repo1 for " + facet[i]);

      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], chapterID1);
      assertEquals(
          1, attachments.size(), "Chapter should have 1 attachment under repo1 for " + facet[i]);
    }
  }

  @Test
  @Order(2)
  void testSwitchToRepo2AttachmentsNotVisible() throws Exception {
    System.out.println(
        "Test (2) : Switch to repo2, verify chapter attachments from repo1 are not visible");

    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    String response = api.checkEntity(appUrl, chapterEntityName, chapterID1);
    assertEquals("Entity exists", response, "Chapter should still be visible after repo switch");

    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], chapterID1);
      assertEquals(
          0,
          attachments.size(),
          "Chapter should have 0 attachments after switching to repo2 for " + facet[i]);
    }
  }

  @Test
  @Order(3)
  void testDuplicateAttachmentCreateAcrossRepos() throws Exception {
    System.out.println(
        "Test (3) : Create attachment with same name on chapter under repo2 in all facets — should succeed");

    // Still on repo2
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Entity in draft mode", response, "Edit book should succeed");

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID1);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID1, srvpath, postData, file);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Duplicate chapter attachment across repos should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Saved", response, "Book save should succeed");
  }

  @Test
  @Order(4)
  void testDuplicateAttachmentRenameAcrossRepos() throws Exception {
    System.out.println(
        "Test (4) : Create new book+chapter with sample.pdf in repo1, switch to repo2, upload"
            + " sample.txt on chapter, rename to sample.pdf in all facets — should succeed");

    // Switch to repo1
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create new book + chapter
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID_rename = response;

    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID_rename);
    assertNotEquals("Could not create entity", chapterResponse, "Chapter creation should succeed");
    chapterID_rename = chapterResponse;

    // Upload sample.pdf to chapter under repo1 in all facets
    ClassLoader classLoader = getClass().getClassLoader();
    File pdfFile = new File(classLoader.getResource("sample.pdf").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID_rename);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID_rename, srvpath, postData, pdfFile);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Attachment creation should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Saved", response, "Book save should succeed under repo1");

    // Switch to repo2
    exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    // Edit book, upload sample.txt to chapter, rename to sample.pdf
    response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Entity in draft mode", response, "Edit book should succeed");

    File txtFile = new File(classLoader.getResource("sample.txt").getFile());

    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID_rename);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID_rename, srvpath, postData, txtFile);
      assertEquals(
          "Attachment created",
          createResponse.get(0),
          "Upload sample.txt to chapter should succeed for " + facet[i]);
      String attachmentID2 = createResponse.get(1);

      response =
          api.renameAttachment(
              appUrl, chapterEntityName, facet[i], chapterID_rename, attachmentID2, "sample.pdf");
      assertEquals(
          "Renamed",
          response,
          "Renaming to duplicate name on chapter across repos should succeed for " + facet[i]);
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Saved", response, "Book save after chapter rename should succeed");
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
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", fakeRepoId);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");

    // Create a book entity (draft creation should still succeed)
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    String bookId = response;

    // Create a chapter inside the book
    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookId);
    assertNotEquals("Could not create entity", chapterResponse, "Chapter creation should succeed");
    String chapterId = chapterResponse;

    // Upload an attachment to the chapter
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterId);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], chapterId, srvpath, postData, file);

    // Save the entity — this should fail because the repo doesn't exist
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookId);
    assertNotEquals("Saved", response, "Save should fail with a non-existent repository");
    assertTrue(
        response.toLowerCase().contains("failed to get repository info")
            || response.toLowerCase().contains("repository")
            || response.toLowerCase().contains("error"),
        "Error should indicate repository issue. Got: " + response);
    System.out.println("Expected error received: " + response);
  }

  @Test
  @Order(6)
  void testRevertToDefaultRepository() throws Exception {
    System.out.println("Test (6) : Revert REPOSITORY_ID to default repository");
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");
  }
}
