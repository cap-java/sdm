package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for the active entity attachment flow.
 *
 * <p>These tests cover the createAttachmentInActive action, which creates attachments directly on
 * published (active) entities without going through the draft choreography. This exercises the full
 * path: AdminServiceHandler → SDMAttachmentsServiceHandler (active entity detection) → ThreadLocal
 * metadata storage → SDMCreateAttachmentsHandler @After update (objectId, folderId, uploadStatus).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_ActiveEntity {
  private static String token;
  private static String tokenNoRoles;
  private static String clientId;
  private static String clientSecret;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String noSDMRoleUsername;
  private static String noSDMRoleUserPassword;
  private static String serviceName = "AdminService";
  private static String entityName = "Books";
  private static String entityName2 = "author";
  private static String chapterEntityName = "Chapters";
  private static String bookEntityName = "Books";
  private static String srvpath = "AdminService";
  private static ApiInterface api;
  private static ApiInterface apiNoRoles;

  // Entity IDs shared across ordered tests
  private static String entityID;
  private static String entityID2;
  private static String entityID3;
  private static String bookIDForChapter;
  private static String chapterID;

  // Attachment IDs discovered after active entity creation
  private static String activeAttachmentID1 = "";
  private static String activeAttachmentID2 = "";

  @BeforeAll
  static void setup() throws IOException {
    Properties credentialsProperties = Credentials.getCredentials();
    String tenancyModel = System.getProperty("tenancyModel");
    String tenant = System.getProperty("tenant");

    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");
    noSDMRoleUsername = credentialsProperties.getProperty("noSDMRoleUsername");
    noSDMRoleUserPassword = credentialsProperties.getProperty("noSDMRoleUserPassword");
    if (tenancyModel.equals("single")) {
      System.out.println("Running active entity integration tests | Single tenant Scenario");
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
            "Running active entity integration tests | Multitenant Scenario | SDM DEV Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMT1");
      } else if (tenant.equals("TENANT2")) {
        System.out.println(
            "Running active entity integration tests | Multitenant Scenario | Googleworkspace Consumer");
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

  @Test
  @Order(1)
  void testCreateActiveEntityAndCreateAttachmentInActive() throws IOException {
    System.out.println(
        "Test (1) : Create entity, activate it, and create attachment via active entity flow");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID = response;
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (response.equals("Saved")) {
        response = api.createAttachmentInActive(appUrl, entityName, "attachments", entityID);
        if (response.equals("Attachment created in active entity")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not create attachment via active entity flow. Last response: " + response);
    }
  }

  @Test
  @Order(2)
  void testVerifyAttachmentExistsAfterActiveEntityCreation() throws IOException {
    System.out.println(
        "Test (2) : Verify attachment appears in entity metadata after active entity creation");
    Boolean testStatus = false;
    List<Map<String, Object>> attachments =
        api.fetchEntityMetadata(appUrl, entityName, "attachments", entityID);
    if (attachments != null && !attachments.isEmpty()) {
      activeAttachmentID1 = (String) attachments.get(0).get("ID");
      if (activeAttachmentID1 != null && !activeAttachmentID1.isEmpty()) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail(
          "Attachment not found in entity metadata after createAttachmentInActive. Attachments: "
              + attachments);
    }
  }

  @Test
  @Order(3)
  void testVerifySDMMetadataOnActiveEntityAttachment() throws IOException {
    System.out.println(
        "Test (3) : Verify SDM metadata (objectId, folderId, uploadStatus) is set correctly"
            + " — tests ThreadLocal mechanism and @After handler");
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, entityName, "attachments", entityID, activeAttachmentID1);
    assertNotNull(metadata, "Metadata must not be null");
    Object objectId = metadata.get("objectId");
    assertNotNull(
        objectId, "objectId must be populated by SDMCreateAttachmentsHandler @After handler");
    assertFalse(objectId.toString().isEmpty(), "objectId must not be empty");
    Object folderId = metadata.get("folderId");
    assertNotNull(folderId, "folderId must be set after active entity upload");
    assertFalse(folderId.toString().isEmpty(), "folderId must not be empty");
    Object uploadStatus = metadata.get("uploadStatus");
    assertEquals(
        "Success", uploadStatus, "uploadStatus must be Success after active entity upload");
  }

  @Test
  @Order(4)
  void testReadContentOfActiveEntityAttachment() throws IOException {
    System.out.println(
        "Test (4) : Read attachment content from active entity — confirms file is in SDM");
    String response =
        api.readAttachment(appUrl, entityName, "attachments", entityID, activeAttachmentID1);
    if (!response.equals("OK")) {
      fail("Could not read attachment content. Response: " + response);
    }
  }

  @Test
  @Order(5)
  void testCreateAttachmentInActiveWithoutSDMRole() throws IOException {
    System.out.println(
        "Test (5) : Create attachment in active entity without SDM role — expect 500");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID2 = response;
      String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
      if (saveResponse.equals("Saved")) {
        String createResponse =
            apiNoRoles.createAttachmentInActive(appUrl, entityName, "attachments", entityID2);
        if (createResponse.contains("\"code\":\"500\"")
            && createResponse.contains(
                "You do not have the required permissions to upload attachments")) {
          testStatus = true;
        } else {
          System.out.println(
              "Unexpected response from no-roles user createAttachmentInActive: " + createResponse);
        }
      }
    }
    if (!testStatus) {
      fail("Attachment was created without SDM role, or entity setup failed");
    }
  }

  @Test
  @Order(6)
  void testCreateAttachmentInActiveOnEntityWithExistingDraftAttachments() throws IOException {
    System.out.println(
        "Test (6) : Active entity flow coexists with draft-uploaded attachments on same entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID3 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");
      List<String> createResponse =
          api.createAttachment(
              appUrl, entityName, "attachments", entityID3, srvpath, postData, file);
      if (createResponse.get(0).equals("Attachment created")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          // Now add an attachment via the active entity flow on the same entity
          String activeResponse =
              api.createAttachmentInActive(appUrl, entityName, "attachments", entityID3);
          if (activeResponse.equals("Attachment created in active entity")) {
            List<Map<String, Object>> attachments =
                api.fetchEntityMetadata(appUrl, entityName, "attachments", entityID3);
            if (attachments != null && attachments.size() == 2) {
              // Verify the active-flow attachment also has objectId
              boolean allHaveObjectId =
                  attachments.stream()
                      .allMatch(
                          m ->
                              m.get("objectId") != null && !m.get("objectId").toString().isEmpty());
              if (allHaveObjectId) {
                testStatus = true;
              }
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Active entity flow did not coexist properly with draft-uploaded attachments");
    }
  }

  @Test
  @Order(7)
  void testCreateMultipleAttachmentsViaActiveEntityFlow() throws IOException {
    System.out.println(
        "Test (7) : Create second attachment via active entity flow on same entity — timestamp-based"
            + " filename prevents duplicates");
    Boolean testStatus = false;
    String response = api.createAttachmentInActive(appUrl, entityName, "attachments", entityID);
    if (response.equals("Attachment created in active entity")) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, "attachments", entityID);
      if (attachments != null && attachments.size() == 2) {
        // Discover the second attachment ID (the one that isn't activeAttachmentID1)
        for (Map<String, Object> att : attachments) {
          String id = (String) att.get("ID");
          if (id != null && !id.equals(activeAttachmentID1)) {
            activeAttachmentID2 = id;
            break;
          }
        }
        if (!activeAttachmentID2.isEmpty()) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail(
          "Could not create second attachment via active entity flow or count mismatch. Response: "
              + response);
    }
  }

  @Test
  @Order(8)
  void testCreateAttachmentInActiveOnReferencesFacet() throws IOException {
    System.out.println("Test (8) : Create attachment in active entity on 'references' facet");
    Boolean testStatus = false;
    String response = api.createAttachmentInActive(appUrl, entityName, "references", entityID);
    if (response.equals("Attachment created in active entity")) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, "references", entityID);
      if (attachments != null && !attachments.isEmpty()) {
        String refAttachmentID = (String) attachments.get(0).get("ID");
        if (refAttachmentID != null && !refAttachmentID.isEmpty()) {
          Map<String, Object> metadata =
              api.fetchMetadata(appUrl, entityName, "references", entityID, refAttachmentID);
          Object objectId = metadata.get("objectId");
          if (objectId != null && !objectId.toString().isEmpty()) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail(
          "Could not create attachment in active entity on references facet. Response: "
              + response);
    }
  }

  @Test
  @Order(9)
  void testCreateAttachmentInActiveOnFootnotesFacet() throws IOException {
    System.out.println("Test (9) : Create attachment in active entity on 'footnotes' facet");
    Boolean testStatus = false;
    String response = api.createAttachmentInActive(appUrl, entityName, "footnotes", entityID);
    if (response.equals("Attachment created in active entity")) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, "footnotes", entityID);
      if (attachments != null && !attachments.isEmpty()) {
        String footnoteAttachmentID = (String) attachments.get(0).get("ID");
        if (footnoteAttachmentID != null && !footnoteAttachmentID.isEmpty()) {
          Map<String, Object> metadata =
              api.fetchMetadata(appUrl, entityName, "footnotes", entityID, footnoteAttachmentID);
          Object objectId = metadata.get("objectId");
          if (objectId != null && !objectId.toString().isEmpty()) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail(
          "Could not create attachment in active entity on footnotes facet. Response: " + response);
    }
  }

  @Test
  @Order(10)
  void testDeleteAttachmentCreatedViaActiveEntityFlow() throws IOException {
    System.out.println("Test (10) : Delete attachment that was created via active entity flow");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response =
          api.deleteAttachment(appUrl, entityName, "attachments", entityID, activeAttachmentID1);
      if (response == "Deleted") {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response == "Saved") {
          response =
              api.readAttachment(appUrl, entityName, "attachments", entityID, activeAttachmentID1);
          if (response.equals("Could not read Attachment")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail(
          "Could not delete attachment created via active entity flow. Last response: " + response);
    }
  }

  @Test
  @Order(11)
  void testRenameAttachmentCreatedViaActiveEntityFlow() throws IOException {
    System.out.println("Test (11) : Rename attachment that was created via active entity flow");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      String newName = "renamed-active-attachment.txt";
      response =
          api.renameAttachment(
              appUrl, entityName, "attachments", entityID, activeAttachmentID2, newName);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          Map<String, Object> metadata =
              api.fetchMetadata(appUrl, entityName, "attachments", entityID, activeAttachmentID2);
          if (newName.equals(metadata.get("fileName"))) {
            testStatus = true;
          }
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Could not rename attachment created via active entity flow");
    }
  }

  @Test
  @Order(12)
  void testDownloadAttachmentCreatedViaActiveEntityFlow() throws IOException {
    System.out.println("Test (12) : Batch download attachment created via active entity flow");
    Boolean testStatus = false;
    List<String> ids = new ArrayList<>();
    ids.add(activeAttachmentID2);
    String response =
        api.downloadSelectedAttachments(appUrl, entityName, "attachments", entityID, ids);
    if (response != null && !response.isEmpty()) {
      testStatus = true;
    }
    if (!testStatus) {
      fail("Could not download attachment created via active entity flow. Response: " + response);
    }
  }

  @Test
  @Order(13)
  void testCopyActiveEntityAttachmentToAnotherEntity() throws IOException {
    System.out.println(
        "Test (13) : Copy attachment (created via active entity flow) to another entity");
    Boolean testStatus = false;
    Map<String, Object> metadata =
        api.fetchMetadata(appUrl, entityName, "attachments", entityID, activeAttachmentID2);
    if (!metadata.containsKey("objectId")) {
      fail("Source attachment metadata does not contain objectId");
    }
    String sourceObjectId = metadata.get("objectId").toString();
    // Use entityID3 (already has attachments) as the copy target
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      List<String> objectIdsToCopy = new ArrayList<>();
      objectIdsToCopy.add(sourceObjectId);
      String copyResponse =
          api.copyAttachment(appUrl, entityName, "attachments", entityID3, objectIdsToCopy);
      if (copyResponse.equals("Attachments copied successfully")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          List<Map<String, Object>> copiedAttachments =
              api.fetchEntityMetadata(appUrl, entityName, "attachments", entityID3);
          // entityID3 had 2 attachments (1 draft + 1 active) — after copy it should have 3
          if (copiedAttachments != null && copiedAttachments.size() >= 3) {
            boolean copiedHasObjectId =
                copiedAttachments.stream()
                    .anyMatch(
                        m -> m.get("objectId") != null && !m.get("objectId").toString().isEmpty());
            if (copiedHasObjectId) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not copy attachment created via active entity flow to another entity");
    }
  }

  @Test
  @Order(14)
  void testNestedChapterEntityCreateAttachmentInActive() throws IOException {
    System.out.println(
        "Test (14) : Create attachment in active entity on nested Chapters entity — exercises"
            + " extractParentId CQN path traversal in AdminServiceHandler");
    Boolean testStatus = false;
    // Create a book first
    String bookResponse = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!bookResponse.equals("Could not create entity")) {
      bookIDForChapter = bookResponse;
      // Create a chapter inside the book
      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookIDForChapter);
      if (!chapterResponse.equals("Could not create entity")) {
        chapterID = chapterResponse;
        // Save the book (this activates both book and chapter)
        String saveResponse =
            api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookIDForChapter);
        if (saveResponse.equals("Saved")) {
          // Verify chapter exists as active entity
          String checkResponse = api.checkEntity(appUrl, chapterEntityName, chapterID);
          if (checkResponse.equals("Entity exists")) {
            // Call createAttachmentInActive on the active chapter
            String activeResponse =
                api.createAttachmentInActive(appUrl, chapterEntityName, "attachments", chapterID);
            if (activeResponse.equals("Attachment created in active entity")) {
              List<Map<String, Object>> attachments =
                  api.fetchEntityMetadata(appUrl, chapterEntityName, "attachments", chapterID);
              if (attachments != null && !attachments.isEmpty()) {
                String chapterAttachmentID = (String) attachments.get(0).get("ID");
                if (chapterAttachmentID != null && !chapterAttachmentID.isEmpty()) {
                  Map<String, Object> chapterMetadata =
                      api.fetchMetadata(
                          appUrl, chapterEntityName, "attachments", chapterID, chapterAttachmentID);
                  Object objectId = chapterMetadata.get("objectId");
                  if (objectId != null && !objectId.toString().isEmpty()) {
                    testStatus = true;
                  }
                }
              }
            } else {
              System.out.println("createAttachmentInActive on chapter failed: " + activeResponse);
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not create attachment via active entity flow on nested Chapters entity");
    }
  }

  @Test
  @Order(15)
  void testDeleteEntityWithActiveEntityAttachments() {
    System.out.println(
        "Test (15) : Delete entity that has attachments created via active entity flow");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, entityName, entityID);
    if (response == "Entity Deleted") {
      response = api.checkEntity(appUrl, entityName, entityID);
      if (response.equals("Entity doesn't exist")) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Could not delete entity with active entity attachments. Response: " + response);
    }
  }

  @Test
  @Order(16)
  void testCleanup() {
    System.out.println("Test (16) : Cleanup remaining test entities");
    Boolean testStatus = true;
    if (entityID2 != null && !entityID2.isEmpty()) {
      String response = api.deleteEntity(appUrl, entityName, entityID2);
      if (response != "Entity Deleted") {
        System.out.println("Warning: could not delete entityID2: " + response);
        testStatus = false;
      }
    }
    if (entityID3 != null && !entityID3.isEmpty()) {
      String response = api.deleteEntity(appUrl, entityName, entityID3);
      if (response != "Entity Deleted") {
        System.out.println("Warning: could not delete entityID3: " + response);
        testStatus = false;
      }
    }
    // Deleting the book also removes the chapter (composition)
    if (bookIDForChapter != null && !bookIDForChapter.isEmpty()) {
      String response = api.deleteEntity(appUrl, bookEntityName, bookIDForChapter);
      if (response != "Entity Deleted") {
        System.out.println("Warning: could not delete bookIDForChapter: " + response);
        testStatus = false;
      }
    }
    if (!testStatus) {
      fail("Cleanup failed — some test entities could not be deleted");
    }
  }
}
