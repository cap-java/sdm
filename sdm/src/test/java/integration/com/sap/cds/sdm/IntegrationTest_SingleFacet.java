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
class IntegrationTest_SingleFacet {
  private static String token;
  private static String tokenNoRoles;
  private static String entityID;
  private static String entityID2;
  private static String facetName = "attachments";
  private static String entityID3;
  private static String entityID4;
  private static String entityID5;
  private static String entityID6;
  private static String entityID7;
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
  private static ApiInterface api;
  private static ApiInterface apiNoRoles;
  private static String attachmentID1 = "";
  private static String attachmentID2 = "";
  private static String attachmentID3 = "";
  private static String attachmentID4 = "";
  private static String attachmentID5 = "";
  private static String attachmentID6 = "";
  private static String attachmentID7 = "";
  private static String attachmentID8 = "";
  private static String attachmentID9 = "";
  private static String attachmentID10 = "";
  private static String copyAttachmentSourceEntity;
  private static String copyAttachmentTargetEntity;
  private static String copyAttachmentTargetEntityEmpty;
  private static String copyTargetEntity;
  private static String copySourceEntity;
  private static String createLinkEntity;
  private static String editLinkEntity;
  private static List<String> sourceObjectIds = new ArrayList<>();
  private static List<String> targetAttachmentIds = new ArrayList<>();

  private static IntegrationTestUtils integrationTestUtils;

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
    integrationTestUtils = new IntegrationTestUtils();

