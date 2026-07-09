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
 * Integration tests for the active entity attachment flow on nested Chapters entities across
 * multiple facets.
 *
 * <p>Exercises {@code createAttachmentInActive} on a Chapter (child composition of Book) for every
 * facet ({@code attachments}, {@code references}, {@code footnotes}). This targets the
 * extractParentId CQN path traversal in AdminServiceHandler and confirms the active-entity flow
 * handles nested compositions correctly.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_ActiveEntity_Chapters_MultipleFacet {
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
  private static String bookEntityName = "Books";
  private static String chapterEntityName = "Chapters";
  private static String entityName2 = "author";
  private static String srvpath = "AdminService";
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static ApiInterface api;
  private static ApiInterface apiNoRoles;

  // Entity IDs shared across ordered tests
  private static String bookID;
  private static String chapterID;
  private static String bookID2;
  private static String chapterID2;
  private static String bookID3;
  private static String chapterID3;

  // Attachment IDs (one per facet) discovered after active entity creation
  private static String[] chapterAttachmentIDs = new String[facet.length];

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
      System.out.println(
          "Running active entity multi-facet chapter integration tests | Single tenant Scenario");
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
            "Running active entity multi-facet chapter integration tests | Multitenant | SDM DEV Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMT1");
      } else if (tenant.equals("TENANT2")) {
        System.out.println(
            "Running active entity multi-facet chapter integration tests | Multitenant | Googleworkspace Consumer");
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

  /**
   * Creates a Book + nested Chapter and saves the draft so both become active. Returns an {@code
   * [bookID, chapterID]} array, or {@code null} if any step fails.
   */
  private static String[] createAndActivateBookWithChapter() {
    String bookResponse = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (bookResponse.equals("Could not create entity")) {
      return null;
    }
    String bId = bookResponse;
    String chapterResponse =
        api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bId);
    if (chapterResponse.equals("Could not create entity")) {
      return null;
    }
    String cId = chapterResponse;
    String saveResponse = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bId);
    if (!saveResponse.equals("Saved")) {
      return null;
    }
    String checkResponse = api.checkEntity(appUrl, chapterEntityName, cId);
    if (!checkResponse.equals("Entity exists")) {
      return null;
    }
    return new String[] {bId, cId};
  }

  @Test
  @Order(1)
  void testCreateAttachmentInActiveOnChapterAllFacets() throws IOException {
    System.out.println(
        "Test (1) : Create Book+Chapter, activate, then create attachment on all chapter facets"
            + " via active entity flow");
    boolean testStatus = false;
    String[] ids = createAndActivateBookWithChapter();
    if (ids != null) {
      bookID = ids[0];
      chapterID = ids[1];
      int successCount = 0;
      for (String f : facet) {
        String r = api.createAttachmentInActive(appUrl, chapterEntityName, f, chapterID);
        if (r.equals("Attachment created in active entity")) {
          successCount++;
        } else {
          System.out.println("createAttachmentInActive failed on chapter facet '" + f + "': " + r);
        }
      }
      if (successCount == facet.length) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Could not create attachment via active entity flow on all chapter facets");
    }
  }

  @Test
  @Order(2)
  void testVerifyChapterAttachmentsExistOnAllFacets() throws IOException {
    System.out.println(
        "Test (2) : Verify attachment appears in metadata on every chapter facet after active"
            + " entity create");
    boolean testStatus = true;
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, chapterEntityName, facet[i], chapterID);
      if (attachments == null || attachments.isEmpty()) {
        testStatus = false;
        System.out.println("No attachments found on chapter facet '" + facet[i] + "'");
        continue;
      }
      String id = (String) attachments.get(0).get("ID");
      if (id == null || id.isEmpty()) {
        testStatus = false;
        continue;
      }
      chapterAttachmentIDs[i] = id;
    }
    if (!testStatus) {
      fail(
          "Attachment not found in chapter metadata on one or more facets after"
              + " createAttachmentInActive");
    }
  }

  @Test
  @Order(3)
  void testVerifySDMMetadataOnChapterAllFacets() throws IOException {
    System.out.println(
        "Test (3) : Verify SDM metadata (objectId, folderId, uploadStatus) on every chapter facet");
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> metadata =
          api.fetchMetadata(
              appUrl, chapterEntityName, facet[i], chapterID, chapterAttachmentIDs[i]);
      assertNotNull(metadata, "Metadata must not be null for chapter facet '" + facet[i] + "'");
      Object objectId = metadata.get("objectId");
      assertNotNull(
          objectId,
          "objectId must be populated on chapter facet '" + facet[i] + "' by @After handler");
      assertFalse(
          objectId.toString().isEmpty(),
          "objectId must not be empty on chapter facet '" + facet[i] + "'");
      Object folderId = metadata.get("folderId");
      assertNotNull(folderId, "folderId must be set on chapter facet '" + facet[i] + "'");
      assertFalse(
          folderId.toString().isEmpty(),
          "folderId must not be empty on chapter facet '" + facet[i] + "'");
      Object uploadStatus = metadata.get("uploadStatus");
      assertEquals(
          "Success",
          uploadStatus,
          "uploadStatus must be Success on chapter facet '" + facet[i] + "'");
    }
  }

  @Test
  @Order(4)
  void testReadContentOfChapterAttachmentsOnAllFacets() throws IOException {
    System.out.println(
        "Test (4) : Read chapter attachment content on every facet — confirms file is in SDM");
    for (int i = 0; i < facet.length; i++) {
      String response =
          api.readAttachment(
              appUrl, chapterEntityName, facet[i], chapterID, chapterAttachmentIDs[i]);
      if (!response.equals("OK")) {
        fail(
            "Could not read chapter attachment content on facet '"
                + facet[i]
                + "'. Response: "
                + response);
      }
    }
  }

  @Test
  @Order(5)
  void testCreateAttachmentInActiveOnChapterWithoutSDMRoleAllFacets() throws IOException {
    System.out.println(
        "Test (5) : Create chapter attachment via active entity without SDM role on every facet —"
            + " expect 500");
    boolean testStatus = false;
    String[] ids = createAndActivateBookWithChapter();
    if (ids != null) {
      bookID2 = ids[0];
      chapterID2 = ids[1];
      int rejectedCount = 0;
      for (String f : facet) {
        String createResponse =
            apiNoRoles.createAttachmentInActive(appUrl, chapterEntityName, f, chapterID2);
        if (createResponse.contains("\"code\":\"500\"")
            && createResponse.contains(
                "You do not have the required permissions to upload attachments")) {
          rejectedCount++;
        } else {
          System.out.println(
              "Unexpected response from no-roles user on chapter facet '"
                  + f
                  + "': "
                  + createResponse);
        }
      }
      if (rejectedCount == facet.length) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail(
          "Chapter attachment was created without SDM role on one or more facets, or entity"
              + " setup failed");
    }
  }

  @Test
  @Order(6)
  void testCreateAttachmentInActiveCoexistsWithDraftUploadsOnChapterAllFacets() throws IOException {
    System.out.println(
        "Test (6) : Active entity flow coexists with draft-uploaded chapter attachments across"
            + " all facets");
    boolean testStatus = false;
    // Create a fresh book+chapter draft (kept in draft mode so we can upload draft attachments)
    String bookResponse = api.createEntityDraft(appUrl, bookEntityName, entityName2, srvpath);
    if (!bookResponse.equals("Could not create entity")) {
      bookID3 = bookResponse;
      String chapterResponse =
          api.createEntityDraft(appUrl, chapterEntityName, entityName2, srvpath, bookID3);
      if (!chapterResponse.equals("Could not create entity")) {
        chapterID3 = chapterResponse;
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("sample.pdf").getFile());
        int draftUploadCount = 0;
        for (String f : facet) {
          Map<String, Object> postData = new HashMap<>();
          postData.put("up__ID", chapterID3);
          postData.put("mimeType", "application/pdf");
          postData.put("createdAt", new Date().toString());
          postData.put("createdBy", "test@test.com");
          postData.put("modifiedBy", "test@test.com");
          List<String> createResponse =
              api.createAttachment(
                  appUrl, chapterEntityName, f, chapterID3, srvpath, postData, file);
          if (createResponse.get(0).equals("Attachment created")) {
            draftUploadCount++;
          }
        }
        if (draftUploadCount == facet.length) {
          String response = api.saveEntityDraft(appUrl, bookEntityName, srvpath, bookID3);
          if (response.equals("Saved")) {
            int activeCount = 0;
            for (String f : facet) {
              String activeResponse =
                  api.createAttachmentInActive(appUrl, chapterEntityName, f, chapterID3);
              if (activeResponse.equals("Attachment created in active entity")) {
                activeCount++;
              }
            }
            if (activeCount == facet.length) {
              boolean allValid = true;
              for (String f : facet) {
                List<Map<String, Object>> attachments =
                    api.fetchEntityMetadata(appUrl, chapterEntityName, f, chapterID3);
                if (attachments == null || attachments.size() != 2) {
                  allValid = false;
                  break;
                }
                boolean allHaveObjectId =
                    attachments.stream()
                        .allMatch(
                            m ->
                                m.get("objectId") != null
                                    && !m.get("objectId").toString().isEmpty());
                if (!allHaveObjectId) {
                  allValid = false;
                  break;
                }
              }
              if (allValid) {
                testStatus = true;
              }
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail(
          "Active entity flow did not coexist properly with draft-uploaded chapter attachments"
              + " on all facets");
    }
  }

  @Test
  @Order(7)
  void testDeleteBookRemovesChapterWithActiveAttachments() {
    System.out.println(
        "Test (7) : Delete book — cascade should remove chapter + its active-entity attachments");
    boolean testStatus = false;
    String response = api.deleteEntity(appUrl, bookEntityName, bookID);
    if (response == "Entity Deleted") {
      response = api.checkEntity(appUrl, chapterEntityName, chapterID);
      if (response.equals("Entity doesn't exist")) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Could not delete book with chapter's active entity attachments. Response: " + response);
    }
  }

  @Test
  @Order(8)
  void testCleanup() {
    System.out.println("Test (8) : Cleanup remaining test books");
    boolean testStatus = true;
    if (bookID2 != null && !bookID2.isEmpty()) {
      String response = api.deleteEntity(appUrl, bookEntityName, bookID2);
      if (response != "Entity Deleted") {
        System.out.println("Warning: could not delete bookID2: " + response);
        testStatus = false;
      }
    }
    if (bookID3 != null && !bookID3.isEmpty()) {
      String response = api.deleteEntity(appUrl, bookEntityName, bookID3);
      if (response != "Entity Deleted") {
        System.out.println("Warning: could not delete bookID3: " + response);
        testStatus = false;
      }
    }
    if (!testStatus) {
      fail("Cleanup failed — some test books could not be deleted");
    }
  }
}
