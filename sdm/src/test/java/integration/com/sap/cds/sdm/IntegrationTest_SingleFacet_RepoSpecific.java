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
 * Integration tests for "Displaying Attachments Specific To Repository". Verifies that attachments
 * created under one repository are not visible when the application switches to a different
 * repository, and that duplicate file names across repositories are allowed.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_SingleFacet_RepoSpecific {

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
  private static String facetName = "attachments";
  private static String repo1;
  private static String repo2;
  private static String defaultRepositoryID;
  private static ApiInterface api;

  // Entity IDs used across tests
  private static String bookID1; // Book for top-level tests (create duplicate)
  private static String attachmentID1; // Attachment created under repo1
  private static String bookID_rename; // Separate book for rename duplicate test
  private static String bookID2; // Book for chapter-level tests (create duplicate)
  private static String chapterID1; // Chapter for nested entity tests (create duplicate)
  private static String chapterAttachmentID1; // Attachment on chapter under repo1
  private static String bookID_chapterRename; // Separate book for chapter rename test
  private static String chapterID_rename; // Separate chapter for rename test

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

  // ───────────────────────────────────────────────────────────────────────────
  // Test 1 – Setup: Switch to repo1, create entity, upload attachment
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(1)
  void testSetupRepo1AndCreateAttachments() throws Exception {
    System.out.println(
        "Test (1) : Setup — switch to repo1 (" + repo1 + "), create entity with attachment");

    // Switch to repo1
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create a book entity
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID1 = response;

    // Upload an attachment (sample.pdf) under repo1
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", bookID1);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(appUrl, bookEntityName, facetName, bookID1, srvpath, postData, file);
    assertEquals("Attachment created", createResponse.get(0), "Attachment creation should succeed");
    attachmentID1 = createResponse.get(1);

    // Save the entity
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Saved", response, "Entity save should succeed");

    // Verify attachment is readable
    response = api.readAttachment(appUrl, bookEntityName, facetName, bookID1, attachmentID1);
    assertEquals("OK", response, "Attachment should be readable under repo1");

    // Verify attachment count is 1
    List<Map<String, Object>> attachments =
        api.fetchEntityMetadata(appUrl, bookEntityName, facetName, bookID1);
    assertEquals(1, attachments.size(), "Entity should have exactly 1 attachment under repo1");
    System.out.println("Setup complete: entity " + bookID1 + " with attachment " + attachmentID1);
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 2 – Switch to repo2, verify previous attachments are not visible
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(2)
  void testSwitchToRepo2AttachmentsNotVisible() throws Exception {
    System.out.println(
        "Test (2) : Switch to repo2 ("
            + repo2
            + "), verify attachments from repo1 are not visible");

    // Switch to repo2
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    // The entity should still exist but have 0 attachments
    String response = api.checkEntity(appUrl, bookEntityName, bookID1);
    assertEquals("Entity exists", response, "Entity should still be visible after repo switch");

    List<Map<String, Object>> attachments =
        api.fetchEntityMetadata(appUrl, bookEntityName, facetName, bookID1);
    assertEquals(
        0, attachments.size(), "Entity should have 0 attachments after switching to repo2");
    System.out.println("Verified: entity " + bookID1 + " has no attachments visible under repo2");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 3 – Duplicate attachment name across repos (create)
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(3)
  void testDuplicateAttachmentCreateAcrossRepos() throws Exception {
    System.out.println(
        "Test (3) : Create attachment with same name (sample.pdf) under repo2 — should succeed");

    // Still on repo2 from previous test
    // Edit the entity to draft
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Entity in draft mode", response, "Edit entity should succeed");

    // Upload same file name (sample.pdf) under repo2
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", bookID1);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(appUrl, bookEntityName, facetName, bookID1, srvpath, postData, file);
    assertEquals(
        "Attachment created",
        createResponse.get(0),
        "Creating attachment with duplicate name across repos should succeed");

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID1);
    assertEquals("Saved", response, "Entity save should succeed");
    System.out.println("Duplicate attachment (sample.pdf) created successfully under repo2");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 4 – Duplicate attachment name via rename across repos
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(4)
  void testDuplicateAttachmentRenameAcrossRepos() throws Exception {
    System.out.println(
        "Test (4) : Create new entity with sample.pdf in repo1, switch to repo2, upload"
            + " sample.txt, rename to sample.pdf — should succeed");

    // Switch to repo1 to create a fresh entity with sample.pdf
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create a new entity under repo1
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID_rename = response;

    // Upload sample.pdf under repo1
    ClassLoader classLoader = getClass().getClassLoader();
    File pdfFile = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", bookID_rename);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, bookEntityName, facetName, bookID_rename, srvpath, postData, pdfFile);
    assertEquals("Attachment created", createResponse.get(0), "Attachment creation should succeed");
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Saved", response, "Entity save should succeed under repo1");

    // Switch to repo2
    exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    // Edit the entity and upload sample.txt, then rename to sample.pdf
    response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Entity in draft mode", response, "Edit entity should succeed");

    File txtFile = new File(classLoader.getResource("sample.txt").getFile());
    postData = new HashMap<>();
    postData.put("up__ID", bookID_rename);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    createResponse =
        api.createAttachment(
            appUrl, bookEntityName, facetName, bookID_rename, srvpath, postData, txtFile);
    assertEquals("Attachment created", createResponse.get(0), "Upload sample.txt should succeed");
    String attachmentID2 = createResponse.get(1);

    // Rename sample.txt to sample.pdf (same name as attachment in repo1 — not in repo2)
    response =
        api.renameAttachment(
            appUrl, bookEntityName, facetName, bookID_rename, attachmentID2, "sample.pdf");
    assertEquals("Renamed", response, "Renaming to duplicate name across repos should succeed");

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_rename);
    assertEquals("Saved", response, "Entity save after rename should succeed");
    System.out.println("Renamed sample.txt to sample.pdf under repo2 — success");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 5 – Nested entities: repeat scenarios for Chapters
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  @Order(5)
  void testNestedEntitySetupRepo1() throws Exception {
    System.out.println(
        "Test (5a) : Switch to repo1 ("
            + repo1
            + "), create book+chapter with attachment on chapter");

    // Switch to repo1
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create a book
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID2 = response;

    // Create a chapter inside the book
    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID2);
    assertNotEquals("Could not create entity", chapterResponse, "Chapter creation should succeed");
    chapterID1 = chapterResponse;

    // Upload attachment to chapter
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID1);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facetName, chapterID1, srvpath, postData, file);
    assertEquals(
        "Attachment created", createResponse.get(0), "Chapter attachment creation should succeed");
    chapterAttachmentID1 = createResponse.get(1);

    // Save the book (saves chapter too)
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID2);
    assertEquals("Saved", response, "Book save should succeed");

    // Verify chapter attachment is readable
    response =
        api.readAttachment(appUrl, chapterEntityName, facetName, chapterID1, chapterAttachmentID1);
    assertEquals("OK", response, "Chapter attachment should be readable under repo1");

    List<Map<String, Object>> attachments =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, chapterID1);
    assertEquals(1, attachments.size(), "Chapter should have exactly 1 attachment under repo1");
    System.out.println(
        "Nested setup complete: book "
            + bookID2
            + ", chapter "
            + chapterID1
            + ", attachment "
            + chapterAttachmentID1);
  }

  @Test
  @Order(6)
  void testNestedEntitySwitchToRepo2() throws Exception {
    System.out.println(
        "Test (5b) : Switch to repo2, verify chapter attachments from repo1 are not visible");

    // Switch to repo2
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    // Chapter should still exist but have 0 attachments
    String response = api.checkEntity(appUrl, chapterEntityName, chapterID1);
    assertEquals("Entity exists", response, "Chapter should still be visible after repo switch");

    List<Map<String, Object>> attachments =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, chapterID1);
    assertEquals(
        0, attachments.size(), "Chapter should have 0 attachments after switching to repo2");
    System.out.println(
        "Verified: chapter " + chapterID1 + " has no attachments visible under repo2");
  }

  @Test
  @Order(7)
  void testNestedEntityDuplicateCreate() throws Exception {
    System.out.println(
        "Test (5c) : Create attachment with same name on chapter under repo2 — should succeed");

    // Still on repo2
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID2);
    assertEquals("Entity in draft mode", response, "Edit book should succeed");

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID1);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facetName, chapterID1, srvpath, postData, file);
    assertEquals(
        "Attachment created",
        createResponse.get(0),
        "Duplicate chapter attachment across repos should succeed");

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID2);
    assertEquals("Saved", response, "Book save should succeed");
    System.out.println(
        "Duplicate attachment (sample.pdf) created on chapter under repo2 — success");
  }

  @Test
  @Order(8)
  void testNestedEntityDuplicateRename() throws Exception {
    System.out.println(
        "Test (5d) : Create new book+chapter with sample.pdf in repo1, switch to repo2, upload"
            + " sample.txt on chapter, rename to sample.pdf — should succeed");

    // Switch to repo1 to create a fresh book+chapter with sample.pdf on chapter
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo1);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo1");

    // Create a new book
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    bookID_chapterRename = response;

    // Create a chapter inside the book
    String chapterResponse =
        api.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, bookID_chapterRename);
    assertNotEquals("Could not create entity", chapterResponse, "Chapter creation should succeed");
    chapterID_rename = chapterResponse;

    // Upload sample.pdf to chapter under repo1
    ClassLoader classLoader = getClass().getClassLoader();
    File pdfFile = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID_rename);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facetName, chapterID_rename, srvpath, postData, pdfFile);
    assertEquals(
        "Attachment created", createResponse.get(0), "Chapter attachment creation should succeed");
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_chapterRename);
    assertEquals("Saved", response, "Book save should succeed under repo1");

    // Switch to repo2
    exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", repo2);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0 for repo2");

    // Edit the book, upload sample.txt to chapter, rename to sample.pdf
    response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID_chapterRename);
    assertEquals("Entity in draft mode", response, "Edit book should succeed");

    File txtFile = new File(classLoader.getResource("sample.txt").getFile());
    postData = new HashMap<>();
    postData.put("up__ID", chapterID_rename);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facetName, chapterID_rename, srvpath, postData, txtFile);
    assertEquals(
        "Attachment created", createResponse.get(0), "Upload sample.txt to chapter should succeed");
    String chapterAttachmentID2 = createResponse.get(1);

    response =
        api.renameAttachment(
            appUrl,
            chapterEntityName,
            facetName,
            chapterID_rename,
            chapterAttachmentID2,
            "sample.pdf");
    assertEquals(
        "Renamed", response, "Renaming to duplicate name on chapter across repos should succeed");

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID_chapterRename);
    assertEquals("Saved", response, "Book save after chapter rename should succeed");
    System.out.println("Renamed sample.txt to sample.pdf on chapter under repo2 — success");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 9 – Create attachment with non-existent repository ID → error
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(9)
  void testCreateAttachment_NonExistentRepo_FailsWithRepoInfoError() throws Exception {
    String fakeRepoId = "non-existent-repo-" + UUID.randomUUID();
    System.out.println(
        "Test (9) : Switch to non-existent repo ("
            + fakeRepoId
            + ") and attempt attachment creation — expect failure");

    // Switch to a random non-existent repository ID
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", fakeRepoId);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");

    // Create a book entity (draft creation should still succeed)
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    assertNotEquals("Could not create entity", response, "Book creation should succeed");
    String bookId = response;

    // Upload an attachment
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", bookId);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(appUrl, bookEntityName, facetName, bookId, srvpath, postData, file);

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

  // ───────────────────────────────────────────────────────────────────────────
  // Test 10 – Revert REPOSITORY_ID back to default
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(10)
  void testRevertToDefaultRepository() throws Exception {
    System.out.println(
        "Test (10) : Revert REPOSITORY_ID to default repository: " + defaultRepositoryID);
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--value", defaultRepositoryID);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");
    System.out.println("Reverted to default repository: " + defaultRepositoryID);
  }
}