    // Encode clientId:clientSecret to Base64
    String credentials = clientId + ":" + clientSecret;
    String basicAuth =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    OkHttpClient client = new OkHttpClient().newBuilder().build();
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
  void testCreateEntityAndCheck() {
    System.out.println("Test (1) : Create entity and check if it exists");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID = response;
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, entityName, entityID);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not create entity");
    }
  }

  @Test
  @Order(2)
  void testUpdateEmptyEntity() {
    System.out.println("Test (2) : Update an existing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, entityName, entityID);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not update entity");
    }
  }

  @Test
  @Order(3)
  void testUploadSingleAttachmentPDF() throws IOException {
    System.out.println("Test (3) : Upload pdf");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID1 = createResponse.get(1);
        response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID, attachmentID1);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID1);

            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf " + response);
    }
  }

  @Test
  @Order(4)
  void testUploadSingleAttachmentTXT() throws IOException {
    System.out.println("Test (4) : Upload txt");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/txt");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID2 = createResponse.get(1);
        response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID, attachmentID2);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID2);
            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.txt");
    }
  }

  @Test
  @Order(5)
  void testUploadSingleAttachmentEXE() throws IOException {
    System.out.println("Test (5) : Upload exe");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.exe").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/exe");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID3 = createResponse.get(1);
        response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID, attachmentID3);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID3);
            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not create sample.exe");
    }
  }

  @Test
  @Order(6)
  void testUploadAttachmentWithoutSDMRole() throws IOException {
    System.out.println("Test (6) : Upload attachment with no SDM role");
    Boolean testStatus = false;
    String response = apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID4 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
      Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID4);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          apiNoRoles.createAttachment(
              appUrl, entityName, facetName, entityID4, srvpath, postData, tempFile);
      String check = createResponse.get(0);
      String expectedString =
          "{\"error\":{\"code\":\"500\",\"message\":\"You do not have the required permissions to upload attachments. Please contact your administrator for access.\"}}";
      if (check.equals(expectedString)) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Attachment created without SDM role");
    }
  }

  @Test
  @Order(7)
  void testUploadSingleAttachmentPDFDuplicate() throws IOException {
    System.out.println("Test (7) : Upload duplicate pdf");
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Boolean testStatus = false;

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        testStatus = false;
      } else {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          String expectedJson =
              "{\"error\":{\"code\":\"500\",\"message\":\"sample.pdf already exists.\"}}";
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode actualJsonNode = objectMapper.readTree(check);
          JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
          if (expectedJsonNode.equals(actualJsonNode)) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Attachment created");
    }
  }

  @Test
  @Order(8)
  void testUploadSingleAttachmentPDFDuplicateDifferentEntity() throws IOException {
    System.out.println("Test (8) : Upload duplicate pdf in different entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID2 = response;
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, entityName, entityID2);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not create entity");
    }

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID2);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    response = api.editEntityDraft(appUrl, entityName, srvpath, entityID2);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID2, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals(facetName + " created")) {
        attachmentID4 = createResponse.get(1);
        response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID2, attachmentID4);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, entityName, facetName, entityID2, attachmentID4);

            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf " + response);
    }
  }

  @Test
  @Order(9)
  void testCreateAttachmentWithRestrictedCharacterInFilename() throws IOException {
    System.out.println("Test (9): Create attachment with restricted character in filename");

    boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

    File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
    Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      List<String> createResponse =
          api.createAttachment(
              appUrl, entityName, facetName, entityID, srvpath, postData, tempFile);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID6 = createResponse.get(1);

        String restrictedFilename = "a/\\bc.pdf";
        response =
            api.renameAttachment(
                appUrl, entityName, facetName, entityID, attachmentID6, restrictedFilename);

        if (response.equals("Renamed")) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          String expected =
              "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported "
                  + "characters (/, \\\\). \\n\\n\\t\\u2022 a/\\bc.pdf\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
          if (response.equals(expected)) {
            testStatus = true;
          }
        } else {
          api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        }
      }
    }
    if (!testStatus) {
      fail("Attachment created with restricted character in filename");
    }
  }

  @Test
  @Order(10)
  void testDraftUpdateWithFileUploadDeleteAndCreate() throws IOException {
    System.out.println("Test (10): Upload attachments, delete one and create entity");

    boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {

      entityID5 = response;
      ClassLoader classLoader = getClass().getClassLoader();

      File file = new File(classLoader.getResource("sample.pdf").getFile());
      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID5);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      List<String> createResponse1 =
          api.createAttachment(appUrl, entityName, facetName, entityID5, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")) {
        attachmentID7 = createResponse1.get(1);
      }

      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID5);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse2 =
          api.createAttachment(appUrl, entityName, facetName, entityID5, srvpath, postData2, file);
      if (createResponse2.get(0).equals("Attachment created")) {
        attachmentID8 = createResponse2.get(1);
      }
      response = api.deleteAttachment(appUrl, entityName, facetName, entityID5, attachmentID8);
      if (response.equals("Deleted")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);

        if (response.equals("Saved")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Failed to create entity after deleting one attachment");
    }
  }

  @Test
  @Order(11)
  void testUpdateEntityDraft() throws IOException {
    System.out.println("Test (11): Update entity in draft");
    boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

    File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
    Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID5);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID5);
    if (response.equals("Entity in draft mode")) {
      List<String> createResponse =
          api.createAttachment(
              appUrl, entityName, facetName, entityID5, srvpath, postData, tempFile);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("update entity draft with uploading attachment failed");
    }
    api.deleteEntity(appUrl, entityName, entityID5);
  }

  @Test
  @Order(12)
  void testRenameSingleAttachment() {
    System.out.println("Test (12) : Rename single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String name = "sample123";
    if (response == "Entity in draft mode") {
      response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(13)
  void testRenameAttachmentWithUnsupportedCharacter() {
    System.out.println("Test (13) : Rename single attachment with unsupported characters");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String name = "invalid/name";
    if (response == "Entity in draft mode") {
      response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters "
                + "(/, \\\\). \\n\\n\\t\\u2022 invalid/name\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
        if (response.equals(expected)) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was renamed with unsupported characters");
    }
  }

  @Test
  @Order(14)
  void testRenameMultipleAttachments() {
    System.out.println("Test (14) : Rename multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String name1 = "sample1234";
    String name2 = "sample12345";
    if (response == "Entity in draft mode") {
      String response1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID2, name1);
      String response2 =
          api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3, name2);
      if (response1.equals("Renamed") && response2.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(15)
  void testRenameSingleAttachmentDuplicate() {
    System.out.println("Test (15) : Rename single attachment duplicate");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String name = "sample123";
    String name2 = "sample123456";
    if (response == "Entity in draft mode") {
      response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "{\"error\":{\"code\":\"400\",\"message\":\"The file(s) sample123 have been added "
                + "multiple times. Please rename and try again.\"}}";
        if (response.equals(expected)) {
          response =
              api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3, name2);
          if (response.equals("Renamed")) {
            response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
            if (response.equals("Saved")) {
              testStatus = true;
            }
          }
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was renamed");
    }
  }

  @Test
  @Order(16)
  void testRenameMultipleAttachmentsWithOneUnsupportedCharacter() {
    System.out.println(
        "Test (16) : Rename multiple attachments where one name has unsupported characters");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);

    if (response.equals("Entity in draft mode")) {
      String validName1 = "valid_attachment1.pdf";
      String invalidName2 = "invalid/attachment2.pdf";

      String renameResponse1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1, validName1);
      String renameResponse2 =
          api.renameAttachment(
              appUrl, entityName, facetName, entityID, attachmentID2, invalidName2);

      if (renameResponse1.equals("Renamed") && renameResponse2.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters"
                + " (/, \\\\). \\n\\n\\t\\u2022 invalid/attachment2.pdf\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
        if (response.equals(expected)) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }

    if (!testStatus) {
      fail("Multiple renames should have failed due to one unsupported characters");
    }
  }

  @Test
  @Order(17)
  void testRenameSingleAttachmentWithoutSDMRole() throws IOException {
    System.out.println("Test (17) : Rename attachments where user don't have SDM-Roles");
    boolean testStatus = false;
    String apiResponse = apiNoRoles.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String name = "sample123";
    if (apiResponse == "Entity in draft mode") {
      apiResponse =
          apiNoRoles.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1, name);
      if (apiResponse.equals("Renamed")) {
        apiResponse = apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "[{\"code\":\"<none>\",\"message\":\"Could not update the following files. \\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 valid_attachment1.pdf\\n"
                + //
                "\\n"
                + //
                "You do not have the required permissions to update attachments. Kindly contact the admin\",\"numericSeverity\":3}]";
        if (apiResponse.equals(expected)) {
          testStatus = true;
        }
      } else {
        apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment got renamed without SDM roles.");
    }
  }

  @Test
  @Order(18)
  void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (18) : Delete single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response = api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID1);
      if (response == "Deleted") {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response == "Saved") {
          response = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID1);
          if (response.equals("Could not read Attachment")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not read Attachment");
    }
  }

  @Test
  @Order(19)
  void testDeleteMultipleAttachments() throws IOException {
    System.out.println("Test (19) : Delete multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      String response1 =
          api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID2);
      String response2 =
          api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID3);
      if (response1 == "Deleted" && response2 == "Deleted") {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        if (response == "Saved") {
          response1 = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID2);
          response2 = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID3);
          if (response1.equals("Could not read Attachment")
              && response2.equals("Could not read Attachment")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not delete attachment");
    }
  }

  @Test
  @Order(20)
  void testDeleteEntity() {
    System.out.println("Test (20) : Delete entity");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, entityName, entityID);
    String response2 = api.deleteEntity(appUrl, entityName, entityID2);
    if (response == "Entity Deleted" && response2 == "Entity Deleted") {
      testStatus = true;
    }
    if (!testStatus) {
      fail("Could not delete entity");
    }
  }

  @Test
  @Order(21)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_singleAttachment() throws IOException {
    System.out.println("Test (21): Rename & Update secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;
      System.out.println("Entity created");
      System.out.println("Creating attachment");
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID1 = createResponse.get(1);
        System.out.println("Attachment created");
        String name1 = "sample1234.pdf";
        String secondaryPropertyString = "sample12345";
        Integer secondaryPropertyInt = 1234;
        LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
        System.out.println("Renaming and updating secondary properties for attachment");
        String response1 =
            api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
        if (response1 == "Renamed"
            && updateSecondaryPropertyResponse1 == "Updated"
            && updateSecondaryPropertyResponse2 == "Updated"
            && updateSecondaryPropertyResponse3 == "Updated"
            && updateSecondaryPropertyResponse4 == "Updated") {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
          if (response.equals("Saved")) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println("Renamed & updated Secondary properties for attachment");
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(22)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_singleAttachment() {
    System.out.println("Test (22): Rename & Update secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      String name1 = "sample.pdf";
      String secondaryPropertyString = "sample";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for attachment");
      String response1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue1 = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      if (response1 == "Renamed"
          && updateSecondaryPropertyResponse1 == "Updated"
          && updateSecondaryPropertyResponse2 == "Updated"
          && updateSecondaryPropertyResponse3 == "Updated"
          && updateSecondaryPropertyResponse4 == "Updated") {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties for attachment");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property after entity is saved");
    }
  }

  @Test
  @Order(23)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_singleAttachment()
      throws IOException {
    System.out.println(
        "Test (23): Rename & Update invalid secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      entityID3 = response;
      System.out.println("Entity created");
      System.out.println("Creating attachment");
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData, file);
      String check = createResponse.get(0);
      if ("Attachment created".equals(check)) {
        attachmentID1 = createResponse.get(1);
        System.out.println("Attachment created");
        String name1 = "sample1234.pdf";

        // Dropdown values for secondaryPropertyString
        String[] dropdownValues = {"A", "B", "C"};
        // Select one dropdown value (e.g., "A")
        String secondaryPropertyString = dropdownValues[0];

        Integer secondaryPropertyInt = 1234;
        LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
        String invalidProperty = "testid";

        System.out.println("Renaming and updating invalid secondary properties for attachment");
        String response1 =
            api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);

        // Update secondary properties for String using dropdown selected value as object with code

        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown1 =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown1);

        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);

        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);

        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

        // Update invalid secondary property
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, invalidProperty);

        if ("Renamed".equals(response1)
            && "Updated".equals(updateSecondaryPropertyResponse1)
            && "Updated".equals(updateSecondaryPropertyResponse2)
            && "Updated".equals(updateSecondaryPropertyResponse3)
            && "Updated".equals(updateSecondaryPropertyResponse4)
            && "Updated".equals(updateSecondaryPropertyResponse5)) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
          Map<String, Object> attachmentMetadata =
              api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
          assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
          assertNull(attachmentMetadata.get("customProperty3"));
          assertNull(attachmentMetadata.get("customProperty4"));
          assertNull(attachmentMetadata.get("customProperty1_code"));
          assertNull(attachmentMetadata.get("customProperty2"));
          assertNull(attachmentMetadata.get("customProperty6"));
          assertNull(attachmentMetadata.get("customProperty5"));

          String expectedResponse =
              "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                  + //
                  "\\n"
                  + //
                  "\\t\\u2022 id1\\n"
                  + //
                  "\\n"
                  + //
                  "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3}]";
          if (response.equals(expectedResponse)) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println(
                "Rename & update secondary properties for attachment is unsuccessfull");
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(24)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_singleAttachment() throws IOException {
    System.out.println(
        "Test (24): Rename & Update invalid secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      String name1 = "sample.pdf";
      String secondaryPropertyString = "A";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testidinvalid";
      System.out.println("Renaming and updating invalid secondary properties for attachment");
      String response1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
      String dropdownValue = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      // Update invalid secondary property
      String updateSecondaryPropertyResponse5 =
          api.updateInvalidSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, invalidProperty);
      if (response1 == "Renamed"
          && updateSecondaryPropertyResponse1 == "Updated"
          && updateSecondaryPropertyResponse2 == "Updated"
          && updateSecondaryPropertyResponse3 == "Updated"
          && updateSecondaryPropertyResponse4 == "Updated"
          && updateSecondaryPropertyResponse5 == "Updated") {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        Map<String, Object> attachmentMetadata =
            api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
        assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
        assertNull(attachmentMetadata.get("customProperty3"));
        assertNull(attachmentMetadata.get("customProperty4"));
        assertNull(attachmentMetadata.get("customProperty1_code"));
        assertNull(attachmentMetadata.get("customProperty2"));
        assertNull(attachmentMetadata.get("customProperty6"));
        assertNull(attachmentMetadata.get("customProperty5"));

        String expectedResponse =
            "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3}]";
        if (response.equals(expectedResponse)) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update secondary properties for attachment is unsuccessfull");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(25)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (25): Rename & Update valid secondary properties for multiple attachments before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;

      System.out.println("Entity created");

      System.out.println("Creating attachment PDF");
      ClassLoader classLoader = getClass().getClassLoader();

      File file = new File(classLoader.getResource("sample.pdf").getFile());
      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID3);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      List<String> createResponse1 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")) {
        attachmentID1 = createResponse1.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID3);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse2 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData2, file);
      if (createResponse2.get(0).equals("Attachment created")) {
        attachmentID2 = createResponse2.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      Map<String, Object> postData3 = new HashMap<>();
      postData3.put("up__ID", entityID3);
      postData3.put("mimeType", "application/exe");
      postData3.put("createdAt", new Date().toString());
      postData3.put("createdBy", "test@test.com");
      postData3.put("modifiedBy", "test@test.com");

      List<String> createResponse3 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData3, file);
      if (createResponse3.get(0).equals("Attachment created")) {
        attachmentID3 = createResponse3.get(1);
        System.out.println("Attachment created");
      }

      String check1 = createResponse1.get(0);
      String check2 = createResponse2.get(0);
      String check3 = createResponse3.get(0);
      if (check1.equals("Attachment created")
          && check2.equals("Attachment created")
          && check3.equals("Attachment created")) {
        Boolean attachment1Updated = false;
        Boolean attachment2Updated = false;
        Boolean attachment3Updated = false;

        String name1 = "sample1234.pdf";
        Integer secondaryPropertyInt1 = 1234;
        LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
        System.out.println("Renaming and updating secondary properties for attachment PDF");
        String responsePDF1 =
            api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponsePDF1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponsePDF2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponsePDF3 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponsePDF4 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
        if (responsePDF1 == "Renamed"
            && updateSecondaryPropertyResponsePDF1 == "Updated"
            && updateSecondaryPropertyResponsePDF2 == "Updated"
            && updateSecondaryPropertyResponsePDF3 == "Updated"
            && updateSecondaryPropertyResponsePDF4 == "Updated") {
          System.out.println("Renamed & updated Secondary properties for attachment PDF");
          attachment1Updated = true;
        }

        System.out.println("Updating secondary properties for attachment TXT");
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
        if (updateSecondaryPropertyResponseTXT1 == "Updated") {
          System.out.println("Updated Secondary properties for attachment TXT");
          attachment2Updated = true;
        }
        Integer secondaryPropertyInt3 = 1234;
        LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
        System.out.println("Updating secondary properties for attachment EXE");
        // Update secondary properties for String
        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown1 =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
        // Update secondary properties for Integer
        RequestBody bodyInt3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID3, bodyDateTime3);

        if (updateSecondaryPropertyResponseEXE1 == "Updated"
            && updateSecondaryPropertyResponseEXE2 == "Updated"
            && updateSecondaryPropertyResponseEXE3 == "Updated") {
          System.out.println("Updated Secondary properties for attachment EXE");
          attachment3Updated = true;
        }

        if (attachment1Updated && attachment2Updated && attachment3Updated) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
          if (response.equals("Saved")) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println("Renamed & updated Secondary properties for attachments");
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(26)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_multipleAttachments() {
    System.out.println(
        "Test (26): Rename & Update  valid secondary properties for multiple attachments after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      Boolean attachment1Updated = false;
      Boolean attachment2Updated = false;
      Boolean attachment3Updated = false;

      String name1 = "sample1.pdf";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for attachment PDF");
      String responsePDF1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue1 = integrationTestUtils.getDropDownValue();
      String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
      RequestBody bodyDropdown1 =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown1);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

      if (responsePDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF1 == "Updated"
          && updateSecondaryPropertyResponsePDF2 == "Updated"
          && updateSecondaryPropertyResponsePDF3 == "Updated"
          && updateSecondaryPropertyResponsePDF4 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for attachment PDF");
        attachment1Updated = true;
      }

      System.out.println("Updating secondary properties for attachment TXT");
      // Update secondary properties for Boolean
      RequestBody bodyBool =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponseTXT1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
      if (updateSecondaryPropertyResponseTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for attachment TXT");
        attachment2Updated = true;
      }

      Integer secondaryPropertyInt3 = 123;
      LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
      System.out.println("Updating secondary properties for attachment EXE");
      // Update secondary properties for String
      String dropdownValue2 = integrationTestUtils.getDropDownValue();
      String jsonDropdown2 = "{ \"customProperty1_code\" : \"" + dropdownValue2 + "\" }";
      RequestBody bodyDropdown2 =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown2);
      String updateSecondaryPropertyResponseEXE1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown2);
      // Update secondary properties for Integer
      RequestBody bodyInt3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
      String updateSecondaryPropertyResponseEXE3 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID3, bodyDateTime3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated"
          && updateSecondaryPropertyResponseEXE3 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      if (attachment1Updated && attachment2Updated && attachment3Updated) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties for attachments");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property after entity is saved");
    }
  }

  @Test
  @Order(27)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (27): Rename & Update invalid and valid secondary properties for multiple attachments before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;

      System.out.println("Entity created");

      System.out.println("Creating attachment PDF");
      ClassLoader classLoader = getClass().getClassLoader();

      File file = new File(classLoader.getResource("sample.pdf").getFile());
      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID3);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      List<String> createResponse1 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")) {
        attachmentID1 = createResponse1.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID3);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse2 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData2, file);
      if (createResponse2.get(0).equals("Attachment created")) {
        attachmentID2 = createResponse2.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      Map<String, Object> postData3 = new HashMap<>();
      postData3.put("up__ID", entityID3);
      postData3.put("mimeType", "application/exe");
      postData3.put("createdAt", new Date().toString());
      postData3.put("createdBy", "test@test.com");
      postData3.put("modifiedBy", "test@test.com");

      List<String> createResponse3 =
          api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData3, file);
      if (createResponse3.get(0).equals("Attachment created")) {
        attachmentID3 = createResponse3.get(1);
        System.out.println("Attachment created");
      }

      String check1 = createResponse1.get(0);
      String check2 = createResponse2.get(0);
      String check3 = createResponse3.get(0);
      if (check1.equals("Attachment created")
          && check2.equals("Attachment created")
          && check3.equals("Attachment created")) {
        Boolean attachment1Updated = false;
        Boolean attachment2Updated = false;
        Boolean attachment3Updated = false;

        String name1 = "sample1234.pdf";
        Integer secondaryPropertyInt1 = 1234;
        LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
        String invalidPropertyPDF = "testidinvalidPDF";
        System.out.println("Renaming and updating invalid secondary properties for attachment PDF");
        String responsePDF1 =
            api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponsePDF1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyint =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponsePDF2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyint);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponsePDF3 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponsePDF4 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
        // Update invalid secondary property
        String updateSecondaryPropertyResponsePDF5 =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID1, invalidPropertyPDF);
        if (responsePDF1 == "Renamed"
            && updateSecondaryPropertyResponsePDF1 == "Updated"
            && updateSecondaryPropertyResponsePDF2 == "Updated"
            && updateSecondaryPropertyResponsePDF3 == "Updated"
            && updateSecondaryPropertyResponsePDF4 == "Updated"
            && updateSecondaryPropertyResponsePDF5 == "Updated") {
          attachment1Updated = true;
        }

        System.out.println("Updating valid secondary properties for attachment TXT");
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
        if (updateSecondaryPropertyResponseTXT1 == "Updated") {
          System.out.println("Updated Secondary properties for attachment TXT");
          attachment2Updated = true;
        }

        Integer secondaryPropertyInt3 = 1234;
        System.out.println("Updating valid secondary properties for attachment EXE");

        // Update secondary properties for String
        RequestBody bodyDropdown1 =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
        // Update secondary properties for Integer
        RequestBody bodyInt3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);

        if (updateSecondaryPropertyResponseEXE1 == "Updated"
            && updateSecondaryPropertyResponseEXE2 == "Updated") {
          System.out.println("Updated Secondary properties for attachment EXE");
          attachment3Updated = true;
        }

        if (attachment1Updated && attachment2Updated && attachment3Updated) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
          Map<String, Object> attachmentMetadataPDF =
              api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
          assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
          assertNull(attachmentMetadataPDF.get("customProperty3"));
          assertNull(attachmentMetadataPDF.get("customProperty4"));
          assertNull(attachmentMetadataPDF.get("customProperty1_code"));
          assertNull(attachmentMetadataPDF.get("customProperty2"));
          assertNull(attachmentMetadataPDF.get("customProperty6"));
          assertNull(attachmentMetadataPDF.get("customProperty5"));

          Map<String, Object> attachmentMetadataTXT =
              api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID2);
          assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
          assertNull(attachmentMetadataTXT.get("customProperty3"));
          assertNull(attachmentMetadataTXT.get("customProperty4"));
          assertNull(attachmentMetadataTXT.get("customProperty1_code"));
          assertNull(attachmentMetadataTXT.get("customProperty2"));
          assertTrue((Boolean) attachmentMetadataTXT.get("customProperty6"));
          assertNull(attachmentMetadataTXT.get("customProperty5"));

          Map<String, Object> attachmentMetadataEXE =
              api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID3);
          assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
          assertNull(attachmentMetadataEXE.get("customProperty3"));
          assertNull(attachmentMetadataEXE.get("customProperty4"));
          assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
          assertEquals(1234, attachmentMetadataEXE.get("customProperty2"));

          String expectedResponse =
              "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                  + //
                  "\\n"
                  + //
                  "\\t\\u2022 id1\\n"
                  + //
                  "\\n"
                  + //
                  "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3}]";
          if (response.equals(expectedResponse)) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println(
                "Rename & update unsuccessfull for invalid Secondary properties and successfull for valid property attachments");
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(28)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (28): Rename & Update invalid and valid secondary properties for multiple attachments after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      Boolean attachment1Updated = false;
      Boolean attachment2Updated = false;
      Boolean attachment3Updated = false;

      String name1 = "sample.pdf";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";
      System.out.println("Renaming and updating invalid secondary properties for attachment PDF");
      String responsePDF1 =
          api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      // Update invalid secondary property
      String updateSecondaryPropertyResponsePDF5 =
          api.updateInvalidSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID1, invalidPropertyPDF);
      if (responsePDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF1 == "Updated"
          && updateSecondaryPropertyResponsePDF2 == "Updated"
          && updateSecondaryPropertyResponsePDF3 == "Updated"
          && updateSecondaryPropertyResponsePDF4 == "Updated"
          && updateSecondaryPropertyResponsePDF5 == "Updated") {
        attachment1Updated = true;
      }

      System.out.println("Updating valid secondary properties for attachment TXT");
      // Update secondary properties for Boolean
      RequestBody bodyBool =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + false + "\n}"));
      String updateSecondaryPropertyResponseTXT1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
      if (updateSecondaryPropertyResponseTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for attachment TXT");
        attachment2Updated = true;
      }

      Integer secondaryPropertyInt3 = 12;
      System.out.println("Updating valid secondary properties for attachment EXE");

      // Update secondary properties for String
      RequestBody bodyDropdown1 =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponseEXE1 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
      // Update secondary properties for Integer
      RequestBody bodyInt3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryProperty(
              appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      if (attachment1Updated && attachment2Updated && attachment3Updated) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        Map<String, Object> attachmentMetadataPDF =
            api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
        assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
        assertNull(attachmentMetadataPDF.get("customProperty3"));
        assertNull(attachmentMetadataPDF.get("customProperty4"));
        assertNull(attachmentMetadataPDF.get("customProperty1_code"));
        assertNull(attachmentMetadataPDF.get("customProperty2"));
        assertNull(attachmentMetadataPDF.get("customProperty6"));
        assertNull(attachmentMetadataPDF.get("customProperty5"));

        Map<String, Object> attachmentMetadataTXT =
            api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID2);
        assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
        assertNull(attachmentMetadataTXT.get("customProperty3"));
        assertNull(attachmentMetadataTXT.get("customProperty4"));
        assertNull(attachmentMetadataTXT.get("customProperty1_code"));
        assertNull(attachmentMetadataTXT.get("customProperty2"));
        assertFalse((Boolean) attachmentMetadataTXT.get("customProperty6"));
        assertNull(attachmentMetadataTXT.get("customProperty5"));

        Map<String, Object> attachmentMetadataEXE =
            api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID3);
        assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
        assertNull(attachmentMetadataEXE.get("customProperty3"));
        assertNull(attachmentMetadataEXE.get("customProperty4"));
        assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
        assertEquals(12, attachmentMetadataEXE.get("customProperty2"));

        String expectedResponse =
            "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3}]";
        if (response.equals(expectedResponse)) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update unsuccessfull for invalid Secondary properties and successfull for valid property attachments");
        }
        String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
        if (deleteEntityResponse != "Entity Deleted") {
          fail("Could not delete entity");
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(29)
  void testNAttachments_NewEntity() throws IOException {
    System.out.println(
        "Test (29): Creating new entity and checking only max 4 attachments are allowed to be uploaded");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID4 = response;

      System.out.println("Entity created");

      System.out.println("Creating attachment PDF");
      ClassLoader classLoader = getClass().getClassLoader();

      File file = new File(classLoader.getResource("sample.pdf").getFile());
      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID4);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      List<String> createResponse1 =
          api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")) {
        attachmentID1 = createResponse1.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID4);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse2 =
          api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData2, file);
      if (createResponse2.get(0).equals("Attachment created")) {
        attachmentID2 = createResponse2.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating attachment EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      Map<String, Object> postData3 = new HashMap<>();
      postData3.put("up__ID", entityID4);
      postData3.put("mimeType", "application/exe");
      postData3.put("createdAt", new Date().toString());
      postData3.put("createdBy", "test@test.com");
      postData3.put("modifiedBy", "test@test.com");

      List<String> createResponse3 =
          api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3, file);
      if (createResponse3.get(0).equals("Attachment created")) {
        attachmentID3 = createResponse3.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating second attachment pdf");
      file = new File(classLoader.getResource("sample1.pdf").getFile());
      Map<String, Object> postData4 = new HashMap<>();
      postData4.put("up__ID", entityID4);
      postData4.put("mimeType", "application/pdf");
      postData4.put("createdAt", new Date().toString());
      postData4.put("createdBy", "test@test.com");
      postData4.put("modifiedBy", "test@test.com");

      List<String> createResponse4 =
          api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3, file);
      if (createResponse4.get(0).equals("Attachment created")) {
        attachmentID4 = createResponse4.get(1);
        System.out.println("Attachment created");
      }

      System.out.println("Creating third attachment pdf");
      file = new File(classLoader.getResource("sample2.pdf").getFile());
      Map<String, Object> postData5 = new HashMap<>();
      postData5.put("up__ID", entityID4);
      postData5.put("mimeType", "application/pdf");
      postData5.put("createdAt", new Date().toString());
      postData5.put("createdBy", "test@test.com");
      postData5.put("modifiedBy", "test@test.com");

      List<String> createResponse5 =
          api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3, file);
      if (createResponse5.get(0).equals("Only 4 attachments allowed.")) {
        testStatus = true;
        attachmentID5 = createResponse5.get(1);
        System.out.println("Expected error received: Only 4 attachments allowed.");
      }
      String check = createResponse5.get(0);
      if (check.equals("Attachment created")) {
        testStatus = false;
      } else {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID4);
        if (response.equals("Saved")) {
          String expectedJson =
              "{\"error\":{\"code\":\"500\",\"message\":\"Only 4 attachments allowed.\"}}";
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode actualJsonNode = objectMapper.readTree(check);
          JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
          if (expectedJsonNode.equals(actualJsonNode)) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Attachment was created");
    }
  }

  @Test
  @Order(30)
  void testUploadNAttachments() throws IOException {
    System.out.println("Test (30): Upload maximum 4 attachments in an exsisting entity");

    ClassLoader classLoader = getClass().getClassLoader();
    File originalFile = new File(classLoader.getResource("sample.exe").getFile());

    boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID4);
    System.out.println("response: " + response);

    if ("Entity in draft mode".equals(response)) {
      for (int i = 1; i <= 5; i++) {
        // Ensure only one file is uploaded at a time and complete before next
        File tempFile = File.createTempFile("sample_" + i + "_", ".exe");
        Files.copy(originalFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Map<String, Object> postData = new HashMap<>();
        postData.put("up__ID", entityID4);
        postData.put("mimeType", "application/exe");
        postData.put("createdAt", new Date().toString());
        postData.put("createdBy", "test@test.com");
        postData.put("modifiedBy", "test@test.com");

        List<String> createResponse =
            api.createAttachment(
                appUrl, entityName, facetName, entityID4, srvpath, postData, tempFile);

        String resultMessage = createResponse.get(0);
        System.out.println("Result message for attachment " + i + ": " + resultMessage);

        String expectedResponse =
            "{\"error\":{\"code\":\"500\",\"message\":\"Only 4 attachments allowed.\"}}";
        if (resultMessage.equals(expectedResponse)) {
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode actualJsonNode = objectMapper.readTree(resultMessage);
          JsonNode expectedJsonNode = objectMapper.readTree(expectedResponse);
          if (expectedJsonNode.equals(actualJsonNode)) {
            testStatus = true;
          }
        } else {
          testStatus = false;
        }
        tempFile.delete();
      }
      if (!testStatus) {
        fail("5th attachment did not trigger the expected error.");
      }
      // Delete the newly created entity
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID4);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      } else {
        System.out.println("Successfully deleted the test entity4");
      }
    }
  }

  @Test
  @Order(31)
  void testDiscardDraftWithoutAttachments() {
    System.out.println("Test (31) : Discard draft without adding attachments");

    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (response.equals("Could not create entity")) {
      fail("Could not create entity");
    }

    response = api.deleteEntityDraft(appUrl, entityName, response);
    if (!response.equals("Entity Draft Deleted")) {
      fail("Draft was not discarded properly");
    }
  }

  @Test
  @Order(32)
  void testDiscardDraftWithAttachments() throws IOException {
    System.out.println("Test (32) : Discard draft with attachments");
    boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID7 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID7);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facetName, entityID7, srvpath, postData1, file);
      if (createResponse.get(0).equals("Attachment created")) {
        attachmentID1 = createResponse.get(1);
      }
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        response = api.deleteEntityDraft(appUrl, entityName, entityID7);
      }
      if (response.equals("Entity Draft Deleted")) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Draft was not discarded properly");
    }
  }

  @Test
  @Order(33)
  void testCopyAttachmentsSuccessNewEntity() throws IOException {
    System.out.println("Test (33): Copy attachments from one entity to another new entity");
    List<String> attachments = new ArrayList<>();
    copyAttachmentSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    copyAttachmentTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!copyAttachmentSourceEntity.equals("Could not create entity")
        && !copyAttachmentTargetEntity.equals("Could not create entity")) {
      ClassLoader classLoader = getClass().getClassLoader();
      List<File> files = new ArrayList<>();
      files.add(new File(classLoader.getResource("sample.pdf").getFile()));
      files.add(new File(classLoader.getResource("sample1.pdf").getFile()));
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID7);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      for (File file : files) {
        List<String> createResponse =
            api.createAttachment(
                appUrl, entityName, facetName, copyAttachmentSourceEntity, srvpath, postData, file);
        if (createResponse.get(0).equals("Attachment created")) {
          attachments.add(createResponse.get(1));
        } else {
          fail("Could not create attachment");
        }
      }
      api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
      List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
      Map<String, Object> fetchAttachmentMetadataResponse;
      for (String attachment : attachments) {
        try {
          fetchAttachmentMetadataResponse =
              api.fetchMetadata(
                  appUrl, entityName, facetName, copyAttachmentSourceEntity, attachment);
          attachmentsMetadata.add(fetchAttachmentMetadataResponse);
        } catch (IOException e) {
          fail("Could not fetch attachment metadata: " + e.getMessage());
        }
      }
      for (Map<String, Object> metadata : attachmentsMetadata) {
        if (metadata.containsKey("objectId")) {
          sourceObjectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }

      if (sourceObjectIds.size() == 2) {
        String copyResponse;
        copyResponse =
            api.copyAttachment(
                appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
        if (copyResponse.equals("Attachments copied successfully")) {
          String saveEntityResponse =
              api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
          if (saveEntityResponse.equals("Saved")) {
            List<Map<String, Object>> fetchEntityMetadataResponse;
            fetchEntityMetadataResponse =
                api.fetchEntityMetadata(appUrl, entityName, facetName, copyAttachmentTargetEntity);
            targetAttachmentIds =
                fetchEntityMetadataResponse.stream()
                    .map(item -> (String) item.get("ID"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            String readResponse;
            for (String targetAttachmentId : targetAttachmentIds) {
              readResponse =
                  api.readAttachment(
                      appUrl,
                      entityName,
                      facetName,
                      copyAttachmentTargetEntity,
                      targetAttachmentId);
              if (!readResponse.equals("OK")) {
                fail("Could not read copied attachment");
              }
            }
          } else {
            fail("Could not save entity after copying attachments: " + saveEntityResponse);
          }
        } else {
          fail("Could not copy attachments: " + copyResponse);
        }
      } else {
        fail("Could not fetch objects Ids for all attachments");
      }
    } else {
      fail("Could not create entities");
    }
  }

  @Test
  @Order(34)
  void testCopyAttachmentsUnsuccessfulNewEntity() throws IOException {
    System.out.println("Test (34): Copy attachments from one entity to another new entity");
    String editResponse1 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
    copyAttachmentTargetEntityEmpty =
        api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (editResponse1.equals("Entity in draft mode")
        && !copyAttachmentTargetEntityEmpty.equals("Could not create entity")) {
      sourceObjectIds.add("incorrectObjectId");
      if (sourceObjectIds.size() == 3) {
        try {
          api.copyAttachment(
              appUrl, entityName, facetName, copyAttachmentTargetEntityEmpty, sourceObjectIds);
          fail("Copy attachments did not throw an error");
        } catch (IOException e) {
          String saveEntityResponse1 =
              api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
          String saveEntityResponse2 =
              api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntityEmpty);
          String deleteResponse =
              api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntityEmpty);
          if (!saveEntityResponse1.equals("Saved")
              || !saveEntityResponse2.equals("Saved")
              || !deleteResponse.equals("Entity Deleted")) {
            fail("Could not save entities");
          }
        }
      } else {
        fail("Could not fetch objects Ids for all attachments");
      }
    } else {
      fail("Could not edit entities");
    }
  }

  @Test
  @Order(35)
  void testCopyAttachmentsSuccessExistingEntity() throws IOException {
    System.out.println("Test (35): Copy attachments from one entity to another existing entity");
    List<String> attachments = new ArrayList<>();
    ClassLoader classLoader = getClass().getClassLoader();
    List<File> files = new ArrayList<>();
    File file1 = new File(classLoader.getResource("sample.pdf").getFile());
    File file2 = new File(classLoader.getResource("sample1.pdf").getFile());
    File tempFile1 = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
    Files.copy(file1.toPath(), tempFile1.toPath(), StandardCopyOption.REPLACE_EXISTING);
    File tempFile2 = new File(System.getProperty("java.io.tmpdir"), "sample4.pdf");
    Files.copy(file2.toPath(), tempFile2.toPath(), StandardCopyOption.REPLACE_EXISTING);
    files.add(tempFile1);
    files.add(tempFile2);
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID7);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");
    String editResponse1 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
    String editResponse2 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
    if (editResponse1.equals("Entity in draft mode")
        && editResponse2.equals("Entity in draft mode")) {
      for (File file : files) {
        List<String> createResponse =
            api.createAttachment(
                appUrl, entityName, facetName, copyAttachmentSourceEntity, srvpath, postData, file);
        if (createResponse.get(0).equals("Attachment created")) {
          attachments.add(createResponse.get(1));
        } else {
          fail("Could not create attachment");
        }
      }
      api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
      List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
      Map<String, Object> fetchAttachmentMetadataResponse;
      for (String attachment : attachments) {
        try {
          fetchAttachmentMetadataResponse =
              api.fetchMetadata(
                  appUrl, entityName, facetName, copyAttachmentSourceEntity, attachment);
          attachmentsMetadata.add(fetchAttachmentMetadataResponse);
        } catch (IOException e) {
          fail("Could not fetch attachment metadata: " + e.getMessage());
        }
      }

      sourceObjectIds.clear();
      for (Map<String, Object> metadata : attachmentsMetadata) {
        if (metadata.containsKey("objectId")) {
          sourceObjectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }

      if (sourceObjectIds.size() == 2) {
        String copyResponse;
        copyResponse =
            api.copyAttachment(
                appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
        if (copyResponse.equals("Attachments copied successfully")) {
          String saveEntityResponse =
              api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
          if (saveEntityResponse.equals("Saved")) {
            List<Map<String, Object>> fetchEntityMetadataResponse;
            fetchEntityMetadataResponse =
                api.fetchEntityMetadata(appUrl, entityName, facetName, copyAttachmentTargetEntity);
            targetAttachmentIds =
                fetchEntityMetadataResponse.stream()
                    .map(item -> (String) item.get("ID"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            String readResponse;
            if (targetAttachmentIds.size() == 4) {
              for (String targetAttachmentId : targetAttachmentIds) {
                readResponse =
                    api.readAttachment(
                        appUrl,
                        entityName,
                        facetName,
                        copyAttachmentTargetEntity,
                        targetAttachmentId);
                if (!readResponse.equals("OK")) {
                  fail("Could not read copied attachment");
                }
              }
            }
            // api.deleteEntity(appUrl, entityName, copyAttachmentSourceEntity);
            // api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
          } else {
            fail("Could not save entity after copying attachments: " + saveEntityResponse);
          }
        } else {
          fail("Could not copy attachments: " + copyResponse);
        }
      } else {
        fail("Could not fetch objects Ids for all attachments");
      }
    } else {
      fail("Could not edit entities");
    }
  }

  @Test
  @Order(36)
  void testCopyAttachmentsUnsuccessfulExistingEntity() throws IOException {
    System.out.println("Test (36): Copy attachments from one entity to another new entity");
    String editResponse1 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
    String editResponse2 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
    if (editResponse1.equals("Entity in draft mode")
        && editResponse2.equals("Entity in draft mode")) {
      sourceObjectIds.add("incorrectObjectId");
      if (sourceObjectIds.size() == 3) {
        try {
          api.copyAttachment(
              appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
          fail("Copy attachments did not throw an error");
        } catch (IOException e) {
          api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
          api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
          api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
          api.deleteEntity(appUrl, entityName, copyAttachmentSourceEntity);
        }
      } else {
        fail("Could not fetch objects Ids for all attachments");
      }
    } else {
      fail("Could not edit entities");
    }
  }

  @Test
  @Order(37)
  void testCreateLinkSuccess() throws IOException {
    System.out.println("Test (37): Create link in entity");
    List<String> attachments = new ArrayList<>();
    createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!createLinkEntity.equals("Could not create entity")) {
      String linkName = "sample";
      String linkUrl = "https://www.example.com";
      String createLinkResponse1 =
          api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
      String createLinkResponse2 =
          api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName + "1", linkUrl);
      if (createLinkResponse1.equals("Link created successfully")
          && createLinkResponse2.equals("Link created successfully")) {
        String saveEntityResponse =
            api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
        if (saveEntityResponse.equals("Saved")) {
          attachments =
              api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
                  .map(item -> (String) item.get("ID"))
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
          String openAttachmentResponse;
          for (String attachment : attachments) {
            openAttachmentResponse =
                api.openAttachment(appUrl, entityName, facetName, createLinkEntity, attachment);
            if (!openAttachmentResponse.equals("Attachment opened successfully")) {
              fail("Could not open created link");
            }
          }
        } else {
          fail("Could not save entity");
        }
      } else {
        fail("Could not create link");
      }
    } else {
      fail("Could not create entity");
    }
  }

  @Test
  @Order(38)
  void testCreateLinkDifferentEntity() throws IOException {
    System.out.println("Test (38): Create link with same name in different entity");
    String createLinkDifferentEntity =
        api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!createLinkDifferentEntity.equals("Could not edit entity")) {
      String linkName = "sample";
      String linkUrl = "https://example.com";
      String createResponse =
          api.createLink(
              appUrl, entityName, facetName, createLinkDifferentEntity, linkName, linkUrl);
      if (!createResponse.equals("Link created successfully")) {
        fail("Could not create link in different entity with same name");
      }
      String response = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkDifferentEntity);
      if (!response.equals("Saved")) {
        fail("Could not save entity");
      }
      response = api.deleteEntity(appUrl, entityName, createLinkDifferentEntity);
      if (!response.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    } else {
      fail("Could not edit entity");
    }
  }

  @Test
  @Order(39)
  void testCreateLinkFailure() throws IOException {
    System.out.println("Test (39): Create link fails due to invalid URL and name");
    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Could not edit entity")) {
      String linkName = "sample";
      String linkUrl = "example.com";
      try {
        api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
        fail("Create link did not throw an error for invalid url");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        assertEquals("400018", errorCode);
        assertEquals("Enter a value that is within the expected pattern.", errorMessage);
      }
      try {
        api.createLink(
            appUrl, entityName, facetName, createLinkEntity, linkName + "//", "https://" + linkUrl);
        fail("Create link did not throw an error for invalid name");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        String expected =
            "Link could not be created. The following name(s) contain unsupported characters (/, \\).\n\n"
                + " • sample//\n\n"
                + "Rename the link and try again.";
        assertEquals("500", errorCode);
        assertEquals(
            expected.replaceAll("\\s+", " ").trim(), errorMessage.replaceAll("\\s+", " ").trim());
      }
      try {
        api.createLink(appUrl, entityName, facetName, createLinkEntity, "", "");
        fail("Create link did not throw an error for empty name and url");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        String expected = "Provide the missing value.";
        assertEquals("409008", errorCode);
        assertEquals(expected, errorMessage);
      }
      try {
        api.createLink(
            appUrl, entityName, facetName, createLinkEntity, linkName, "https://" + linkUrl);
        fail("Create link did not throw an error for duplicate name");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        assertEquals("500", errorCode);
        assertEquals("sample already exists.", errorMessage);
      }
      try {
        for (int i = 2; i < 5; i++) {
          api.createLink(
              appUrl, entityName, facetName, createLinkEntity, linkName + i, "https://" + linkUrl);
        }
        fail("More than 5 links were created in the same entity");
      } catch (IOException e) {
        String message = e.getMessage();
        int jsonStart = message.indexOf("{");
        String jsonPart = message.substring(jsonStart);
        JSONObject json = new JSONObject(jsonPart);
        String errorCode = json.getJSONObject("error").getString("code");
        String errorMessage = json.getJSONObject("error").getString("message");
        assertEquals("500", errorCode);
        assertEquals("Only 4 attachments allowed.", errorMessage);
      }
      String response = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
      if (!response.equals("Saved")) {
        fail("Could not save entity");
      }
      response = api.deleteEntity(appUrl, entityName, createLinkEntity);
      if (!response.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    } else {
      fail("Could not edit entity");
    }
  }

  @Test
  @Order(40)
  void testCreateLinkNoSDMRoles() throws IOException {
    System.out.println("Test (40): Create link fails due to no SDM roles assigned");
    String createLinkEntityNoSDMRoles =
        apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!createLinkEntityNoSDMRoles.equals("Could not edit entity")) {
      String linkName = "sample27";
      String linkUrl = "https://example.com";
      try {
        apiNoRoles.createLink(
            appUrl, entityName, facetName, createLinkEntityNoSDMRoles, linkName, linkUrl);
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
      String response =
          apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntityNoSDMRoles);
      if (!response.equals("Saved")) {
        fail("Could not save entity");
      }
      response = api.deleteEntity(appUrl, entityName, createLinkEntityNoSDMRoles);
      if (!response.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    } else {
      fail("Could not edit entity");
    }
  }

  @Test
  @Order(41)
  void testDeleteLink() throws IOException {
    System.out.println("Test (41): Delete link in entity");
    List<String> attachments = new ArrayList<>();
    String createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!createLinkEntity.equals("Could not create entity")) {
      String linkName = "sample";
      String linkUrl = "https://www.example.com";
      String createLinkResponse =
          api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
      if (createLinkResponse.equals("Link created successfully")) {
        String saveEntityResponse =
            api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
        if (saveEntityResponse.equals("Saved")) {
          attachments =
              api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
                  .map(item -> (String) item.get("ID"))
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
          String editEntityResponse =
              api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
          if (!editEntityResponse.equals("Entity in draft mode")) {
            fail("Could not edit entity");
          }
          String deleteLinkResponse =
              api.deleteAttachment(
                  appUrl, entityName, facetName, createLinkEntity, attachments.get(0));
          if (!deleteLinkResponse.equals("Deleted")) {
            fail("Could not delete created link");
          } else {
            saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
            if (!saveEntityResponse.equals("Saved")) {
              fail("Could not save entity");
            }
            attachments =
                api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
                    .map(item -> (String) item.get("ID"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (attachments.size() != 0) {
              fail("Link wasn't deleted");
            }
            String response = api.deleteEntity(appUrl, entityName, createLinkEntity);
            if (!response.equals("Entity Deleted")) {
              fail("Could not delete entity");
            }
          }
        } else {
          fail("Could not save entity");
        }
      } else {
        fail("Could not create link");
      }
    } else {
      fail("Could not create entity");
    }
  }

  @Test
  @Order(42)
  void testRenameLinkSuccess() throws IOException {
    System.out.println("Test (42): Rename link in entity");
    List<String> attachments = new ArrayList<>();

    createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (createLinkEntity.equals("Could not create entity")) {
      fail("Could not create entity");
    }

    String linkName = "sample";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link");
    }

    String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }

    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }

    attachmentID9 = attachments.get(0);
    String renameLinkResponse =
        api.renameAttachment(
            appUrl, entityName, facetName, createLinkEntity, attachments.get(0), "sampleRenamed");
    if (!renameLinkResponse.equals("Renamed")) fail("Could not Renamed created link");

    saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }
  }

  @Test
  @Order(43)
  void testRenameLinkDuplicate() throws IOException {
    System.out.println("Test (43): Rename link in entity fails due to duplicate error");
    List<String> attachments = new ArrayList<>();

    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }

    String linkName = "sample";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link");
    }

    String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }

    editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }

    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
            .filter(item -> !attachmentID9.equals(item.get("ID"))) // skip unwanted filename
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    attachmentID10 = attachments.get(0);
    api.renameAttachment(
        appUrl, entityName, facetName, createLinkEntity, attachments.get(0), "sampleRenamed");

    String saveError =
        saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    String expectedWarning =
        "{\"error\":{\"code\":\"400\",\"message\":\"The file(s) sampleRenamed have been added multiple times. Please rename and try again.\"}}";
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(mapper.readTree(expectedWarning), mapper.readTree(saveError));

    String deleteEntityResponse = api.deleteEntityDraft(appUrl, entityName, createLinkEntity);
    if (!deleteEntityResponse.equals("Entity Draft Deleted")) {
      fail("Entity draft not deleted");
    }
  }

  @Test
  @Order(44)
  void testRenameLinkUnsupportedCharacters() throws IOException {
    System.out.println(
        "Test (44): Rename link in entity fails due to unsupported characters in name");
    List<String> attachments = new ArrayList<>();

    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }

    String linkName = "sample2";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link");
    }

    String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }

    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
            // .filter(item -> "sample2".equals(item.get("filename"))) // skip unwanted filename
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    System.out.println("attachments: " + attachments);

    editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }

    api.renameAttachment(
        appUrl, entityName, facetName, createLinkEntity, attachments.get(0), "sampleRenamed//");
    String warning =
        saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
    String expectedWarning =
        "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters (/, \\\\). \\n\\n\\t• sampleRenamed//\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
    ObjectMapper mapper = new ObjectMapper();
    assertEquals(mapper.readTree(expectedWarning), mapper.readTree(warning));

    String deleteEntityResponse = api.deleteEntity(appUrl, entityName, createLinkEntity);
    if (!deleteEntityResponse.equals("Entity Deleted")) {
      fail("Entity draft not deleted");
    }
  }

  @Test
  @Order(45)
  void testEditLinkSuccess() throws IOException {
    System.out.println("Test (45): Edit existing link in entity");

    List<String> attachments = new ArrayList<>();
    editLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (editLinkEntity.equals("Could not create entity")) {
      fail("Could not create entity");
    }
    String linkName = "sample";
    String linkUrl = "https://www.example.com";

    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, editLinkEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link");
    }

    String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }
    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }
    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (attachments.isEmpty()) {
      fail("Could not edit link");
    }
    String linkId = attachments.get(0);
    String updatedUrl = "https://editedexample.com";
    String editLinkResponse =
        api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
    if (!editLinkResponse.equals("Link edited successfully")) {
      fail("Could not edit link");
    }
    saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!saveEntityResponse.equals("Saved")) {
      fail("Could not save entity");
    }
  }

  @Test
  @Order(46)
  void testEditLinkFailureInvalidURL() throws IOException {
    System.out.println("Test (46): Edit existing link with invalid url");
    Boolean testStatus = false;
    List<String> attachments = new ArrayList<>();

    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }
    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (attachments.isEmpty()) {
      fail("Could not edit link");
    }
    String linkId = attachments.get(0);
    String updatedUrl = "https://editedexample";
    try {

      api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
      fail("Create link did not throw an error for invalid url");
    } catch (IOException e) {
      String message = e.getMessage();
      int jsonStart = message.indexOf("{");
      String jsonPart = message.substring(jsonStart);
      JSONObject json = new JSONObject(jsonPart);
      String errorCode = json.getJSONObject("error").getString("code");
      String errorMessage = json.getJSONObject("error").getString("message");
      assertEquals("400018", errorCode);
      assertTrue(
          errorMessage.equals("Enter a value that is within the expected pattern.")
              || errorMessage.equals("Enter a value that matches the expected pattern."),
          "Unexpected error message: " + errorMessage);

      testStatus = true;
    }
    api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!testStatus) {
      fail("Could not edit link with an invalid URL");
    }
  }

  @Test
  @Order(47)
  void testEditLinkFailureEmptyURL() throws IOException {
    System.out.println("Test (47): Edit existing link with an empty url");
    Boolean testStatus = false;
    List<String> attachments = new ArrayList<>();

    String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
    if (!editEntityResponse.equals("Entity in draft mode")) {
      fail("Could not edit entity");
    }
    attachments =
        api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
            .map(item -> (String) item.get("ID"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (attachments.isEmpty()) {
      fail("Could not edit link");
    }
    String linkId = attachments.get(0);
    String updatedUrl = "";
    try {
      api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
      fail("edit link did not throw an error for empty url");
    } catch (IOException e) {
      String message = e.getMessage();
      int jsonStart = message.indexOf("{");
      String jsonPart = message.substring(jsonStart);
      JSONObject json = new JSONObject(jsonPart);
      String errorCode = json.getJSONObject("error").getString("code");
      String errorMessage = json.getJSONObject("error").getString("message");
      String expected = "Provide the missing value.";
      assertEquals("409008", errorCode);
      assertEquals(expected, errorMessage);
      testStatus = true;
    }
    api.deleteEntityDraft(appUrl, entityName, editLinkEntity);
    if (!testStatus) {
      fail("Could not edit link with an empty URL");
    }
  }

  @Test
  @Order(48)
  void testCopyLinkSuccessNewEntity() throws IOException {
    System.out.println("Test (48): Copy link from one entity to another new entity");

    copySourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    copyTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (copySourceEntity.equals("Could not create entity")
        || copyTargetEntity.equals("Could not create entity")) {
      fail("Could not create source or target entity");
    }

    String linkName = "sample";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, copySourceEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link in source entity");
    }

    api.saveEntityDraft(appUrl, entityName, srvpath, copySourceEntity);

    List<String> sourceObjectIds =
        api.fetchEntityMetadata(appUrl, entityName, facetName, copySourceEntity).stream()
            .map(item -> (String) item.get("objectId"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (sourceObjectIds.isEmpty()) {
      fail("Could not fetch object Id for link");
    }

    String copyResponse =
        api.copyAttachment(appUrl, entityName, facetName, copyTargetEntity, sourceObjectIds);
    if (!copyResponse.equals("Attachments copied successfully")) {
      fail("Could not copy link: " + copyResponse);
    }

    String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target entity after copying link");
    }

    String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copySourceEntity);
    String deleteTargetResponse = api.deleteEntity(appUrl, entityName, copyTargetEntity);
    if (!deleteSourceResponse.equals("Entity Deleted")
        || !deleteTargetResponse.equals("Entity Deleted")) {
      fail("could not delete source or target entity");
    }
  }

  @Test
  @Order(49)
  void testCopyLinkUnsuccessfulNewEntity() throws IOException {
    System.out.println("Test (49): Copy invalid type of link from one entity to another new entity");

    copySourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    copyTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (copySourceEntity.equals("Could not create entity")
        || copyTargetEntity.equals("Could not create entity")) {
      fail("Could not create source or target entity");
    }

    api.saveEntityDraft(appUrl, entityName, srvpath, copySourceEntity);
    List<String> invalidObjectIds = Collections.singletonList("incorrectObjectId");

    try {
      api.copyAttachment(appUrl, entityName, facetName, copyTargetEntity, invalidObjectIds);
      fail("Copy attachments did not throw error for invalid ID");
    } catch (IOException e) {
      System.out.println("Caught expected error: " + e.getMessage());
    }

    String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);
    if (!saveResponse.equals("Saved")) {
      fail("Could not save target entity after unsuccessful copy");
    }

    String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copySourceEntity);
    if (!deleteSourceResponse.equals("Entity Deleted")) {
      fail("Could not delete source entity");
    }
  }

  @Test
  @Order(50)
  void testCopyLinkFromNewEntityToExistingEntity() throws IOException {
    System.out.println("Test (50): Copy link from a new entity to an existing target entity");

    copySourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (copySourceEntity.equals("Could not create entity")) {
      fail("Could not create new source entity");
    }

    String linkName = "Sample";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, copySourceEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link in new source entity");
    }

    String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copySourceEntity);
    if (!saveSourceResponse.equals("Saved")) {
      fail("Could not save new source entity");
    }

    String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target entity draft");
    }

    List<String> sourceObjectIds =
        api.fetchEntityMetadata(appUrl, entityName, facetName, copySourceEntity).stream()
            .map(item -> (String) item.get("objectId"))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (sourceObjectIds.isEmpty()) {
      fail("Could not fetch objectId from new source entity");
    }

    String copyResponse =
        api.copyAttachment(appUrl, entityName, facetName, copyTargetEntity, sourceObjectIds);
    if (!copyResponse.equals("Attachments copied successfully")) {
      fail("Could not copy link from new source entity to existing target entity: " + copyResponse);
    }

    String saveTargetResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);

    if (!saveTargetResponse.equals("Saved")) {
      fail("Could not save target entity after copying link");
    }

    String deleteResponse = api.deleteEntity(appUrl, entityName, copySourceEntity);
    if (!deleteResponse.equals("Entity Deleted")) {
      fail("Could not delete new source entity");
    }
  }

  @Test
  @Order(51)
  void testCopyInvalidLinkFromNewEntityToExistingEntity() throws IOException {
    System.out.println(
        "Test (51): Copy invalid type of link from new entity to existing target entity");

    copySourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (copySourceEntity.equals("Could not create entity")) {
      fail("Could not create new source entity");
    }

    String linkName = "Sample";
    String linkUrl = "https://www.example.com";
    String createLinkResponse =
        api.createLink(appUrl, entityName, facetName, copySourceEntity, linkName, linkUrl);
    if (!createLinkResponse.equals("Link created successfully")) {
      fail("Could not create link in new source entity");
    }

    String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copySourceEntity);
    if (!saveSourceResponse.equals("Saved")) {
      fail("Could not save new source entity");
    }

    String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);
    if (!editResponse.equals("Entity in draft mode")) {
      fail("Could not edit target entity draft");
    }

    List<String> invalidObjectIds = Collections.singletonList("invalidObjectId123");

    try {
      api.copyAttachment(appUrl, entityName, facetName, copyTargetEntity, invalidObjectIds);
      fail("Copy did not throw error for invalid link ID");
    } catch (IOException e) {
      System.out.println("Caught expected error while copying invalid link: " + e.getMessage());
    }

    String saveTargetResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyTargetEntity);
    if (!saveTargetResponse.equals("Saved")) {
      fail("Could not save target entity after unsuccessful copy");
    }

    String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copySourceEntity);
    String deleteTargetResponse = api.deleteEntity(appUrl, entityName, copyTargetEntity);
    if (!deleteSourceResponse.equals("Entity Deleted")
        || !deleteTargetResponse.equals("Entity Deleted")) {
      fail("Could not delete new source entity or target entity");
    }
  }
}
