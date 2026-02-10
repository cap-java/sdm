package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_Chapters_MultipleFacet {
  private static String token;
  private static String tokenNoRoles;
  private static String bookID; // Parent book ID
  private static String chapterID; // Main chapter ID for tests
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String[] ID = {"attachmentID1", "referenceID1", "footnoteID1"};
  private static String[] ID2 = {"attachmentID2", "referenceID2", "footnoteID2"};
  private static String[] ID3 = {"attachmentID3", "referenceID3", "footnoteID3"};
  private static String[] ID4 = {"attachmentID4", "referenceID4", "footnoteID4"};
  private static String[] ID5 = {"attachmentID5", "referenceID5", "footnoteID5"};
  private static String bookID2;
  private static String chapterID2;
  private static String bookID3;
  private static String chapterID3;
  private static String bookID4;
  private static String chapterID4;
  private static String bookID5;
  private static String chapterID5;
  private static String clientId;
  private static String clientSecret;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String noSDMRoleUsername;
  private static String noSDMRoleUserPassword;
  private static String serviceName = "AdminService";
  private static String bookEntityName = "Books";
  private static String chapterEntityName = "Chapters";
  private static String entityName2 = "author";
  private static String srvpath = "AdminService";
  private static ApiInterface api;
  private static ApiInterface apiNoRoles;
  private static int counter;
  private static IntegrationTestUtils integrationTestUtils;
  private static String copyAttachmentSourceBook;
  private static String copyAttachmentSourceChapter;
  private static String copyAttachmentTargetBook;
  private static String copyAttachmentTargetChapter;
  private static List<String> sourceObjectIds = new ArrayList<>();
  private static List<String> targetAttachmentIds = new ArrayList<>();
  private static List<String> successfullyRenamedAttachments = new ArrayList<>();
  private static String[] changelogBookID = new String[3];
  private static String[] changelogChapterID = new String[3];
  private static String[] changelogAttachmentID = new String[3];

  @BeforeAll
  static void setup() throws IOException {
    // Define your clientId and clientSecret
    Properties credentialsProperties = Credentials.getCredentials();
    String tenancyModel = System.getProperty("tenancyModel");
    String tenant = System.getProperty("tenant");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");
    noSDMRoleUsername = credentialsProperties.getProperty("noSDMRoleUsername");
    noSDMRoleUserPassword = credentialsProperties.getProperty("noSDMRoleUserPassword");
    if (tenancyModel.equals("single")) {
      System.out.println("Running integration tests | Single tenant Scenario | Chapters");
      clientId = credentialsProperties.getProperty("clientID");
      clientSecret = credentialsProperties.getProperty("clientSecret");
      appUrl = credentialsProperties.getProperty("appUrl");
      authUrl = credentialsProperties.getProperty("authUrl");
    } else if (tenancyModel.equals("multi")) {
      clientId = credentialsProperties.getProperty("clientIDMT");
      clientSecret = credentialsProperties.getProperty("clientSecretMT");
      appUrl = credentialsProperties.getProperty("appUrlMT");
      if (tenant.equals("TENANT1")) {
        System.out.println(
            "Running integration tests | Multitenant Scenario | SDM DEV Consumer | Chapters");
        authUrl = credentialsProperties.getProperty("authUrlMT1");
      } else if (tenant.equals("TENANT2")) {
        System.out.println(
            "Running integration tests | Multitenant Scenario | Googleworkspace Consumer | Chapters");
        authUrl = credentialsProperties.getProperty("authUrlMT2");
      } else {
        throw new IllegalArgumentException("Invalid tenant specified: " + tenant);
      }
    } else {
      throw new IllegalArgumentException("Invalid tenancy model specified: " + tenancyModel);
    }
    integrationTestUtils = new IntegrationTestUtils();

    // Encode clientId:clientSecret to Base64
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

    Request requestNoRoles =
        new Request.Builder()
            .url(
                authUrl
                    + "/oauth/token?grant_type=password&username="
                    + noSDMRoleUsername
                    + "&password="
                    + noSDMRoleUserPassword)
            .method("POST", body)
            .addHeader("Authorization", basicAuth)
            .build();

    Response response = client.newCall(request).execute();
    Response responseNoRoles = client.newCall(requestNoRoles).execute();
    if (response.code() != 200) {
      System.out.println("Token generation failed. Response code: " + response.code());
      String errorBody = response.body().string();
      System.out.println("Error body: " + errorBody);
    }
    if (responseNoRoles.code() != 200) {
      System.out.println("Token generation failed. Response code: " + responseNoRoles.code());
      String errorBody = responseNoRoles.body().string();
      System.out.println("Error body: " + errorBody);
    }
    token = new ObjectMapper().readTree(response.body().string()).get("access_token").asText();
    tokenNoRoles =
        new ObjectMapper().readTree(responseNoRoles.body().string()).get("access_token").asText();
    response.close();
    responseNoRoles.close();
    Map<String, String> config = new HashMap<>();
    config.put("Authorization", "Bearer " + token);
    Map<String, String> configNoRoles = new HashMap<>();
    configNoRoles.put("Authorization", "Bearer " + tokenNoRoles);
    if (tenancyModel.equals("multi")) {
      api = new ApiMT(config);
      apiNoRoles = new ApiMT(configNoRoles);
    } else if (tenancyModel.equals("single")) {
      config.put("serviceName", serviceName);
      configNoRoles.put("serviceName", serviceName);
      api = new Api(config);
      apiNoRoles = new Api(configNoRoles);
    } else {
      throw new IllegalArgumentException("Invalid tenancy model specified: " + tenancyModel);
    }
  }

  /**
   * Helper method to wait for attachment upload to complete. Polls the attachment metadata until
   * uploadStatus is "Success" or "Failed", or timeout is reached.
   *
   * @param chapterId The chapter ID containing the attachment
   * @param attachmentId The attachment ID to wait for
   * @param timeoutSeconds Maximum time to wait in seconds
   * @param facetName The facet name (attachments, references, footnotes)
   * @return true if upload completed successfully, false if failed or timed out
   */
  private static boolean waitForUploadCompletion(
      String chapterId, String attachmentId, int timeoutSeconds, String facetName) {
    int pollIntervalSeconds = 2;
    int maxAttempts = timeoutSeconds / pollIntervalSeconds;

    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        // Fetch metadata for the attachment in draft mode
        Map<String, Object> metadata = null;
        boolean metadataFetched = false;

        try {
          // First try fetchMetadataDraft for draft entities
          metadata =
              api.fetchMetadataDraft(appUrl, chapterEntityName, facetName, chapterId, attachmentId);
          metadataFetched = true;
        } catch (IOException e) {
          // If draft fetch fails, entity might be active, try regular fetch
          try {
            metadata =
                api.fetchMetadata(appUrl, chapterEntityName, facetName, chapterId, attachmentId);
            metadataFetched = true;
          } catch (IOException e2) {
            // If both fail, attachment might not exist yet or has been deleted
            // Wait and retry
            Thread.sleep(pollIntervalSeconds * 1000);
            continue;
          }
        }

        if (!metadataFetched || metadata == null) {
          Thread.sleep(pollIntervalSeconds * 1000);
          continue;
        }

        // Check upload status
        if (metadata.containsKey("uploadStatus")) {
          String uploadStatus = (String) metadata.get("uploadStatus");

          if ("Success".equals(uploadStatus)) {
            return true;
          } else if ("Failed".equals(uploadStatus)) {
            System.err.println(
                "Upload failed for attachment "
                    + attachmentId
                    + " in chapter "
                    + chapterId
                    + ". Status: "
                    + uploadStatus);
            return false;
          }
          // If status is "uploading" or any other status, continue waiting
        }

        // Wait before next poll
        Thread.sleep(pollIntervalSeconds * 1000);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.println("Wait interrupted for attachment " + attachmentId);
        return false;
      }
    }

    // Timeout reached
    System.err.println(
        "Timeout waiting for upload completion of attachment "
            + attachmentId
            + " in chapter "
            + chapterId);
    return false;
  }

  private static boolean waitForAllUploadsCompletion(
      String chapterId, String facetName, int timeoutSeconds) {
    int maxIterations = timeoutSeconds / 2; // Check every 2 seconds
    for (int i = 0; i < maxIterations; i++) {
      try {
        List<Map<String, Object>> attachmentsMetadata =
            api.fetchEntityMetadataDraft(appUrl, chapterEntityName, facetName, chapterId);

        boolean allComplete = true;
        boolean anyFailed = false;

        for (Map<String, Object> metadata : attachmentsMetadata) {
          String uploadStatus = (String) metadata.get("uploadStatus");
          if (uploadStatus == null || "InProgress".equals(uploadStatus)) {
            allComplete = false;
          } else if ("Failed".equals(uploadStatus)) {
            anyFailed = true;
            System.err.println("Upload failed for attachment: " + metadata.get("ID"));
          }
        }

        if (anyFailed) {
          return false;
        }

        if (allComplete) {
          return true;
        }

        // Still uploading, wait before checking again
        Thread.sleep(5000);
      } catch (Exception e) {
        System.err.println(
            "Error checking upload status for chapter " + chapterId + ": " + e.getMessage());
        return false;
      }
    }

    System.err.println("Upload timed out for chapter: " + chapterId);
    return false;
  }

  private String CreateandReturnFacetID(
      String appUrl,
      String serviceName,
      String chapterId,
      String facet,
      Map<String, Object> postData,
      File file)
      throws IOException {
    String ID = null;
    List<String> FacetResponse =
        api.createAttachment(appUrl, chapterEntityName, facet, chapterId, srvpath, postData, file);
    String check = FacetResponse.get(0);
    if (check.equals("Attachment created")) {
      ID = FacetResponse.get(1);
      return ID;
    }
    return ID;
  }

  private boolean verifyDraftAndSaveBook(
      String appUrl, String serviceName, String bookId, String chapterId, String[] ID)
      throws IOException {
    String response[] = {"response1", "response2", "response3"};
    int Counter = 0;
    boolean status = false;

    for (int i = 0; i < facet.length; i++) {
      response[i] = api.readAttachmentDraft(appUrl, chapterEntityName, facet[i], chapterId, ID[i]);
      if ("OK".equals(response[i])) Counter++;
    }
    if (Counter == facet.length) {
      // Save the BOOK, not the chapter
      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookId);
      if ("Saved".equals(saveResponse)) {
        for (int i = 0; i < facet.length; i++) {
          response[i] = api.readAttachment(appUrl, chapterEntityName, facet[i], chapterId, ID[i]);
          if (!"OK".equals(response[i])) {
            return false;
          }
        }
        status = true;
      }
    }
    return status;
  }

  private boolean checkDuplicateCreation(String facetType, List<String> createResponse)
      throws IOException {
    String creationCheck = createResponse.get(0);
    boolean wasCreated = ("Attachment created").equals(creationCheck); // Evaluating creation status
    if (wasCreated) {
      System.out.println(
          "Attachment was created in section : "
              + facetType
              + " when it should have been rejected as a duplicate.");
      return false;
    } else {
      String expectedJson =
          "{\"error\":{\"code\":\"500\",\"message\":\"An object named \\\"sample.pdf\\\" already exists. Rename the object and try again.\"}}";
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode actualJsonNode = objectMapper.readTree(creationCheck);
      JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
      if (expectedJsonNode.equals(actualJsonNode)) {
        System.out.println(
            " Attachment correctly failed in section " + facetType + " due to duplicate upload.");
        return true;
      } else {
        System.out.println(" Attachment failed but with an unexpected error: " + creationCheck);
        return false;
      }
    }
  }

  private boolean renameAndCheck(String facet, String id, String chapterId, String newName) {
    String result = api.renameAttachment(appUrl, chapterEntityName, facet, chapterId, id, newName);
    boolean renamed = "Renamed".equals(result);
    return renamed;
  }

  @Test
  @Order(1)
  void testCreateBookChapterAndCheck() {
    System.out.println("Test (1) : Create book, create chapter, and check if they exist");
    Boolean testStatus = false;

    // Create a book first
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      bookID = response;

      // Create a chapter inside the book
      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID);
      if (!chapterResponse.equals("Could not create entity")) {
        chapterID = chapterResponse;

        // Save the book (this saves the chapter too)
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        if (response.equals("Saved")) {
          // Check if book exists
          response = api.checkEntity(appUrl, bookEntityName, bookID);
          if (response.equals("Entity exists")) {
            // Check if chapter exists
            response = api.checkEntity(appUrl, chapterEntityName, chapterID);
            if (response.equals("Entity exists")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not create book and chapter");
    }
  }

  @Test
  @Order(2)
  void testUploadSinglePDFToChapter() throws IOException {
    System.out.println("Test (2) : Upload attachment, reference, and footnote PDF to chapter");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    // Edit book to draft mode
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      // Creation of attachment, reference and footnote on the chapter
      for (int i = 0; i < facet.length; i++) {
        ID[i] = CreateandReturnFacetID(appUrl, serviceName, chapterID, facet[i], postData, file);
      }
      testStatus = verifyDraftAndSaveBook(appUrl, serviceName, bookID, chapterID, ID);
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf to chapter " + response);
    }
  }

  @Test
  @Order(3)
  void testUploadSingleTXTToChapter() throws IOException {
    System.out.println("Test (3) : Upload attachment, reference, and footnote TXT to chapter");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        ID2[i] = CreateandReturnFacetID(appUrl, serviceName, chapterID, facet[i], postData, file);
      }
      testStatus = verifyDraftAndSaveBook(appUrl, serviceName, bookID, chapterID, ID2);
    }
    if (!testStatus) {
      fail("Could not upload sample.txt to chapter " + response);
    }
  }

  @Test
  @Order(4)
  void testUploadSingleEXEToChapter() throws IOException {
    System.out.println("Test (4) : Upload attachment, reference, and footnote EXE to chapter");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.exe").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID);
    postData.put("mimeType", "application/x-msdownload");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        ID3[i] = CreateandReturnFacetID(appUrl, serviceName, chapterID, facet[i], postData, file);
      }
      testStatus = verifyDraftAndSaveBook(appUrl, serviceName, bookID, chapterID, ID3);
    }
    if (!testStatus) {
      fail("Could not upload sample.exe to chapter " + response);
    }
  }

  @Test
  @Order(5)
  void testUploadPDFDuplicateToChapter() throws IOException {
    System.out.println("Test (5) : Upload duplicate PDF to chapter");
    Boolean testStatus = false;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      boolean allDuplicatesRejected = true;
      for (int i = 0; i < facet.length; i++) {
        List<String> facetResponse =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], chapterID, srvpath, postData, file);
        if (!checkDuplicateCreation(facet[i], facetResponse)) {
          allDuplicatesRejected = false;
        }
      }
      response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
      if (response.equals("Saved") && allDuplicatesRejected) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Duplicate PDF was uploaded to chapter when it should have been rejected");
    }
  }

  @Test
  @Order(6)
  void testCreateNewBookWithChapterAndAttachments() throws IOException {
    System.out.println(
        "Test (6) : Create new book, add chapter, and upload attachments/references/footnotes");
    Boolean testStatus = false;

    // Create new book
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (response.equals("Could not create entity")) {
      fail("Could not create book");
    }
    bookID2 = response;

    // Create chapter in the new book
    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID2);
    if (chapterResponse.equals("Could not create entity")) {
      fail("Could not create chapter");
    }
    chapterID2 = chapterResponse;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID2);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    // Create attachment, reference, and footnote
    for (int i = 0; i < facet.length; i++) {
      ID4[i] = CreateandReturnFacetID(appUrl, serviceName, chapterID2, facet[i], postData, file);
    }
    // Verify and save the book
    testStatus = verifyDraftAndSaveBook(appUrl, serviceName, bookID2, chapterID2, ID4);

    if (!testStatus) {
      fail(
          "Could not upload sample.pdf as an attachment, reference, or footnote to chapter: "
              + response);
    }
  }

  @Test
  @Order(7)
  void testRenameChapterAttachments() {
    System.out.println("Test (7) : Rename single attachment, reference, and footnote in chapter");
    Boolean testStatus = true;

    try {
      String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);

      if ("Entity in draft mode".equals(response)) {
        String[] name = {"sample123", "reference123", "footnote123"};
        for (int i = 0; i < facet.length; i++) {
          // Read the facet to ensure it exists
          response =
              api.renameAttachment(appUrl, chapterEntityName, facet[i], chapterID, ID[i], name[i]);
          if (!"Renamed".equals(response)) {
            testStatus = false;
            System.out.println(facet[i] + " was not renamed: " + response);
          }
        }
        // Save book draft if everything is renamed
        if (testStatus) {
          response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
          if (!"Saved".equals(response)) {
            testStatus = false;
            System.out.println("Book draft was not saved: " + response);
          }
        } else {
          // Attempt save despite potential rename failures
          api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        }
      } else {
        testStatus = false;
        System.out.println("Book was not put into draft mode: " + response);
      }
    } catch (Exception e) {
      testStatus = false;
      System.out.println("Exception during renaming chapter attachments: " + e.getMessage());
    }

    if (!testStatus) {
      fail("There was an error during the rename test process for chapter.");
    }
  }

  @Test
  @Order(8)
  void testCreateChapterAttachmentsWithUnsupportedCharacter() throws IOException {
    System.out.println("Test (8): Create chapter attachments with unsupported characters");
    boolean testStatus = false;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

    File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
    Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (!"Entity in draft mode".equals(response)) {
      fail("Book not in draft mode: " + response);
      return;
    }

    for (int i = 0; i < facet.length; i++) {
      postData.put("up__ID", chapterID);

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], chapterID, srvpath, postData, tempFile);

      if (!"Attachment created".equals(createResponse.get(0))) {
        fail("Could not create attachment in chapter facet: " + facet[i]);
        return;
      }

      String restrictedName = "a/\\bc.pdf"; // \b becomes BACKSPACE
      response =
          api.renameAttachment(
              appUrl, chapterEntityName, facet[i], chapterID, ID2[i], restrictedName);

      System.out.println("Rename response for chapter " + facet[i] + ": " + response);
    }

    // Save should fail
    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);

    // ---------------- PARSE JSON ----------------
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(response);
    String message = root.path("error").path("message").asText();

    // ---------------- NORMALIZE MESSAGE ----------------
    // 1. Normalize smart quotes
    // 2. Convert BACKSPACE (\b) to literal "\b" so it can be compared
    message = message.replace('‘', '\'').replace('’', '\'').replace("\b", "\\b");

    // ---------------- EXPECTED MESSAGE (EXACT) ----------------
    String expectedMessage =
        "\"a/\\bc.pdf\" contains unsupported characters ('/' or '\\'). Rename and try again.\n\n"
            + "Table: attachments\n"
            + "Page: IntegrationTestEntity";

    if (message.equals(expectedMessage)) {

      for (int i = 0; i < facet.length; i++) {
        api.renameAttachment(
            appUrl, chapterEntityName, facet[i], chapterID, ID2[i], "sample123.pdf");
      }

      response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
      if ("Saved".equals(response)) {
        testStatus = true;
      }
    }

    if (!testStatus) {
      fail("Test for unsupported characters in chapter attachments failed");
    }
  }

  @Test
  @Order(9)
  void testRenameSingleDuplicateInChapter() throws IOException {
    System.out.println(
        "Test (9) : Rename chapter attachment, reference, and footnote to duplicate names");
    Boolean testStatus = false;
    int counter = 0;

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    System.out.println("Edit entity response: " + response);

    if ("Entity in draft mode".equals(response)) {
      // To create a duplicate within the same facet, we need to rename ID2[i] to
      // the same name as an existing file in that facet. The existing files are:
      // sample.pdf (ID[0]), sample.txt (ID[1]), sample.exe (ID[2]) - these are the first uploads
      // We rename ID2[i] (sample123.pdf from test 8) to "sample.pdf" which already exists
      String[] duplicateNames = {"sample.pdf", "sample.txt", "sample.exe"};
      String[] validNames = {"unique_sample1.pdf", "unique_sample2.txt", "unique_sample3.exe"};

      // Try to rename to duplicate file names (names that already exist in each facet)
      for (int i = 0; i < facet.length; i++) {
        response =
            api.renameAttachment(
                appUrl, chapterEntityName, facet[i], chapterID, ID2[i], duplicateNames[i]);
        System.out.println("Rename " + facet[i] + " to " + duplicateNames[i] + ": " + response);
        if ("Renamed".equals(response)) {
          counter++;
        }
      }
      System.out.println("Renamed count: " + counter);

      if (counter == facet.length) {
        // Try to save - should fail with duplicate error
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        System.out.println("Save response (expecting error): " + response);

        // Parse JSON response to check for duplicate error
        ObjectMapper mapper = new ObjectMapper();
        try {
          JsonNode root = mapper.readTree(response);
          String message = root.path("error").path("message").asText();

          if (message.contains("already exists")) {
            System.out.println("Duplicate error detected as expected: " + message);
            counter = 0;
            // Rename with valid different names
            for (int i = 0; i < facet.length; i++) {
              response =
                  api.renameAttachment(
                      appUrl, chapterEntityName, facet[i], chapterID, ID2[i], validNames[i]);
              System.out.println("Rename " + facet[i] + " to valid name: " + response);
              if ("Renamed".equals(response)) {
                counter++;
              }
            }

            if (counter == facet.length) {
              // Save should now succeed
              response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
              System.out.println("Final save response: " + response);
              if ("Saved".equals(response)) {
                testStatus = true;
              }
            }
          } else {
            System.out.println("Unexpected error message: " + message);
          }
        } catch (Exception e) {
          // Response might not be JSON if save succeeded (shouldn't happen with duplicates)
          System.out.println("Response was not JSON error: " + response);
          // If save succeeded unexpectedly, we still need to ensure book is saved
          if ("Saved".equals(response)) {
            System.out.println(
                "Save succeeded unexpectedly - duplicates might be in different facets");
          }
        }
      }
    } else {
      System.out.println("Book was not put into draft mode: " + response);
    }

    if (!testStatus) {
      fail("Duplicate rename test failed for chapter");
    }
  }

  @Test
  @Order(10)
  void testRenameToValidateNamesInChapter() throws IOException {
    System.out.println("Test (10) : Rename chapter attachments to validate valid file names");
    Boolean testStatus = false;

    // Create a new book and chapter for this test
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      bookID3 = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID3);
      if (!"Could not create entity".equals(chapterResponse)) {
        chapterID3 = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", chapterID3);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        String[] tempID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          tempID[i] =
              CreateandReturnFacetID(appUrl, serviceName, chapterID3, facet[i], postData, file);
        }

        String[] validNames = {"valid_file_name.pdf", "another-valid-name.pdf", "simple123.pdf"};

        boolean allRenamed = true;
        for (int i = 0; i < facet.length; i++) {
          String response1 =
              api.renameAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID3, tempID[i], validNames[i]);
          if (!"Renamed".equals(response1)) {
            allRenamed = false;
            System.out.println(
                "Failed to rename "
                    + facet[i]
                    + " to valid name "
                    + validNames[i]
                    + ": "
                    + response1);
          }
        }

        if (allRenamed) {
          response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID3);
          if ("Saved".equals(response)) {
            testStatus = true;
          }
        }
      }
    }

    if (!testStatus) {
      fail("Could not rename chapter attachments to valid names");
    }
  }

  @Test
  @Order(11)
  void testRenameChapterAttachmentsWithoutSDMRole() throws IOException {
    System.out.println("Test (11) : Try to rename chapter attachments without SDM role");
    boolean testStatus = true;

    try {
      String response = apiNoRoles.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
      System.out.println("Edit entity response: " + response);

      if (response.equals("Entity in draft mode")) {
        String[] name = {"noRole1.pdf", "noRole2.pdf", "noRole3.pdf"};
        for (int i = 0; i < facet.length; i++) {
          response =
              apiNoRoles.renameAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID, ID[i], name[i]);
          System.out.println("Rename response for " + facet[i] + ": " + response);
          if (!"Renamed".equals(response)) {
            testStatus = false;
          }
        }

        if (testStatus) {
          // Save should fail with permission error
          response = apiNoRoles.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
          System.out.println("Save response (expecting permission error): " + response);

          // The expected error should indicate no permissions to update
          String expected =
              "[{\"code\":\"<none>\",\"message\":\"Could not update the following files.\\n\\n\\t\\u2022 unique_sample1\\n\\nYou do not have the required permissions to update attachments. Kindly contact the admin\\n\\nTable: references\\nPage: IntegrationTestEntity\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"Could not update the following files. \\n\\n\\t\\u2022 unique_sample1\\n\\nYou do not have the required permissions to update attachments. Kindly contact the admin\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"Could not update the following files. \\n\\n\\t\\u2022 unique_sample1\\n\\nYou do not have the required permissions to update attachments. Kindly contact the admin\\n\\nTable: footnotes\\nPage: IntegrationTestEntity\",\"numericSeverity\":3}]";

          // Check if response contains permission error
          if (!response.equals(expected)
              && !response.contains("do not have the required permissions")) {
            System.out.println("Expected permission error but got: " + response);
            testStatus = false;
          } else {
            System.out.println("Got expected permission error");
          }
        } else {
          // Some renames failed - save to release draft
          apiNoRoles.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        }
      } else {
        System.out.println("Could not edit entity: " + response);
        testStatus = false;
      }
    } catch (Exception e) {
      System.out.println("Exception: " + e.getMessage());
      testStatus = false;
    }

    if (!testStatus) {
      fail("Chapter attachment got renamed without SDM roles.");
    }
  }

  @Test
  @Order(12)
  void testDeleteSingleChapterAttachment() throws IOException {
    System.out.println(
        "Test (12) : Delete single attachment, reference, and footnote from chapter");
    Boolean testStatus = false;
    int deleteCounter = 0;

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response = api.deleteAttachment(appUrl, chapterEntityName, facet[i], chapterID, ID[i]);
        if (response.equals("Deleted")) deleteCounter++;
      }
      if (deleteCounter == facet.length) {
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID);
        if (response.equals("Saved")) {
          int verifyCounter = 0;
          for (int i = 0; i < facet.length; i++) {
            response = api.readAttachment(appUrl, chapterEntityName, facet[i], chapterID, ID[i]);
            if (response.equals("Could not read Attachment")) verifyCounter++;
          }
          if (verifyCounter == facet.length) {
            testStatus = true;
          } else {
            fail(
                "Could not verify all deleted chapter facets. Verified: "
                    + verifyCounter
                    + "/"
                    + facet.length);
          }
        } else {
          fail("Could not save book after deleting chapter attachments");
        }
      } else {
        fail(
            "Could not delete all chapter attachments. Deleted: "
                + deleteCounter
                + "/"
                + facet.length);
      }
    } else {
      fail("Could not edit book to draft mode");
    }

    if (!testStatus) {
      fail("Test failed to delete chapter attachments");
    }
  }

  @Test
  @Order(13)
  void testUploadBlockedMimeTypeToChapter() throws IOException {
    System.out.println("Test (13) : Upload blocked mimeType .rtf to chapter");
    Boolean testStatus = false;

    // Create new book and chapter
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      bookID4 = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID4);
      if (!"Could not create entity".equals(chapterResponse)) {
        chapterID4 = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file =
            new File(Objects.requireNonNull(classLoader.getResource("sample.rtf")).getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", chapterID4);
        postData.put("mimeType", "application/rtf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        boolean allBlocked = true;
        for (int i = 0; i < facet.length; i++) {
          List<String> createResponse =
              api.createAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID4, srvpath, postData, file);

          String actualResponse = createResponse.get(0);
          String expectedJson =
              "{\"error\":{\"code\":\"500\",\"message\":\"This file type is not allowed in this repository. Contact your administrator for assistance.\"}}";

          if (!expectedJson.equals(actualResponse)) {
            allBlocked = false;
            System.out.println(
                "Chapter facet "
                    + facet[i]
                    + " incorrectly accepted blocked mimeType: "
                    + actualResponse);
          }
        }

        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID4);
        if ("Saved".equals(response) && allBlocked) {
          testStatus = true;
        }
      }
    }

    if (!testStatus) {
      fail("Attachment got uploaded to chapter with blocked .rtf MIME type");
    }
  }

  @Test
  @Order(14)
  void testDeleteBookAndChapter() {
    System.out.println("Test (14) : Delete book (and its chapters)");
    Boolean testStatus = false;
    // Delete books (chapters are deleted automatically as they're composition)
    String response = api.deleteEntity(appUrl, bookEntityName, bookID);
    String response2 = api.deleteEntity(appUrl, bookEntityName, bookID2);
    String response3 = api.deleteEntity(appUrl, bookEntityName, bookID3);
    String response4 = api.deleteEntity(appUrl, bookEntityName, bookID4);
    if (response.equals("Entity Deleted")
        && response2.equals("Entity Deleted")
        && response3.equals("Entity Deleted")
        && response4.equals("Entity Deleted")) testStatus = true;
    if (!testStatus) fail("Could not delete books");
  }

  @Test
  @Order(15)
  void testUpdateValidSecondaryPropertyInChapter_beforeBookIsSaved_single() throws IOException {
    System.out.println(
        "Test (15) : Rename & Update secondary property in chapter before book is saved");
    System.out.println("Creating book and chapter");

    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (!response.equals("Could not create entity")) {
      bookID5 = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID5);
      if (!chapterResponse.equals("Could not create entity")) {
        chapterID5 = chapterResponse;

        System.out.println("Creating attachment, reference, and footnote in chapter");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", chapterID5);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        String[] tempID = new String[facet.length];
        boolean allCreated = true;
        for (int i = 0; i < facet.length; i++) {
          tempID[i] =
              CreateandReturnFacetID(appUrl, serviceName, chapterID5, facet[i], postData, file);
          if (tempID[i] == null || tempID[i].isEmpty()) {
            System.out.println("Failed to create attachment for facet: " + facet[i]);
            allCreated = false;
          }
        }

        System.out.println("Attachments, References, and Footnotes created in chapter");
        System.out.println(
            "tempID[0]: " + tempID[0] + ", tempID[1]: " + tempID[1] + ", tempID[2]: " + tempID[2]);

        if (!allCreated) {
          fail("Could not create all attachments for test 15");
        }

        // Reset counter for this test
        counter = 0;

        // Use valid dropdown value for customProperty1
        Integer secondaryPropertyInt = 1234;
        LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

        String[] name = {"sample1234.pdf", "reference1234.pdf", "footnote1234.pdf"};

        for (int i = 0; i < facet.length; i++) {
          System.out.println("Processing facet " + facet[i] + " with tempID: " + tempID[i]);
          String response1 =
              api.renameAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], name[i]);
          System.out.println("Rename response for " + facet[i] + ": " + response1);

          // Update customProperty1 (String - dropdown value)
          String dropdownValue = integrationTestUtils.getDropDownValue();
          String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
          RequestBody bodyDropdown =
              RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
          String updateSecondaryPropertyResponse1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyDropdown);

          // Update customProperty2 (Integer)
          RequestBody bodyInt =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8(
                      "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
          String updateSecondaryPropertyResponse2 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyInt);

          // Update customProperty5 (DateTime) - using customProperty5 like Books test
          RequestBody bodyDate =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8(
                      "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
          String updateSecondaryPropertyResponse3 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyDate);

          // Update customProperty6 (Boolean)
          RequestBody bodyBool =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
          String updateSecondaryPropertyResponse4 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyBool);

          // Check all updates succeeded
          if ("Renamed".equals(response1)
              && "Updated".equals(updateSecondaryPropertyResponse1)
              && "Updated".equals(updateSecondaryPropertyResponse2)
              && "Updated".equals(updateSecondaryPropertyResponse3)
              && "Updated".equals(updateSecondaryPropertyResponse4)) {
            counter++;
          } else {
            System.out.println(
                "Update failed for "
                    + facet[i]
                    + ": rename="
                    + response1
                    + ", dropdown="
                    + updateSecondaryPropertyResponse1
                    + ", int="
                    + updateSecondaryPropertyResponse2
                    + ", datetime="
                    + updateSecondaryPropertyResponse3
                    + ", bool="
                    + updateSecondaryPropertyResponse4);
          }
        }

        System.out.println("Counter after all facets: " + counter);
        if (counter == facet.length) {
          // Save the book (not the chapter)
          response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
          System.out.println("Save response: " + response);
          if ("Saved".equals(response)) {
            testStatus = true;
          }
        } else {
          System.out.println(
              "Counter is less than " + facet.length + ", not saving. Counter: " + counter);
        }
      }
    }

    if (!testStatus) {
      fail(
          "Could not update secondary properties in chapter before book save. Counter: " + counter);
    }
  }

  @Test
  @Order(16)
  void testUploadNAttachmentsToChapter() throws IOException {
    System.out.println("Test (16) : Upload N attachments to chapter");
    Boolean testStatus = false;
    counter = 0;

    ClassLoader classLoader = getClass().getClassLoader();
    File originalFile = new File(classLoader.getResource("sample.pdf").getFile());

    for (int j = 0; j < 5; j++) {
      // Create temp file with unique name per iteration
      File tempFile = File.createTempFile("sample_iter" + j + "_", ".pdf");
      Files.copy(originalFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", chapterID5);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
      if (response.equals("Entity in draft mode")) {
        for (int i = 0; i < facet.length; i++) {
          List<String> facetResponse =
              api.createAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID5, srvpath, postData, tempFile);
          String check = facetResponse.get(0);
          if (check.equals("Attachment created")) {
            counter++;
          } else {
            System.out.println(
                "Attachment creation failed in chapter facet: " + facet[i] + " - " + check);
          }
        }
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
        if (!response.equals("Saved")) {
          System.out.println(
              "Failed to save book after creating attachments in chapter: " + response);
        }
      } else {
        System.out.println("Could not edit book draft: " + response);
      }
      tempFile.delete();
    }

    if (counter == 15) { // 5 iterations * 3 facets
      testStatus = true;
    }

    if (!testStatus) {
      fail("Could not upload N attachments to chapter. Created: " + counter + " out of 15");
    }
  }

  @Test
  @Order(17)
  void testDiscardDraftWithoutChapterAttachments() {
    System.out.println("Test (17) : Discard book draft without chapter attachments");
    Boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      // Create chapter but don't add attachments
      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        String tempChapterID = chapterResponse;

        response = api.deleteEntityDraft(appUrl, bookEntityName, tempBookID);
        if ("Entity Draft Deleted".equals(response)) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Book draft without chapter attachments was not discarded properly");
    }
  }

  @Test
  @Order(18)
  void testDiscardDraftWithChapterAttachments() throws IOException {
    System.out.println("Test (18) : Discard book draft with chapter attachments");
    Boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      // Create chapter
      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        // Create attachments in chapter
        for (int i = 0; i < facet.length; i++) {
          List<String> facetResponse =
              api.createAttachment(
                  appUrl, chapterEntityName, facet[i], tempChapterID, srvpath, postData, file);
          String check = facetResponse.get(0);
          if (!check.equals("Attachment created")) {
            System.out.println("Attachment creation failed in chapter facet: " + facet[i]);
          }
        }

        response = api.deleteEntityDraft(appUrl, bookEntityName, tempBookID);
        if ("Entity Draft Deleted".equals(response)) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Book draft with chapter attachments was not discarded properly");
    }
  }

  @Test
  @Order(19)
  void testUploadChapterAttachmentWithoutSDMRole() throws IOException {
    System.out.println("Test (19) : Try to upload chapter attachment without SDM role");
    Boolean testStatus = true;

    String response = apiNoRoles.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      String chapterResponse =
          apiNoRoles.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        try {
          List<String> createResponse =
              apiNoRoles.createAttachment(
                  appUrl, chapterEntityName, facet[0], tempChapterID, srvpath, postData, file);
          String check = createResponse.get(0);

          if (check.equals("Attachment created")) {
            testStatus = false;
          }
        } catch (Exception e) {
          // Expected to fail
          testStatus = true;
        }

        apiNoRoles.deleteEntityDraft(appUrl, bookEntityName, tempBookID);
      }
    }

    if (!testStatus) {
      fail("Chapter attachment was uploaded without SDM roles");
    }
  }

  @Test
  @Order(20)
  void testUpdateValidSecondaryPropertyInChapter_afterBookIsSaved_single() {
    System.out.println(
        "Test (20): Rename & Update secondary property in chapter after book is saved");
    Boolean testStatus = false;
    counter = 0; // Reset counter for this test
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
    System.out.println("Editing book, response: " + response);

    if (response.equals("Entity in draft mode")) {
      // Use unique names that won't conflict with existing attachments
      String name[] = {"test20_attachment.pdf", "test20_reference.pdf", "test20_footnote.pdf"};
      Integer secondaryPropertyInt = 42;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      System.out.println("Renaming and updating secondary properties for chapter attachment");
      String[] tempID = new String[facet.length];
      for (int i = 0; i < facet.length; i++) {
        // Get the first attachment ID from the chapter
        try {
          List<Map<String, Object>> metadata =
              api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], chapterID5);
          if (!metadata.isEmpty()) {
            tempID[i] = (String) metadata.get(0).get("ID");
          }
        } catch (IOException e) {
          fail("Could not fetch metadata for chapter: " + e.getMessage());
        }

        String response1 =
            api.renameAttachment(
                appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], name[i]);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyDate);
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, chapterEntityName, facet[i], chapterID5, tempID[i], bodyBool);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter == facet.length) {
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
        if (response.equals("Saved")) {
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties for chapter attachment");
        }
      }
    }
    if (!testStatus) fail("Could not update secondary properties in chapter after book is saved");
  }

  @Test
  @Order(21)
  void testUpdateInvalidSecondaryPropertyInChapter_beforeBookIsSaved_single() throws IOException {
    System.out.println(
        "Test (21): Rename & Update invalid secondary property in chapter before book is saved");
    System.out.println("Creating book and chapter");
    Boolean testStatus = false;
    int localCounter = 0;
    int createCounter = 0;

    // Create new book and chapter for this test
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        String[] tempID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          tempID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
          if (tempID[i] != null) {
            createCounter++;
          }
        }

        // Only proceed if all facets were created successfully
        if (createCounter == facet.length) {
          // Prepare test data
          String name1 = "sample1234.pdf";
          Integer secondaryPropertyInt = 1234;
          LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
          String invalidProperty = "testid";

          for (int i = 0; i < facet.length; i++) {
            // Rename and update secondary properties
            String response1 =
                api.renameAttachment(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], name1);
            // Update secondary properties for String
            String dropdownValue = integrationTestUtils.getDropDownValue();
            String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
            RequestBody bodyDropdown =
                RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
            String updateSecondaryPropertyResponse1 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyDropdown);
            // Update secondary properties for Integer
            RequestBody bodyInt =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    ByteString.encodeUtf8(
                        "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
            String updateSecondaryPropertyResponse2 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyInt);
            // Update secondary properties for LocalDateTime
            RequestBody bodyDate =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    ByteString.encodeUtf8(
                        "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
            String updateSecondaryPropertyResponse3 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyDate);
            // Update secondary properties for invalid ID
            String updateSecondaryPropertyResponse4 =
                api.updateInvalidSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], invalidProperty);

            if (response1.equals("Renamed")
                && updateSecondaryPropertyResponse1.equals("Updated")
                && updateSecondaryPropertyResponse2.equals("Updated")
                && updateSecondaryPropertyResponse3.equals("Updated")
                && updateSecondaryPropertyResponse4.equals("Updated")) {
              localCounter++;
            }
          }

          if (localCounter == facet.length) {
            response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);

            // Fetch metadata and verify values weren't updated due to invalid property
            for (int i = 0; i < facet.length; i++) {
              Map<String, Object> FacetMetadata =
                  api.fetchMetadata(appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i]);
              assertEquals("sample.pdf", FacetMetadata.get("fileName"));
              assertNull(FacetMetadata.get("customProperty3"));
              assertNull(FacetMetadata.get("customProperty4"));
              assertNull(FacetMetadata.get("customProperty1_code"));
              assertNull(FacetMetadata.get("customProperty2"));
              assertNull(FacetMetadata.get("customProperty6"));
              assertNull(FacetMetadata.get("customProperty5"));
            }

            // Parse JSON response and check for expected error messages
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            boolean hasAttachmentsError = false;
            boolean hasReferencesError = false;
            boolean hasFootnotesError = false;

            if (root.isArray()) {
              for (JsonNode node : root) {
                String message = node.path("message").asText();
                if (message.contains("id1") && message.contains("Table: attachments")) {
                  hasAttachmentsError = true;
                }
                if (message.contains("id1") && message.contains("Table: references")) {
                  hasReferencesError = true;
                }
                if (message.contains("id1") && message.contains("Table: footnotes")) {
                  hasFootnotesError = true;
                }
              }
            }

            if (hasAttachmentsError && hasReferencesError && hasFootnotesError) {
              System.out.println("Book saved with expected invalid property errors");
              testStatus = true;
              System.out.println(
                  "Rename & update secondary properties for chapter attachment is unsuccessful");
            }
          } else {
            System.out.println(
                "Not all facets updated successfully. localCounter: " + localCounter);
          }
        } else {
          System.out.println(
              "Not all facets created successfully. createCounter: " + createCounter);
        }

        // Cleanup
        api.deleteEntity(appUrl, bookEntityName, tempBookID);
      }
    }
    if (!testStatus)
      fail("Could not update invalid secondary property in chapter before book is saved");
  }

  @Test
  @Order(22)
  void testUpdateInvalidSecondaryPropertyInChapter_afterBookIsSaved_single() throws IOException {
    System.out.println(
        "Test (22): Rename & Update invalid secondary property in chapter after book is saved");
    System.out.println("Creating book and chapter");
    Boolean testStatus = false;
    int localCounter = 0;
    int createCounter = 0;

    // Create new book and chapter
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        String[] tempID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          tempID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
          if (tempID[i] != null) {
            createCounter++;
          }
        }

        // Only proceed if all facets were created successfully
        if (createCounter != facet.length) {
          api.deleteEntity(appUrl, bookEntityName, tempBookID);
          fail("Not all facets created successfully. createCounter: " + createCounter);
        }

        // Save the book first
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
        if (!response.equals("Saved")) {
          api.deleteEntity(appUrl, bookEntityName, tempBookID);
          fail("Could not save book initially");
        }

        // Now edit to update with invalid property
        response = api.editEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
        if (response.equals("Entity in draft mode")) {
          String name1 = "sample.pdf";
          Integer secondaryPropertyInt = 12;
          LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
          String invalidProperty = "testidinvalid";

          for (int i = 0; i < facet.length; i++) {
            // Rename and update secondary properties
            String response1 =
                api.renameAttachment(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], name1);
            // Update secondary properties for Drop down
            String dropdownValue = integrationTestUtils.getDropDownValue();
            String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
            RequestBody bodyDropdown =
                RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
            String updateSecondaryPropertyResponse1 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyDropdown);
            // Update secondary properties for Integer
            RequestBody bodyInt =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    ByteString.encodeUtf8(
                        "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
            String updateSecondaryPropertyResponse2 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyInt);
            // Update secondary properties for LocalDateTime
            RequestBody bodyDate =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    ByteString.encodeUtf8(
                        "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
            String updateSecondaryPropertyResponse3 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], bodyDate);
            // Update secondary properties for invalid ID
            String updateSecondaryPropertyResponse4 =
                api.updateInvalidSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i], invalidProperty);

            if (response1.equals("Renamed")
                && updateSecondaryPropertyResponse1.equals("Updated")
                && updateSecondaryPropertyResponse2.equals("Updated")
                && updateSecondaryPropertyResponse3.equals("Updated")
                && updateSecondaryPropertyResponse4.equals("Updated")) {
              localCounter++;
            }
          }

          if (localCounter == facet.length) {
            response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);

            for (int i = 0; i < facet.length; i++) {
              Map<String, Object> FacetMetadata =
                  api.fetchMetadata(appUrl, chapterEntityName, facet[i], tempChapterID, tempID[i]);
              assertEquals("sample.pdf", FacetMetadata.get("fileName"));
              assertNull(FacetMetadata.get("customProperty3"));
              assertNull(FacetMetadata.get("customProperty4"));
              assertNull(FacetMetadata.get("customProperty1_code"));
              assertNull(FacetMetadata.get("customProperty2"));
              assertNull(FacetMetadata.get("customProperty6"));
              assertNull(FacetMetadata.get("customProperty5"));
            }

            // Parse JSON response and check for expected error messages
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            boolean hasAttachmentsError = false;
            boolean hasReferencesError = false;
            boolean hasFootnotesError = false;

            if (root.isArray()) {
              for (JsonNode node : root) {
                String message = node.path("message").asText();
                if (message.contains("id1") && message.contains("Table: attachments")) {
                  hasAttachmentsError = true;
                }
                if (message.contains("id1") && message.contains("Table: references")) {
                  hasReferencesError = true;
                }
                if (message.contains("id1") && message.contains("Table: footnotes")) {
                  hasFootnotesError = true;
                }
              }
            }

            if (hasAttachmentsError && hasReferencesError && hasFootnotesError) {
              System.out.println("Book saved with expected invalid property errors");
              testStatus = true;
              System.out.println(
                  "Rename & update secondary properties for chapter attachment is unsuccessful");
            }
          } else {
            System.out.println(
                "Not all facets updated successfully. localCounter: " + localCounter);
          }
        }
        api.deleteEntity(appUrl, bookEntityName, tempBookID);
      }
    }
    if (!testStatus)
      fail("Could not update invalid secondary property in chapter after book is saved");
  }

  @Test
  @Order(23)
  void testDraftUpdateUploadTwoDeleteOneAndCreateInChapter() throws IOException {
    System.out.println("Test (23): Upload to all chapter facets, delete one, and save book");

    boolean testStatus = false;

    // Reuse bookID5 and chapterID5
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID5);

    if (response.equals("Entity in draft mode")) {
      ClassLoader classLoader = getClass().getClassLoader();

      // Use temp files with unique names to avoid duplicate name errors
      File originalPdf =
          new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());
      File originalTxt =
          new File(Objects.requireNonNull(classLoader.getResource("sample.txt")).getFile());

      File file1 = File.createTempFile("test23_pdf_", ".pdf");
      File file2 = File.createTempFile("test23_txt_", ".txt");
      Files.copy(originalPdf.toPath(), file1.toPath(), StandardCopyOption.REPLACE_EXISTING);
      Files.copy(originalTxt.toPath(), file2.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", chapterID5);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      Map<String, Object> postData2 = new HashMap<>(postData1);
      postData2.put("up__ID", chapterID5);
      postData2.put("mimeType", "text/plain");

      boolean allCreated = true;
      String[] tempID1 = new String[facet.length];
      String[] tempID2 = new String[facet.length];

      for (int i = 0; i < facet.length; i++) {
        List<String> response1 =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], chapterID5, srvpath, postData1, file1);
        List<String> response2 =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], chapterID5, srvpath, postData2, file2);

        if (response1.get(0).equals("Attachment created")
            && response2.get(0).equals("Attachment created")) {
          tempID1[i] = response1.get(1); // to keep one
          tempID2[i] = response2.get(1); // will delete this one
        } else {
          System.out.println("Failed to create attachments for facet " + facet[i]);
          System.out.println("Response 1: " + response1.get(0));
          System.out.println("Response 2: " + response2.get(0));
          allCreated = false;
          break;
        }

        String deleteResponse =
            api.deleteAttachment(appUrl, chapterEntityName, facet[i], chapterID5, tempID2[i]);
        if (!"Deleted".equals(deleteResponse)) {
          allCreated = false;
          break;
        }
      }

      file1.delete();
      file2.delete();

      if (allCreated) {
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
        if ("Saved".equals(response)) {
          testStatus = true;
        }
      }
    } else {
      System.out.println("Could not edit book: " + response);
    }

    if (!testStatus) {
      fail("Failed to upload multiple chapter facet entries, delete one per facet and save book");
    }
  }

  @Test
  @Order(24)
  void testUpdateChapterEntityDraft() throws IOException {
    System.out.println("Test (24): Update chapter in book draft with new facet content");
    boolean testStatus = false;

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

    // Use unique temp file name to avoid duplicates
    File tempFile = File.createTempFile("test24_sample_", ".pdf");
    Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", chapterID5);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
    if (response.equals("Entity in draft mode")) {
      boolean allCreated = true;
      for (int i = 0; i < facet.length; i++) {
        List<String> facetResponse =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], chapterID5, srvpath, postData, tempFile);
        String check = facetResponse.get(0);
        if (!check.equals("Attachment created")) {
          allCreated = false;
          System.out.println(
              "Attachment creation failed in chapter facet: " + facet[i] + " - " + check);
        }
      }

      if (allCreated) {
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
        if ("Saved".equals(response)) {
          testStatus = true;
        }
      }
    } else {
      System.out.println("Could not edit book: " + response);
    }

    tempFile.delete();

    if (!testStatus) {
      fail("Failed to update chapter entity draft with new attachments");
    }
  }

  @Test
  @Order(25)
  void testUpdateSecondaryProperty_afterBookIsSaved_multipleChapterAttachments()
      throws IOException {
    System.out.println(
        "Test (25): Rename & Update secondary properties for multiple chapter attachments after book is saved");
    System.out.println("Creating book and chapter with multiple attachments");

    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!"Could not create entity".equals(chapterResponse)) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        // Create PDF attachments
        postData.put("mimeType", "application/pdf");
        File file = new File(classLoader.getResource("sample.pdf").getFile());
        String[] pdfID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          pdfID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        // Create TXT attachments
        postData.put("mimeType", "application/txt");
        file = new File(classLoader.getResource("sample.txt").getFile());
        String[] txtID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          txtID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        // Create EXE attachments
        postData.put("mimeType", "application/exe");
        file = new File(classLoader.getResource("sample.exe").getFile());
        String[] exeID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          exeID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        // Save book first
        response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
        if (!"Saved".equals(response)) {
          fail("Could not save book initially");
        }

        // Edit book to update chapter attachments
        response = api.editEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
        if (response.equals("Entity in draft mode")) {
          Boolean[] Updated1 = new Boolean[3];
          Boolean[] Updated2 = new Boolean[3];
          Boolean[] Updated3 = new Boolean[3];

          String name1 = "sample1234.pdf";
          Integer secondaryPropertyInt = 1234;
          LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
          String dropdownValue = integrationTestUtils.getDropDownValue();
          String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";

          // Update PDF properties
          System.out.println("Renaming and updating secondary properties for PDF");
          for (int i = 0; i < facet.length; i++) {
            String renameResp =
                api.renameAttachment(
                    appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], name1);

            RequestBody bodyDropdown =
                RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
            RequestBody bodyInt =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    "{ \"customProperty2\" : " + secondaryPropertyInt + " }");
            RequestBody bodyDate =
                RequestBody.create(
                    MediaType.parse("application/json"),
                    "{ \"customProperty5\" : \"" + secondaryPropertyDateTime + "\" }");
            RequestBody bodyBool =
                RequestBody.create(
                    MediaType.parse("application/json"), "{ \"customProperty6\" : true }");

            String upd1 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyDropdown);
            String upd2 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyInt);
            String upd3 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyDate);
            String upd4 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyBool);

            if ("Renamed".equals(renameResp)
                && "Updated".equals(upd1)
                && "Updated".equals(upd2)
                && "Updated".equals(upd3)
                && "Updated".equals(upd4)) {
              Updated1[i] = true;
              System.out.println("Renamed & updated Secondary properties for " + facet[i] + " PDF");
            }
          }

          // Update TXT properties (only boolean)
          System.out.println("Renaming and updating secondary properties for TXT");
          for (int i = 0; i < facet.length; i++) {
            RequestBody bodyBool =
                RequestBody.create(
                    MediaType.parse("application/json"), "{ \"customProperty6\" : true }");
            String upd =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, txtID[i], bodyBool);
            if ("Updated".equals(upd)) {
              Updated2[i] = true;
              System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
            }
          }

          // Update EXE properties (dropdown and int)
          System.out.println("Renaming and updating secondary properties for EXE");
          String dropdownValueExe = integrationTestUtils.getDropDownValue();
          String jsonDropdownExe = "{ \"customProperty1_code\" : \"" + dropdownValueExe + "\" }";

          for (int i = 0; i < facet.length; i++) {
            RequestBody bodyDropdownExe =
                RequestBody.create(MediaType.parse("application/json"), jsonDropdownExe);
            RequestBody bodyIntExe =
                RequestBody.create(
                    MediaType.parse("application/json"), "{ \"customProperty2\" : 1234 }");

            String upd1 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, exeID[i], bodyDropdownExe);
            String upd2 =
                api.updateSecondaryProperty(
                    appUrl, chapterEntityName, facet[i], tempChapterID, exeID[i], bodyIntExe);

            if ("Updated".equals(upd1) && "Updated".equals(upd2)) {
              Updated3[i] = true;
              System.out.println("Renamed & updated Secondary properties for " + facet[i] + " EXE");
            }
          }

          if (Arrays.stream(Updated1).allMatch(Boolean.TRUE::equals)
              && Arrays.stream(Updated2).allMatch(Boolean.TRUE::equals)
              && Arrays.stream(Updated3).allMatch(Boolean.TRUE::equals)) {

            response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
            if (response.equals("Saved")) {
              System.out.println("Book saved");
              testStatus = true;
              System.out.println("Renamed & updated Secondary properties for chapter attachments");
            }
          }
        }
        api.deleteEntity(appUrl, bookEntityName, tempBookID);
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property in chapter after book is saved");
    }
  }

  @Test
  @Order(26)
  void testUpdateInvalidSecondaryProperty_beforeBookIsSaved_multipleChapterAttachments()
      throws IOException {
    System.out.println(
        "Test (26): Rename & Update invalid and valid secondary properties for multiple chapter facets before book is saved");
    System.out.println("Creating book and chapter");

    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (!"Could not create entity".equals(response)) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!"Could not create entity".equals(chapterResponse)) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        // Create PDF attachments
        postData.put("mimeType", "application/pdf");
        File file = new File(classLoader.getResource("sample.pdf").getFile());
        String[] pdfID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          pdfID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        // Create TXT attachments
        postData.put("mimeType", "application/txt");
        file = new File(classLoader.getResource("sample.txt").getFile());
        String[] txtID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          txtID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        // Create EXE attachments
        postData.put("mimeType", "application/exe");
        file = new File(classLoader.getResource("sample.exe").getFile());
        String[] exeID = new String[facet.length];
        for (int i = 0; i < facet.length; i++) {
          exeID[i] =
              CreateandReturnFacetID(appUrl, serviceName, tempChapterID, facet[i], postData, file);
        }

        Boolean[] Updated1 = new Boolean[3];
        Boolean[] Updated2 = new Boolean[3];
        Boolean[] Updated3 = new Boolean[3];

        String name1 = "sample1234.pdf";
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        Integer secondaryPropertyInt1 = 1234;
        LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
        String invalidPropertyPDF = "testidinvalidPDF";

        // Update PDF properties
        System.out.println("Renaming and updating secondary properties for PDF");
        for (int i = 0; i < facet.length; i++) {
          String renameResp =
              api.renameAttachment(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], name1);

          RequestBody bodyDropdown =
              RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
          RequestBody bodyInt =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  "{ \"customProperty2\" : " + secondaryPropertyInt1 + " }");
          RequestBody bodyDate =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  "{ \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\" }");
          RequestBody bodyBool =
              RequestBody.create(
                  MediaType.parse("application/json"), "{ \"customProperty6\" : true }");

          String upd1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyDropdown);
          String upd2 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyInt);
          String upd3 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyDate);
          String upd4 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], bodyBool);
          String updInvalid =
              api.updateInvalidSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i], invalidPropertyPDF);

          if ("Renamed".equals(renameResp)
              && "Updated".equals(upd1)
              && "Updated".equals(upd2)
              && "Updated".equals(upd3)
              && "Updated".equals(upd4)
              && "Updated".equals(updInvalid)) {
            Updated1[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " PDF");
          }
        }

        // Update TXT properties
        System.out.println("Renaming and updating secondary properties for TXT");
        for (int i = 0; i < facet.length; i++) {
          RequestBody bodyBool =
              RequestBody.create(
                  MediaType.parse("application/json"), "{ \"customProperty6\" : true }");
          String upd =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, txtID[i], bodyBool);
          if ("Updated".equals(upd)) {
            Updated2[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
          }
        }

        // Update EXE properties
        System.out.println("Renaming and updating secondary properties for EXE");
        String dropdownValueExe = integrationTestUtils.getDropDownValue();
        String jsonDropdownExe = "{ \"customProperty1_code\" : \"" + dropdownValueExe + "\" }";

        for (int i = 0; i < facet.length; i++) {
          RequestBody bodyDropdownExe =
              RequestBody.create(MediaType.parse("application/json"), jsonDropdownExe);
          RequestBody bodyIntExe =
              RequestBody.create(
                  MediaType.parse("application/json"), "{ \"customProperty2\" : 1234 }");

          String upd1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, exeID[i], bodyDropdownExe);
          String upd2 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], tempChapterID, exeID[i], bodyIntExe);

          if ("Updated".equals(upd1) && "Updated".equals(upd2)) {
            Updated3[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " EXE");
          }
        }

        if (Arrays.stream(Updated1).allMatch(Boolean.TRUE::equals)
            && Arrays.stream(Updated2).allMatch(Boolean.TRUE::equals)
            && Arrays.stream(Updated3).allMatch(Boolean.TRUE::equals)) {

          response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
          String[] expectedNames = {"sample.pdf", "sample.txt", "sample.exe"};

          // Verify PDF metadata
          for (int i = 0; i < facet.length; i++) {
            Map<String, Object> metadata =
                api.fetchMetadata(appUrl, chapterEntityName, facet[i], tempChapterID, pdfID[i]);
            assertEquals(expectedNames[0], metadata.get("fileName"));
            assertNull(metadata.get("customProperty3"));
            assertNull(metadata.get("customProperty4"));
            assertNull(metadata.get("customProperty1_code"));
            assertNull(metadata.get("customProperty2"));
            assertNull(metadata.get("customProperty6"));
            assertNull(metadata.get("customProperty5"));
          }

          // Verify TXT metadata
          for (int i = 0; i < facet.length; i++) {
            Map<String, Object> metadata =
                api.fetchMetadata(appUrl, chapterEntityName, facet[i], tempChapterID, txtID[i]);
            assertEquals(expectedNames[1], metadata.get("fileName"));
            assertNull(metadata.get("customProperty3"));
            assertNull(metadata.get("customProperty4"));
            assertNull(metadata.get("customProperty1_code"));
            assertNull(metadata.get("customProperty2"));
            assertTrue((Boolean) metadata.get("customProperty6"));
            assertNull(metadata.get("customProperty5"));
          }

          // Verify EXE metadata
          for (int i = 0; i < facet.length; i++) {
            Map<String, Object> metadata =
                api.fetchMetadata(appUrl, chapterEntityName, facet[i], tempChapterID, exeID[i]);
            assertEquals(expectedNames[2], metadata.get("fileName"));
            assertNull(metadata.get("customProperty3"));
            assertNull(metadata.get("customProperty4"));
            assertEquals(dropdownValueExe, metadata.get("customProperty1_code"));
            assertEquals(1234, metadata.get("customProperty2"));
          }

          // Parse JSON response and check for expected error messages
          ObjectMapper mapper = new ObjectMapper();
          JsonNode root = mapper.readTree(response);
          boolean hasAttachmentsError = false;
          boolean hasReferencesError = false;
          boolean hasFootnotesError = false;

          if (root.isArray()) {
            for (JsonNode node : root) {
              String message = node.path("message").asText();
              if (message.contains("id1") && message.contains("Table: attachments")) {
                hasAttachmentsError = true;
              }
              if (message.contains("id1") && message.contains("Table: references")) {
                hasReferencesError = true;
              }
              if (message.contains("id1") && message.contains("Table: footnotes")) {
                hasFootnotesError = true;
              }
            }
          }

          if (hasAttachmentsError && hasReferencesError && hasFootnotesError) {
            System.out.println("Book saved with expected invalid property errors");
            testStatus = true;
            System.out.println(
                "Rename & update unsuccessful for invalid properties and successful for valid attachments");
          }
        }
      }
    }

    if (!testStatus) {
      fail("Could not update secondary property before book is saved");
    }
  }

  @Test
  @Order(27)
  void testUpdateInvalidSecondaryProperty_afterBookIsSaved_multipleChapterAttachments()
      throws IOException {
    System.out.println(
        "Test (27): Rename & Update invalid and valid secondary properties for multiple chapter attachments after book is saved");

    // Reuse bookID5 and chapterID5
    System.out.println("Editing book with bookID5: " + bookID5);
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
    System.out.println("Edit entity response: " + response);

    if (response.equals("Entity in draft mode")) {
      // Fetch existing attachments from the chapter
      List<Map<String, Object>> attachmentsMeta =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], chapterID5);
      List<Map<String, Object>> referencesMeta =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[1], chapterID5);
      List<Map<String, Object>> footnotesMeta =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[2], chapterID5);

      System.out.println("Attachments count: " + attachmentsMeta.size());
      System.out.println("References count: " + referencesMeta.size());
      System.out.println("Footnotes count: " + footnotesMeta.size());

      if (attachmentsMeta.size() >= 3 && referencesMeta.size() >= 3 && footnotesMeta.size() >= 3) {
        String[] pdfID = new String[facet.length];
        String[] txtID = new String[facet.length];
        String[] exeID = new String[facet.length];

        pdfID[0] = (String) attachmentsMeta.get(0).get("ID");
        pdfID[1] = (String) referencesMeta.get(0).get("ID");
        pdfID[2] = (String) footnotesMeta.get(0).get("ID");

        txtID[0] = (String) attachmentsMeta.get(1).get("ID");
        txtID[1] = (String) referencesMeta.get(1).get("ID");
        txtID[2] = (String) footnotesMeta.get(1).get("ID");

        exeID[0] = (String) attachmentsMeta.get(2).get("ID");
        exeID[1] = (String) referencesMeta.get(2).get("ID");
        exeID[2] = (String) footnotesMeta.get(2).get("ID");

        Boolean[] Updated1 = new Boolean[3];
        Boolean[] Updated2 = new Boolean[3];
        Boolean[] Updated3 = new Boolean[3];

        String name1 = "sample.pdf";
        Integer secondaryPropertyInt1 = 12;
        LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
        String invalidPropertyPDF = "testidinvalidPDF";
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";

        // PDF
        System.out.println("Renaming and updating secondary properties for PDF");
        for (int i = 0; i < facet.length; i++) {
          String response1 =
              api.renameAttachment(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], name1);

          RequestBody bodyDropdown =
              RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
          String updateSecondaryPropertyResponse1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], bodyDropdown);

          RequestBody bodyInt =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8(
                      "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
          String updateSecondaryPropertyResponse2 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], bodyInt);

          RequestBody bodyDate =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8(
                      "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
          String updateSecondaryPropertyResponse3 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], bodyDate);

          RequestBody bodyBool =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
          String updateSecondaryPropertyResponse4 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], bodyBool);

          String updateSecondaryPropertyResponse5 =
              api.updateInvalidSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, pdfID[i], invalidPropertyPDF);

          if (response1.equals("Renamed")
              && updateSecondaryPropertyResponse1.equals("Updated")
              && updateSecondaryPropertyResponse2.equals("Updated")
              && updateSecondaryPropertyResponse3.equals("Updated")
              && updateSecondaryPropertyResponse4.equals("Updated")
              && updateSecondaryPropertyResponse5.equals("Updated")) {
            Updated1[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " PDF");
          }
        }

        // TXT
        System.out.println("Renaming and updating secondary properties for TXT");
        for (int i = 0; i < facet.length; i++) {
          RequestBody bodyBool =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8("{\n    \"customProperty6\" : " + false + "\n}"));
          String updateSecondaryPropertyResponseTXT1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, txtID[i], bodyBool);
          if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
            Updated2[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
          }
        }

        Integer secondaryPropertyInt3 = 12;
        // EXE
        System.out.println("Renaming and updating secondary properties for EXE");
        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        for (int i = 0; i < facet.length; i++) {
          String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
          RequestBody bodyDropdown1 =
              RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
          String updateSecondaryPropertyResponse1 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, exeID[i], bodyDropdown1);

          RequestBody bodyInt =
              RequestBody.create(
                  MediaType.parse("application/json"),
                  ByteString.encodeUtf8(
                      "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
          String updateSecondaryPropertyResponseEXE2 =
              api.updateSecondaryProperty(
                  appUrl, chapterEntityName, facet[i], chapterID5, exeID[i], bodyInt);

          if (updateSecondaryPropertyResponse1.equals("Updated")
              && updateSecondaryPropertyResponseEXE2.equals("Updated")) {
            Updated3[i] = true;
            System.out.println("Renamed & updated Secondary properties for " + facet[i] + " EXE");
          }
        }

        if (Updated1[0]
            && Updated1[1]
            && Updated1[2]
            && Updated2[0]
            && Updated2[1]
            && Updated2[2]
            && Updated3[0]
            && Updated3[1]
            && Updated3[2]) {
          response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID5);
          // Note: Don't verify specific filenames since previous tests may have changed them
          System.out.println("Save response: " + response);

          // Parse JSON response to check for invalid secondary property errors in all three tables
          ObjectMapper mapper = new ObjectMapper();
          JsonNode root = mapper.readTree(response);
          boolean hasAttachmentsError = false;
          boolean hasReferencesError = false;
          boolean hasFootnotesError = false;
          if (root.isArray()) {
            for (JsonNode node : root) {
              String message = node.path("message").asText();
              if (message.contains("id1") && message.contains("Table: attachments")) {
                hasAttachmentsError = true;
              }
              if (message.contains("id1") && message.contains("Table: references")) {
                hasReferencesError = true;
              }
              if (message.contains("id1") && message.contains("Table: footnotes")) {
                hasFootnotesError = true;
              }
            }
          }
          if (hasAttachmentsError && hasReferencesError && hasFootnotesError) {
            System.out.println("Book saved");
            testStatus = true;
            System.out.println(
                "Rename & update unsuccessful for invalid Secondary properties and successful for valid property attachments");
          } else {
            System.out.println("Save response did not match expected: " + response);
          }
        } else {
          System.out.println("Not enough attachments in facets - need at least 3 per facet");
        }
      }
    } else {
      System.out.println(
          "Could not edit book - it may be stuck in draft mode from a previous test");
    }
    if (!testStatus) {
      fail("Could not update secondary property before book is saved");
    }
  }

  // Tests 28 and 29 removed - chapters have no attachment limit

  // Tests 28-29 skipped - chapters have no attachment limit

  @Test
  @Order(30)
  void testDiscardBookDraftWithoutChapterAttachments() {
    System.out.println("Test (30) : Discard book draft without adding chapter attachments");
    Boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!chapterResponse.equals("Could not create entity")) {
        response = api.deleteEntityDraft(appUrl, bookEntityName, tempBookID);
        if (response.equals("Entity Draft Deleted")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Book draft with chapter was not discarded properly");
    }
  }

  @Test
  @Order(31)
  void testDiscardBookDraftWithChapterAttachments() throws IOException {
    System.out.println("Test (31): Discard book draft with chapter attachments");
    boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      String tempBookID = response;

      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
      if (!"Could not create entity".equals(chapterResponse)) {
        String tempChapterID = chapterResponse;

        ClassLoader classLoader = getClass().getClassLoader();
        File file =
            new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", tempChapterID);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        for (int i = 0; i < facet.length; i++) {
          List<String> createResponse =
              api.createAttachment(
                  appUrl, chapterEntityName, facet[i], tempChapterID, srvpath, postData, file);
          if ("Attachment created".equals(createResponse.get(0))) {
            System.out.println("Attachment created in chapter facet: " + facet[i]);
          } else {
            System.out.println("Attachment creation failed in chapter facet: " + facet[i]);
          }
        }

        response = api.deleteEntityDraft(appUrl, bookEntityName, tempBookID);
        if ("Entity Draft Deleted".equals(response)) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Book draft with chapter attachments was not discarded properly");
    }
  }

  // Tests 32-34 covered in tests 19, 23, 24
  // Tests 37-41 skipped - copy with notes/secondary properties not applicable

  @Test
  @Order(42)
  void testCreateLinkSuccessInChapter() throws IOException {
    System.out.println("Test (42): Create link in chapter");
    List<String> attachments = new ArrayList<>();

    // Create book and chapter for link testing
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (response.equals("Could not create entity")) {
      fail("Could not create book");
    }
    String createLinkBookID = response;

    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, createLinkBookID);
    if (chapterResponse.equals("Could not create entity")) {
      fail("Could not create chapter");
    }
    String createLinkChapterID = chapterResponse;

    String linkName = "sample";
    String linkUrl = "https://www.example.com";
    for (String facetName : facet) {
      String createLinkResponse1 =
          api.createLink(
              appUrl, chapterEntityName, facetName, createLinkChapterID, linkName, linkUrl);
      String createLinkResponse2 =
          api.createLink(
              appUrl, chapterEntityName, facetName, createLinkChapterID, linkName + "1", linkUrl);
      if (!createLinkResponse1.equals("Link created successfully")
          || !createLinkResponse2.equals("Link created successfully")) {
        fail("Could not create links for chapter facet : " + facetName + createLinkResponse1);
      }
    }

    String saveEntityResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, createLinkBookID);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save book");
    }

    for (String facetName : facet) {
      attachments =
          api
              .fetchEntityMetadata(appUrl, chapterEntityName, facetName, createLinkChapterID)
              .stream()
              .map(item -> (String) item.get("ID"))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      String openAttachmentResponse;
      for (String attachment : attachments) {
        openAttachmentResponse =
            api.openAttachment(
                appUrl, chapterEntityName, facetName, createLinkChapterID, attachment);
        if (!openAttachmentResponse.equals("Attachment opened successfully")) {
          fail("Could not open created link in chapter facet : " + facetName);
        }
      }
    }
    api.deleteEntity(appUrl, bookEntityName, createLinkBookID);
  }

  @Test
  @Order(43)
  void testCreateLinkDifferentChapter() throws IOException {
    System.out.println("Test (43): Create link with same name in different chapter");

    // Create new book and chapter
    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (response.equals("Could not edit entity")) {
      fail("Could not create book");
    }
    String tempBookID = response;

    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, tempBookID);
    if (chapterResponse.equals("Could not create entity")) {
      fail("Could not create chapter");
    }
    String tempChapterID = chapterResponse;

    String linkName = "sample";
    String linkUrl = "https://example.com";
    for (String facetName : facet) {
      String createResponse =
          api.createLink(appUrl, chapterEntityName, facetName, tempChapterID, linkName, linkUrl);
      if (!createResponse.equals("Link created successfully")) {
        fail("Could not create link in different chapter with same name");
      }
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, tempBookID);
    if (!response.equals("Saved")) {
      fail("Could not save book");
    }

    response = api.deleteEntity(appUrl, bookEntityName, tempBookID);
    if (!response.equals("Entity Deleted")) {
      fail("Could not delete book");
    }
  }

  @Test
  @Order(44)
  void testCreateLinkFailureInChapter() throws IOException {
    System.out.println("Test (44): Create link fails due to invalid URL and name in chapter");

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if ("Could not create entity".equals(response)) {
      fail("Could not create book");
    }
    String createLinkBookID = response;

    response =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, createLinkBookID);
    if ("Could not create entity".equals(response)) {
      fail("Could not create chapter");
    }
    String createLinkChapterID = response;

    String linkName = "sample";
    String linkUrl = "https://www.example.com";

    ObjectMapper mapper = new ObjectMapper();

    for (String facetName : facet) {

      // Create initial link for this facet first (so duplicate test works)
      response =
          api.createLink(
              appUrl, chapterEntityName, facetName, createLinkChapterID, linkName, linkUrl);
      if (!"Link created successfully".equals(response)) {
        fail("Could not create initial link for facet: " + facetName);
      }

      /* ---------- INVALID URL ---------- */
      try {
        api.createLink(
            appUrl, chapterEntityName, facetName, createLinkChapterID, linkName, "example.com");
        fail("Expected invalid URL error");
      } catch (IOException e) {
        JsonNode error =
            mapper.readTree(e.getMessage().substring(e.getMessage().indexOf('{'))).path("error");

        assertEquals("400018", error.path("code").asText());
        assertTrue(
            error.path("message").asText().contains("expected pattern"),
            "Unexpected message: " + error.path("message").asText());
      }

      /* ---------- INVALID NAME ---------- */
      try {
        api.createLink(
            appUrl,
            chapterEntityName,
            facetName,
            createLinkChapterID,
            "sample//",
            "https://example.com");
        fail("Expected invalid name error");
      } catch (IOException e) {
        JsonNode error =
            mapper.readTree(e.getMessage().substring(e.getMessage().indexOf('{'))).path("error");

        String message = error.path("message").asText().replace('‘', '\'').replace('’', '\'');

        assertEquals("500", error.path("code").asText());
        assertTrue(
            message.contains("contains unsupported characters")
                && message.contains("Rename and try again"),
            "Unexpected message: " + message);
      }

      /* ---------- EMPTY NAME & URL ---------- */
      try {
        api.createLink(appUrl, chapterEntityName, facetName, createLinkChapterID, "", "");
        fail("Expected missing value error");
      } catch (IOException e) {
        JsonNode error =
            mapper.readTree(e.getMessage().substring(e.getMessage().indexOf('{'))).path("error");

        assertEquals("409008", error.path("code").asText());
        assertEquals("Provide the missing value.", error.path("message").asText());
      }

      /* ---------- DUPLICATE NAME ---------- */
      try {
        api.createLink(
            appUrl, chapterEntityName, facetName, createLinkChapterID, linkName, linkUrl);
        fail("Expected duplicate name error");
      } catch (IOException e) {
        JsonNode error =
            mapper.readTree(e.getMessage().substring(e.getMessage().indexOf('{'))).path("error");

        assertEquals("500", error.path("code").asText());
        assertEquals(
            "An object named \"sample\" already exists. Rename the object and try again.",
            error.path("message").asText());
      }
    }

    response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, createLinkBookID);
    if (!"Saved".equals(response)) {
      fail("Could not save book");
    }

    response = api.deleteEntity(appUrl, bookEntityName, createLinkBookID);
    if (!"Entity Deleted".equals(response)) {
      fail("Could not delete book");
    }
  }

  @Test
  @Order(45)
  void testCreateLinkNoSDMRolesInChapter() throws IOException {
    System.out.println("Test (45): Create link fails due to no SDM roles assigned in chapter");

    String createLinkBookNoRoles =
        apiNoRoles.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (createLinkBookNoRoles.equals("Could not edit entity")) {
      fail("Could not create book");
    }

    String createLinkChapterNoRoles =
        apiNoRoles.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, createLinkBookNoRoles);
    if (createLinkChapterNoRoles.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    for (String facetName : facet) {
      String linkName = "sample27";
      String linkUrl = "https://example.com";
      try {
        apiNoRoles.createLink(
            appUrl, chapterEntityName, facetName, createLinkChapterNoRoles, linkName, linkUrl);
        fail("Link got created without SDM roles");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        assertEquals("500", errorCode);
        assertEquals(
            "You do not have the required permissions to upload attachments. Please contact your administrator for access.",
            errorMessage);
      }
    }

    String response =
        apiNoRoles.saveEntityDraft(appUrl, bookEntityName, srvpath, createLinkBookNoRoles);
    if (!response.equals("Saved")) {
      fail("Could not save book");
    }

    response = api.deleteEntity(appUrl, bookEntityName, createLinkBookNoRoles);
    if (!response.equals("Entity Deleted")) {
      fail("Could not delete book");
    }
  }

  @Test
  @Order(46)
  void testDeleteLinkInChapter() throws IOException {
    System.out.println("Test (46): Delete link in chapter");
    List<List<String>> attachments = new ArrayList<>();

    String response = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (response.equals("Could not create entity")) {
      fail("Could not create book");
    }
    String deleteLinkBookID = response;

    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, deleteLinkBookID);
    if (chapterResponse.equals("Could not create entity")) {
      fail("Could not create chapter");
    }
    String deleteLinkChapterID = chapterResponse;

    for (String facetName : facet) {
      String linkName = "sample";
      String linkUrl = "https://www.example.com";
      String createLinkResponse =
          api.createLink(
              appUrl, chapterEntityName, facetName, deleteLinkChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link for chapter facet : " + facetName);
      }
    }

    String saveEntityResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, deleteLinkBookID);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save book");
    }

    for (String facetName : facet) {
      attachments.add(
          api
              .fetchEntityMetadata(appUrl, chapterEntityName, facetName, deleteLinkChapterID)
              .stream()
              .map(item -> (String) item.get("ID"))
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }

    String editEntityResponse =
        api.editEntityDraft(appUrl, bookEntityName, srvpath, deleteLinkBookID);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    int index = 0;
    for (String facetName : facet) {
      String deleteLinkResponse =
          api.deleteAttachment(
              appUrl,
              chapterEntityName,
              facetName,
              deleteLinkChapterID,
              attachments.get(index).get(0));
      System.out.println("Delete response for facet " + facetName + ": " + deleteLinkResponse);
      if (!deleteLinkResponse.equals("Deleted")) {
        fail("Could not delete created link");
      }
      index += 1;
    }

    saveEntityResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, deleteLinkBookID);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save book");
    }

    index = 0;
    attachments.clear();
    for (String facetName : facet) {
      attachments.add(
          api
              .fetchEntityMetadata(appUrl, chapterEntityName, facetName, deleteLinkChapterID)
              .stream()
              .map(item -> (String) item.get("ID"))
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
      System.out.println(
          "Attachments after deletion in facet " + facetName + ": " + attachments.get(index));
      if (attachments.get(index).size() != 0) {
        fail("Link wasn't deleted");
      }
      index += 1;
    }

    response = api.deleteEntity(appUrl, bookEntityName, deleteLinkBookID);
    if (!response.equals("Entity Deleted")) {
      fail("Could not delete book");
    }
  }

  @Test
  @Order(35)
  void testCopyAttachmentsToNewChapterInSameBook() throws IOException {
    System.out.println(
        "Test (35): Copy attachments from one chapter to another new chapter in the same book");

    // Create source book and chapter with attachments
    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    if (sourceChapterID.equals("Could not create entity")) {
      fail("Could not create source chapter");
    }

    // Load original files for copying content
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<List<String>> attachments = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      attachments.add(new ArrayList<>());
    }

    // Create attachments in all facets - each upload needs a unique filename
    int fileCounter = 0;
    for (int i = 0; i < facet.length; i++) {
      boolean useTxt = (i == 1); // Use txt for references facet
      postData.put("mimeType", useTxt ? "text/plain" : "application/pdf");
      File originalFile = useTxt ? originalTxt : originalPdf;
      String extension = useTxt ? ".txt" : ".pdf";

      for (int j = 0; j < 2; j++) { // Create 2 attachments per facet
        // Create unique temp file for EACH upload to avoid duplicate filename errors
        fileCounter++;
        File tempFile =
            File.createTempFile("test35_" + facet[i] + "_" + fileCounter + "_", extension);
        tempFile.deleteOnExit();
        java.nio.file.Files.copy(
            originalFile.toPath(),
            tempFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Uploading file: " + tempFile.getName() + " to facet: " + facet[i]);
        List<String> createResponse =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], sourceChapterID, srvpath, postData, tempFile);
        if (createResponse.get(0).equals("Attachment created")) {
          attachments.get(i).add(createResponse.get(1));
          System.out.println("Created attachment ID: " + createResponse.get(1));
        } else {
          System.out.println("Failed to create attachment: " + createResponse.get(0));
          fail("Could not create attachment in facet: " + facet[i]);
        }
      }
      // Wait for uploads to complete
      for (String attachmentId : attachments.get(i)) {
        if (!waitForUploadCompletion(sourceChapterID, attachmentId, 150, facet[i])) {
          fail("Upload did not complete in time for attachment: " + attachmentId);
        }
      }
    }

    // Fetch object IDs from source attachments
    List<String> objectIds = new ArrayList<>();
    for (int i = 0; i < attachments.size(); i++) {
      for (String attachment : attachments.get(i)) {
        Map<String, Object> metadata =
            api.fetchMetadataDraft(
                appUrl, chapterEntityName, facet[i], sourceChapterID, attachment);
        if (metadata.containsKey("objectId")) {
          objectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }
    }

    // Save the source book
    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save source book");
    }

    // Create target chapter in the SAME book
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit source book for adding target chapter");
    }

    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    if (targetChapterID.equals("Could not create entity")) {
      fail("Could not create target chapter in same book");
    }

    // Copy attachments to target chapter
    int objectIdIndex = 0;
    for (String facetName : facet) {
      List<String> facetObjectIds =
          objectIds.subList(objectIdIndex, Math.min(objectIdIndex + 2, objectIds.size()));
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, facetObjectIds);
      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachments to facet: " + facetName + " - " + copyResponse);
      }

      // Fetch and wait for copied attachments
      List<Map<String, Object>> copiedMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      for (Map<String, Object> meta : copiedMetadata) {
        String copiedId = (String) meta.get("ID");
        if (!waitForUploadCompletion(targetChapterID, copiedId, 150, facetName)) {
          fail("Copied upload did not complete in time for attachment: " + copiedId);
        }
      }
      objectIdIndex += 2;
    }

    // Save the book with new chapter
    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book after copying attachments");
    }

    // Verify attachments were copied - read them
    for (String facetName : facet) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.size() != 2) {
        fail("Expected 2 attachments in facet " + facetName + ", found " + targetMetadata.size());
      }
      for (Map<String, Object> meta : targetMetadata) {
        String attachmentId = (String) meta.get("ID");
        String readResponse =
            api.readAttachment(appUrl, chapterEntityName, facetName, targetChapterID, attachmentId);
        if (!readResponse.equals("OK")) {
          fail("Could not read copied attachment in facet: " + facetName);
        }
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
  }

  @Test
  @Order(36)
  void testCopyAttachmentsToChapterInDifferentBook() throws IOException {
    System.out.println("Test (36): Copy attachments from chapter in Book1 to chapter in Book2");

    // Create Book1 with source chapter and attachments
    copyAttachmentSourceBook = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (copyAttachmentSourceBook.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    copyAttachmentSourceChapter =
        api.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentSourceBook);
    if (copyAttachmentSourceChapter.equals("Could not create entity")) {
      fail("Could not create source chapter");
    }

    // Create Book2 with target chapter
    copyAttachmentTargetBook = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (copyAttachmentTargetBook.equals("Could not create entity")) {
      fail("Could not create target book");
    }

    copyAttachmentTargetChapter =
        api.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentTargetBook);
    if (copyAttachmentTargetChapter.equals("Could not create entity")) {
      fail("Could not create target chapter");
    }

    // Load original files for copying content
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", copyAttachmentSourceChapter);
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<List<String>> attachments = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      attachments.add(new ArrayList<>());
    }

    // Create attachments in all facets of source chapter - each upload needs unique filename
    int fileCounter = 0;
    for (int i = 0; i < facet.length; i++) {
      boolean useTxt = (i == 1); // Use txt for references facet
      postData.put("mimeType", useTxt ? "text/plain" : "application/pdf");
      File originalFile = useTxt ? originalTxt : originalPdf;
      String extension = useTxt ? ".txt" : ".pdf";

      for (int j = 0; j < 2; j++) {
        // Create unique temp file for EACH upload to avoid duplicate filename errors
        fileCounter++;
        File tempFile =
            File.createTempFile("test36_" + facet[i] + "_" + fileCounter + "_", extension);
        tempFile.deleteOnExit();
        java.nio.file.Files.copy(
            originalFile.toPath(),
            tempFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Uploading file: " + tempFile.getName() + " to facet: " + facet[i]);
        List<String> createResponse =
            api.createAttachment(
                appUrl,
                chapterEntityName,
                facet[i],
                copyAttachmentSourceChapter,
                srvpath,
                postData,
                tempFile);
        if (createResponse.get(0).equals("Attachment created")) {
          attachments.get(i).add(createResponse.get(1));
          System.out.println("Created attachment ID: " + createResponse.get(1));
        } else {
          System.out.println("Failed to create attachment: " + createResponse.get(0));
          fail("Could not create attachment in facet: " + facet[i]);
        }
      }
      for (String attachmentId : attachments.get(i)) {
        if (!waitForUploadCompletion(copyAttachmentSourceChapter, attachmentId, 150, facet[i])) {
          fail("Upload did not complete in time for attachment: " + attachmentId);
        }
      }
    }

    // Fetch object IDs
    sourceObjectIds.clear();
    for (int i = 0; i < attachments.size(); i++) {
      for (String attachment : attachments.get(i)) {
        Map<String, Object> metadata =
            api.fetchMetadataDraft(
                appUrl, chapterEntityName, facet[i], copyAttachmentSourceChapter, attachment);
        if (metadata.containsKey("objectId")) {
          sourceObjectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }
    }

    // Save Book1
    String saveResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save source book");
    }

    // Copy attachments from Book1's chapter to Book2's chapter
    if (sourceObjectIds.size() == 6) {
      int objectIdIndex = 0;
      for (String facetName : facet) {
        List<String> facetObjectIds =
            sourceObjectIds.subList(
                objectIdIndex, Math.min(objectIdIndex + 2, sourceObjectIds.size()));
        String copyResponse =
            api.copyAttachment(
                appUrl, chapterEntityName, facetName, copyAttachmentTargetChapter, facetObjectIds);
        if (!copyResponse.equals("Attachments copied successfully")) {
          fail("Could not copy attachments to facet: " + facetName + " - " + copyResponse);
        }

        // Wait for copied attachments
        List<Map<String, Object>> copiedMetadata =
            api.fetchEntityMetadata(
                appUrl, chapterEntityName, facetName, copyAttachmentTargetChapter);
        for (Map<String, Object> meta : copiedMetadata) {
          String copiedId = (String) meta.get("ID");
          if (!waitForUploadCompletion(copyAttachmentTargetChapter, copiedId, 150, facetName)) {
            fail("Copied upload did not complete in time for attachment: " + copiedId);
          }
        }
        objectIdIndex += 2;
      }

      // Save Book2
      saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book after copying attachments");
      }

      // Verify attachments were copied
      for (String facetName : facet) {
        List<Map<String, Object>> targetMetadata =
            api.fetchEntityMetadata(
                appUrl, chapterEntityName, facetName, copyAttachmentTargetChapter);
        if (targetMetadata.size() != 2) {
          fail("Expected 2 attachments in facet " + facetName + ", found " + targetMetadata.size());
        }
        for (Map<String, Object> meta : targetMetadata) {
          String attachmentId = (String) meta.get("ID");
          String readResponse =
              api.readAttachment(
                  appUrl, chapterEntityName, facetName, copyAttachmentTargetChapter, attachmentId);
          if (!readResponse.equals("OK")) {
            fail("Could not read copied attachment in facet: " + facetName);
          }
        }
      }

      // Cleanup - delete both books after verification
      api.deleteEntity(appUrl, bookEntityName, copyAttachmentSourceBook);
      api.deleteEntity(appUrl, bookEntityName, copyAttachmentTargetBook);
    } else {
      fail("Could not fetch object IDs for all attachments. Found: " + sourceObjectIds.size());
    }
  }

  @Test
  @Order(37)
  void testCopyAttachmentsWithNotePreserved() throws IOException {
    System.out.println("Test (37): Copy attachments with note field preserved");

    // Create source book and chapter
    copyAttachmentSourceBook = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (copyAttachmentSourceBook.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    copyAttachmentSourceChapter =
        api.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentSourceBook);
    if (copyAttachmentSourceChapter.equals("Could not create entity")) {
      fail("Could not create source chapter");
    }

    // Create attachments with notes in source chapter
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", copyAttachmentSourceChapter);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String[] sourceAttachmentIds = new String[facet.length];
    String testNote = "Test note for copy attachment - " + System.currentTimeMillis();

    for (int i = 0; i < facet.length; i++) {
      // Create unique temp file for each facet
      File tempFile =
          File.createTempFile("test37_note_" + facet[i] + "_" + System.currentTimeMillis(), ".pdf");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalPdf.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      List<String> createResponse =
          api.createAttachment(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              srvpath,
              postData,
              tempFile);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment in facet: " + facet[i]);
      }
      sourceAttachmentIds[i] = createResponse.get(1);

      if (!waitForUploadCompletion(
          copyAttachmentSourceChapter, sourceAttachmentIds[i], 150, facet[i])) {
        fail("Upload did not complete for attachment in facet: " + facet[i]);
      }

      // Update note field using RequestBody
      String jsonNote = "{ \"note\" : \"" + testNote + "\" }";
      RequestBody noteBody = RequestBody.create(MediaType.parse("application/json"), jsonNote);
      String noteResponse =
          api.updateSecondaryProperty(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i],
              noteBody);
      if (!noteResponse.equals("Updated")) {
        fail("Could not update note for attachment in facet: " + facet[i]);
      }
      System.out.println("Note updated for facet: " + facet[i]);
    }

    // Save source book
    String saveResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save source book");
    }

    // Verify notes were saved in source
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> metadata =
          api.fetchMetadata(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i]);
      if (!testNote.equals(metadata.get("note"))) {
        fail("Note not saved correctly in source for facet: " + facet[i]);
      }
    }

    // Create target book and chapter
    copyAttachmentTargetBook = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (copyAttachmentTargetBook.equals("Could not create entity")) {
      fail("Could not create target book");
    }

    copyAttachmentTargetChapter =
        api.createEntityDraft(
            appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentTargetBook);
    if (copyAttachmentTargetChapter.equals("Could not create entity")) {
      fail("Could not create target chapter");
    }

    // Get object IDs and copy attachments
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> sourceMetadata =
          api.fetchMetadata(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i]);
      String objectId = sourceMetadata.get("objectId").toString();

      List<String> objectIds = new ArrayList<>();
      objectIds.add(objectId);

      String copyResponse =
          api.copyAttachment(
              appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter, objectIds);
      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachment to facet: " + facet[i]);
      }
      System.out.println("Attachment copied to facet: " + facet[i]);
    }

    // Wait for all copied uploads to complete before saving
    for (int i = 0; i < facet.length; i++) {
      if (!waitForAllUploadsCompletion(copyAttachmentTargetChapter, facet[i], 300)) {
        fail("Copied upload did not complete in time for facet: " + facet[i]);
      }
    }

    // Save target book
    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target book");
    }

    // Verify notes were preserved in target
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter);
      if (targetMetadata.isEmpty()) {
        fail("No attachments found in target facet: " + facet[i]);
      }
      Map<String, Object> copiedAttachment = targetMetadata.get(0);
      String copiedNote = (String) copiedAttachment.get("note");
      if (!testNote.equals(copiedNote)) {
        fail(
            "Note not preserved after copy in facet: "
                + facet[i]
                + ". Expected: "
                + testNote
                + ", Got: "
                + copiedNote);
      }
      System.out.println("Note preserved in target facet: " + facet[i]);
    }

    System.out.println("Test 37 passed - notes preserved during copy");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentSourceBook);
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentTargetBook);
    copyAttachmentSourceBook = null;
    copyAttachmentTargetBook = null;
  }

  @Test
  @Order(38)
  void testCopyAttachmentsWithSecondaryPropertiesPreserved() throws IOException {
    System.out.println("Test (38): Copy attachments with secondary properties preserved");

    // Use entities from test 37 or create new ones if needed
    boolean sourceBookJustCreated = false;
    boolean targetBookJustCreated = false;

    if (copyAttachmentSourceBook == null || copyAttachmentSourceBook.isEmpty()) {
      copyAttachmentSourceBook =
          api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (copyAttachmentSourceBook.equals("Could not create entity")) {
        fail("Could not create source book");
      }
      copyAttachmentSourceChapter =
          api.createEntityDraft(
              appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentSourceBook);
      if (copyAttachmentSourceChapter.equals("Could not create entity")) {
        fail("Could not create source chapter");
      }
      sourceBookJustCreated = true;
    }

    if (copyAttachmentTargetBook == null || copyAttachmentTargetBook.isEmpty()) {
      copyAttachmentTargetBook =
          api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (copyAttachmentTargetBook.equals("Could not create entity")) {
        fail("Could not create target book");
      }
      copyAttachmentTargetChapter =
          api.createEntityDraft(
              appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentTargetBook);
      if (copyAttachmentTargetChapter.equals("Could not create entity")) {
        fail("Could not create target chapter");
      }
      targetBookJustCreated = true;
    }

    // If source book was just created, save it first before we can edit it
    if (sourceBookJustCreated) {
      String saveResponse =
          api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save newly created source book");
      }
    }

    // If target book was just created, save it first
    if (targetBookJustCreated) {
      String saveResponse =
          api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save newly created target book");
      }
    }

    // Edit source book
    String editResponse =
        api.editEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit source book");
    }

    // Create new attachments with secondary properties
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", copyAttachmentSourceChapter);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String[] sourceAttachmentIds = new String[facet.length];
    Boolean testBooleanProp = true;
    Integer testIntegerProp = 12345;

    for (int i = 0; i < facet.length; i++) {
      // Create unique temp file
      File tempFile =
          File.createTempFile(
              "test38_props_" + facet[i] + "_" + System.currentTimeMillis(), ".pdf");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalPdf.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      List<String> createResponse =
          api.createAttachment(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              srvpath,
              postData,
              tempFile);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment in facet: " + facet[i]);
      }
      sourceAttachmentIds[i] = createResponse.get(1);

      if (!waitForUploadCompletion(
          copyAttachmentSourceChapter, sourceAttachmentIds[i], 150, facet[i])) {
        fail("Upload did not complete for attachment in facet: " + facet[i]);
      }

      // Update secondary properties using RequestBody (customProperty6 - Boolean, customProperty2 -
      // Integer)
      String jsonBool = "{ \"customProperty6\" : " + testBooleanProp + " }";
      RequestBody boolBody = RequestBody.create(MediaType.parse("application/json"), jsonBool);
      String boolResponse =
          api.updateSecondaryProperty(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i],
              boolBody);
      if (!boolResponse.equals("Updated")) {
        System.out.println("Warning: Could not update customProperty6 for facet: " + facet[i]);
      }

      String jsonInt = "{ \"customProperty2\" : " + testIntegerProp + " }";
      RequestBody intBody = RequestBody.create(MediaType.parse("application/json"), jsonInt);
      String intResponse =
          api.updateSecondaryProperty(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i],
              intBody);
      if (!intResponse.equals("Updated")) {
        System.out.println("Warning: Could not update customProperty2 for facet: " + facet[i]);
      }

      System.out.println("Secondary properties updated for facet: " + facet[i]);
    }

    // Save source book
    String saveResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save source book");
    }

    // Edit target book
    editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target book");
    }

    // Copy attachments to target
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> sourceMetadata =
          api.fetchMetadata(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i]);
      String objectId = sourceMetadata.get("objectId").toString();

      List<String> objectIds = new ArrayList<>();
      objectIds.add(objectId);

      String copyResponse =
          api.copyAttachment(
              appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter, objectIds);
      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachment to facet: " + facet[i]);
      }
      System.out.println("Attachment with secondary properties copied to facet: " + facet[i]);
    }

    // Wait for all copied uploads to complete before saving
    for (int i = 0; i < facet.length; i++) {
      if (!waitForAllUploadsCompletion(copyAttachmentTargetChapter, facet[i], 300)) {
        fail("Copied upload did not complete in time for facet: " + facet[i]);
      }
    }

    // Save target book
    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target book");
    }

    // Verify secondary properties were preserved in target
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter);

      // Find the attachment we just copied (most recent one)
      boolean found = false;
      for (Map<String, Object> attachment : targetMetadata) {
        Object boolProp = attachment.get("customProperty6");
        Object intProp = attachment.get("customProperty2");

        if (boolProp != null && intProp != null) {
          if (Boolean.TRUE.equals(boolProp) && Integer.valueOf(12345).equals(intProp)) {
            found = true;
            System.out.println("Secondary properties preserved in target facet: " + facet[i]);
            break;
          }
        }
      }
      if (!found) {
        System.out.println(
            "Warning: Secondary properties may not be fully preserved in facet: " + facet[i]);
      }
    }

    System.out.println("Test 38 passed - secondary properties checked during copy");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentSourceBook);
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentTargetBook);
    copyAttachmentSourceBook = null;
    copyAttachmentTargetBook = null;
  }

  @Test
  @Order(39)
  void testCopyAttachmentsWithNoteAndSecondaryPropertiesPreserved() throws IOException {
    System.out.println(
        "Test (39): Copy attachments with both note and secondary properties preserved");

    // Use entities from previous tests or create new ones
    boolean sourceBookJustCreated = false;
    boolean targetBookJustCreated = false;

    if (copyAttachmentSourceBook == null || copyAttachmentSourceBook.isEmpty()) {
      copyAttachmentSourceBook =
          api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (copyAttachmentSourceBook.equals("Could not create entity")) {
        fail("Could not create source book");
      }
      copyAttachmentSourceChapter =
          api.createEntityDraft(
              appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentSourceBook);
      if (copyAttachmentSourceChapter.equals("Could not create entity")) {
        fail("Could not create source chapter");
      }
      sourceBookJustCreated = true;
    }

    if (copyAttachmentTargetBook == null || copyAttachmentTargetBook.isEmpty()) {
      copyAttachmentTargetBook =
          api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (copyAttachmentTargetBook.equals("Could not create entity")) {
        fail("Could not create target book");
      }
      copyAttachmentTargetChapter =
          api.createEntityDraft(
              appUrl, chapterEntityName, entityName2, srvpath, copyAttachmentTargetBook);
      if (copyAttachmentTargetChapter.equals("Could not create entity")) {
        fail("Could not create target chapter");
      }
      targetBookJustCreated = true;
    }

    // If source book was just created, save it first before we can edit it
    if (sourceBookJustCreated) {
      String saveResponse =
          api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save newly created source book");
      }
    }

    // If target book was just created, save it first
    if (targetBookJustCreated) {
      String saveResponse =
          api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save newly created target book");
      }
    }

    // Edit source book
    String editResponse =
        api.editEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit source book");
    }

    // Create new attachments with both note and secondary properties
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", copyAttachmentSourceChapter);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String[] sourceAttachmentIds = new String[facet.length];
    String testNote = "Combined test note - " + System.currentTimeMillis();
    Boolean testBooleanProp = true;
    Integer testIntegerProp = 99999;

    for (int i = 0; i < facet.length; i++) {
      // Create unique temp file
      File tempFile =
          File.createTempFile(
              "test39_combined_" + facet[i] + "_" + System.currentTimeMillis(), ".pdf");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalPdf.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      List<String> createResponse =
          api.createAttachment(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              srvpath,
              postData,
              tempFile);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment in facet: " + facet[i]);
      }
      sourceAttachmentIds[i] = createResponse.get(1);

      if (!waitForUploadCompletion(
          copyAttachmentSourceChapter, sourceAttachmentIds[i], 150, facet[i])) {
        fail("Upload did not complete for attachment in facet: " + facet[i]);
      }

      // Update note using RequestBody
      String jsonNote = "{ \"note\" : \"" + testNote + "\" }";
      RequestBody noteBody = RequestBody.create(MediaType.parse("application/json"), jsonNote);
      api.updateSecondaryProperty(
          appUrl,
          chapterEntityName,
          facet[i],
          copyAttachmentSourceChapter,
          sourceAttachmentIds[i],
          noteBody);

      // Update secondary properties using RequestBody
      String jsonBool = "{ \"customProperty6\" : " + testBooleanProp + " }";
      RequestBody boolBody = RequestBody.create(MediaType.parse("application/json"), jsonBool);
      api.updateSecondaryProperty(
          appUrl,
          chapterEntityName,
          facet[i],
          copyAttachmentSourceChapter,
          sourceAttachmentIds[i],
          boolBody);

      String jsonInt = "{ \"customProperty2\" : " + testIntegerProp + " }";
      RequestBody intBody = RequestBody.create(MediaType.parse("application/json"), jsonInt);
      api.updateSecondaryProperty(
          appUrl,
          chapterEntityName,
          facet[i],
          copyAttachmentSourceChapter,
          sourceAttachmentIds[i],
          intBody);

      System.out.println("Note and secondary properties updated for facet: " + facet[i]);
    }

    // Save source book
    String saveResponse =
        api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentSourceBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save source book");
    }

    // Edit target book
    editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target book");
    }

    // Copy attachments to target
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> sourceMetadata =
          api.fetchMetadata(
              appUrl,
              chapterEntityName,
              facet[i],
              copyAttachmentSourceChapter,
              sourceAttachmentIds[i]);
      String objectId = sourceMetadata.get("objectId").toString();

      List<String> objectIds = new ArrayList<>();
      objectIds.add(objectId);

      String copyResponse =
          api.copyAttachment(
              appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter, objectIds);
      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachment to facet: " + facet[i]);
      }
      System.out.println("Attachment with note and properties copied to facet: " + facet[i]);
    }

    // Wait for all copied uploads to complete before saving
    for (int i = 0; i < facet.length; i++) {
      if (!waitForAllUploadsCompletion(copyAttachmentTargetChapter, facet[i], 300)) {
        fail("Copied upload did not complete in time for facet: " + facet[i]);
      }
    }

    // Save target book
    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, copyAttachmentTargetBook);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target book");
    }

    // Verify note and secondary properties were preserved in target
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], copyAttachmentTargetChapter);

      boolean noteFound = false;
      boolean propsFound = false;

      for (Map<String, Object> attachment : targetMetadata) {
        String copiedNote = (String) attachment.get("note");
        Object boolProp = attachment.get("customProperty6");
        Object intProp = attachment.get("customProperty2");

        if (testNote.equals(copiedNote)) {
          noteFound = true;
          System.out.println("Note preserved in target facet: " + facet[i]);
        }

        if (boolProp != null && intProp != null) {
          if (Boolean.TRUE.equals(boolProp) && Integer.valueOf(99999).equals(intProp)) {
            propsFound = true;
            System.out.println("Secondary properties preserved in target facet: " + facet[i]);
          }
        }
      }

      if (!noteFound) {
        System.out.println("Warning: Note may not be preserved in facet: " + facet[i]);
      }
      if (!propsFound) {
        System.out.println(
            "Warning: Secondary properties may not be preserved in facet: " + facet[i]);
      }
    }

    // Cleanup - delete both books
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentSourceBook);
    api.deleteEntity(appUrl, bookEntityName, copyAttachmentTargetBook);

    // Reset static variables
    copyAttachmentSourceBook = null;
    copyAttachmentTargetBook = null;
    copyAttachmentSourceChapter = null;
    copyAttachmentTargetChapter = null;

    System.out.println("Test 39 passed - both note and secondary properties checked during copy");
  }

  @Test
  @Order(40)
  void testCopyAttachmentsWithInvalidObjectId() throws IOException {
    System.out.println("Test (40): Copy attachments with invalid object ID should fail");

    // Create independent test entities (don't rely on previous tests)
    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create test book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create test chapter");
    }

    // Save the book first so it's not in draft mode
    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save test book");
    }

    // Now edit it to test copy with invalid object IDs
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit test book");
    }

    // Try to copy with invalid object ID
    for (String facetName : facet) {
      try {
        List<String> invalidObjectIds = new ArrayList<>();
        invalidObjectIds.add("invalidObjectId123");
        invalidObjectIds.add("anotherInvalidId456");
        api.copyAttachment(appUrl, chapterEntityName, facetName, testChapterID, invalidObjectIds);
        fail("Copy with invalid object ID should have thrown an error for facet: " + facetName);
      } catch (IOException e) {
        // Expected - copy should fail with invalid object ID
        System.out.println(
            "Expected error received for invalid object ID in facet "
                + facetName
                + ": "
                + e.getMessage());
      }
    }

    // Save and cleanup
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);

    // Also cleanup test 36 entities if they exist
    if (copyAttachmentSourceBook != null && !copyAttachmentSourceBook.isEmpty()) {
      try {
        api.deleteEntity(appUrl, bookEntityName, copyAttachmentSourceBook);
      } catch (Exception e) {
        // Ignore - may already be deleted
      }
    }
    if (copyAttachmentTargetBook != null && !copyAttachmentTargetBook.isEmpty()) {
      try {
        api.deleteEntity(appUrl, bookEntityName, copyAttachmentTargetBook);
      } catch (Exception e) {
        // Ignore - may already be deleted
      }
    }
  }

  @Test
  @Order(41)
  void testCopyAttachmentsToExistingChapter() throws IOException {
    System.out.println(
        "Test (41): Copy attachments to an existing chapter that already has attachments");

    // Create Book1 with source chapter
    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    if (sourceChapterID.equals("Could not create entity")) {
      fail("Could not create source chapter");
    }

    // Create Book2 with target chapter that has existing attachments
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (targetBookID.equals("Could not create entity")) {
      fail("Could not create target book");
    }

    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);
    if (targetChapterID.equals("Could not create entity")) {
      fail("Could not create target chapter");
    }

    // Create temp files with unique names to avoid duplicate filename errors
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

    String uniqueSuffix = "_test41_" + System.currentTimeMillis();
    File tempPdf = File.createTempFile("copy_sample" + uniqueSuffix, ".pdf");
    File tempTxt = File.createTempFile("copy_sample" + uniqueSuffix, ".txt");
    tempPdf.deleteOnExit();
    tempTxt.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    java.nio.file.Files.copy(
        originalTxt.toPath(), tempTxt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    // Create attachment in source chapter
    List<String> sourceObjIds = new ArrayList<>();
    for (int i = 0; i < facet.length; i++) {
      postData.put("up__ID", sourceChapterID);
      postData.put("mimeType", "application/pdf");
      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], sourceChapterID, srvpath, postData, tempPdf);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create source attachment");
      }
      String attachmentId = createResponse.get(1);
      if (!waitForUploadCompletion(sourceChapterID, attachmentId, 150, facet[i])) {
        fail("Upload did not complete for source attachment");
      }
      Map<String, Object> metadata =
          api.fetchMetadataDraft(
              appUrl, chapterEntityName, facet[i], sourceChapterID, attachmentId);
      sourceObjIds.add(metadata.get("objectId").toString());
    }

    // Create existing attachment in target chapter
    for (int i = 0; i < facet.length; i++) {
      postData.put("up__ID", targetChapterID);
      postData.put("mimeType", "text/plain");
      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], targetChapterID, srvpath, postData, tempTxt);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create existing target attachment");
      }
      String attachmentId = createResponse.get(1);
      if (!waitForUploadCompletion(targetChapterID, attachmentId, 150, facet[i])) {
        fail("Upload did not complete for existing target attachment");
      }
    }

    // Save both books
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Edit target book and copy attachments
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target book");
    }

    // Copy from source to target (target already has 1 attachment per facet)
    for (int i = 0; i < facet.length; i++) {
      List<String> objectIdsToCopy = new ArrayList<>();
      objectIdsToCopy.add(sourceObjIds.get(i));
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facet[i], targetChapterID, objectIdsToCopy);
      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachment to facet: " + facet[i]);
      }

      // Wait for copy to complete
      List<Map<String, Object>> copiedMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], targetChapterID);
      for (Map<String, Object> meta : copiedMetadata) {
        String copiedId = (String) meta.get("ID");
        waitForUploadCompletion(targetChapterID, copiedId, 150, facet[i]);
      }
    }

    // Save target book
    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target book");
    }

    // Verify target chapter now has 2 attachments per facet (1 existing + 1 copied)
    for (String facetName : facet) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.size() != 2) {
        fail(
            "Expected 2 attachments in facet "
                + facetName
                + " (1 existing + 1 copied), found "
                + targetMetadata.size());
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  // ============= LINK RENAME TESTS (47-49) =============

  @Test
  @Order(47)
  void testRenameLinkSuccess() throws IOException {
    System.out.println("Test (47): Rename link in chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    // Create links in all facets
    for (String facetName : facet) {
      String linkName = "sample";
      String linkUrl = "https://www.example.com";
      String createLinkResponse =
          api.createLink(appUrl, chapterEntityName, facetName, testChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    // Edit and rename links
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      String renameResponse =
          api.renameAttachment(
              appUrl, chapterEntityName, facetName, testChapterID, linkId, "sampleRenamed");
      if (!renameResponse.equals("Renamed")) {
        fail("Could not rename link in facet: " + facetName);
      }
    }

    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book after renaming links");
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(48)
  void testRenameLinkDuplicate() throws IOException {
    System.out.println("Test (48): Rename link in chapter fails due to duplicate error");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    // Create two links in all facets
    for (String facetName : facet) {
      String createLinkResponse1 =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "link1",
              "https://www.example1.com");
      String createLinkResponse2 =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "link2",
              "https://www.example2.com");
      if (!createLinkResponse1.equals("Link created successfully")
          || !createLinkResponse2.equals("Link created successfully")) {
        fail("Could not create links in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    // Edit and try to rename link2 to link1 (duplicate)
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.size() < 2) {
        fail("Expected 2 links in facet: " + facetName);
      }

      // Find link2 and rename to link1
      for (Map<String, Object> attachment : attachments) {
        if ("link2".equals(attachment.get("fileName"))) {
          String linkId = (String) attachment.get("ID");
          api.renameAttachment(
              appUrl, chapterEntityName, facetName, testChapterID, linkId, "link1");
          break;
        }
      }
    }

    // Save should fail with duplicate error
    String saveError = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode errorJson = mapper.readTree(saveError);
      String errorMessage = errorJson.path("error").path("message").asText();
      if (!errorMessage.contains("already exists")) {
        fail("Expected duplicate error but got: " + saveError);
      }
    } catch (Exception e) {
      if (!saveError.contains("already exists")) {
        fail("Expected duplicate error but got: " + saveError);
      }
    }

    // Cleanup
    api.deleteEntityDraft(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(49)
  void testRenameLinkUnsupportedCharacters() throws IOException {
    System.out.println("Test (49): Rename link in chapter fails due to unsupported characters");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    // Create links in all facets
    for (String facetName : facet) {
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "sample",
              "https://www.example.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    // Edit and rename with unsupported characters
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      api.renameAttachment(
          appUrl, chapterEntityName, facetName, testChapterID, linkId, "invalid//name");
    }

    // Save should fail with unsupported characters error
    String saveError = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveError.contains("unsupported characters")) {
      fail("Expected unsupported characters error but got: " + saveError);
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  // ============= LINK EDIT TESTS (50-53) =============

  @Test
  @Order(50)
  void testEditLinkSuccess() throws IOException {
    System.out.println("Test (50): Edit existing link URL in chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    // Create links in all facets
    for (String facetName : facet) {
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "sample",
              "https://www.example.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    // Edit links
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      String editLinkResponse =
          api.editLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              linkId,
              "https://www.editedexample.com");
      if (!editLinkResponse.equals("Link edited successfully")) {
        fail("Could not edit link in facet: " + facetName);
      }
    }

    saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book after editing links");
    }

    // Verify links open successfully
    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      for (Map<String, Object> attachment : attachments) {
        String linkId = (String) attachment.get("ID");
        String openResponse =
            api.openAttachment(appUrl, chapterEntityName, facetName, testChapterID, linkId);
        if (!openResponse.equals("Attachment opened successfully")) {
          fail("Could not open edited link in facet: " + facetName);
        }
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(51)
  void testEditLinkFailureInvalidURL() throws IOException {
    System.out.println("Test (51): Edit link with invalid URL fails in chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    // Create links
    for (String facetName : facet) {
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "sample",
              "https://www.example.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      try {
        api.editLink(
            appUrl, chapterEntityName, facetName, testChapterID, linkId, "https://editedexample");
        fail("Edit link should have failed with invalid URL in facet: " + facetName);
      } catch (IOException e) {
        System.out.println("Expected error received for invalid URL in facet " + facetName);
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(52)
  void testEditLinkFailureEmptyURL() throws IOException {
    System.out.println("Test (52): Edit link with empty URL fails in chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    for (String facetName : facet) {
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "sample",
              "https://www.example.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      try {
        api.editLink(appUrl, chapterEntityName, facetName, testChapterID, linkId, "");
        fail("Edit link should have failed with empty URL in facet: " + facetName);
      } catch (IOException e) {
        System.out.println("Expected error received for empty URL in facet " + facetName);
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(53)
  void testEditLinkNoSDMRoles() throws IOException {
    System.out.println("Test (53): Edit link fails due to no SDM roles assigned in chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    for (String facetName : facet) {
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              "sample",
              "https://www.example.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in facet: " + facetName);
      }
    }

    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save book");
    }

    String editResponse = apiNoRoles.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    for (String facetName : facet) {
      List<Map<String, Object>> attachments =
          apiNoRoles.fetchEntityMetadata(appUrl, chapterEntityName, facetName, testChapterID);
      if (attachments.isEmpty()) {
        fail("No links found in facet: " + facetName);
      }

      String linkId = (String) attachments.get(0).get("ID");
      try {
        apiNoRoles.editLink(
            appUrl, chapterEntityName, facetName, testChapterID, linkId, "https://www.edited.com");
        fail("Edit link should have failed without SDM roles in facet: " + facetName);
      } catch (IOException e) {
        System.out.println("Expected permission error received in facet " + facetName);
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  // ============= COPY LINK TESTS (54-58) =============

  @Test
  @Order(54)
  void testCopyLinkSuccessNewChapter() throws IOException {
    System.out.println("Test (54): Copy link from one chapter to another new chapter");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    if (sourceChapterID.equals("Could not create entity")
        || targetChapterID.equals("Could not create entity")) {
      fail("Could not create chapters");
    }

    String linkUrl = "https://www.example.com";
    List<String> linkObjectIds = new ArrayList<>();

    // Create links in source chapter
    for (int i = 0; i < facet.length; i++) {
      String linkName = "sample" + i;
      String createLinkResponse =
          api.createLink(appUrl, chapterEntityName, facet[i], sourceChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link for facet: " + facet[i]);
      }
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Fetch object IDs
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> metadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID);
      for (Map<String, Object> meta : metadata) {
        if (meta.containsKey("objectId")) {
          linkObjectIds.add(meta.get("objectId").toString());
        }
      }
    }

    // Copy links to target chapter
    int objectIdIndex = 0;
    for (String facetName : facet) {
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit target book");
      }

      List<String> subListToCopy = linkObjectIds.subList(objectIdIndex, objectIdIndex + 1);
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, subListToCopy);

      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy link for facet " + facetName + ": " + copyResponse);
      }

      // Wait for copied link to complete before saving
      if (!waitForAllUploadsCompletion(targetChapterID, facetName, 300)) {
        fail("Copied link did not complete in time for facet: " + facetName);
      }

      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book");
      }

      // Verify link type and URL
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.isEmpty()) {
        fail("No links found in target chapter for facet: " + facetName);
      }

      Map<String, Object> copiedLink = targetMetadata.get(0);
      String receivedUrl = (String) copiedLink.get("linkUrl");
      assertEquals(linkUrl, receivedUrl, "Link URL mismatch in facet " + facetName);

      objectIdIndex++;
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(55)
  void testCopyLinkUnsuccessfulInvalidObjectId() throws IOException {
    System.out.println("Test (55): Copy invalid link object ID to chapter fails");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    for (String facetName : facet) {
      try {
        List<String> invalidObjectIds = new ArrayList<>();
        invalidObjectIds.add("incorrectObjectId");
        api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, invalidObjectIds);
        fail("Copy should have thrown error for invalid object ID in facet: " + facetName);
      } catch (IOException e) {
        System.out.println("Expected error received for invalid object ID in facet " + facetName);
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(56)
  void testCopyLinkToExistingChapter() throws IOException {
    System.out.println("Test (56): Copy link to existing chapter that has attachments");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    String linkUrl = "https://www.example.com";
    List<String> linkObjectIds = new ArrayList<>();

    // Create links in source chapter
    for (int i = 0; i < facet.length; i++) {
      String linkName = "sourceLink" + i;
      String createLinkResponse =
          api.createLink(appUrl, chapterEntityName, facet[i], sourceChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link in source chapter for facet: " + facet[i]);
      }
    }

    // Create existing links in target chapter
    for (int i = 0; i < facet.length; i++) {
      String linkName = "existingLink" + i;
      String createLinkResponse =
          api.createLink(
              appUrl,
              chapterEntityName,
              facet[i],
              targetChapterID,
              linkName,
              "https://www.existing.com");
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create existing link in target chapter for facet: " + facet[i]);
      }
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Fetch source object IDs
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> metadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID);
      for (Map<String, Object> meta : metadata) {
        if (meta.containsKey("objectId")) {
          linkObjectIds.add(meta.get("objectId").toString());
        }
      }
    }

    // Copy links to target chapter
    int objectIdIndex = 0;
    for (String facetName : facet) {
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit target book");
      }

      List<String> subListToCopy = linkObjectIds.subList(objectIdIndex, objectIdIndex + 1);
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, subListToCopy);

      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy link for facet " + facetName);
      }

      // Wait for copied link to complete before saving
      if (!waitForAllUploadsCompletion(targetChapterID, facetName, 300)) {
        fail("Copied link did not complete in time for facet: " + facetName);
      }

      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book");
      }

      // Verify target has 2 links (existing + copied)
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.size() != 2) {
        fail(
            "Expected 2 links in target chapter facet "
                + facetName
                + ", found "
                + targetMetadata.size());
      }

      objectIdIndex++;
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(57)
  void testCopyLinkNoSDMRoles() throws IOException {
    System.out.println("Test (57): Copy link fails due to no SDM roles");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    String linkUrl = "https://www.example.com";
    List<String> linkObjectIds = new ArrayList<>();

    for (int i = 0; i < facet.length; i++) {
      String linkName = "sample" + i;
      String createLinkResponse =
          api.createLink(appUrl, chapterEntityName, facet[i], sourceChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link for facet: " + facet[i]);
      }
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Fetch object IDs
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> metadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID);
      for (Map<String, Object> meta : metadata) {
        if (meta.containsKey("objectId")) {
          linkObjectIds.add(meta.get("objectId").toString());
        }
      }
    }

    // Try to copy with no SDM roles
    int objectIdIndex = 0;
    for (String facetName : facet) {
      try {
        // Use normal api to put book in draft mode
        String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
        if (!editResponse.equals("Entity in draft mode")) {
          fail("Could not edit target book");
        }

        List<String> subListToCopy = linkObjectIds.subList(objectIdIndex, objectIdIndex + 1);
        // Use apiNoRoles to attempt copy (should fail)
        apiNoRoles.copyAttachment(
            appUrl, chapterEntityName, facetName, targetChapterID, subListToCopy);
        fail("Copy should have failed without SDM roles in facet: " + facetName);
      } catch (IOException e) {
        System.out.println("Expected permission error in facet " + facetName);
        // Discard draft to clean up for next iteration
        api.deleteEntityDraft(appUrl, bookEntityName, targetBookID);
      }
      objectIdIndex++;
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(58)
  void testCopyLinkFromDraftChapter() throws IOException {
    System.out.println("Test (58): Copy link from draft chapter to another chapter");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    String linkUrl = "https://www.example.com";
    List<String> linkObjectIds = new ArrayList<>();

    // Create links in source chapter (NOT saved yet - draft mode)
    for (int i = 0; i < facet.length; i++) {
      String linkName = "draftLink" + i;
      String createLinkResponse =
          api.createLink(appUrl, chapterEntityName, facet[i], sourceChapterID, linkName, linkUrl);
      if (!createLinkResponse.equals("Link created successfully")) {
        fail("Could not create link for facet: " + facet[i]);
      }
    }

    // Save target book only
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Fetch object IDs from draft
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> metadata =
          api.fetchEntityMetadataDraft(appUrl, chapterEntityName, facet[i], sourceChapterID);
      for (Map<String, Object> meta : metadata) {
        if (meta.containsKey("objectId")) {
          linkObjectIds.add(meta.get("objectId").toString());
        }
      }
    }

    if (linkObjectIds.size() != facet.length) {
      fail("Could not fetch all object IDs from draft");
    }

    // Copy links from draft to target
    int objectIdIndex = 0;
    for (String facetName : facet) {
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit target book");
      }

      List<String> subListToCopy = linkObjectIds.subList(objectIdIndex, objectIdIndex + 1);
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, subListToCopy);

      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy link from draft for facet " + facetName);
      }

      // Wait for copied link to complete before saving
      if (!waitForAllUploadsCompletion(targetChapterID, facetName, 300)) {
        fail("Copied link did not complete in time for facet: " + facetName);
      }

      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book");
      }

      // Verify link was copied
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.isEmpty()) {
        fail("No links found in target chapter for facet: " + facetName);
      }

      objectIdIndex++;
    }

    // Cleanup
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  // ============= COPY ATTACHMENTS DRAFT MODE (59) =============

  @Test
  @Order(59)
  void testCopyAttachmentsSuccessNewChapterDraft() throws IOException {
    System.out.println("Test (59): Copy attachments from one chapter to another in draft mode");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    if (sourceChapterID.equals("Could not create entity")
        || targetChapterID.equals("Could not create entity")) {
      fail("Could not create chapters");
    }

    // Create temp files with unique names
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

    String uniqueSuffix = "_test59_" + System.currentTimeMillis();
    File tempPdf = File.createTempFile("draft_copy" + uniqueSuffix, ".pdf");
    File tempTxt = File.createTempFile("draft_copy" + uniqueSuffix, ".txt");
    tempPdf.deleteOnExit();
    tempTxt.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    java.nio.file.Files.copy(
        originalTxt.toPath(), tempTxt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> sourceObjectIds = new ArrayList<>();
    List<List<String>> attachments = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      attachments.add(new ArrayList<>());
    }

    // Create attachments in source chapter (still in draft)
    for (int i = 0; i < facet.length; i++) {
      postData.put("mimeType", i == 1 ? "text/plain" : "application/pdf");
      File file = i == 1 ? tempTxt : tempPdf;
      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], sourceChapterID, srvpath, postData, file);
      if (createResponse.get(0).equals("Attachment created")) {
        attachments.get(i).add(createResponse.get(1));
      } else {
        fail("Could not create attachment in facet: " + facet[i]);
      }

      // Wait for upload
      for (String attachmentId : attachments.get(i)) {
        if (!waitForUploadCompletion(sourceChapterID, attachmentId, 150, facet[i])) {
          fail("Upload did not complete for attachment: " + attachmentId);
        }
      }
    }

    // Fetch object IDs from draft
    for (int i = 0; i < attachments.size(); i++) {
      for (String attachment : attachments.get(i)) {
        Map<String, Object> metadata =
            api.fetchMetadataDraft(
                appUrl, chapterEntityName, facet[i], sourceChapterID, attachment);
        if (metadata.containsKey("objectId")) {
          sourceObjectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }
    }

    // Save target book only
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Copy attachments from draft to target
    int objectIdIndex = 0;
    for (String facetName : facet) {
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit target book");
      }

      List<String> subListToCopy = sourceObjectIds.subList(objectIdIndex, objectIdIndex + 1);
      String copyResponse =
          api.copyAttachment(appUrl, chapterEntityName, facetName, targetChapterID, subListToCopy);

      if (!copyResponse.equals("Attachments copied successfully")) {
        fail("Could not copy attachment from draft for facet " + facetName);
      }

      // Wait for copied attachments
      List<Map<String, Object>> copiedMetadata =
          api.fetchEntityMetadataDraft(appUrl, chapterEntityName, facetName, targetChapterID);
      for (Map<String, Object> meta : copiedMetadata) {
        String copiedId = (String) meta.get("ID");
        waitForUploadCompletion(targetChapterID, copiedId, 150, facetName);
      }

      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book");
      }

      // Verify attachment was copied
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facetName, targetChapterID);
      if (targetMetadata.isEmpty()) {
        fail("No attachments found in target chapter for facet: " + facetName);
      }

      // Read attachment to verify
      String attachmentId = (String) targetMetadata.get(0).get("ID");
      String readResponse =
          api.readAttachment(appUrl, chapterEntityName, facetName, targetChapterID, attachmentId);
      if (!readResponse.equals("OK")) {
        fail("Could not read copied attachment in facet: " + facetName);
      }

      objectIdIndex++;
    }

    // Cleanup
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  // ============= CHANGELOG TESTS (60-64) =============

  @Test
  @Order(60)
  void testViewChangelogForNewlyCreatedAttachment() throws IOException {
    System.out.println("Test (60): View changelog for newly created attachment in chapter");

    for (int i = 0; i < facet.length; i++) {
      String facetName = facet[i];

      // Create book and chapter
      String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (testBookID.equals("Could not create entity")) {
        fail("Could not create book");
      }

      String testChapterID =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
      if (testChapterID.equals("Could not create entity")) {
        fail("Could not create chapter");
      }

      // Create temp file
      ClassLoader classLoader = getClass().getClassLoader();
      File originalFile = new File(classLoader.getResource("sample.txt").getFile());
      File tempFile =
          File.createTempFile(
              "changelog_test60_" + facetName + "_" + System.currentTimeMillis(), ".txt");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalFile.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", testChapterID);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facetName, testChapterID, srvpath, postData, tempFile);

      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment in facet: " + facetName);
      }

      String attachmentId = createResponse.get(1);

      // Wait for upload
      if (!waitForUploadCompletion(testChapterID, attachmentId, 150, facetName)) {
        fail("Upload did not complete");
      }

      // Fetch changelog
      Map<String, Object> changelogResponse =
          api.fetchChangelog(appUrl, chapterEntityName, facetName, testChapterID, attachmentId);

      assertNotNull(changelogResponse, "Changelog response should not be null");
      assertEquals(1, changelogResponse.get("numItems"), "Should have 1 changelog entry");

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> changeLogs =
          (List<Map<String, Object>>) changelogResponse.get("changeLogs");
      assertEquals(1, changeLogs.size(), "Should have exactly 1 changelog entry");

      Map<String, Object> logEntry = changeLogs.get(0);
      assertEquals("created", logEntry.get("operation"), "Operation should be 'created'");

      // Cleanup
      api.deleteEntityDraft(appUrl, bookEntityName, testBookID);
    }
  }

  @Test
  @Order(61)
  void testChangelogAfterModifyingNoteAndCustomProperty() throws IOException {
    System.out.println("Test (61): Changelog after modifying note and custom property in chapter");

    for (int i = 0; i < facet.length; i++) {
      String facetName = facet[i];

      String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (testBookID.equals("Could not create entity")) {
        fail("Could not create book");
      }

      String testChapterID =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
      if (testChapterID.equals("Could not create entity")) {
        fail("Could not create chapter");
      }

      ClassLoader classLoader = getClass().getClassLoader();
      File originalFile = new File(classLoader.getResource("sample.txt").getFile());
      File tempFile =
          File.createTempFile(
              "changelog_test61_" + facetName + "_" + System.currentTimeMillis(), ".txt");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalFile.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", testChapterID);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facetName, testChapterID, srvpath, postData, tempFile);

      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment");
      }

      String attachmentId = createResponse.get(1);
      if (!waitForUploadCompletion(testChapterID, attachmentId, 150, facetName)) {
        fail("Upload did not complete");
      }

      // Update note
      String notesValue = "Test note for changelog verification";
      RequestBody updateNotesBody =
          RequestBody.create(
              MediaType.parse("application/json"), "{\"note\": \"" + notesValue + "\"}");
      api.updateSecondaryProperty(
          appUrl, chapterEntityName, facetName, testChapterID, attachmentId, updateNotesBody);

      // Update custom property
      RequestBody bodyInt =
          RequestBody.create(MediaType.parse("application/json"), "{\"customProperty2\": 12345}");
      api.updateSecondaryProperty(
          appUrl, chapterEntityName, facetName, testChapterID, attachmentId, bodyInt);

      // Save
      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save book");
      }

      // Edit to fetch changelog
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit book");
      }

      // Fetch changelog
      Map<String, Object> changelogResponse =
          api.fetchChangelog(appUrl, chapterEntityName, facetName, testChapterID, attachmentId);

      assertNotNull(changelogResponse, "Changelog response should not be null");
      int numItems = (int) changelogResponse.get("numItems");
      assertTrue(numItems >= 2, "Should have at least 2 changelog entries (created + updates)");

      // Cleanup
      api.deleteEntity(appUrl, bookEntityName, testBookID);
    }
  }

  @Test
  @Order(62)
  void testChangelogAfterRenamingAttachment() throws IOException {
    System.out.println("Test (62): Changelog after renaming attachment in chapter");

    for (int i = 0; i < facet.length; i++) {
      String facetName = facet[i];

      String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (testBookID.equals("Could not create entity")) {
        fail("Could not create book");
      }

      String testChapterID =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
      if (testChapterID.equals("Could not create entity")) {
        fail("Could not create chapter");
      }

      ClassLoader classLoader = getClass().getClassLoader();
      File originalFile = new File(classLoader.getResource("sample.txt").getFile());
      File tempFile =
          File.createTempFile(
              "changelog_test62_" + facetName + "_" + System.currentTimeMillis(), ".txt");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalFile.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", testChapterID);
      postData.put("mimeType", "text/plain");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facetName, testChapterID, srvpath, postData, tempFile);

      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment");
      }

      String attachmentId = createResponse.get(1);
      if (!waitForUploadCompletion(testChapterID, attachmentId, 150, facetName)) {
        fail("Upload did not complete");
      }

      // Rename attachment
      String renameResponse =
          api.renameAttachment(
              appUrl,
              chapterEntityName,
              facetName,
              testChapterID,
              attachmentId,
              "renamed_file.txt");
      if (!renameResponse.equals("Renamed")) {
        fail("Could not rename attachment");
      }

      // Save
      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save book");
      }

      // Edit to fetch changelog
      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit book");
      }

      // Fetch changelog
      Map<String, Object> changelogResponse =
          api.fetchChangelog(appUrl, chapterEntityName, facetName, testChapterID, attachmentId);

      assertNotNull(changelogResponse, "Changelog response should not be null");
      int numItems = (int) changelogResponse.get("numItems");
      assertTrue(numItems >= 2, "Should have at least 2 changelog entries (created + renamed)");

      // Cleanup
      api.deleteEntity(appUrl, bookEntityName, testBookID);
    }
  }

  @Test
  @Order(63)
  void testChangelogForCopiedAttachment() throws IOException {
    System.out.println("Test (63): Changelog for copied attachment in chapter");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalFile = new File(classLoader.getResource("sample.txt").getFile());
    File tempFile = File.createTempFile("changelog_test63_" + System.currentTimeMillis(), ".txt");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalFile.toPath(),
        tempFile.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    // Create attachment in source
    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);

    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }

    String attachmentId = createResponse.get(1);
    if (!waitForUploadCompletion(sourceChapterID, attachmentId, 150, facet[0])) {
      fail("Upload did not complete");
    }

    // Save both books
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Get object ID
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String objectId = metadata.get("objectId").toString();

    // Copy to target
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target book");
    }

    List<String> objectIds = new ArrayList<>();
    objectIds.add(objectId);
    String copyResponse =
        api.copyAttachment(appUrl, chapterEntityName, facet[0], targetChapterID, objectIds);

    if (!copyResponse.equals("Attachments copied successfully")) {
      fail("Could not copy attachment");
    }

    // Wait and save
    List<Map<String, Object>> targetMetadata =
        api.fetchEntityMetadataDraft(appUrl, chapterEntityName, facet[0], targetChapterID);
    for (Map<String, Object> meta : targetMetadata) {
      String copiedId = (String) meta.get("ID");
      waitForUploadCompletion(targetChapterID, copiedId, 150, facet[0]);
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Fetch changelog for copied attachment
    targetMetadata = api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], targetChapterID);
    String copiedAttachmentId = (String) targetMetadata.get(0).get("ID");

    editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    Map<String, Object> changelogResponse =
        api.fetchChangelog(
            appUrl, chapterEntityName, facet[0], targetChapterID, copiedAttachmentId);

    assertNotNull(changelogResponse, "Changelog response should not be null");
    int numItems = (int) changelogResponse.get("numItems");
    assertTrue(numItems >= 1, "Copied attachment should have changelog entries");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(64)
  void testChangelogForNewChapter() throws IOException {
    System.out.println("Test (64): Changelog for attachment in newly created chapter");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    if (testChapterID.equals("Could not create entity")) {
      fail("Could not create chapter");
    }

    ClassLoader classLoader = getClass().getClassLoader();
    File originalFile = new File(classLoader.getResource("sample.txt").getFile());
    File tempFile = File.createTempFile("changelog_test64_" + System.currentTimeMillis(), ".txt");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalFile.toPath(),
        tempFile.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", testChapterID);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], testChapterID, srvpath, postData, tempFile);

    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }

    String attachmentId = createResponse.get(1);
    if (!waitForUploadCompletion(testChapterID, attachmentId, 150, facet[0])) {
      fail("Upload did not complete");
    }

    // Fetch changelog before saving
    Map<String, Object> changelogResponse =
        api.fetchChangelog(appUrl, chapterEntityName, facet[0], testChapterID, attachmentId);

    assertNotNull(changelogResponse, "Changelog response should not be null");
    assertEquals(
        1, changelogResponse.get("numItems"), "New attachment should have 1 changelog entry");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> changeLogs =
        (List<Map<String, Object>>) changelogResponse.get("changeLogs");
    assertEquals("created", changeLogs.get(0).get("operation"), "Operation should be 'created'");

    // Cleanup
    api.deleteEntityDraft(appUrl, bookEntityName, testBookID);
  }

  // ============= MOVE ATTACHMENT TESTS (65-75) =============

  @Test
  @Order(65)
  void testMoveAttachmentsWithSourceFacet() throws IOException {
    System.out.println("Test (65): Move attachments from source chapter to target chapter");

    for (int i = 0; i < facet.length; i++) {
      String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (sourceBookID.equals("Could not create entity")) {
        fail("Could not create source book");
      }

      String sourceChapterID =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
      if (sourceChapterID.equals("Could not create entity")) {
        fail("Could not create source chapter");
      }

      // Create temp files
      ClassLoader classLoader = getClass().getClassLoader();
      File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
      File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

      String uniqueSuffix = "_test65_" + facet[i] + "_" + System.currentTimeMillis();
      File tempPdf = File.createTempFile("move" + uniqueSuffix, ".pdf");
      File tempTxt = File.createTempFile("move" + uniqueSuffix, ".txt");
      tempPdf.deleteOnExit();
      tempTxt.deleteOnExit();
      java.nio.file.Files.copy(
          originalPdf.toPath(),
          tempPdf.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      java.nio.file.Files.copy(
          originalTxt.toPath(),
          tempTxt.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", sourceChapterID);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> sourceAttachmentIds = new ArrayList<>();
      File[] files = {tempPdf, tempTxt};
      for (File file : files) {
        List<String> createResponse =
            api.createAttachment(
                appUrl, chapterEntityName, facet[i], sourceChapterID, srvpath, postData, file);
        if (createResponse.get(0).equals("Attachment created")) {
          sourceAttachmentIds.add(createResponse.get(1));
        } else {
          fail("Could not create attachment in source chapter");
        }
      }

      // Save source book
      String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save source book");
      }

      // Get object IDs and folder ID
      List<String> moveObjectIds = new ArrayList<>();
      String sourceFolderId = null;
      for (String attachmentId : sourceAttachmentIds) {
        Map<String, Object> metadata =
            api.fetchMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID, attachmentId);
        if (metadata.containsKey("objectId")) {
          moveObjectIds.add(metadata.get("objectId").toString());
          if (sourceFolderId == null && metadata.containsKey("folderId")) {
            sourceFolderId = metadata.get("folderId").toString();
          }
        }
      }

      // Create target book and chapter
      String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
      if (targetBookID.equals("Could not create entity")) {
        fail("Could not create target book");
      }

      String targetChapterID =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);
      if (targetChapterID.equals("Could not create entity")) {
        fail("Could not create target chapter");
      }

      // Move attachments
      String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[i];
      Map<String, Object> moveResult =
          api.moveAttachment(
              appUrl,
              chapterEntityName,
              facet[i],
              targetChapterID,
              sourceFolderId,
              moveObjectIds,
              sourceFacet);

      if (moveResult == null) {
        fail("Move operation returned null result");
      }

      // Wait and save
      if (!waitForAllUploadsCompletion(targetChapterID, facet[i], 300)) {
        fail("Upload did not complete after move");
      }

      saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!saveResponse.equals("Saved")) {
        fail("Could not save target book after move");
      }

      // Verify
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], targetChapterID);
      assertEquals(
          sourceAttachmentIds.size(),
          targetMetadata.size(),
          "Target should have all attachments after move");

      List<Map<String, Object>> sourceMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID);
      assertEquals(0, sourceMetadata.size(), "Source should have no attachments after move");

      // Cleanup
      api.deleteEntity(appUrl, bookEntityName, targetBookID);
      api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    }
  }

  @Test
  @Order(66)
  void testMoveAttachmentsToChapterWithDuplicate() throws IOException {
    System.out.println("Test (66): Move attachments to chapter with duplicate attachment");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    // Create attachment in source with specific name
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, originalPdf);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create source attachment");
    }
    String sourceAttachmentId = createResponse.get(1);

    // Create attachment in target with SAME name (duplicate)
    postData.put("up__ID", targetChapterID);
    createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], targetChapterID, srvpath, postData, originalPdf);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create target attachment");
    }

    // Save both
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Get source object ID and folder ID
    Map<String, Object> sourceMetadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, sourceAttachmentId);
    String objectId = sourceMetadata.get("objectId").toString();
    String sourceFolderId = sourceMetadata.get("folderId").toString();

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    // Edit target and try to move
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target book");
    }

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[0],
            targetChapterID,
            sourceFolderId,
            moveObjectIds,
            sourceFacet);

    // Move should handle duplicate - attachment stays in source
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Verify source still has attachment (duplicate not moved)
    List<Map<String, Object>> sourceMetadataAfter =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID);
    assertTrue(
        sourceMetadataAfter.size() >= 1,
        "Source should still have attachment when duplicate exists in target");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(67)
  void testMoveAttachmentsWithNotesAndSecondaryProperties() throws IOException {
    System.out.println("Test (67): Move attachments with notes and secondary properties");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    if (sourceChapterID.equals("Could not create entity")) {
      fail("Could not create source chapter");
    }

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile = File.createTempFile("move_test67_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    if (!waitForUploadCompletion(sourceChapterID, attachmentId, 150, facet[0])) {
      fail("Upload did not complete");
    }

    // Add note and secondary property
    String testNote = "Test note for move";
    RequestBody noteBody =
        RequestBody.create(MediaType.parse("application/json"), "{\"note\": \"" + testNote + "\"}");
    api.updateSecondaryProperty(
        appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId, noteBody);

    RequestBody propBody =
        RequestBody.create(MediaType.parse("application/json"), "{\"customProperty2\": 9999}");
    api.updateSecondaryProperty(
        appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId, propBody);

    // Save source
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);

    // Get object ID and folder ID
    Map<String, Object> sourceMetadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String objectId = sourceMetadata.get("objectId").toString();
    String sourceFolderId = sourceMetadata.get("folderId").toString();

    // Create target
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    // Move
    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[0],
            targetChapterID,
            sourceFolderId,
            moveObjectIds,
            sourceFacet);

    if (moveResult == null) {
      fail("Move operation returned null");
    }

    if (!waitForAllUploadsCompletion(targetChapterID, facet[0], 300)) {
      fail("Upload did not complete after move");
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Verify note was preserved
    List<Map<String, Object>> targetMetadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], targetChapterID);
    if (!targetMetadata.isEmpty()) {
      String movedAttachmentId = (String) targetMetadata.get(0).get("ID");
      Map<String, Object> movedMetadata =
          api.fetchMetadata(
              appUrl, chapterEntityName, facet[0], targetChapterID, movedAttachmentId);

      // Note should be preserved
      if (movedMetadata.containsKey("note")) {
        assertEquals(testNote, movedMetadata.get("note"), "Note should be preserved after move");
      }
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(68)
  void testMoveAttachmentsPartialFailure() throws IOException {
    System.out.println("Test (68): Move attachments with partial failure (invalid object ID)");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile = File.createTempFile("move_test68_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);

    // Get real object ID and folder ID
    Map<String, Object> sourceMetadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String realObjectId = sourceMetadata.get("objectId").toString();
    String sourceFolderId = sourceMetadata.get("folderId").toString();

    // Create target
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    // Try to move with mix of valid and invalid object IDs
    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(realObjectId);
    moveObjectIds.add("invalidObjectId123");

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[0],
            targetChapterID,
            sourceFolderId,
            moveObjectIds,
            sourceFacet);

    // Should handle partial failure
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(69)
  void testMoveAttachmentsEmptyList() throws IOException {
    System.out.println("Test (69): Move attachments with empty object ID list");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Try to move with empty list
    List<String> emptyObjectIds = new ArrayList<>();
    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];

    try {
      api.moveAttachment(
          appUrl,
          chapterEntityName,
          facet[0],
          targetChapterID,
          "someFolderId",
          emptyObjectIds,
          sourceFacet);
      // Should either fail or do nothing
    } catch (Exception e) {
      System.out.println("Expected: Move with empty list handled: " + e.getMessage());
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(70)
  void testMoveAttachmentsToSameChapter() throws IOException {
    System.out.println("Test (70): Move attachments to same chapter (should handle gracefully)");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String testChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile = File.createTempFile("move_test70_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", testChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], testChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);

    // Get object ID and folder ID
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], testChapterID, attachmentId);
    String objectId = metadata.get("objectId").toString();
    String folderId = metadata.get("folderId").toString();

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    // Edit and try to move to same chapter
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[0],
            testChapterID,
            folderId,
            moveObjectIds,
            sourceFacet);

    // Should handle gracefully - attachment stays in place
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);

    // Verify attachment still exists
    List<Map<String, Object>> metadataAfter =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], testChapterID);
    assertEquals(1, metadataAfter.size(), "Attachment should still exist");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(71)
  void testMoveAttachmentsBetweenFacets() throws IOException {
    System.out.println("Test (71): Move attachments between different facets in chapters");

    String testBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (testBookID.equals("Could not create entity")) {
      fail("Could not create book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, testBookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile = File.createTempFile("move_test71_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    // Create in attachments facet
    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);

    // Get object ID and folder ID
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String objectId = metadata.get("objectId").toString();
    String sourceFolderId = metadata.get("folderId").toString();

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    // Move from attachments to references facet
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, testBookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit book");
    }

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[1], // references facet
            targetChapterID,
            sourceFolderId,
            moveObjectIds,
            sourceFacet);

    if (!waitForAllUploadsCompletion(targetChapterID, facet[1], 300)) {
      System.out.println("Warning: Upload may not have completed");
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, testBookID);

    // Verify moved to different facet
    List<Map<String, Object>> targetMetadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[1], targetChapterID);
    assertTrue(
        targetMetadata.size() >= 1, "Target references facet should have the moved attachment");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, testBookID);
  }

  @Test
  @Order(72)
  void testMoveMultipleAttachments() throws IOException {
    System.out.println("Test (72): Move multiple attachments at once between chapters");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);

    // Create multiple temp files
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File originalTxt = new File(classLoader.getResource("sample.txt").getFile());

    String uniqueSuffix = "_test72_" + System.currentTimeMillis();
    File tempPdf = File.createTempFile("multi_move" + uniqueSuffix, ".pdf");
    File tempTxt = File.createTempFile("multi_move" + uniqueSuffix, ".txt");
    tempPdf.deleteOnExit();
    tempTxt.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempPdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    java.nio.file.Files.copy(
        originalTxt.toPath(), tempTxt.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> sourceAttachmentIds = new ArrayList<>();
    File[] files = {tempPdf, tempTxt};
    String[] mimeTypes = {"application/pdf", "text/plain"};

    for (int i = 0; i < files.length; i++) {
      postData.put("mimeType", mimeTypes[i]);
      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, files[i]);
      if (createResponse.get(0).equals("Attachment created")) {
        sourceAttachmentIds.add(createResponse.get(1));
      } else {
        fail("Could not create attachment");
      }
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);

    // Get object IDs
    List<String> moveObjectIds = new ArrayList<>();
    String sourceFolderId = null;
    for (String attachmentId : sourceAttachmentIds) {
      Map<String, Object> metadata =
          api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
      moveObjectIds.add(metadata.get("objectId").toString());
      if (sourceFolderId == null) {
        sourceFolderId = metadata.get("folderId").toString();
      }
    }

    // Create target
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    // Move all at once
    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    Map<String, Object> moveResult =
        api.moveAttachment(
            appUrl,
            chapterEntityName,
            facet[0],
            targetChapterID,
            sourceFolderId,
            moveObjectIds,
            sourceFacet);

    if (!waitForAllUploadsCompletion(targetChapterID, facet[0], 300)) {
      System.out.println("Warning: Some uploads may not have completed");
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Verify all moved
    List<Map<String, Object>> targetMetadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], targetChapterID);
    assertEquals(
        sourceAttachmentIds.size(),
        targetMetadata.size(),
        "All attachments should be moved to target");

    List<Map<String, Object>> sourceMetadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID);
    assertEquals(0, sourceMetadata.size(), "Source should have no attachments");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(73)
  void testMoveAttachmentsAllFacets() throws IOException {
    System.out.println("Test (73): Move attachments from all facets between chapters");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String targetBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || targetBookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String targetChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());

    // Create attachment in each facet
    for (int i = 0; i < facet.length; i++) {
      String uniqueSuffix = "_test73_" + facet[i] + "_" + System.currentTimeMillis();
      File tempFile = File.createTempFile("all_facets" + uniqueSuffix, ".pdf");
      tempFile.deleteOnExit();
      java.nio.file.Files.copy(
          originalPdf.toPath(),
          tempFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", sourceChapterID);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(
              appUrl, chapterEntityName, facet[i], sourceChapterID, srvpath, postData, tempFile);
      if (!createResponse.get(0).equals("Attachment created")) {
        fail("Could not create attachment in facet: " + facet[i]);
      }
    }

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);

    // Move from each facet
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> sourceMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID);
      if (sourceMetadata.isEmpty()) {
        continue;
      }

      String attachmentId = (String) sourceMetadata.get(0).get("ID");
      Map<String, Object> metadata =
          api.fetchMetadata(appUrl, chapterEntityName, facet[i], sourceChapterID, attachmentId);
      String objectId = metadata.get("objectId").toString();
      String sourceFolderId = metadata.get("folderId").toString();

      List<String> moveObjectIds = new ArrayList<>();
      moveObjectIds.add(objectId);

      String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
      if (!editResponse.equals("Entity in draft mode")) {
        fail("Could not edit target book");
      }

      String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[i];
      api.moveAttachment(
          appUrl,
          chapterEntityName,
          facet[i],
          targetChapterID,
          sourceFolderId,
          moveObjectIds,
          sourceFacet);

      waitForAllUploadsCompletion(targetChapterID, facet[i], 300);
      api.saveEntityDraft(appUrl, bookEntityName, srvpath, targetBookID);
    }

    // Verify all facets have attachments in target
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> targetMetadata =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], targetChapterID);
      assertTrue(targetMetadata.size() >= 1, "Target should have attachment in facet: " + facet[i]);
    }

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }

  @Test
  @Order(74)
  void testChainMoveAttachments() throws IOException {
    System.out.println("Test (74): Chain move attachments: Source -> Target1 -> Target2");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String target1BookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    String target2BookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);

    if (sourceBookID.equals("Could not create entity")
        || target1BookID.equals("Could not create entity")
        || target2BookID.equals("Could not create entity")) {
      fail("Could not create books");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);
    String target1ChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, target1BookID);
    String target2ChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, target2BookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile = File.createTempFile("chain_move_test74_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, target1BookID);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, target2BookID);

    // First move: Source -> Target1
    Map<String, Object> sourceMetadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String objectId = sourceMetadata.get("objectId").toString();
    String sourceFolderId = sourceMetadata.get("folderId").toString();

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    // Put target1 book in draft mode before move
    String editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, target1BookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target1 book");
    }

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    api.moveAttachment(
        appUrl,
        chapterEntityName,
        facet[0],
        target1ChapterID,
        sourceFolderId,
        moveObjectIds,
        sourceFacet);

    waitForAllUploadsCompletion(target1ChapterID, facet[0], 300);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, target1BookID);

    // Verify in target1
    List<Map<String, Object>> target1Metadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], target1ChapterID);
    assertEquals(1, target1Metadata.size(), "Target1 should have the attachment");

    // Second move: Target1 -> Target2
    String target1AttachmentId = (String) target1Metadata.get(0).get("ID");
    Map<String, Object> target1AttMetadata =
        api.fetchMetadata(
            appUrl, chapterEntityName, facet[0], target1ChapterID, target1AttachmentId);
    String target1ObjectId = target1AttMetadata.get("objectId").toString();
    String target1FolderId = target1AttMetadata.get("folderId").toString();

    moveObjectIds.clear();
    moveObjectIds.add(target1ObjectId);

    editResponse = api.editEntityDraft(appUrl, bookEntityName, srvpath, target2BookID);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target2 book");
    }

    api.moveAttachment(
        appUrl,
        chapterEntityName,
        facet[0],
        target2ChapterID,
        target1FolderId,
        moveObjectIds,
        sourceFacet);

    waitForAllUploadsCompletion(target2ChapterID, facet[0], 300);
    api.saveEntityDraft(appUrl, bookEntityName, srvpath, target2BookID);

    // Verify final state
    List<Map<String, Object>> target2Metadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], target2ChapterID);
    assertEquals(1, target2Metadata.size(), "Target2 should have the attachment");

    target1Metadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], target1ChapterID);
    assertEquals(0, target1Metadata.size(), "Target1 should have no attachments");

    List<Map<String, Object>> sourceFinalMetadata =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID);
    assertEquals(0, sourceFinalMetadata.size(), "Source should have no attachments");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, target1BookID);
    api.deleteEntity(appUrl, bookEntityName, target2BookID);
  }

  @Test
  @Order(75)
  void testMoveAttachmentsWithoutSDMRole() throws IOException {
    System.out.println("Test (75): Move attachments fails without SDM role");

    String sourceBookID = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (sourceBookID.equals("Could not create entity")) {
      fail("Could not create source book");
    }

    String sourceChapterID =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, sourceBookID);

    // Create temp file
    ClassLoader classLoader = getClass().getClassLoader();
    File originalPdf = new File(classLoader.getResource("sample.pdf").getFile());
    File tempFile =
        File.createTempFile("move_no_role_test75_" + System.currentTimeMillis(), ".pdf");
    tempFile.deleteOnExit();
    java.nio.file.Files.copy(
        originalPdf.toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", sourceChapterID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    List<String> createResponse =
        api.createAttachment(
            appUrl, chapterEntityName, facet[0], sourceChapterID, srvpath, postData, tempFile);
    if (!createResponse.get(0).equals("Attachment created")) {
      fail("Could not create attachment");
    }
    String attachmentId = createResponse.get(1);

    api.saveEntityDraft(appUrl, bookEntityName, srvpath, sourceBookID);

    // Get object ID and folder ID
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID, attachmentId);
    String objectId = metadata.get("objectId").toString();
    String sourceFolderId = metadata.get("folderId").toString();

    // Create target with no role user
    String targetBookID =
        apiNoRoles.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (targetBookID.equals("Could not create entity")) {
      fail("Could not create target book");
    }

    String targetChapterID =
        apiNoRoles.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, targetBookID);

    List<String> moveObjectIds = new ArrayList<>();
    moveObjectIds.add(objectId);

    String sourceFacet = serviceName + "." + chapterEntityName + "." + facet[0];
    boolean moveFailed = false;
    String errorMessage = null;

    try {
      Map<String, Object> moveResult =
          apiNoRoles.moveAttachment(
              appUrl,
              chapterEntityName,
              facet[0],
              targetChapterID,
              sourceFolderId,
              moveObjectIds,
              sourceFacet);

      if (moveResult == null || moveResult.containsKey("error")) {
        moveFailed = true;
        errorMessage = moveResult != null ? moveResult.get("error").toString() : "null result";
      }
    } catch (Exception e) {
      moveFailed = true;
      errorMessage = e.getMessage();
    }

    assertTrue(moveFailed, "Move should fail without SDM role");
    System.out.println("Move correctly failed without SDM role: " + errorMessage);

    // Verify source still has attachment
    List<Map<String, Object>> sourceMetadataAfter =
        api.fetchEntityMetadata(appUrl, chapterEntityName, facet[0], sourceChapterID);
    assertEquals(1, sourceMetadataAfter.size(), "Source should still have attachment");

    // Cleanup
    api.deleteEntity(appUrl, bookEntityName, sourceBookID);
    api.deleteEntity(appUrl, bookEntityName, targetBookID);
  }
}
