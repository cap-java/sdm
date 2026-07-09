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
 * Integration tests for the active entity attachment flow across multiple facets.
 *
 * <p>Mirrors {@link IntegrationTest_ActiveEntity} but exercises the {@code
 * createAttachmentInActive} action on every facet ({@code attachments}, {@code references}, {@code
 * footnotes}) of the Books entity in sequence, so a regression in one facet doesn't hide behind the
 * others.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_ActiveEntity_MultipleFacet {
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
  private static String srvpath = "AdminService";
  private static String[] facet = {"attachments", "references", "footnotes"};
  // Distinct author-name marker for this suite so its cleanup only touches its own books and
  // doesn't collide with sibling ActiveEntity matrix jobs.
  private static final String AUTHOR_NAME = "author-active-multifacet";
  private static ApiInterface api;
  private static ApiInterface apiNoRoles;

  // Entity IDs shared across ordered tests
  private static String entityID;
  private static String entityID2;
  private static String entityID3;

  // Attachment IDs (one per facet) discovered after active entity creation
  private static String[] activeAttachmentIDs = new String[facet.length];

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
          "Running active entity multi-facet integration tests | Single tenant Scenario");
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
            "Running active entity multi-facet integration tests | Multitenant | SDM DEV Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMT1");
      } else if (tenant.equals("TENANT2")) {
        System.out.println(
            "Running active entity multi-facet integration tests | Multitenant | Googleworkspace Consumer");
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

    // Pre-test cleanup: remove any stale test-fixture books (author == "author") from prior runs.
    // Failures here are logged but never block the tests so we can still see the real test outcome.
    cleanupStaleTestData(client);
  }

  /**
   * Deletes any leftover {@code Books} rows whose {@code author} field equals the test-fixture
   * literal {@code "author"} — both active and draft. Best-effort: any IO/HTTP failure is logged
   * with a warning and the method returns so the real tests can still run.
   */
  private static void cleanupStaleTestData(OkHttpClient client) {
    String tenancyModel = System.getProperty("tenancyModel");
    String baseUrl =
        "multi".equals(tenancyModel)
            ? "https://" + appUrl + "/api/admin/" + entityName
            : "https://" + appUrl + "/odata/v4/" + srvpath + "/" + entityName;
    int activeDeleted =
        cleanupBookSet(client, baseUrl + "?$filter=author/name eq '" + AUTHOR_NAME + "'", true);
    int draftDeleted =
        cleanupBookSet(
            client,
            baseUrl + "?$filter=author/name eq '" + AUTHOR_NAME + "' and IsActiveEntity eq false",
            false);
    System.out.println(
        "🧹 Pre-test cleanup: deleted "
            + activeDeleted
            + " active and "
            + draftDeleted
            + " draft fixture book(s) (author='author')");
  }

  /**
   * Fetches all books matching {@code filterUrl} and deletes each by ID. Returns count successfully
   * deleted. Never throws — IO failures are swallowed with a warning so cleanup never blocks tests.
   */
  private static int cleanupBookSet(OkHttpClient client, String filterUrl, boolean active) {
    int deleted = 0;
    try {
      Request listReq =
          new Request.Builder()
              .url(filterUrl)
              .get()
              .addHeader("Authorization", "Bearer " + token)
              .build();
      try (Response listRes = client.newCall(listReq).execute()) {
        if (listRes.code() != 200) {
          System.out.println(
              "⚠️ Cleanup list ("
                  + (active ? "active" : "draft")
                  + ") returned HTTP "
                  + listRes.code()
                  + " — skipping");
          return 0;
        }
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(listRes.body().string());
        com.fasterxml.jackson.databind.JsonNode values = root.path("value");
        if (!values.isArray()) {
          return 0;
        }
        for (com.fasterxml.jackson.databind.JsonNode book : values) {
          String id = book.path("ID").asText("");
          if (id.isEmpty()) {
            continue;
          }
          try {
            String resp =
                active
                    ? api.deleteEntity(appUrl, entityName, id)
                    : api.deleteEntityDraft(appUrl, entityName, id);
            if ("Entity Deleted".equals(resp) || "Deleted".equals(resp)) {
              deleted++;
            } else {
              System.out.println(
                  "⚠️ Cleanup could not delete "
                      + (active ? "active" : "draft")
                      + " book "
                      + id
                      + " (response: "
                      + resp
                      + ")");
            }
          } catch (Exception innerEx) {
            System.out.println(
                "⚠️ Cleanup delete failed for "
                    + (active ? "active" : "draft")
                    + " book "
                    + id
                    + ": "
                    + innerEx.getMessage());
          }
        }
      }
    } catch (Exception e) {
      System.out.println(
          "⚠️ Cleanup "
              + (active ? "active" : "draft")
              + " list-fetch failed (continuing): "
              + e.getMessage());
    }
    return deleted;
  }

  @Test
  @Order(1)
  void testCreateActiveEntityAndCreateAttachmentInActiveOnAllFacets() throws IOException {
    System.out.println(
        "Test (1) : Create entity, activate it, and create attachment on all facets via active"
            + " entity flow");
    boolean testStatus = false;
    String response =
        api.createEntityDraftWithAuthor(appUrl, entityName, entityName2, srvpath, AUTHOR_NAME);
    if (!response.equals("Could not create entity")) {
      entityID = response;
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (response.equals("Saved")) {
        int successCount = 0;
        for (String f : facet) {
          String r = api.createAttachmentInActive(appUrl, entityName, f, entityID);
          if (r.equals("Attachment created in active entity")) {
            successCount++;
          } else {
            System.out.println("createAttachmentInActive failed on facet '" + f + "': " + r);
          }
        }
        if (successCount == facet.length) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail(
          "Could not create attachment via active entity flow on all facets. Last response: "
              + response);
    }
  }

  @Test
  @Order(2)
  void testVerifyAttachmentsExistOnAllFacetsAfterActiveEntityCreation() throws IOException {
    System.out.println(
        "Test (2) : Verify attachment appears in metadata on every facet after active entity create");
    boolean testStatus = true;
    for (int i = 0; i < facet.length; i++) {
      List<Map<String, Object>> attachments =
          api.fetchEntityMetadata(appUrl, entityName, facet[i], entityID);
      if (attachments == null || attachments.isEmpty()) {
        testStatus = false;
        System.out.println("No attachments found on facet '" + facet[i] + "'");
        continue;
      }
      String id = (String) attachments.get(0).get("ID");
      if (id == null || id.isEmpty()) {
        testStatus = false;
        continue;
      }
      activeAttachmentIDs[i] = id;
    }
    if (!testStatus) {
      fail(
          "Attachment not found in entity metadata on one or more facets after"
              + " createAttachmentInActive");
    }
  }

  @Test
  @Order(3)
  void testVerifySDMMetadataOnAllFacets() throws IOException {
    System.out.println(
        "Test (3) : Verify SDM metadata (objectId, folderId, uploadStatus) on every facet");
    for (int i = 0; i < facet.length; i++) {
      Map<String, Object> metadata =
          api.fetchMetadata(appUrl, entityName, facet[i], entityID, activeAttachmentIDs[i]);
      assertNotNull(metadata, "Metadata must not be null for facet '" + facet[i] + "'");
      Object objectId = metadata.get("objectId");
      assertNotNull(
          objectId, "objectId must be populated on facet '" + facet[i] + "' by @After handler");
      assertFalse(
          objectId.toString().isEmpty(), "objectId must not be empty on facet '" + facet[i] + "'");
      Object folderId = metadata.get("folderId");
      assertNotNull(folderId, "folderId must be set on facet '" + facet[i] + "'");
      assertFalse(
          folderId.toString().isEmpty(), "folderId must not be empty on facet '" + facet[i] + "'");
      Object uploadStatus = metadata.get("uploadStatus");
      assertEquals(
          "Success", uploadStatus, "uploadStatus must be Success on facet '" + facet[i] + "'");
    }
  }

  @Test
  @Order(4)
  void testReadContentOfActiveEntityAttachmentsOnAllFacets() throws IOException {
    System.out.println(
        "Test (4) : Read attachment content on every facet — confirms file is in SDM");
    for (int i = 0; i < facet.length; i++) {
      String response =
          api.readAttachment(appUrl, entityName, facet[i], entityID, activeAttachmentIDs[i]);
      if (!response.equals("OK")) {
        fail(
            "Could not read attachment content on facet '" + facet[i] + "'. Response: " + response);
      }
    }
  }

  @Test
  @Order(5)
  void testCreateAttachmentInActiveWithoutSDMRoleOnAllFacets() throws IOException {
    System.out.println(
        "Test (5) : Create attachment via active entity without SDM role on every facet — expect"
            + " 500");
    boolean testStatus = false;
    String response =
        api.createEntityDraftWithAuthor(appUrl, entityName, entityName2, srvpath, AUTHOR_NAME);
    if (!response.equals("Could not create entity")) {
      entityID2 = response;
      String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
      if (saveResponse.equals("Saved")) {
        int rejectedCount = 0;
        for (String f : facet) {
          String createResponse =
              apiNoRoles.createAttachmentInActive(appUrl, entityName, f, entityID2);
          if (createResponse.contains("\"code\":\"500\"")
              && createResponse.contains(
                  "You do not have the required permissions to upload attachments")) {
            rejectedCount++;
          } else {
            System.out.println(
                "Unexpected response from no-roles user on facet '" + f + "': " + createResponse);
          }
        }
        if (rejectedCount == facet.length) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Attachment was created without SDM role on one or more facets, or entity setup failed");
    }
  }

  @Test
  @Order(6)
  void testCreateAttachmentInActiveCoexistsWithDraftUploadsOnAllFacets() throws IOException {
    System.out.println(
        "Test (6) : Active entity flow coexists with draft-uploaded attachments across all facets");
    boolean testStatus = false;
    String response =
        api.createEntityDraftWithAuthor(appUrl, entityName, entityName2, srvpath, AUTHOR_NAME);
    if (!response.equals("Could not create entity")) {
      entityID3 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());
      int draftUploadCount = 0;
      for (String f : facet) {
        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", entityID3);
        postData.put("mimeType", "application/pdf");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");
        List<String> createResponse =
            api.createAttachment(appUrl, entityName, f, entityID3, srvpath, postData, file);
        if (createResponse.get(0).equals("Attachment created")) {
          draftUploadCount++;
        }
      }
      if (draftUploadCount == facet.length) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          int activeCount = 0;
          for (String f : facet) {
            String activeResponse = api.createAttachmentInActive(appUrl, entityName, f, entityID3);
            if (activeResponse.equals("Attachment created in active entity")) {
              activeCount++;
            }
          }
          if (activeCount == facet.length) {
            // Verify every facet now has 2 attachments and both carry an objectId
            boolean allValid = true;
            for (String f : facet) {
              List<Map<String, Object>> attachments =
                  api.fetchEntityMetadata(appUrl, entityName, f, entityID3);
              if (attachments == null || attachments.size() != 2) {
                allValid = false;
                break;
              }
              boolean allHaveObjectId =
                  attachments.stream()
                      .allMatch(
                          m ->
                              m.get("objectId") != null && !m.get("objectId").toString().isEmpty());
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
    if (!testStatus) {
      fail(
          "Active entity flow did not coexist properly with draft-uploaded attachments on all"
              + " facets");
    }
  }

  @Test
  @Order(7)
  void testDeleteEntityWithActiveEntityAttachmentsOnAllFacets() {
    System.out.println("Test (7) : Delete entity that has active-entity attachments on all facets");
    boolean testStatus = false;
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
  @Order(8)
  void testCleanup() {
    System.out.println("Test (8) : Cleanup remaining test entities");
    boolean testStatus = true;
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
    if (!testStatus) {
      fail("Cleanup failed — some test entities could not be deleted");
    }
  }
}
