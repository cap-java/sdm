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
import okhttp3.*;
import okio.ByteString;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_SingleFacet {
  private static String token;
  private static String entityID;
  private static String entityID2;
  private static String facetName = "attachments";
  private static String entityID3;
  private static String entityID4;
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
  private static ApiInterface api;
  private static String attachmentID1 = "";
  private static String attachmentID2 = "";
  private static String attachmentID3 = "";
  private static String attachmentID4 = "";
  private static String attachmentID5 = "";

  private static IntegrationTestUtils integrationTestUtils;

  @BeforeAll
  static void setup() throws IOException {
    // Define your clientId and clientSecret
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
      if (tenant.equals("SDC")) {
        System.out.println("Running integration tests | Multitenant Scenario | SDM DEV Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMTSDC");
      } else if (tenant.equals("GWC")) {
        System.out.println(
            "Running integration tests | Multitenant Scenario | Googleworkspace Consumer");
        authUrl = credentialsProperties.getProperty("authUrlMTGWC");
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
      api = new Api(config);
    } else {
      throw new IllegalArgumentException("Invalid tenancy model specified: " + tenancyModel);
    }
  }

  @Test
  @Order(1)
  void testCreateEntityAndCheck() {
    System.out.println("Test (1) : Create entity and check if it exists");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID = response;
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID);
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
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID1 = createResponse.get(1);
        response =
            api.readAttachmentDraft(
                appUrl, serviceName, entityName, facetName, entityID, attachmentID1);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response =
                api.readAttachment(
                    appUrl, serviceName, entityName, facetName, entityID, attachmentID1);

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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID2 = createResponse.get(1);
        response =
            api.readAttachmentDraft(
                appUrl, serviceName, entityName, facetName, entityID, attachmentID2);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response =
                api.readAttachment(
                    appUrl, serviceName, entityName, facetName, entityID, attachmentID2);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID3 = createResponse.get(1);
        response =
            api.readAttachmentDraft(
                appUrl, serviceName, entityName, facetName, entityID, attachmentID3);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response =
                api.readAttachment(
                    appUrl, serviceName, entityName, facetName, entityID, attachmentID3);
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
  void testUploadSingleAttachmentPDFDuplicate() throws IOException {
    System.out.println("Test (6) : Upload duplicate pdf");
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Boolean testStatus = false;

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        testStatus = false;
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
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
  @Order(7)
  void testUploadSingleAttachmentPDFDuplicateDifferentEntity() throws IOException {
    System.out.println("Test (7) : Upload duplicate pdf in different entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID2 = response;
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID2);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID2);
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

    response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID2);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID2, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals(facetName + " created")) {
        attachmentID4 = createResponse.get(1);
        response =
            api.readAttachmentDraft(
                appUrl, serviceName, entityName, facetName, entityID2, attachmentID4);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID2);
          if (response.equals("Saved")) {
            response =
                api.readAttachment(
                    appUrl, serviceName, entityName, facetName, entityID2, attachmentID4);

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
  @Order(8)
  void testRenameSingleAttachment() {
    System.out.println("Test (8) : Rename single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String name = "sample123";
    if (response == "Entity in draft mode") {
      response =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID, attachmentID1, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(9)
  void testRenameMultipleAttachments() {
    System.out.println("Test (9) : Rename multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String name1 = "sample1234";
    String name2 = "sample12345";
    if (response == "Entity in draft mode") {
      String response1 =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID, attachmentID2, name1);
      String response2 =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID, attachmentID3, name2);
      if (response1.equals("Renamed") && response2.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(10)
  void testRenameSingleAttachmentDuplicate() {
    System.out.println("Test (10) : Rename single attachment duplicate");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String name = "sample123";
    String name2 = "sample123456";
    if (response == "Entity in draft mode") {
      response =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID, attachmentID3, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        String expected =
            "{\"error\":{\"code\":\"400\",\"message\":\"The file(s) sample123 have been added "
                + "multiple times. Please rename and try again.\"}}";
        if (response.equals(expected)) {
          response =
              api.renameAttachment(
                  appUrl, serviceName, entityName, facetName, entityID, attachmentID3, name2);
          if (response.equals("Renamed")) {
            response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
            if (response.equals("Saved")) {
              testStatus = true;
            }
          }
        }
      } else {
        api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was renamed");
    }
  }

  @Test
  @Order(11)
  void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (11) : Delete single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response =
          api.deleteAttachment(appUrl, serviceName, entityName, facetName, entityID, attachmentID1);
      if (response == "Deleted") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response == "Saved") {
          response =
              api.readAttachment(
                  appUrl, serviceName, entityName, facetName, entityID, attachmentID1);
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
  @Order(12)
  void testDeleteMultipleAttachments() throws IOException {
    System.out.println("Test (12) : Delete multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      String response1 =
          api.deleteAttachment(appUrl, serviceName, entityName, facetName, entityID, attachmentID2);
      String response2 =
          api.deleteAttachment(appUrl, serviceName, entityName, facetName, entityID, attachmentID3);
      if (response1 == "Deleted" && response2 == "Deleted") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response == "Saved") {
          response1 =
              api.readAttachment(
                  appUrl, serviceName, entityName, facetName, entityID, attachmentID2);
          response2 =
              api.readAttachment(
                  appUrl, serviceName, entityName, facetName, entityID, attachmentID3);
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
  @Order(13)
  void testDeleteEntity() {
    System.out.println("Test (13) : Delete entity");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, serviceName, entityName, entityID);
    String response2 = api.deleteEntity(appUrl, serviceName, entityName, entityID2);
    if (response == "Entity Deleted" && response2 == "Entity Deleted") {
      testStatus = true;
    }
    if (!testStatus) {
      fail("Could not delete entity");
    }
  }

  @Test
  @Order(14)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_singleAttachment() throws IOException {
    System.out.println("Test (14): Rename & Update secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData, file);
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
            api.renameAttachment(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
        if (response1 == "Renamed"
            && updateSecondaryPropertyResponse1 == "Updated"
            && updateSecondaryPropertyResponse2 == "Updated"
            && updateSecondaryPropertyResponse3 == "Updated"
            && updateSecondaryPropertyResponse4 == "Updated") {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
          if (response == "Saved") {
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
  @Order(15)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_singleAttachment() {
    System.out.println("Test (15): Rename & Update secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      String name1 = "sample.pdf";
      String secondaryPropertyString = "sample";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for attachment");
      String response1 =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue1 = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      if (response1 == "Renamed"
          && updateSecondaryPropertyResponse1 == "Updated"
          && updateSecondaryPropertyResponse2 == "Updated"
          && updateSecondaryPropertyResponse3 == "Updated"
          && updateSecondaryPropertyResponse4 == "Updated") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        if (response == "Saved") {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties for attachment");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property after entity is saved");
    }
  }

  @Test
  @Order(16)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_singleAttachment()
      throws IOException {
    System.out.println(
        "Test (16): Rename & Update invalid secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData, file);
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
            api.renameAttachment(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);

        // Update secondary properties for String using dropdown selected value as object with code

        String dropdownValue1 = integrationTestUtils.getDropDownValue();
        String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown1 =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID1,
                bodyDropdown1);

        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);

        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);

        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

        // Update invalid secondary property
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID1,
                invalidProperty);

        if ("Renamed".equals(response1)
            && "Updated".equals(updateSecondaryPropertyResponse1)
            && "Updated".equals(updateSecondaryPropertyResponse2)
            && "Updated".equals(updateSecondaryPropertyResponse3)
            && "Updated".equals(updateSecondaryPropertyResponse4)
            && "Updated".equals(updateSecondaryPropertyResponse5)) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
          Map<String, Object> attachmentMetadata =
              api.fetchMetadata(
                  appUrl, serviceName, entityName, facetName, entityID3, attachmentID1);
          assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
          assertNull(attachmentMetadata.get("customProperty3"));
          assertNull(attachmentMetadata.get("customProperty4"));
          assertNull(attachmentMetadata.get("customProperty1_code"));
          assertNull(attachmentMetadata.get("customProperty2"));
          assertNull(attachmentMetadata.get("customProperty6"));
          assertNull(attachmentMetadata.get("customProperty5"));
          if ("Saved".equals(response)) {
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
  @Order(17)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_singleAttachment() throws IOException {
    System.out.println(
        "Test (17): Rename & Update invalid secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      String name1 = "sample.pdf";
      String secondaryPropertyString = "A";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testidinvalid";
      System.out.println("Renaming and updating invalid secondary properties for attachment");
      String response1 =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
      String dropdownValue = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      // Update invalid secondary property
      String updateSecondaryPropertyResponse5 =
          api.updateInvalidSecondaryProperty(
              appUrl,
              serviceName,
              entityName,
              facetName,
              entityID3,
              attachmentID1,
              invalidProperty);
      if (response1 == "Renamed"
          && updateSecondaryPropertyResponse1 == "Updated"
          && updateSecondaryPropertyResponse2 == "Updated"
          && updateSecondaryPropertyResponse3 == "Updated"
          && updateSecondaryPropertyResponse4 == "Updated"
          && updateSecondaryPropertyResponse5 == "Updated") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        Map<String, Object> attachmentMetadata =
            api.fetchMetadata(appUrl, serviceName, entityName, facetName, entityID3, attachmentID1);
        assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
        assertNull(attachmentMetadata.get("customProperty3"));
        assertNull(attachmentMetadata.get("customProperty4"));
        assertNull(attachmentMetadata.get("customProperty1_code"));
        assertNull(attachmentMetadata.get("customProperty2"));
        assertNull(attachmentMetadata.get("customProperty6"));
        assertNull(attachmentMetadata.get("customProperty5"));
        if (response == "Saved") {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update secondary properties for attachment is unsuccessfull");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(18)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (18): Rename & Update valid secondary properties for multiple attachments before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData1, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData2, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData3, file);
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
            api.renameAttachment(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponsePDF1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponsePDF2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponsePDF3 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponsePDF4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
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
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID2, bodyBool);
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
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID3,
                bodyDropdown1);
        // Update secondary properties for Integer
        RequestBody bodyInt3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyInt3);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID3,
                bodyDateTime3);

        if (updateSecondaryPropertyResponseEXE1 == "Updated"
            && updateSecondaryPropertyResponseEXE2 == "Updated"
            && updateSecondaryPropertyResponseEXE3 == "Updated") {
          System.out.println("Updated Secondary properties for attachment EXE");
          attachment3Updated = true;
        }

        if (attachment1Updated && attachment2Updated && attachment3Updated) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
          if (response == "Saved") {
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
  @Order(19)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_multipleAttachments() {
    System.out.println(
        "Test (19): Rename & Update  valid secondary properties for multiple attachments after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    if (response == "Entity in draft mode") {
      Boolean attachment1Updated = false;
      Boolean attachment2Updated = false;
      Boolean attachment3Updated = false;

      String name1 = "sample1.pdf";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for attachment PDF");
      String responsePDF1 =
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue1 = integrationTestUtils.getDropDownValue();
      String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
      RequestBody bodyDropdown1 =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown1);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

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
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID2, bodyBool);
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
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyDropdown2);
      // Update secondary properties for Integer
      RequestBody bodyInt3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyInt3);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
      String updateSecondaryPropertyResponseEXE3 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyDateTime3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated"
          && updateSecondaryPropertyResponseEXE3 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      if (attachment1Updated && attachment2Updated && attachment3Updated) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        if (response == "Saved") {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties for attachments");
        }
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property after entity is saved");
    }
  }

  @Test
  @Order(20)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (20): Rename & Update invalid and valid secondary properties for multiple attachments before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData1, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData2, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, srvpath, postData3, file);
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
            api.renameAttachment(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponsePDF1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyint =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponsePDF2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyint);
        // Update secondary properties for DateTime
        RequestBody bodyDateTime =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponsePDF3 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
        // Update secondary properties for Boolean
        RequestBody bodyBoolean =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponsePDF4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
        // Update invalid secondary property
        String updateSecondaryPropertyResponsePDF5 =
            api.updateInvalidSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID1,
                invalidPropertyPDF);
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
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID2, bodyBool);
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
                appUrl,
                serviceName,
                entityName,
                facetName,
                entityID3,
                attachmentID3,
                bodyDropdown1);
        // Update secondary properties for Integer
        RequestBody bodyInt3 =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyInt3);

        if (updateSecondaryPropertyResponseEXE1 == "Updated"
            && updateSecondaryPropertyResponseEXE2 == "Updated") {
          System.out.println("Updated Secondary properties for attachment EXE");
          attachment3Updated = true;
        }

        if (attachment1Updated && attachment2Updated && attachment3Updated) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
          Map<String, Object> attachmentMetadataPDF =
              api.fetchMetadata(
                  appUrl, serviceName, entityName, facetName, entityID3, attachmentID1);
          assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
          assertNull(attachmentMetadataPDF.get("customProperty3"));
          assertNull(attachmentMetadataPDF.get("customProperty4"));
          assertNull(attachmentMetadataPDF.get("customProperty1_code"));
          assertNull(attachmentMetadataPDF.get("customProperty2"));
          assertNull(attachmentMetadataPDF.get("customProperty6"));
          assertNull(attachmentMetadataPDF.get("customProperty5"));

          Map<String, Object> attachmentMetadataTXT =
              api.fetchMetadata(
                  appUrl, serviceName, entityName, facetName, entityID3, attachmentID2);
          assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
          assertNull(attachmentMetadataTXT.get("customProperty3"));
          assertNull(attachmentMetadataTXT.get("customProperty4"));
          assertNull(attachmentMetadataTXT.get("customProperty1_code"));
          assertNull(attachmentMetadataTXT.get("customProperty2"));
          assertTrue((Boolean) attachmentMetadataTXT.get("customProperty6"));
          assertNull(attachmentMetadataTXT.get("customProperty5"));

          Map<String, Object> attachmentMetadataEXE =
              api.fetchMetadata(
                  appUrl, serviceName, entityName, facetName, entityID3, attachmentID3);
          assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
          assertNull(attachmentMetadataEXE.get("customProperty3"));
          assertNull(attachmentMetadataEXE.get("customProperty4"));
          assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
          assertEquals(1234, attachmentMetadataEXE.get("customProperty2"));

          if (response == "Saved") {
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
  @Order(21)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (21): Rename & Update invalid and valid secondary properties for multiple attachments after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
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
          api.renameAttachment(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, name1);
      // Update secondary properties for String
      String dropdownValue = integrationTestUtils.getDropDownValue();
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
      RequestBody bodyDropdown =
          RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
      // Update secondary properties for Integer
      RequestBody bodyInt =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyInt);
      // Update secondary properties for DateTime
      RequestBody bodyDateTime =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
      // Update secondary properties for Boolean
      RequestBody bodyBoolean =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
      // Update invalid secondary property
      String updateSecondaryPropertyResponsePDF5 =
          api.updateInvalidSecondaryProperty(
              appUrl,
              serviceName,
              entityName,
              facetName,
              entityID3,
              attachmentID1,
              invalidPropertyPDF);
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
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID2, bodyBool);
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
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
      // Update secondary properties for Integer
      RequestBody bodyInt3 =
          RequestBody.create(
              MediaType.parse("application/json"),
              ByteString.encodeUtf8(
                  "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryProperty(
              appUrl, serviceName, entityName, facetName, entityID3, attachmentID3, bodyInt3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      if (attachment1Updated && attachment2Updated && attachment3Updated) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        Map<String, Object> attachmentMetadataPDF =
            api.fetchMetadata(appUrl, serviceName, entityName, facetName, entityID3, attachmentID1);
        assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
        assertNull(attachmentMetadataPDF.get("customProperty3"));
        assertNull(attachmentMetadataPDF.get("customProperty4"));
        assertNull(attachmentMetadataPDF.get("customProperty1_code"));
        assertNull(attachmentMetadataPDF.get("customProperty2"));
        assertNull(attachmentMetadataPDF.get("customProperty6"));
        assertNull(attachmentMetadataPDF.get("customProperty5"));

        Map<String, Object> attachmentMetadataTXT =
            api.fetchMetadata(appUrl, serviceName, entityName, facetName, entityID3, attachmentID2);
        assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
        assertNull(attachmentMetadataTXT.get("customProperty3"));
        assertNull(attachmentMetadataTXT.get("customProperty4"));
        assertNull(attachmentMetadataTXT.get("customProperty1_code"));
        assertNull(attachmentMetadataTXT.get("customProperty2"));
        assertFalse((Boolean) attachmentMetadataTXT.get("customProperty6"));
        assertNull(attachmentMetadataTXT.get("customProperty5"));

        Map<String, Object> attachmentMetadataEXE =
            api.fetchMetadata(appUrl, serviceName, entityName, facetName, entityID3, attachmentID3);
        assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
        assertNull(attachmentMetadataEXE.get("customProperty3"));
        assertNull(attachmentMetadataEXE.get("customProperty4"));
        assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
        assertEquals(12, attachmentMetadataEXE.get("customProperty2"));

        if (response == "Saved") {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update unsuccessfull for invalid Secondary properties and successfull for valid property attachments");
        }
        String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
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
  @Order(22)
  void testNAttachments_NewEntity() throws IOException {
    System.out.println(
        "Test (22): Creating new entity and checking only max 4 attachments are allowed to be uploaded");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData1, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData2, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData3, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData3, file);
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
          api.createAttachment(
              appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData3, file);
      if (createResponse5.get(0).equals("Only 4 attachments allowed.")) {
        testStatus = true;
        attachmentID5 = createResponse5.get(1);
        System.out.println("Expected error received: Only 4 attachments allowed.");
      }
      String check = createResponse5.get(0);
      if (check.equals("Attachment created")) {
        testStatus = false;
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID4);
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
  @Order(23)
  void testUploadNAttachments() throws IOException {
    System.out.println("Test (23): Upload maximum 4 attachments in an exsisting entity");

    ClassLoader classLoader = getClass().getClassLoader();
    File originalFile = new File(classLoader.getResource("sample.exe").getFile());

    boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID4);
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
                appUrl, serviceName, entityName, facetName, entityID4, srvpath, postData, tempFile);

        String resultMessage = createResponse.get(0);
        System.out.println("Result message for attachment " + i + ": " + resultMessage);

        if (resultMessage.contains("Only 4 attachments allowed")) {
          String expectedJson =
              "{\"error\":{\"code\":\"500\",\"message\":\"Only 4 attachments allowed.\"}}";
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode actualJsonNode = objectMapper.readTree(resultMessage);
          JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
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
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID4);
      if (deleteEntityResponse != "Entity Deleted") {
        fail("Could not delete entity");
      } else {
        System.out.println("Successfully deleted the test entity4");
      }
    }
  }
}
