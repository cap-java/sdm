package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import integration.com.sap.cds.sdm.utils.CmisDocumentHelper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
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
  private static String changelogEntityID = "";
  private static String changelogAttachmentID = "";
  private static String copyAttachmentSourceEntity;
  private static String copyAttachmentTargetEntity;
  private static String copyAttachmentTargetEntityEmpty;
  private static String copyLinkSourceEntity;
  private static String copyLinkTargetEntity;
  private static String copyCustomSourceEntity;
  private static String copyCustomTargetEntity;
  private static String createLinkEntity;
  private static String editLinkEntity;
  private static List<String> sourceObjectIds = new ArrayList<>();
  private static List<String> targetAttachmentIds = new ArrayList<>();
  private static String moveSourceEntity;
  private static String moveTargetEntity;
  private static List<String> moveObjectIds = new ArrayList<>();
  private static String moveSourceFolderId;

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
   * Helper method to wait for attachment upload completion.
   *
   * @param entityId The ID of the entity containing the attachment
   * @param attachmentId The ID of the attachment to check
   * @param timeoutSeconds Maximum time to wait in seconds
   * @return true if upload completed successfully, false if failed or timed out
   */
  private boolean waitForUploadCompletion(
      String entityId, String attachmentId, int timeoutSeconds) {
    int maxIterations = timeoutSeconds / 2; // Check every 2 seconds
    for (int i = 0; i < maxIterations; i++) {
      try {
        Map<String, Object> metadata =
            api.fetchMetadataDraft(appUrl, entityName, facetName, entityId, attachmentId);
        String uploadStatus = (String) metadata.get("uploadStatus");

        if ("Success".equals(uploadStatus)) {
          return true;
        } else if ("Failed".equals(uploadStatus)) {
          System.err.println("Upload failed for attachment: " + attachmentId);
          return false;
        }

        // Still uploading, wait before checking again
        Thread.sleep(2000);
      } catch (Exception e) {
        System.err.println(
            "Error checking upload status for attachment " + attachmentId + ": " + e.getMessage());
        return false;
      }
    }

    System.err.println("Upload timed out for attachment: " + attachmentId);
    return false;
  }

  /**
   * Helper method to wait for all attachments in an entity to complete upload.
   *
   * @param entityId The ID of the entity containing the attachments
   * @param timeoutSeconds Maximum time to wait in seconds
   * @return true if all uploads completed successfully, false if any failed or timed out
   */
  private boolean waitForAllUploadsCompletion(String entityId, int timeoutSeconds) {
    int maxIterations = timeoutSeconds / 2; // Check every 2 seconds
    for (int i = 0; i < maxIterations; i++) {
      try {
        List<Map<String, Object>> attachmentsMetadata =
            api.fetchEntityMetadataDraft(appUrl, entityName, facetName, entityId);

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
        Thread.sleep(2000);
      } catch (Exception e) {
        System.err.println(
            "Error checking upload status for entity " + entityId + ": " + e.getMessage());
        return false;
      }
    }

    System.err.println("Upload timed out for entity: " + entityId);
    return false;
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
              CmisDocumentHelper.createDocumentInCmis("README.md", "../README.md", entityID);
              CmisDocumentHelper.deleteDocumentFromCmis(entityID, file.getName());
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
  void testUploadVirusFileInScannedRepo() throws IOException {
    System.out.println("Test (4) : Upload EICAR virus file — expect successful upload");

    boolean testStatus = false;

    String eicarFilePath = System.getProperty("eicar.file.path", "eicar.com.txt");
    File file = new File(eicarFilePath);
    if (!file.exists()) {
      fail("EICAR virus test file not found at: " + file.getAbsolutePath());
    }

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "text/plain");
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
              System.out.println("File uploaded successfully");
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload file successfully");
    }
  }

  // @Test
  // @Order(4)
  // void testUploadSingleAttachmentTXT() throws IOException {
  //   System.out.println("Test (4) : Upload txt");
  //   Boolean testStatus = false;
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.txt").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID);
  //   postData.put("mimeType", "application/txt");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response == "Entity in draft mode") {
  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       attachmentID2 = createResponse.get(1);
  //       response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID,
  // attachmentID2);
  //       if (response.equals("OK")) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //         if (response.equals("Saved")) {
  //           response = api.readAttachment(appUrl, entityName, facetName, entityID,
  // attachmentID2);
  //           if (response.equals("OK")) {
  //             testStatus = true;
  //           }
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not upload sample.txt");
  //   }
  // }

  // @Test
  // @Order(5)
  // void testUploadSingleAttachmentEXE() throws IOException {
  //   System.out.println("Test (5) : Upload exe");
  //   Boolean testStatus = false;
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.exe").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID);
  //   postData.put("mimeType", "application/exe");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response == "Entity in draft mode") {
  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       attachmentID3 = createResponse.get(1);
  //       response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID,
  // attachmentID3);
  //       if (response.equals("OK")) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //         if (response.equals("Saved")) {
  //           response = api.readAttachment(appUrl, entityName, facetName, entityID,
  // attachmentID3);
  //           if (response.equals("OK")) {
  //             testStatus = true;
  //           }
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not create sample.exe");
  //   }
  // }

  // @Test
  // @Order(6)
  // void testUploadAttachmentWithoutSDMRole() throws IOException {
  //   System.out.println("Test (6) : Upload attachment with no SDM role");
  //   Boolean testStatus = false;
  //   String response = apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!response.equals("Could not create entity")) {
  //     entityID4 = response;
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     File file = new File(classLoader.getResource("sample.pdf").getFile());

  //     File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
  //     Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID4);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     List<String> createResponse =
  //         apiNoRoles.createAttachment(
  //             appUrl, entityName, facetName, entityID4, srvpath, postData, tempFile);
  //     String check = createResponse.get(0);
  //     String expectedString =
  //         "{\"error\":{\"code\":\"500\",\"message\":\"You do not have the required permissions to
  // upload attachments. Please contact your administrator for access.\"}}";
  //     if (check.equals(expectedString)) {
  //       testStatus = true;
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment created without SDM role");
  //   }
  // }

  // @Test
  // @Order(7)
  // void testUploadSingleAttachmentPDFDuplicate() throws IOException {
  //   System.out.println("Test (7) : Upload duplicate pdf");
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.pdf").getFile());
  //   Boolean testStatus = false;

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response == "Entity in draft mode") {
  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID, srvpath, postData, file);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       testStatus = false;
  //     } else {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       if (response.equals("Saved")) {
  //         String expectedJson =
  //             "{\"error\":{\"code\":\"500\",\"message\":\"An object named \\\"sample.pdf\\\"
  // already exists. Rename the object and try again.\"}}";
  //         ObjectMapper objectMapper = new ObjectMapper();
  //         JsonNode actualJsonNode = objectMapper.readTree(check);
  //         JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
  //         if (expectedJsonNode.equals(actualJsonNode)) {
  //           testStatus = true;
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment created");
  //   }
  // }

  // @Test
  // @Order(8)
  // void testUploadSingleAttachmentPDFDuplicateDifferentEntity() throws IOException {
  //   System.out.println("Test (8) : Upload duplicate pdf in different entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {
  //     entityID2 = response;
  //     response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
  //     if (response == "Saved") {
  //       response = api.checkEntity(appUrl, entityName, entityID2);
  //       if (response.equals("Entity exists")) {
  //         testStatus = true;
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not create entity");
  //   }

  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.pdf").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID2);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   response = api.editEntityDraft(appUrl, entityName, srvpath, entityID2);
  //   if (response == "Entity in draft mode") {
  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID2, srvpath, postData,
  // file);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       attachmentID4 = createResponse.get(1);
  //       response = api.readAttachmentDraft(appUrl, entityName, facetName, entityID2,
  // attachmentID4);
  //       if (response.equals("OK")) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
  //         if (response.equals("Saved")) {
  //           response = api.readAttachment(appUrl, entityName, facetName, entityID2,
  // attachmentID4);

  //           if (response.equals("OK")) {
  //             testStatus = true;
  //           }
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not upload sample.pdf " + response);
  //   }
  // }

  // @Test
  // @Order(9)
  // void testCreateAttachmentWithRestrictedCharacterInFilename() throws IOException {
  //   System.out.println("Test (9): Create attachment with restricted character in filename");

  //   boolean testStatus = false;
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new
  // File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

  //   File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
  //   Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response.equals("Entity in draft mode")) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, entityID, srvpath, postData, tempFile);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       attachmentID6 = createResponse.get(1);

  //       String restrictedFilename = "a/\\bc.pdf";
  //       response =
  //           api.renameAttachment(
  //               appUrl, entityName, facetName, entityID, attachmentID6, restrictedFilename);

  //       if (response.equals("Renamed")) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //         String expected =
  //             "{\"error\":{\"code\":\"400\",\"message\":\"\\\"a/\\bc.pdf\\\" contains unsupported
  // characters (‘/’ or ‘\\\\’). Rename and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //         if (response.equals(expected)) {
  //           api.renameAttachment(
  //               appUrl, entityName, facetName, entityID, attachmentID6, "sample3.pdf");
  //           response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //           if ("Saved".equals(response)) testStatus = true;
  //         }
  //       } else {
  //         api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment created with restricted character in filename");
  //   }
  // }

  // @Test
  // @Order(10)
  // void testDraftUpdateWithFileUploadDeleteAndCreate() throws IOException {
  //   System.out.println("Test (10): Upload attachments, delete one and create entity");

  //   boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {

  //     entityID5 = response;
  //     ClassLoader classLoader = getClass().getClassLoader();

  //     File file = new File(classLoader.getResource("sample.pdf").getFile());
  //     Map<String, Object> postData1 = new HashMap<>();
  //     postData1.put("up__ID", entityID5);
  //     postData1.put("mimeType", "application/pdf");
  //     postData1.put("createdAt", new Date().toString());
  //     postData1.put("createdBy", "test@test.com");
  //     postData1.put("modifiedBy", "test@test.com");

  //     List<String> createResponse1 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID5, srvpath, postData1,
  // file);
  //     if (createResponse1.get(0).equals("Attachment created")) {
  //       attachmentID7 = createResponse1.get(1);
  //     }

  //     file = new File(classLoader.getResource("sample.txt").getFile());
  //     Map<String, Object> postData2 = new HashMap<>();
  //     postData2.put("up__ID", entityID5);
  //     postData2.put("mimeType", "application/txt");
  //     postData2.put("createdAt", new Date().toString());
  //     postData2.put("createdBy", "test@test.com");
  //     postData2.put("modifiedBy", "test@test.com");

  //     List<String> createResponse2 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID5, srvpath, postData2,
  // file);
  //     if (createResponse2.get(0).equals("Attachment created")) {
  //       attachmentID8 = createResponse2.get(1);
  //     }
  //     response = api.deleteAttachment(appUrl, entityName, facetName, entityID5, attachmentID8);
  //     if (response.equals("Deleted")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);

  //       if (response.equals("Saved")) {
  //         testStatus = true;
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Failed to create entity after deleting one attachment");
  //   }
  // }

  // @Test
  // @Order(11)
  // void testUpdateEntityDraft() throws IOException {
  //   System.out.println("Test (11): Update entity in draft");
  //   boolean testStatus = false;
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new
  // File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

  //   File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
  //   Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID5);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID5);
  //   if (response.equals("Entity in draft mode")) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, entityID5, srvpath, postData, tempFile);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);
  //       if (response.equals("Saved")) {
  //         testStatus = true;
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("update entity draft with uploading attachment failed");
  //   }
  //   api.deleteEntity(appUrl, entityName, entityID5);
  // }

  // @Test
  // @Order(12)
  // void testRenameSingleAttachment() {
  //   System.out.println("Test (12) : Rename single attachment");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   String name = "sample123";
  //   if (response == "Entity in draft mode") {
  //     response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1,
  // name);
  //     if (response.equals("Renamed")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       if (response.equals("Saved")) {
  //         testStatus = true;
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment was not renamed");
  //   }
  // }

  // @Test
  // @Order(13)
  // void testRenameAttachmentWithUnsupportedCharacter() {
  //   System.out.println("Test (13) : Rename single attachment with unsupported characters");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   String name = "invalid/name";
  //   if (response == "Entity in draft mode") {
  //     response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1,
  // name);
  //     if (response.equals("Renamed")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       String expected =
  //           "{\"error\":{\"code\":\"400\",\"message\":\"\\\"invalid/name\\\" contains unsupported
  // characters (‘/’ or ‘\\\\’). Rename and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //       if (response.equals(expected)) {
  //         api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1,
  // "sample123");
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //         if ("Saved".equals(response)) testStatus = true;
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment was renamed with unsupported characters");
  //   }
  // }

  // @Test
  // @Order(14)
  // void testRenameMultipleAttachments() {
  //   System.out.println("Test (14) : Rename multiple attachments");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   String name1 = "sample1234";
  //   String name2 = "sample12345";
  //   if (response == "Entity in draft mode") {
  //     String response1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID2, name1);
  //     String response2 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3, name2);
  //     if (response1.equals("Renamed") && response2.equals("Renamed")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       if (response.equals("Saved")) {
  //         testStatus = true;
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment was not renamed");
  //   }
  // }

  // @Test
  // @Order(15)
  // void testRenameSingleAttachmentDuplicate() {
  //   System.out.println("Test (15) : Rename single attachment duplicate");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   String name = "sample123";
  //   String name2 = "sample123456";
  //   if (response == "Entity in draft mode") {
  //     response = api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3,
  // name);
  //     if (response.equals("Renamed")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       String expected =
  //           "{\"error\":{\"code\":\"400\",\"message\":\"An object named \\\"sample123\\\" already
  // exists. Rename the object and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //       if (response.equals(expected)) {
  //         response =
  //             api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID3,
  // name2);
  //         if (response.equals("Renamed")) {
  //           response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //           if (response.equals("Saved")) {
  //             testStatus = true;
  //           }
  //         }
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment was renamed");
  //   }
  // }

  // @Test
  // @Order(16)
  // void testRenameMultipleAttachmentsWithOneUnsupportedCharacter() {
  //   System.out.println(
  //       "Test (16) : Rename multiple attachments where one name has unsupported characters");
  //   Boolean testStatus = false;

  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);

  //   if (response.equals("Entity in draft mode")) {
  //     String validName1 = "valid_attachment1.pdf";
  //     String invalidName2 = "invalid/attachment2.pdf";

  //     String renameResponse1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1,
  // validName1);
  //     String renameResponse2 =
  //         api.renameAttachment(
  //             appUrl, entityName, facetName, entityID, attachmentID2, invalidName2);

  //     if (renameResponse1.equals("Renamed") && renameResponse2.equals("Renamed")) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       String expected =
  //           "{\"error\":{\"code\":\"400\",\"message\":\"\\\"invalid/attachment2.pdf\\\" contains
  // unsupported characters (‘/’ or ‘\\\\’). Rename and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //       if (response.equals(expected)) {
  //         api.renameAttachment(
  //             appUrl, entityName, facetName, entityID, attachmentID2, "sample1234");
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //         if ("Saved".equals(response)) testStatus = true;
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }

  //   if (!testStatus) {
  //     fail("Multiple renames should have failed due to one unsupported characters");
  //   }
  // }

  // @Test
  // @Order(17)
  // void testRenameSingleAttachmentWithoutSDMRole() throws IOException {
  //   System.out.println("Test (17) : Rename attachments where user don't have SDM Roles");
  //   boolean testStatus = false;
  //   String apiResponse = apiNoRoles.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   String name = "sample123"; // Renaming the attachment
  //   if (apiResponse == "Entity in draft mode") {
  //     apiResponse =
  //         apiNoRoles.renameAttachment(appUrl, entityName, facetName, entityID, attachmentID1,
  // name);
  //     if (apiResponse.equals("Renamed")) {
  //       apiResponse = apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       String expected =
  //           "[{\"code\":\"<none>\",\"message\":\"Could not update the following files. \\n"
  //               + //
  //               "\\n"
  //               + //
  //               "\\t\\u2022 valid_attachment1.pdf\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "You do not have the required permissions to update attachments. Kindly contact
  // the admin\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"numericSeverity\":3}]";
  //       if (apiResponse.equals(expected)) {
  //         testStatus = true;
  //       }
  //     } else {
  //       apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment got renamed without SDM roles.");
  //   }
  // }

  // @Test
  // @Order(18)
  // void testRenameToValidateNames() throws IOException {
  //   System.out.println("Test (18) : Rename attachments to validate names");
  //   boolean testStatus = false, successCount = true;
  //   String generatedID = "";
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!response.equals("Could not create entity")) {
  //     entityID3 = response;
  //     String[] filetoUpload = {"sample.pdf", "sample.txt", "sample.exe", "sample2.pdf"};
  //     String[] names = {"Restricted/Character", "    ", "duplicateName.pdf",
  // "duplicateName.pdf"};

  //     ClassLoader classLoader = getClass().getClassLoader();
  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID3);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     for (int i = 0; i < filetoUpload.length; i++) {
  //       File file = new File(classLoader.getResource(filetoUpload[i]).getFile());
  //       List<String> createResponse =
  //           api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData,
  // file);
  //       generatedID = createResponse.get(1);
  //       response =
  //           api.renameAttachment(appUrl, entityName, facetName, entityID3, generatedID,
  // names[i]);
  //       successCount &= "Renamed".equals(response);
  //     }
  //     if (successCount) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //       String expected =
  //           "{\"error\":{\"code\":\"400\",\"message\":\"The object name cannot be empty or
  // consist entirely of space characters. Enter a value.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\",\"details\":[{\"code\":\"<none>\",\"message\":\"\\\"Restricted/Character\\\" contains unsupported characters (‘/’ or ‘\\\\’). Rename and try again.\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"@Common.numericSeverity\":4},{\"code\":\"<none>\",\"message\":\"An object named \\\"duplicateName.pdf\\\" already exists. Rename the object and try again.\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"@Common.numericSeverity\":4}]}}";
  //       if (response.equals(expected)) {
  //         response = api.deleteEntityDraft(appUrl, entityName, entityID3);
  //         if (response.equals("Entity Draft Deleted")) testStatus = true;
  //       }
  //     }
  //     if (!testStatus) fail("Could not create entity");
  //   } else {
  //     fail("Could not create entity");
  //     return;
  //   }
  // }

  // @Test
  // @Order(19)
  // void testDeleteSingleAttachment() throws IOException {
  //   System.out.println("Test (19) : Delete single attachment");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response == "Entity in draft mode") {
  //     response = api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID1);
  //     if (response == "Deleted") {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       if (response == "Saved") {
  //         response = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID1);
  //         if (response.equals("Could not read Attachment")) {
  //           testStatus = true;
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not read Attachment");
  //   }
  // }

  // @Test
  // @Order(20)
  // void testDeleteMultipleAttachments() throws IOException {
  //   System.out.println("Test (20) : Delete multiple attachments");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
  //   if (response == "Entity in draft mode") {
  //     String response1 =
  //         api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID2);
  //     String response2 =
  //         api.deleteAttachment(appUrl, entityName, facetName, entityID, attachmentID3);
  //     if (response1 == "Deleted" && response2 == "Deleted") {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
  //       if (response == "Saved") {
  //         response1 = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID2);
  //         response2 = api.readAttachment(appUrl, entityName, facetName, entityID, attachmentID3);
  //         if (response1.equals("Could not read Attachment")
  //             && response2.equals("Could not read Attachment")) {
  //           testStatus = true;
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not delete attachment");
  //   }
  // }

  // @Test
  // @Order(21)
  // void testUploadBlockedMimeType() throws IOException {
  //   System.out.println("Test (21): Upload blocked mimeType .rtf");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!"Could not create entity".equals(response)) {
  //     entityID2 = response;

  //     ClassLoader classLoader = getClass().getClassLoader();
  //     File file = new
  // File(Objects.requireNonNull(classLoader.getResource("sample.rtf")).getFile());

  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID2);
  //     postData.put("mimeType", "application/rtf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID2, srvpath, postData,
  // file);
  //     String actualResponse = createResponse.get(0);
  //     String expectedJson =
  //         "{\"error\":{\"code\":\"500\",\"message\":\"This file type is not allowed in this
  // repository. Contact your administrator for assistance.\"}}";

  //     if (expectedJson.equals(actualResponse)) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
  //       if ("Saved".equals(response)) {
  //         testStatus = true;
  //       }
  //     } else {
  //       api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment got uploaded with blocked .rtf MIME type");
  //   }
  // }

  // @Test
  // @Order(22)
  // void testDeleteEntity() {
  //   System.out.println("Test (22) : Delete entity");
  //   Boolean testStatus = false;
  //   String response = api.deleteEntity(appUrl, entityName, entityID);
  //   String response2 = api.deleteEntity(appUrl, entityName, entityID2);
  //   if (response == "Entity Deleted" && response2 == "Entity Deleted") {
  //     testStatus = true;
  //   }
  //   if (!testStatus) {
  //     fail("Could not delete entity");
  //   }
  // }

  // @Test
  // @Order(23)
  // void testUpdateValidSecondaryProperty_beforeEntityIsSaved_singleAttachment() throws IOException
  // {
  //   System.out.println("Test (23): Rename & Update secondary property before entity is saved");
  //   System.out.println("Creating entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {
  //     entityID3 = response;
  //     System.out.println("Entity created");
  //     System.out.println("Creating attachment");
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     File file = new File(classLoader.getResource("sample.pdf").getFile());

  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID3);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData,
  // file);
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       attachmentID1 = createResponse.get(1);
  //       System.out.println("Attachment created");
  //       String name1 = "sample1234.pdf";
  //       String secondaryPropertyString = "sample12345";
  //       Integer secondaryPropertyInt = 1234;
  //       LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
  //       System.out.println("Renaming and updating secondary properties for attachment");
  //       String response1 =
  //           api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //       // Update secondary properties for String
  //       String dropdownValue1 = integrationTestUtils.getDropDownValue();
  //       String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
  //       RequestBody bodyDropdown =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //       String updateSecondaryPropertyResponse1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //       // Update secondary properties for Integer
  //       RequestBody bodyInt =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
  //       String updateSecondaryPropertyResponse2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //       // Update secondary properties for DateTime
  //       RequestBody bodyDateTime =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
  //       String updateSecondaryPropertyResponse3 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //       // Update secondary properties for Boolean
  //       RequestBody bodyBoolean =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponse4 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //       if (response1 == "Renamed"
  //           && updateSecondaryPropertyResponse1 == "Updated"
  //           && updateSecondaryPropertyResponse2 == "Updated"
  //           && updateSecondaryPropertyResponse3 == "Updated"
  //           && updateSecondaryPropertyResponse4 == "Updated") {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //         if (response.equals("Saved")) {
  //           System.out.println("Entity saved");
  //           testStatus = true;
  //           System.out.println("Renamed & updated Secondary properties for attachment");
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(24)
  // void testUpdateValidSecondaryProperty_afterEntityIsSaved_singleAttachment() {
  //   System.out.println("Test (24): Rename & Update secondary property after entity is saved");
  //   System.out.println("Editing entity");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
  //   if (response == "Entity in draft mode") {
  //     String name1 = "sample.pdf";
  //     String secondaryPropertyString = "sample";
  //     Integer secondaryPropertyInt = 12;
  //     LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
  //     System.out.println("Renaming and updating secondary properties for attachment");
  //     String response1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //     // Update secondary properties for String
  //     String dropdownValue1 = integrationTestUtils.getDropDownValue();
  //     String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
  //     RequestBody bodyDropdown =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //     String updateSecondaryPropertyResponse1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
  //     String updateSecondaryPropertyResponse2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //     // Update secondary properties for DateTime
  //     RequestBody bodyDateTime =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
  //     String updateSecondaryPropertyResponse3 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBoolean =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //     String updateSecondaryPropertyResponse4 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //     if (response1 == "Renamed"
  //         && updateSecondaryPropertyResponse1 == "Updated"
  //         && updateSecondaryPropertyResponse2 == "Updated"
  //         && updateSecondaryPropertyResponse3 == "Updated"
  //         && updateSecondaryPropertyResponse4 == "Updated") {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //       if (response.equals("Saved")) {
  //         System.out.println("Entity saved");
  //         testStatus = true;
  //         System.out.println("Renamed & updated Secondary properties for attachment");
  //       }
  //     }
  //     String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
  //     if (deleteEntityResponse != "Entity Deleted") {
  //       fail("Could not delete entity");
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property after entity is saved");
  //   }
  // }

  // @Test
  // @Order(25)
  // void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_singleAttachment()
  //     throws IOException {
  //   System.out.println(
  //       "Test (25): Rename & Update invalid secondary property before entity is saved");
  //   System.out.println("Creating entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!"Could not create entity".equals(response)) {
  //     entityID3 = response;
  //     System.out.println("Entity created");
  //     System.out.println("Creating attachment");
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     File file = new File(classLoader.getResource("sample.pdf").getFile());

  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID3);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData,
  // file);
  //     String check = createResponse.get(0);
  //     if ("Attachment created".equals(check)) {
  //       attachmentID1 = createResponse.get(1);
  //       System.out.println("Attachment created");
  //       String name1 = "sample1234.pdf";

  //       // Dropdown values for secondaryPropertyString
  //       String[] dropdownValues = {"A", "B", "C"};
  //       // Select one dropdown value (e.g., "A")
  //       String secondaryPropertyString = dropdownValues[0];

  //       Integer secondaryPropertyInt = 1234;
  //       LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
  //       String invalidProperty = "testid";

  //       System.out.println("Renaming and updating invalid secondary properties for attachment");
  //       String response1 =
  //           api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);

  //       // Update secondary properties for String using dropdown selected value as object with
  // code

  //       String dropdownValue1 = integrationTestUtils.getDropDownValue();
  //       String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
  //       RequestBody bodyDropdown1 =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
  //       String updateSecondaryPropertyResponse1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown1);

  //       // Update secondary properties for Integer
  //       RequestBody bodyInt =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
  //       String updateSecondaryPropertyResponse2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);

  //       // Update secondary properties for DateTime
  //       RequestBody bodyDateTime =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
  //       String updateSecondaryPropertyResponse3 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);

  //       // Update secondary properties for Boolean
  //       RequestBody bodyBoolean =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponse4 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

  //       // Update invalid secondary property
  //       String updateSecondaryPropertyResponse5 =
  //           api.updateInvalidSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, invalidProperty);

  //       if ("Renamed".equals(response1)
  //           && "Updated".equals(updateSecondaryPropertyResponse1)
  //           && "Updated".equals(updateSecondaryPropertyResponse2)
  //           && "Updated".equals(updateSecondaryPropertyResponse3)
  //           && "Updated".equals(updateSecondaryPropertyResponse4)
  //           && "Updated".equals(updateSecondaryPropertyResponse5)) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //         Map<String, Object> attachmentMetadata =
  //             api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
  //         assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
  //         assertNull(attachmentMetadata.get("customProperty3"));
  //         assertNull(attachmentMetadata.get("customProperty4"));
  //         assertNull(attachmentMetadata.get("customProperty1_code"));
  //         assertNull(attachmentMetadata.get("customProperty2"));
  //         assertNull(attachmentMetadata.get("customProperty6"));
  //         assertNull(attachmentMetadata.get("customProperty5"));

  //         String expectedResponse =
  //             "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not
  // supported.\\n"
  //                 + //
  //                 "\\n"
  //                 + //
  //                 "\\t\\u2022 id1\\n"
  //                 + //
  //                 "\\n"
  //                 + //
  //                 "Please contact your administrator for assistance with any necessary
  // adjustments.\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"numericSeverity\":3}]";
  //         if (response.equals(expectedResponse)) {
  //           System.out.println("Entity saved");
  //           testStatus = true;
  //           System.out.println(
  //               "Rename & update secondary properties for attachment is unsuccessfull");
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(26)
  // void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_singleAttachment() throws
  // IOException {
  //   System.out.println(
  //       "Test (26): Rename & Update invalid secondary property after entity is saved");
  //   System.out.println("Editing entity");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
  //   if (response == "Entity in draft mode") {
  //     String name1 = "sample.pdf";
  //     String secondaryPropertyString = "A";
  //     Integer secondaryPropertyInt = 12;
  //     LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
  //     String invalidProperty = "testidinvalid";
  //     System.out.println("Renaming and updating invalid secondary properties for attachment");
  //     String response1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //     String dropdownValue = integrationTestUtils.getDropDownValue();
  //     String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
  //     RequestBody bodyDropdown =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //     String updateSecondaryPropertyResponse1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
  //     String updateSecondaryPropertyResponse2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //     // Update secondary properties for DateTime
  //     RequestBody bodyDateTime =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
  //     String updateSecondaryPropertyResponse3 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBoolean =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //     String updateSecondaryPropertyResponse4 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //     // Update invalid secondary property
  //     String updateSecondaryPropertyResponse5 =
  //         api.updateInvalidSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, invalidProperty);
  //     if (response1 == "Renamed"
  //         && updateSecondaryPropertyResponse1 == "Updated"
  //         && updateSecondaryPropertyResponse2 == "Updated"
  //         && updateSecondaryPropertyResponse3 == "Updated"
  //         && updateSecondaryPropertyResponse4 == "Updated"
  //         && updateSecondaryPropertyResponse5 == "Updated") {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //       Map<String, Object> attachmentMetadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
  //       assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
  //       assertNull(attachmentMetadata.get("customProperty3"));
  //       assertNull(attachmentMetadata.get("customProperty4"));
  //       assertNull(attachmentMetadata.get("customProperty1_code"));
  //       assertNull(attachmentMetadata.get("customProperty2"));
  //       assertNull(attachmentMetadata.get("customProperty6"));
  //       assertNull(attachmentMetadata.get("customProperty5"));

  //       String expectedResponse =
  //           "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not
  // supported.\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "\\t\\u2022 id1\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "Please contact your administrator for assistance with any necessary
  // adjustments.\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"numericSeverity\":3}]";
  //       if (response.equals(expectedResponse)) {
  //         System.out.println("Entity saved");
  //         testStatus = true;
  //         System.out.println(
  //             "Rename & update secondary properties for attachment is unsuccessfull");
  //       }
  //     }
  //     String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
  //     if (deleteEntityResponse != "Entity Deleted") {
  //       fail("Could not delete entity");
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(27)
  // void testUpdateValidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
  //     throws IOException {
  //   System.out.println(
  //       "Test (27): Rename & Update valid secondary properties for multiple attachments before
  // entity is saved");
  //   System.out.println("Creating entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {
  //     entityID3 = response;

  //     System.out.println("Entity created");

  //     System.out.println("Creating attachment PDF");
  //     ClassLoader classLoader = getClass().getClassLoader();

  //     File file = new File(classLoader.getResource("sample.pdf").getFile());
  //     Map<String, Object> postData1 = new HashMap<>();
  //     postData1.put("up__ID", entityID3);
  //     postData1.put("mimeType", "application/pdf");
  //     postData1.put("createdAt", new Date().toString());
  //     postData1.put("createdBy", "test@test.com");
  //     postData1.put("modifiedBy", "test@test.com");

  //     List<String> createResponse1 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData1,
  // file);
  //     if (createResponse1.get(0).equals("Attachment created")) {
  //       attachmentID1 = createResponse1.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment TXT");
  //     file = new File(classLoader.getResource("sample.txt").getFile());
  //     Map<String, Object> postData2 = new HashMap<>();
  //     postData2.put("up__ID", entityID3);
  //     postData2.put("mimeType", "application/txt");
  //     postData2.put("createdAt", new Date().toString());
  //     postData2.put("createdBy", "test@test.com");
  //     postData2.put("modifiedBy", "test@test.com");

  //     List<String> createResponse2 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData2,
  // file);
  //     if (createResponse2.get(0).equals("Attachment created")) {
  //       attachmentID2 = createResponse2.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment EXE");
  //     file = new File(classLoader.getResource("sample.exe").getFile());
  //     Map<String, Object> postData3 = new HashMap<>();
  //     postData3.put("up__ID", entityID3);
  //     postData3.put("mimeType", "application/exe");
  //     postData3.put("createdAt", new Date().toString());
  //     postData3.put("createdBy", "test@test.com");
  //     postData3.put("modifiedBy", "test@test.com");

  //     List<String> createResponse3 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData3,
  // file);
  //     if (createResponse3.get(0).equals("Attachment created")) {
  //       attachmentID3 = createResponse3.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     String check1 = createResponse1.get(0);
  //     String check2 = createResponse2.get(0);
  //     String check3 = createResponse3.get(0);
  //     if (check1.equals("Attachment created")
  //         && check2.equals("Attachment created")
  //         && check3.equals("Attachment created")) {
  //       Boolean attachment1Updated = false;
  //       Boolean attachment2Updated = false;
  //       Boolean attachment3Updated = false;

  //       String name1 = "sample1234.pdf";
  //       Integer secondaryPropertyInt1 = 1234;
  //       LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
  //       System.out.println("Renaming and updating secondary properties for attachment PDF");
  //       String responsePDF1 =
  //           api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //       // Update secondary properties for String
  //       String dropdownValue = integrationTestUtils.getDropDownValue();
  //       String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
  //       RequestBody bodyDropdown =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //       String updateSecondaryPropertyResponsePDF1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //       // Update secondary properties for Integer
  //       RequestBody bodyInt =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
  //       String updateSecondaryPropertyResponsePDF2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //       // Update secondary properties for DateTime
  //       RequestBody bodyDateTime =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
  //       String updateSecondaryPropertyResponsePDF3 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //       // Update secondary properties for Boolean
  //       RequestBody bodyBoolean =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponsePDF4 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //       if (responsePDF1 == "Renamed"
  //           && updateSecondaryPropertyResponsePDF1 == "Updated"
  //           && updateSecondaryPropertyResponsePDF2 == "Updated"
  //           && updateSecondaryPropertyResponsePDF3 == "Updated"
  //           && updateSecondaryPropertyResponsePDF4 == "Updated") {
  //         System.out.println("Renamed & updated Secondary properties for attachment PDF");
  //         attachment1Updated = true;
  //       }

  //       System.out.println("Updating secondary properties for attachment TXT");
  //       // Update secondary properties for Boolean
  //       RequestBody bodyBool =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponseTXT1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
  //       if (updateSecondaryPropertyResponseTXT1 == "Updated") {
  //         System.out.println("Updated Secondary properties for attachment TXT");
  //         attachment2Updated = true;
  //       }
  //       Integer secondaryPropertyInt3 = 1234;
  //       LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
  //       System.out.println("Updating secondary properties for attachment EXE");
  //       // Update secondary properties for String
  //       String dropdownValue1 = integrationTestUtils.getDropDownValue();
  //       String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
  //       RequestBody bodyDropdown1 =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
  //       String updateSecondaryPropertyResponseEXE1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
  //       // Update secondary properties for Integer
  //       RequestBody bodyInt3 =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
  //       String updateSecondaryPropertyResponseEXE2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);
  //       // Update secondary properties for DateTime
  //       RequestBody bodyDateTime3 =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
  //       String updateSecondaryPropertyResponseEXE3 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID3, bodyDateTime3);

  //       if (updateSecondaryPropertyResponseEXE1 == "Updated"
  //           && updateSecondaryPropertyResponseEXE2 == "Updated"
  //           && updateSecondaryPropertyResponseEXE3 == "Updated") {
  //         System.out.println("Updated Secondary properties for attachment EXE");
  //         attachment3Updated = true;
  //       }

  //       if (attachment1Updated && attachment2Updated && attachment3Updated) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //         if (response.equals("Saved")) {
  //           System.out.println("Entity saved");
  //           testStatus = true;
  //           System.out.println("Renamed & updated Secondary properties for attachments");
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(28)
  // void testUpdateValidSecondaryProperty_afterEntityIsSaved_multipleAttachments() {
  //   System.out.println(
  //       "Test (28): Rename & Update  valid secondary properties for multiple attachments after
  // entity is saved");
  //   System.out.println("Editing entity");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
  //   if (response == "Entity in draft mode") {
  //     Boolean attachment1Updated = false;
  //     Boolean attachment2Updated = false;
  //     Boolean attachment3Updated = false;

  //     String name1 = "sample1.pdf";
  //     Integer secondaryPropertyInt1 = 12;
  //     LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
  //     System.out.println("Renaming and updating secondary properties for attachment PDF");
  //     String responsePDF1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //     // Update secondary properties for String
  //     String dropdownValue1 = integrationTestUtils.getDropDownValue();
  //     String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
  //     RequestBody bodyDropdown1 =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
  //     String updateSecondaryPropertyResponsePDF1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown1);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
  //     String updateSecondaryPropertyResponsePDF2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //     // Update secondary properties for DateTime
  //     RequestBody bodyDateTime =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
  //     String updateSecondaryPropertyResponsePDF3 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBoolean =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //     String updateSecondaryPropertyResponsePDF4 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);

  //     if (responsePDF1 == "Renamed"
  //         && updateSecondaryPropertyResponsePDF1 == "Updated"
  //         && updateSecondaryPropertyResponsePDF2 == "Updated"
  //         && updateSecondaryPropertyResponsePDF3 == "Updated"
  //         && updateSecondaryPropertyResponsePDF4 == "Updated") {
  //       System.out.println("Renamed & updated Secondary properties for attachment PDF");
  //       attachment1Updated = true;
  //     }

  //     System.out.println("Updating secondary properties for attachment TXT");
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBool =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //     String updateSecondaryPropertyResponseTXT1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
  //     if (updateSecondaryPropertyResponseTXT1 == "Updated") {
  //       System.out.println("Updated Secondary properties for attachment TXT");
  //       attachment2Updated = true;
  //     }

  //     Integer secondaryPropertyInt3 = 123;
  //     LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
  //     System.out.println("Updating secondary properties for attachment EXE");
  //     // Update secondary properties for String
  //     String dropdownValue2 = integrationTestUtils.getDropDownValue();
  //     String jsonDropdown2 = "{ \"customProperty1_code\" : \"" + dropdownValue2 + "\" }";
  //     RequestBody bodyDropdown2 =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown2);
  //     String updateSecondaryPropertyResponseEXE1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown2);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt3 =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
  //     String updateSecondaryPropertyResponseEXE2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);
  //     // Update secondary properties for DateTime
  //     RequestBody bodyDateTime3 =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime3 + "\"\n}"));
  //     String updateSecondaryPropertyResponseEXE3 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID3, bodyDateTime3);

  //     if (updateSecondaryPropertyResponseEXE1 == "Updated"
  //         && updateSecondaryPropertyResponseEXE2 == "Updated"
  //         && updateSecondaryPropertyResponseEXE3 == "Updated") {
  //       System.out.println("Updated Secondary properties for attachment EXE");
  //       attachment3Updated = true;
  //     }

  //     if (attachment1Updated && attachment2Updated && attachment3Updated) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //       if (response.equals("Saved")) {
  //         System.out.println("Entity saved");
  //         testStatus = true;
  //         System.out.println("Renamed & updated Secondary properties for attachments");
  //       }
  //     }
  //     String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
  //     if (deleteEntityResponse != "Entity Deleted") {
  //       fail("Could not delete entity");
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property after entity is saved");
  //   }
  // }

  // @Test
  // @Order(29)
  // void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
  //     throws IOException {
  //   System.out.println(
  //       "Test (29): Rename & Update invalid and valid secondary properties for multiple
  // attachments before entity is saved");
  //   System.out.println("Creating entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {
  //     entityID3 = response;

  //     System.out.println("Entity created");

  //     System.out.println("Creating attachment PDF");
  //     ClassLoader classLoader = getClass().getClassLoader();

  //     File file = new File(classLoader.getResource("sample.pdf").getFile());
  //     Map<String, Object> postData1 = new HashMap<>();
  //     postData1.put("up__ID", entityID3);
  //     postData1.put("mimeType", "application/pdf");
  //     postData1.put("createdAt", new Date().toString());
  //     postData1.put("createdBy", "test@test.com");
  //     postData1.put("modifiedBy", "test@test.com");

  //     List<String> createResponse1 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData1,
  // file);
  //     if (createResponse1.get(0).equals("Attachment created")) {
  //       attachmentID1 = createResponse1.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment TXT");
  //     file = new File(classLoader.getResource("sample.txt").getFile());
  //     Map<String, Object> postData2 = new HashMap<>();
  //     postData2.put("up__ID", entityID3);
  //     postData2.put("mimeType", "application/txt");
  //     postData2.put("createdAt", new Date().toString());
  //     postData2.put("createdBy", "test@test.com");
  //     postData2.put("modifiedBy", "test@test.com");

  //     List<String> createResponse2 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData2,
  // file);
  //     if (createResponse2.get(0).equals("Attachment created")) {
  //       attachmentID2 = createResponse2.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment EXE");
  //     file = new File(classLoader.getResource("sample.exe").getFile());
  //     Map<String, Object> postData3 = new HashMap<>();
  //     postData3.put("up__ID", entityID3);
  //     postData3.put("mimeType", "application/exe");
  //     postData3.put("createdAt", new Date().toString());
  //     postData3.put("createdBy", "test@test.com");
  //     postData3.put("modifiedBy", "test@test.com");

  //     List<String> createResponse3 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID3, srvpath, postData3,
  // file);
  //     if (createResponse3.get(0).equals("Attachment created")) {
  //       attachmentID3 = createResponse3.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     String check1 = createResponse1.get(0);
  //     String check2 = createResponse2.get(0);
  //     String check3 = createResponse3.get(0);
  //     if (check1.equals("Attachment created")
  //         && check2.equals("Attachment created")
  //         && check3.equals("Attachment created")) {
  //       Boolean attachment1Updated = false;
  //       Boolean attachment2Updated = false;
  //       Boolean attachment3Updated = false;

  //       String name1 = "sample1234.pdf";
  //       Integer secondaryPropertyInt1 = 1234;
  //       LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
  //       String invalidPropertyPDF = "testidinvalidPDF";
  //       System.out.println("Renaming and updating invalid secondary properties for attachment
  // PDF");
  //       String responsePDF1 =
  //           api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //       // Update secondary properties for String
  //       String dropdownValue = integrationTestUtils.getDropDownValue();
  //       String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
  //       RequestBody bodyDropdown =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //       String updateSecondaryPropertyResponsePDF1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //       // Update secondary properties for Integer
  //       RequestBody bodyint =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
  //       String updateSecondaryPropertyResponsePDF2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyint);
  //       // Update secondary properties for DateTime
  //       RequestBody bodyDateTime =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
  //       String updateSecondaryPropertyResponsePDF3 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //       // Update secondary properties for Boolean
  //       RequestBody bodyBoolean =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponsePDF4 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //       // Update invalid secondary property
  //       String updateSecondaryPropertyResponsePDF5 =
  //           api.updateInvalidSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID1, invalidPropertyPDF);
  //       if (responsePDF1 == "Renamed"
  //           && updateSecondaryPropertyResponsePDF1 == "Updated"
  //           && updateSecondaryPropertyResponsePDF2 == "Updated"
  //           && updateSecondaryPropertyResponsePDF3 == "Updated"
  //           && updateSecondaryPropertyResponsePDF4 == "Updated"
  //           && updateSecondaryPropertyResponsePDF5 == "Updated") {
  //         attachment1Updated = true;
  //       }

  //       System.out.println("Updating valid secondary properties for attachment TXT");
  //       // Update secondary properties for Boolean
  //       RequestBody bodyBool =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //       String updateSecondaryPropertyResponseTXT1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
  //       if (updateSecondaryPropertyResponseTXT1 == "Updated") {
  //         System.out.println("Updated Secondary properties for attachment TXT");
  //         attachment2Updated = true;
  //       }

  //       Integer secondaryPropertyInt3 = 1234;
  //       System.out.println("Updating valid secondary properties for attachment EXE");

  //       // Update secondary properties for String
  //       RequestBody bodyDropdown1 =
  //           RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //       String updateSecondaryPropertyResponseEXE1 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
  //       // Update secondary properties for Integer
  //       RequestBody bodyInt3 =
  //           RequestBody.create(
  //               MediaType.parse("application/json"),
  //               ByteString.encodeUtf8(
  //                   "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
  //       String updateSecondaryPropertyResponseEXE2 =
  //           api.updateSecondaryProperty(
  //               appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);

  //       if (updateSecondaryPropertyResponseEXE1 == "Updated"
  //           && updateSecondaryPropertyResponseEXE2 == "Updated") {
  //         System.out.println("Updated Secondary properties for attachment EXE");
  //         attachment3Updated = true;
  //       }

  //       if (attachment1Updated && attachment2Updated && attachment3Updated) {
  //         response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //         Map<String, Object> attachmentMetadataPDF =
  //             api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
  //         assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
  //         assertNull(attachmentMetadataPDF.get("customProperty3"));
  //         assertNull(attachmentMetadataPDF.get("customProperty4"));
  //         assertNull(attachmentMetadataPDF.get("customProperty1_code"));
  //         assertNull(attachmentMetadataPDF.get("customProperty2"));
  //         assertNull(attachmentMetadataPDF.get("customProperty6"));
  //         assertNull(attachmentMetadataPDF.get("customProperty5"));

  //         Map<String, Object> attachmentMetadataTXT =
  //             api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID2);
  //         assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
  //         assertNull(attachmentMetadataTXT.get("customProperty3"));
  //         assertNull(attachmentMetadataTXT.get("customProperty4"));
  //         assertNull(attachmentMetadataTXT.get("customProperty1_code"));
  //         assertNull(attachmentMetadataTXT.get("customProperty2"));
  //         assertTrue((Boolean) attachmentMetadataTXT.get("customProperty6"));
  //         assertNull(attachmentMetadataTXT.get("customProperty5"));

  //         Map<String, Object> attachmentMetadataEXE =
  //             api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID3);
  //         assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
  //         assertNull(attachmentMetadataEXE.get("customProperty3"));
  //         assertNull(attachmentMetadataEXE.get("customProperty4"));
  //         assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
  //         assertEquals(1234, attachmentMetadataEXE.get("customProperty2"));

  //         String expectedResponse =
  //             "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not
  // supported.\\n"
  //                 + //
  //                 "\\n"
  //                 + //
  //                 "\\t\\u2022 id1\\n"
  //                 + //
  //                 "\\n"
  //                 + //
  //                 "Please contact your administrator for assistance with any necessary
  // adjustments.\\n\\nTable: attachments\\nPage: IntegrationTestEntity\",\"numericSeverity\":3}]";
  //         if (response.equals(expectedResponse)) {
  //           System.out.println("Entity saved");
  //           testStatus = true;
  //           System.out.println(
  //               "Rename & update unsuccessfull for invalid Secondary properties and successfull
  // for valid property attachments");
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(30)
  // void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_multipleAttachments()
  //     throws IOException {
  //   System.out.println(
  //       "Test (30): Rename & Update invalid and valid secondary properties for multiple
  // attachments after entity is saved");
  //   System.out.println("Editing entity");
  //   Boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
  //   if (response == "Entity in draft mode") {
  //     Boolean attachment1Updated = false;
  //     Boolean attachment2Updated = false;
  //     Boolean attachment3Updated = false;

  //     String name1 = "sample.pdf";
  //     Integer secondaryPropertyInt1 = 12;
  //     LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
  //     String invalidPropertyPDF = "testidinvalidPDF";
  //     System.out.println("Renaming and updating invalid secondary properties for attachment
  // PDF");
  //     String responsePDF1 =
  //         api.renameAttachment(appUrl, entityName, facetName, entityID3, attachmentID1, name1);
  //     // Update secondary properties for String
  //     String dropdownValue = integrationTestUtils.getDropDownValue();
  //     String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
  //     RequestBody bodyDropdown =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //     String updateSecondaryPropertyResponsePDF1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDropdown);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
  //     String updateSecondaryPropertyResponsePDF2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyInt);
  //     // Update secondary properties for DateTime
  //     RequestBody bodyDateTime =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
  //     String updateSecondaryPropertyResponsePDF3 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyDateTime);
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBoolean =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
  //     String updateSecondaryPropertyResponsePDF4 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, bodyBoolean);
  //     // Update invalid secondary property
  //     String updateSecondaryPropertyResponsePDF5 =
  //         api.updateInvalidSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID1, invalidPropertyPDF);
  //     if (responsePDF1 == "Renamed"
  //         && updateSecondaryPropertyResponsePDF1 == "Updated"
  //         && updateSecondaryPropertyResponsePDF2 == "Updated"
  //         && updateSecondaryPropertyResponsePDF3 == "Updated"
  //         && updateSecondaryPropertyResponsePDF4 == "Updated"
  //         && updateSecondaryPropertyResponsePDF5 == "Updated") {
  //       attachment1Updated = true;
  //     }

  //     System.out.println("Updating valid secondary properties for attachment TXT");
  //     // Update secondary properties for Boolean
  //     RequestBody bodyBool =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8("{\n    \"customProperty6\" : " + false + "\n}"));
  //     String updateSecondaryPropertyResponseTXT1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID2, bodyBool);
  //     if (updateSecondaryPropertyResponseTXT1 == "Updated") {
  //       System.out.println("Updated Secondary properties for attachment TXT");
  //       attachment2Updated = true;
  //     }

  //     Integer secondaryPropertyInt3 = 12;
  //     System.out.println("Updating valid secondary properties for attachment EXE");

  //     // Update secondary properties for String
  //     RequestBody bodyDropdown1 =
  //         RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
  //     String updateSecondaryPropertyResponseEXE1 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID3, bodyDropdown1);
  //     // Update secondary properties for Integer
  //     RequestBody bodyInt3 =
  //         RequestBody.create(
  //             MediaType.parse("application/json"),
  //             ByteString.encodeUtf8(
  //                 "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
  //     String updateSecondaryPropertyResponseEXE2 =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, entityID3, attachmentID3, bodyInt3);

  //     if (updateSecondaryPropertyResponseEXE1 == "Updated"
  //         && updateSecondaryPropertyResponseEXE2 == "Updated") {
  //       System.out.println("Updated Secondary properties for attachment EXE");
  //       attachment3Updated = true;
  //     }

  //     if (attachment1Updated && attachment2Updated && attachment3Updated) {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
  //       Map<String, Object> attachmentMetadataPDF =
  //           api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID1);
  //       assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
  //       assertNull(attachmentMetadataPDF.get("customProperty3"));
  //       assertNull(attachmentMetadataPDF.get("customProperty4"));
  //       assertNull(attachmentMetadataPDF.get("customProperty1_code"));
  //       assertNull(attachmentMetadataPDF.get("customProperty2"));
  //       assertNull(attachmentMetadataPDF.get("customProperty6"));
  //       assertNull(attachmentMetadataPDF.get("customProperty5"));

  //       Map<String, Object> attachmentMetadataTXT =
  //           api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID2);
  //       assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
  //       assertNull(attachmentMetadataTXT.get("customProperty3"));
  //       assertNull(attachmentMetadataTXT.get("customProperty4"));
  //       assertNull(attachmentMetadataTXT.get("customProperty1_code"));
  //       assertNull(attachmentMetadataTXT.get("customProperty2"));
  //       assertFalse((Boolean) attachmentMetadataTXT.get("customProperty6"));
  //       assertNull(attachmentMetadataTXT.get("customProperty5"));

  //       Map<String, Object> attachmentMetadataEXE =
  //           api.fetchMetadata(appUrl, entityName, facetName, entityID3, attachmentID3);
  //       assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
  //       assertNull(attachmentMetadataEXE.get("customProperty3"));
  //       assertNull(attachmentMetadataEXE.get("customProperty4"));
  //       assertEquals(dropdownValue, attachmentMetadataEXE.get("customProperty1_code"));
  //       assertEquals(12, attachmentMetadataEXE.get("customProperty2"));

  //       String expectedResponse =
  //           "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not
  // supported.\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "\\t\\u2022 id1\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "Please contact your administrator for assistance with any necessary
  // adjustments.\\n"
  //               + //
  //               "\\n"
  //               + //
  //               "Table: attachments\\n"
  //               + //
  //               "Page: IntegrationTestEntity\",\"numericSeverity\":3}]";
  //       if (response.equals(expectedResponse)) {
  //         System.out.println("Entity saved");
  //         testStatus = true;
  //         System.out.println(
  //             "Rename & update unsuccessfull for invalid Secondary properties and successfull for
  // valid property attachments");
  //       }
  //       String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
  //       if (deleteEntityResponse != "Entity Deleted") {
  //         fail("Could not delete entity");
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Could not update secondary property before entity is saved");
  //   }
  // }

  // @Test
  // @Order(31)
  // void testNAttachments_NewEntity() throws IOException {
  //   System.out.println(
  //       "Test (31): Creating new entity and checking only max 4 attachments are allowed to be
  // uploaded");
  //   System.out.println("Creating entity");
  //   Boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response != "Could not create entity") {
  //     entityID4 = response;

  //     System.out.println("Entity created");

  //     System.out.println("Creating attachment PDF");
  //     ClassLoader classLoader = getClass().getClassLoader();

  //     File file = new File(classLoader.getResource("sample.pdf").getFile());
  //     Map<String, Object> postData1 = new HashMap<>();
  //     postData1.put("up__ID", entityID4);
  //     postData1.put("mimeType", "application/pdf");
  //     postData1.put("createdAt", new Date().toString());
  //     postData1.put("createdBy", "test@test.com");
  //     postData1.put("modifiedBy", "test@test.com");

  //     List<String> createResponse1 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData1,
  // file);
  //     if (createResponse1.get(0).equals("Attachment created")) {
  //       attachmentID1 = createResponse1.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment TXT");
  //     file = new File(classLoader.getResource("sample.txt").getFile());
  //     Map<String, Object> postData2 = new HashMap<>();
  //     postData2.put("up__ID", entityID4);
  //     postData2.put("mimeType", "application/txt");
  //     postData2.put("createdAt", new Date().toString());
  //     postData2.put("createdBy", "test@test.com");
  //     postData2.put("modifiedBy", "test@test.com");

  //     List<String> createResponse2 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData2,
  // file);
  //     if (createResponse2.get(0).equals("Attachment created")) {
  //       attachmentID2 = createResponse2.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating attachment EXE");
  //     file = new File(classLoader.getResource("sample.exe").getFile());
  //     Map<String, Object> postData3 = new HashMap<>();
  //     postData3.put("up__ID", entityID4);
  //     postData3.put("mimeType", "application/exe");
  //     postData3.put("createdAt", new Date().toString());
  //     postData3.put("createdBy", "test@test.com");
  //     postData3.put("modifiedBy", "test@test.com");

  //     List<String> createResponse3 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3,
  // file);
  //     if (createResponse3.get(0).equals("Attachment created")) {
  //       attachmentID3 = createResponse3.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating second attachment pdf");
  //     file = new File(classLoader.getResource("sample1.pdf").getFile());
  //     Map<String, Object> postData4 = new HashMap<>();
  //     postData4.put("up__ID", entityID4);
  //     postData4.put("mimeType", "application/pdf");
  //     postData4.put("createdAt", new Date().toString());
  //     postData4.put("createdBy", "test@test.com");
  //     postData4.put("modifiedBy", "test@test.com");

  //     List<String> createResponse4 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3,
  // file);
  //     if (createResponse4.get(0).equals("Attachment created")) {
  //       attachmentID4 = createResponse4.get(1);
  //       System.out.println("Attachment created");
  //     }

  //     System.out.println("Creating third attachment pdf");
  //     file = new File(classLoader.getResource("sample2.pdf").getFile());
  //     Map<String, Object> postData5 = new HashMap<>();
  //     postData5.put("up__ID", entityID4);
  //     postData5.put("mimeType", "application/pdf");
  //     postData5.put("createdAt", new Date().toString());
  //     postData5.put("createdBy", "test@test.com");
  //     postData5.put("modifiedBy", "test@test.com");

  //     List<String> createResponse5 =
  //         api.createAttachment(appUrl, entityName, facetName, entityID4, srvpath, postData3,
  // file);
  //     if (createResponse5.get(0).equals("Only 4 attachments allowed.")) {
  //       testStatus = true;
  //       attachmentID5 = createResponse5.get(1);
  //       System.out.println("Expected error received: Only 4 attachments allowed.");
  //     }
  //     String check = createResponse5.get(0);
  //     if (check.equals("Attachment created")) {
  //       testStatus = false;
  //     } else {
  //       response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID4);
  //       if (response.equals("Saved")) {
  //         String expectedJson =
  //             "{\"error\":{\"code\":\"500\",\"message\":\"Cannot upload more than 4
  // attachments.\"}}";
  //         ObjectMapper objectMapper = new ObjectMapper();
  //         JsonNode actualJsonNode = objectMapper.readTree(check);
  //         JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
  //         if (expectedJsonNode.equals(actualJsonNode)) {
  //           testStatus = true;
  //         }
  //       }
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Attachment was created");
  //   }
  // }

  // @Test
  // @Order(32)
  // void testUploadNAttachments() throws IOException {
  //   System.out.println("Test (32): Upload maximum 4 attachments in an exsisting entity");

  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File originalFile = new File(classLoader.getResource("sample.exe").getFile());

  //   boolean testStatus = false;
  //   String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID4);
  //   System.out.println("response: " + response);

  //   if ("Entity in draft mode".equals(response)) {
  //     for (int i = 1; i <= 5; i++) {
  //       // Ensure only one file is uploaded at a time and complete before next
  //       File tempFile = File.createTempFile("sample_" + i + "_", ".exe");
  //       Files.copy(originalFile.toPath(), tempFile.toPath(),
  // StandardCopyOption.REPLACE_EXISTING);

  //       Map<String, Object> postData = new HashMap<>();
  //       postData.put("up__ID", entityID4);
  //       postData.put("mimeType", "application/exe");
  //       postData.put("createdAt", new Date().toString());
  //       postData.put("createdBy", "test@test.com");
  //       postData.put("modifiedBy", "test@test.com");

  //       List<String> createResponse =
  //           api.createAttachment(
  //               appUrl, entityName, facetName, entityID4, srvpath, postData, tempFile);

  //       String resultMessage = createResponse.get(0);
  //       System.out.println("Result message for attachment " + i + ": " + resultMessage);

  //       String expectedResponse =
  //           "{\"error\":{\"code\":\"500\",\"message\":\"Cannot upload more than 4
  // attachments.\"}}";
  //       if (resultMessage.equals(expectedResponse)) {
  //         ObjectMapper objectMapper = new ObjectMapper();
  //         JsonNode actualJsonNode = objectMapper.readTree(resultMessage);
  //         JsonNode expectedJsonNode = objectMapper.readTree(expectedResponse);
  //         if (expectedJsonNode.equals(actualJsonNode)) {
  //           testStatus = true;
  //         }
  //       } else {
  //         testStatus = false;
  //       }
  //       tempFile.delete();
  //     }
  //     if (!testStatus) {
  //       fail("5th attachment did not trigger the expected error.");
  //     }
  //     // Delete the newly created entity
  //     String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID4);
  //     if (deleteEntityResponse != "Entity Deleted") {
  //       fail("Could not delete entity");
  //     } else {
  //       System.out.println("Successfully deleted the test entity4");
  //     }
  //   }
  // }

  // @Test
  // @Order(33)
  // void testDiscardDraftWithoutAttachments() {
  //   System.out.println("Test (33) : Discard draft without adding attachments");

  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

  //   if (response.equals("Could not create entity")) {
  //     fail("Could not create entity");
  //   }

  //   response = api.deleteEntityDraft(appUrl, entityName, response);
  //   if (!response.equals("Entity Draft Deleted")) {
  //     fail("Draft was not discarded properly");
  //   }
  // }

  // @Test
  // @Order(34)
  // void testDiscardDraftWithAttachments() throws IOException {
  //   System.out.println("Test (34) : Discard draft with attachments");
  //   boolean testStatus = false;
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!response.equals("Could not create entity")) {
  //     entityID7 = response;
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     File file = new File(classLoader.getResource("sample.pdf").getFile());

  //     Map<String, Object> postData1 = new HashMap<>();
  //     postData1.put("up__ID", entityID7);
  //     postData1.put("mimeType", "application/pdf");
  //     postData1.put("createdAt", new Date().toString());
  //     postData1.put("createdBy", "test@test.com");
  //     postData1.put("modifiedBy", "test@test.com");

  //     List<String> createResponse =
  //         api.createAttachment(appUrl, entityName, facetName, entityID7, srvpath, postData1,
  // file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       attachmentID1 = createResponse.get(1);
  //     }
  //     String check = createResponse.get(0);
  //     if (check.equals("Attachment created")) {
  //       response = api.deleteEntityDraft(appUrl, entityName, entityID7);
  //     }
  //     if (response.equals("Entity Draft Deleted")) {
  //       testStatus = true;
  //     }
  //   }
  //   if (!testStatus) {
  //     fail("Draft was not discarded properly");
  //   }
  // }

  // @Test
  // @Order(35)
  // void testCopyAttachmentsSuccessNewEntity() throws IOException {
  //   System.out.println("Test (35): Copy attachments from one entity to another new entity");
  //   List<String> attachments = new ArrayList<>();
  //   copyAttachmentSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   copyAttachmentTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!copyAttachmentSourceEntity.equals("Could not create entity")
  //       && !copyAttachmentTargetEntity.equals("Could not create entity")) {
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     List<File> files = new ArrayList<>();
  //     files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //     files.add(new File(classLoader.getResource("sample1.pdf").getFile()));
  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID7);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     for (File file : files) {
  //       List<String> createResponse =
  //           api.createAttachment(
  //               appUrl, entityName, facetName, copyAttachmentSourceEntity, srvpath, postData,
  // file);
  //       if (createResponse.get(0).equals("Attachment created")) {
  //         attachments.add(createResponse.get(1));
  //       } else {
  //         fail("Could not create attachment");
  //       }
  //     }
  //     api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //     List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
  //     Map<String, Object> fetchAttachmentMetadataResponse;
  //     for (String attachment : attachments) {
  //       try {
  //         fetchAttachmentMetadataResponse =
  //             api.fetchMetadata(
  //                 appUrl, entityName, facetName, copyAttachmentSourceEntity, attachment);
  //         attachmentsMetadata.add(fetchAttachmentMetadataResponse);
  //       } catch (IOException e) {
  //         fail("Could not fetch attachment metadata: " + e.getMessage());
  //       }
  //     }
  //     for (Map<String, Object> metadata : attachmentsMetadata) {
  //       if (metadata.containsKey("objectId")) {
  //         sourceObjectIds.add(metadata.get("objectId").toString());
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     }

  //     if (sourceObjectIds.size() == 2) {
  //       String copyResponse;
  //       copyResponse =
  //           api.copyAttachment(
  //               appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
  //       if (copyResponse.equals("Attachments copied successfully")) {
  //         // Wait for all uploads to complete before saving
  //         if (!waitForAllUploadsCompletion(copyAttachmentTargetEntity, 60)) {
  //           fail("Upload did not complete in time after copying attachments");
  //         }
  //         String saveEntityResponse =
  //             api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //         if (saveEntityResponse.equals("Saved")) {
  //           List<Map<String, Object>> fetchEntityMetadataResponse;
  //           fetchEntityMetadataResponse =
  //               api.fetchEntityMetadata(appUrl, entityName, facetName,
  // copyAttachmentTargetEntity);
  //           targetAttachmentIds =
  //               fetchEntityMetadataResponse.stream()
  //                   .map(item -> (String) item.get("ID"))
  //                   .filter(Objects::nonNull)
  //                   .collect(Collectors.toList());
  //           String readResponse;
  //           for (String targetAttachmentId : targetAttachmentIds) {
  //             readResponse =
  //                 api.readAttachment(
  //                     appUrl,
  //                     entityName,
  //                     facetName,
  //                     copyAttachmentTargetEntity,
  //                     targetAttachmentId);
  //             if (!readResponse.equals("OK")) {
  //               fail("Could not read copied attachment");
  //             }
  //           }
  //         } else {
  //           fail("Could not save entity after copying attachments: " + saveEntityResponse);
  //         }
  //       } else {
  //         fail("Could not copy attachments: " + copyResponse);
  //       }
  //     } else {
  //       fail("Could not fetch objects Ids for all attachments");
  //     }
  //   } else {
  //     fail("Could not create entities");
  //   }
  // }

  // @Test
  // @Order(36)
  // void testCopyAttachmentsUnsuccessfulNewEntity() throws IOException {
  //   System.out.println("Test (36): Copy attachments from one entity to another new entity");
  //   String editResponse1 =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //   copyAttachmentTargetEntityEmpty =
  //       api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (editResponse1.equals("Entity in draft mode")
  //       && !copyAttachmentTargetEntityEmpty.equals("Could not create entity")) {
  //     sourceObjectIds.add("incorrectObjectId");
  //     if (sourceObjectIds.size() == 3) {
  //       try {
  //         api.copyAttachment(
  //             appUrl, entityName, facetName, copyAttachmentTargetEntityEmpty, sourceObjectIds);
  //         fail("Copy attachments did not throw an error");
  //       } catch (IOException e) {
  //         String saveEntityResponse1 =
  //             api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //         String saveEntityResponse2 =
  //             api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntityEmpty);
  //         String deleteResponse =
  //             api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntityEmpty);
  //         if (!saveEntityResponse1.equals("Saved")
  //             || !saveEntityResponse2.equals("Saved")
  //             || !deleteResponse.equals("Entity Deleted")) {
  //           fail("Could not save entities");
  //         }
  //       }
  //     } else {
  //       fail("Could not fetch objects Ids for all attachments");
  //     }
  //   } else {
  //     fail("Could not edit entities");
  //   }
  // }

  // @Test
  // @Order(37)
  // void testCopyAttachmentWithNotesField() throws IOException {
  //   System.out.println(
  //       "Test (37): Create entity with attachment containing notes, copy to new entity and verify
  // notes field");
  //   Boolean testStatus = false;
  //   // Create source entity
  //   copyCustomSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (copyCustomSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Create and upload attachment to source entity
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.pdf").getFile());
  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", copyCustomSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, srvpath, postData, file);

  //   if (!createResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create attachment");
  //   }

  //   String sourceAttachmentId = createResponse.get(1);

  //   // Update attachment with notes field
  //   String notesValue = "This is a test note for copy attachment verification";
  //   MediaType mediaType = MediaType.parse("application/json");
  //   String jsonPayload = "{\"note\": \"" + notesValue + "\"}";
  //   RequestBody updateBody = RequestBody.create(jsonPayload, mediaType);

  //   String updateResponse =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId,
  // updateBody);

  //   if (!updateResponse.equals("Updated")) {
  //     fail("Could not update attachment notes field");
  //   }

  //   // Save source entity
  //   String saveSourceResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity");
  //   }

  //   // Fetch attachment metadata to get objectId
  //   Map<String, Object> sourceAttachmentMetadata =
  //       api.fetchMetadata(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId);

  //   if (!sourceAttachmentMetadata.containsKey("objectId")) {
  //     fail("Source attachment metadata does not contain objectId");
  //   }

  //   // Store objectId in array
  //   String sourceObjectId = sourceAttachmentMetadata.get("objectId").toString();
  //   if (sourceObjectIds.isEmpty()) {
  //     sourceObjectIds.add(sourceObjectId);
  //   } else {
  //     sourceObjectIds.set(0, sourceObjectId);
  //   }

  //   String sourceNoteValue =
  //       sourceAttachmentMetadata.get("note") != null
  //           ? sourceAttachmentMetadata.get("note").toString()
  //           : null;

  //   if (!notesValue.equals(sourceNoteValue)) {
  //     fail(
  //         "Notes field was not properly set in source attachment. Expected: "
  //             + notesValue
  //             + ", Got: "
  //             + sourceNoteValue);
  //   }

  //   // Create target entity
  //   copyCustomTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (copyCustomTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Copy attachment to target entity
  //   List<String> objectIdsToCopy = new ArrayList<>();
  //   objectIdsToCopy.add(sourceObjectIds.get(0)); // Use objectId from array

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyCustomTargetEntity,
  // objectIdsToCopy);

  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy attachment to target entity: " + copyResponse);
  //   }

  //   // Wait for all uploads to complete before saving
  //   if (!waitForAllUploadsCompletion(copyCustomTargetEntity, 60)) {
  //     fail("Upload did not complete in time after copying attachment");
  //   }

  //   // Save target entity
  //   String saveTargetResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity");
  //   }

  //   // Fetch target entity attachments metadata
  //   List<Map<String, Object>> targetAttachmentsMetadata =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyCustomTargetEntity);

  //   if (targetAttachmentsMetadata.isEmpty()) {
  //     fail("No attachments found in target entity");
  //   }

  //   // Verify the copied attachment has the same notes value
  //   Map<String, Object> copiedAttachmentMetadata = targetAttachmentsMetadata.get(0);
  //   String copiedNoteValue =
  //       copiedAttachmentMetadata.get("note") != null
  //           ? copiedAttachmentMetadata.get("note").toString()
  //           : null;

  //   if (!notesValue.equals(copiedNoteValue)) {
  //     fail(
  //         "Notes field was not properly copied. Expected: "
  //             + notesValue
  //             + ", Got: "
  //             + copiedNoteValue);
  //   }

  //   // Verify attachment content can be read from target entity
  //   String targetAttachmentId = (String) copiedAttachmentMetadata.get("ID");
  //   String readResponse =
  //       api.readAttachment(
  //           appUrl, entityName, facetName, copyCustomTargetEntity, targetAttachmentId);

  //   if (readResponse.equals("OK")) {
  //     testStatus = true;
  //   }
  //   if (!testStatus) {
  //     fail("Could not verify that notes field was copied from source to target attachment");
  //   }
  // }

  // @Test
  // @Order(38)
  // void testCopyAttachmentWithSecondaryPropertiesField() throws IOException {
  //   System.out.println(
  //       "Test (38): Verify that secondary properties are preserved when copying attachments
  // between entities");
  //   Boolean testStatus = false;

  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // copyCustomSourceEntity);
  //   if (!editResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit source entity");
  //   }

  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample1.pdf").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", copyCustomSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, srvpath, postData, file);

  //   if (!createResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create attachment");
  //   }

  //   String sourceAttachmentId = createResponse.get(1);

  //   // Update attachment with secondary properties
  //   // DocumentInfoRecordBoolean : Set to true
  //   RequestBody bodyBoolean =
  //       RequestBody.create(
  //           MediaType.parse("application/json"),
  //           ByteString.encodeUtf8("{\n \"customProperty6\" : " + true + "\n}"));
  //   String updateSecondaryPropertyResponse1 =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId,
  // bodyBoolean);

  //   if (!updateSecondaryPropertyResponse1.equals("Updated")) {
  //     fail(
  //         "Could not update attachment DocumentInfoRecordBoolean field. Response: "
  //             + updateSecondaryPropertyResponse1);
  //   }

  //   // customProperty2 : Set to 12345
  //   Integer customProperty2Value = 12345;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           MediaType.parse("application/json"),
  //           ByteString.encodeUtf8("{\n \"customProperty2\" : " + customProperty2Value + "\n}"));
  //   String updateSecondaryPropertyResponse2 =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId, bodyInt);

  //   if (!updateSecondaryPropertyResponse2.equals("Updated")) {
  //     fail(
  //         "Could not update attachment customProperty2 field. Response: "
  //             + updateSecondaryPropertyResponse2);
  //   }

  //   // Save source entity
  //   String saveSourceResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity. Response: " + saveSourceResponse);
  //   }

  //   // Fetch attachment metadata to get objectId and verify secondary properties
  //   Map<String, Object> sourceAttachmentMetadata =
  //       api.fetchMetadata(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId);

  //   if (!sourceAttachmentMetadata.containsKey("objectId")) {
  //     fail("Source attachment metadata does not contain objectId");
  //   }

  //   // Store objectId in array for reuse
  //   String sourceObjectId = sourceAttachmentMetadata.get("objectId").toString();
  //   if (sourceObjectIds.size() < 2) {
  //     sourceObjectIds.add(sourceObjectId);
  //   } else {
  //     sourceObjectIds.set(1, sourceObjectId);
  //   }

  //   // Verify all secondary properties in source attachment
  //   Boolean sourceCustomProperty6 =
  //       sourceAttachmentMetadata.get("customProperty6") != null
  //           ? (Boolean) sourceAttachmentMetadata.get("customProperty6")
  //           : null;
  //   Integer sourceCustomProperty2 =
  //       sourceAttachmentMetadata.get("customProperty2") != null
  //           ? (Integer) sourceAttachmentMetadata.get("customProperty2")
  //           : null;

  //   if (sourceCustomProperty6 == null || !sourceCustomProperty6) {
  //     fail(
  //         "DocumentInfoRecordBoolean was not properly set in source attachment. Expected: true,
  // Got: "
  //             + sourceCustomProperty6);
  //   }

  //   if (!customProperty2Value.equals(sourceCustomProperty2)) {
  //     fail(
  //         "customProperty2 was not properly set in source attachment. Expected: "
  //             + customProperty2Value
  //             + ", Got: "
  //             + sourceCustomProperty2);
  //   }

  //   String editTargetResponse =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyCustomTargetEntity);
  //   if (!editTargetResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit target entity");
  //   }

  //   // Copy attachment to target entity
  //   List<String> objectIdsToCopy = new ArrayList<>();
  //   objectIdsToCopy.add(sourceObjectIds.get(1)); // Use objectId from array

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyCustomTargetEntity,
  // objectIdsToCopy);

  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy attachment to target entity: " + copyResponse);
  //   }

  //   // Wait for all uploads to complete before saving
  //   if (!waitForAllUploadsCompletion(copyCustomTargetEntity, 60)) {
  //     fail("Upload did not complete in time after copying attachment");
  //   }

  //   // Save target entity
  //   String saveTargetResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity");
  //   }

  //   // Fetch target entity attachments metadata
  //   List<Map<String, Object>> targetAttachmentsMetadata =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyCustomTargetEntity);

  //   if (targetAttachmentsMetadata.isEmpty()) {
  //     fail("No attachments found in target entity");
  //   }

  //   // Verify the copied attachment has the same secondary properties
  //   // Find the attachment we just copied by matching the filename
  //   Map<String, Object> copiedAttachmentMetadata =
  //       targetAttachmentsMetadata.stream()
  //           .filter(attachment -> "sample1.pdf".equals(attachment.get("fileName")))
  //           .findFirst()
  //           .orElse(null);

  //   if (copiedAttachmentMetadata == null) {
  //     fail("Could not find the copied attachment with file in target entity");
  //   }

  //   Boolean copiedCustomProperty6 =
  //       copiedAttachmentMetadata.get("customProperty6") != null
  //           ? (Boolean) copiedAttachmentMetadata.get("customProperty6")
  //           : null;
  //   Integer copiedCustomProperty2 =
  //       copiedAttachmentMetadata.get("customProperty2") != null
  //           ? (Integer) copiedAttachmentMetadata.get("customProperty2")
  //           : null;

  //   // Verify DocumentInfoRecordBoolean
  //   if (copiedCustomProperty6 == null || !copiedCustomProperty6) {
  //     fail(
  //         "DocumentInfoRecordBoolean as not properly copied. Expected: true, Got: "
  //             + copiedCustomProperty6);
  //   }

  //   // Verify customProperty2
  //   if (!customProperty2Value.equals(copiedCustomProperty2)) {
  //     fail(
  //         "customProperty2 was not properly copied. Expected: "
  //             + customProperty2Value
  //             + ", Got: "
  //             + copiedCustomProperty2);
  //   }

  //   // Verify attachment content can be read from target entity
  //   String targetAttachmentId = (String) copiedAttachmentMetadata.get("ID");
  //   String readResponse =
  //       api.readAttachment(
  //           appUrl, entityName, facetName, copyCustomTargetEntity, targetAttachmentId);

  //   if (readResponse.equals("OK")) {
  //     testStatus = true;
  //   }
  //   if (!testStatus) {
  //     fail(
  //         "Could not verify that all secondary properties were copied from source to target
  // attachment");
  //   }
  // }

  // @Test
  // @Order(39)
  // void testCopyAttachmentWithNotesAndSecondaryPropertiesField() throws IOException {
  //   System.out.println(
  //       "Test (39): Verify that both notes field and secondary properties are preserved during
  // attachment copy");
  //   Boolean testStatus = false;

  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // copyCustomSourceEntity);
  //   if (!editResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit source entity");
  //   }

  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample2.pdf").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", copyCustomSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, srvpath, postData, file);

  //   if (!createResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create attachment");
  //   }

  //   String sourceAttachmentId = createResponse.get(1);

  //   // Update attachment with notes field
  //   String notesValue = "This attachment has both notes and secondary properties for testing";
  //   MediaType mediaType = MediaType.parse("application/json");
  //   String jsonPayload = "{\"note\": \"" + notesValue + "\"}";
  //   RequestBody updateNotesBody = RequestBody.create(jsonPayload, mediaType);

  //   String updateNotesResponse =
  //       api.updateSecondaryProperty(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           copyCustomSourceEntity,
  //           sourceAttachmentId,
  //           updateNotesBody);

  //   if (!updateNotesResponse.equals("Updated")) {
  //     fail("Could not update attachment notes field");
  //   }

  //   // Update attachment with secondary properties
  //   // DocumentInfoRecordBoolean : Set to true
  //   RequestBody bodyBoolean =
  //       RequestBody.create(
  //           MediaType.parse("application/json"),
  //           ByteString.encodeUtf8("{\n \"customProperty6\" : " + true + "\n}"));
  //   String updateSecondaryPropertyResponse1 =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId,
  // bodyBoolean);

  //   if (!updateSecondaryPropertyResponse1.equals("Updated")) {
  //     fail(
  //         "Could not update attachment DocumentInfoRecordBoolean (customProperty6) field.
  // Response: "
  //             + updateSecondaryPropertyResponse1);
  //   }

  //   // customProperty2 : Set to 99999
  //   Integer customProperty2Value = 99999;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           MediaType.parse("application/json"),
  //           ByteString.encodeUtf8("{\n \"customProperty2\" : " + customProperty2Value + "\n}"));
  //   String updateSecondaryPropertyResponse2 =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId, bodyInt);

  //   if (!updateSecondaryPropertyResponse2.equals("Updated")) {
  //     fail(
  //         "Could not update attachment customProperty2 field. Response: "
  //             + updateSecondaryPropertyResponse2);
  //   }

  //   // Save source entity
  //   String saveSourceResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity. Response: " + saveSourceResponse);
  //   }

  //   // Fetch attachment metadata to get objectId and verify notes and secondary properties
  //   Map<String, Object> sourceAttachmentMetadata =
  //       api.fetchMetadata(
  //           appUrl, entityName, facetName, copyCustomSourceEntity, sourceAttachmentId);

  //   if (!sourceAttachmentMetadata.containsKey("objectId")) {
  //     fail("Source attachment metadata does not contain objectId");
  //   }

  //   String sourceObjectId = sourceAttachmentMetadata.get("objectId").toString();
  //   if (sourceObjectIds.size() < 3) {
  //     sourceObjectIds.add(sourceObjectId);
  //   } else {
  //     sourceObjectIds.set(2, sourceObjectId);
  //   }

  //   String sourceNoteValue =
  //       sourceAttachmentMetadata.get("note") != null
  //           ? sourceAttachmentMetadata.get("note").toString()
  //           : null;

  //   if (!notesValue.equals(sourceNoteValue)) {
  //     fail(
  //         "Notes field was not properly set in source attachment. Expected: "
  //             + notesValue
  //             + ", Got: "
  //             + sourceNoteValue);
  //   }

  //   Boolean sourceCustomProperty6 =
  //       sourceAttachmentMetadata.get("customProperty6") != null
  //           ? (Boolean) sourceAttachmentMetadata.get("customProperty6")
  //           : null;
  //   Integer sourceCustomProperty2 =
  //       sourceAttachmentMetadata.get("customProperty2") != null
  //           ? (Integer) sourceAttachmentMetadata.get("customProperty2")
  //           : null;

  //   if (sourceCustomProperty6 == null || !sourceCustomProperty6) {
  //     fail(
  //         "DocumentInfoRecordBoolean was not properly set in source attachment. Expected: true,
  // Got: "
  //             + sourceCustomProperty6);
  //   }

  //   if (!customProperty2Value.equals(sourceCustomProperty2)) {
  //     fail(
  //         "customProperty2 was not properly set in source attachment. Expected: "
  //             + customProperty2Value
  //             + ", Got: "
  //             + sourceCustomProperty2);
  //   }

  //   String editTargetResponse =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyCustomTargetEntity);
  //   if (!editTargetResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit target entity");
  //   }

  //   // Copy attachment to target entity
  //   List<String> objectIdsToCopy = new ArrayList<>();
  //   objectIdsToCopy.add(sourceObjectIds.get(2)); // Use objectId from array

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyCustomTargetEntity,
  // objectIdsToCopy);

  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy attachment to target entity: " + copyResponse);
  //   }

  //   // Wait for all uploads to complete before saving
  //   if (!waitForAllUploadsCompletion(copyCustomTargetEntity, 60)) {
  //     fail("Upload did not complete in time after copying attachment");
  //   }

  //   // Save target entity
  //   String saveTargetResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyCustomTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity");
  //   }

  //   // Fetch target entity attachments metadata
  //   List<Map<String, Object>> targetAttachmentsMetadata =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyCustomTargetEntity);

  //   if (targetAttachmentsMetadata.isEmpty()) {
  //     fail("No attachments found in target entity");
  //   }

  //   // Verify the copied attachment has the same notes and secondary properties
  //   // Find the attachment we just copied by matching the filename
  //   Map<String, Object> copiedAttachmentMetadata =
  //       targetAttachmentsMetadata.stream()
  //           .filter(attachment -> "sample2.pdf".equals(attachment.get("fileName")))
  //           .findFirst()
  //           .orElse(null);

  //   if (copiedAttachmentMetadata == null) {
  //     fail("Could not find the copied attachment with fil in target entity");
  //   }

  //   // Verify notes field was copied
  //   String copiedNoteValue =
  //       copiedAttachmentMetadata.get("note") != null
  //           ? copiedAttachmentMetadata.get("note").toString()
  //           : null;

  //   if (!notesValue.equals(copiedNoteValue)) {
  //     fail(
  //         "Notes field was not properly copied. Expected: "
  //             + notesValue
  //             + ", Got: "
  //             + copiedNoteValue);
  //   }

  //   // Verify secondary properties were copied
  //   Boolean copiedCustomProperty6 =
  //       copiedAttachmentMetadata.get("customProperty6") != null
  //           ? (Boolean) copiedAttachmentMetadata.get("customProperty6")
  //           : null;
  //   Integer copiedCustomProperty2 =
  //       copiedAttachmentMetadata.get("customProperty2") != null
  //           ? (Integer) copiedAttachmentMetadata.get("customProperty2")
  //           : null;

  //   // Verify DocumentInfoRecordBoolean
  //   if (copiedCustomProperty6 == null || !copiedCustomProperty6) {
  //     fail(
  //         "DocumentInfoRecordBoolean was not properly copied. Expected: true, Got: "
  //             + copiedCustomProperty6);
  //   }

  //   // Verify customProperty2
  //   if (!customProperty2Value.equals(copiedCustomProperty2)) {
  //     fail(
  //         "customProperty2 was not properly copied. Expected: "
  //             + customProperty2Value
  //             + ", Got: "
  //             + copiedCustomProperty2);
  //   }

  //   // Verify attachment content can be read from target entity
  //   String targetAttachmentId = (String) copiedAttachmentMetadata.get("ID");
  //   String readResponse =
  //       api.readAttachment(
  //           appUrl, entityName, facetName, copyCustomTargetEntity, targetAttachmentId);

  //   if (readResponse.equals("OK")) {
  //     testStatus = true;
  //   }
  //   if (!testStatus) {
  //     fail(
  //         "Could not verify that notes field and all secondary properties were copied from source
  // to target attachment");
  //   }
  //   api.deleteEntity(appUrl, entityName, copyCustomSourceEntity);
  //   api.deleteEntity(appUrl, entityName, copyCustomTargetEntity);
  // }

  // @Test
  // @Order(40)
  // void testCopyAttachmentsSuccessExistingEntity() throws IOException {
  //   System.out.println("Test (40): Copy attachments from one entity to another existing entity");
  //   List<String> attachments = new ArrayList<>();
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   File file1 = new File(classLoader.getResource("sample.pdf").getFile());
  //   File file2 = new File(classLoader.getResource("sample1.pdf").getFile());
  //   File tempFile1 = new File(System.getProperty("java.io.tmpdir"),
  // "sample_copy_existing_1.pdf");
  //   Files.copy(file1.toPath(), tempFile1.toPath(), StandardCopyOption.REPLACE_EXISTING);
  //   File tempFile2 = new File(System.getProperty("java.io.tmpdir"),
  // "sample_copy_existing_2.pdf");
  //   Files.copy(file2.toPath(), tempFile2.toPath(), StandardCopyOption.REPLACE_EXISTING);
  //   files.add(tempFile1);
  //   files.add(tempFile2);
  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", entityID7);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");
  //   String editResponse1 =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //   String editResponse2 =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //   if (editResponse1.equals("Entity in draft mode")
  //       && editResponse2.equals("Entity in draft mode")) {
  //     for (File file : files) {
  //       List<String> createResponse =
  //           api.createAttachment(
  //               appUrl, entityName, facetName, copyAttachmentSourceEntity, srvpath, postData,
  // file);
  //       if (createResponse.get(0).equals("Attachment created")) {
  //         attachments.add(createResponse.get(1));
  //       } else {
  //         fail("Could not create attachment");
  //       }
  //     }
  //     api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //     List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
  //     Map<String, Object> fetchAttachmentMetadataResponse;
  //     for (String attachment : attachments) {
  //       try {
  //         fetchAttachmentMetadataResponse =
  //             api.fetchMetadata(
  //                 appUrl, entityName, facetName, copyAttachmentSourceEntity, attachment);
  //         attachmentsMetadata.add(fetchAttachmentMetadataResponse);
  //       } catch (IOException e) {
  //         fail("Could not fetch attachment metadata: " + e.getMessage());
  //       }
  //     }

  //     sourceObjectIds.clear();
  //     for (Map<String, Object> metadata : attachmentsMetadata) {
  //       if (metadata.containsKey("objectId")) {
  //         sourceObjectIds.add(metadata.get("objectId").toString());
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     }

  //     if (sourceObjectIds.size() == 2) {
  //       String copyResponse;
  //       copyResponse =
  //           api.copyAttachment(
  //               appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
  //       if (copyResponse.equals("Attachments copied successfully")) {
  //         // Wait for all uploads to complete before saving
  //         if (!waitForAllUploadsCompletion(copyAttachmentTargetEntity, 60)) {
  //           fail("Upload did not complete in time after copying attachments");
  //         }
  //         String saveEntityResponse =
  //             api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //         if (saveEntityResponse.equals("Saved")) {
  //           List<Map<String, Object>> fetchEntityMetadataResponse;
  //           fetchEntityMetadataResponse =
  //               api.fetchEntityMetadata(appUrl, entityName, facetName,
  // copyAttachmentTargetEntity);
  //           targetAttachmentIds =
  //               fetchEntityMetadataResponse.stream()
  //                   .map(item -> (String) item.get("ID"))
  //                   .filter(Objects::nonNull)
  //                   .collect(Collectors.toList());
  //           String readResponse;
  //           if (targetAttachmentIds.size() == 4) {
  //             for (String targetAttachmentId : targetAttachmentIds) {
  //               readResponse =
  //                   api.readAttachment(
  //                       appUrl,
  //                       entityName,
  //                       facetName,
  //                       copyAttachmentTargetEntity,
  //                       targetAttachmentId);
  //               if (!readResponse.equals("OK")) {
  //                 fail("Could not read copied attachment");
  //               }
  //             }
  //           }
  //           // api.deleteEntity(appUrl, entityName, copyAttachmentSourceEntity);
  //           // api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
  //         } else {
  //           fail("Could not save entity after copying attachments: " + saveEntityResponse);
  //         }
  //       } else {
  //         fail("Could not copy attachments: " + copyResponse);
  //       }
  //     } else {
  //       fail("Could not fetch objects Ids for all attachments");
  //     }
  //   } else {
  //     fail("Could not edit entities");
  //   }
  // }

  // @Test
  // @Order(41)
  // void testCopyAttachmentsUnsuccessfulExistingEntity() throws IOException {
  //   System.out.println(
  //       "Test (41): Copy attachments from one entity to another existing entity - unsuccessful");
  //   String editResponse1 =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //   String editResponse2 =
  //       api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //   if (editResponse1.equals("Entity in draft mode")
  //       && editResponse2.equals("Entity in draft mode")) {
  //     sourceObjectIds.add("incorrectObjectId");
  //     if (sourceObjectIds.size() == 3) {
  //       try {
  //         api.copyAttachment(
  //             appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
  //         fail("Copy attachments did not throw an error");
  //       } catch (IOException e) {
  //         api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
  //         api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //         api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
  //         api.deleteEntity(appUrl, entityName, copyAttachmentSourceEntity);
  //       }
  //     } else {
  //       fail("Could not fetch objects Ids for all attachments");
  //     }
  //   } else {
  //     fail("Could not edit entities");
  //   }
  // }

  // @Test
  // @Order(42)
  // void testCreateLinkSuccess() throws IOException {
  //   System.out.println("Test (42): Create link in entity");
  //   List<String> attachments = new ArrayList<>();
  //   createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!createLinkEntity.equals("Could not create entity")) {
  //     String linkName = "sample";
  //     String linkUrl = "https://www.example.com";
  //     String createLinkResponse1 =
  //         api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //     String createLinkResponse2 =
  //         api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName + "1",
  // linkUrl);
  //     if (createLinkResponse1.equals("Link created successfully")
  //         && createLinkResponse2.equals("Link created successfully")) {
  //       String saveEntityResponse =
  //           api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //       if (saveEntityResponse.equals("Saved")) {
  //         attachments =
  //             api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //                 .map(item -> (String) item.get("ID"))
  //                 .filter(Objects::nonNull)
  //                 .collect(Collectors.toList());
  //         String openAttachmentResponse;
  //         for (String attachment : attachments) {
  //           openAttachmentResponse =
  //               api.openAttachment(appUrl, entityName, facetName, createLinkEntity, attachment);
  //           System.out.println("openAttachmentResponse: " + openAttachmentResponse);
  //           if (!openAttachmentResponse.equals("Attachment opened successfully")) {
  //             fail("Could not open created link");
  //           }
  //         }
  //       } else {
  //         fail("Could not save entity");
  //       }
  //     } else {
  //       fail("Could not create link");
  //     }
  //   } else {
  //     fail("Could not create entity");
  //   }
  // }

  // @Test
  // @Order(43)
  // void testCreateLinkDifferentEntity() throws IOException {
  //   System.out.println("Test (43): Create link with same name in different entity");
  //   String createLinkDifferentEntity =
  //       api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!createLinkDifferentEntity.equals("Could not edit entity")) {
  //     String linkName = "sample";
  //     String linkUrl = "https://example.com";
  //     String createResponse =
  //         api.createLink(
  //             appUrl, entityName, facetName, createLinkDifferentEntity, linkName, linkUrl);
  //     if (!createResponse.equals("Link created successfully")) {
  //       fail("Could not create link in different entity with same name");
  //     }
  //     String response = api.saveEntityDraft(appUrl, entityName, srvpath,
  // createLinkDifferentEntity);
  //     if (!response.equals("Saved")) {
  //       fail("Could not save entity");
  //     }
  //     response = api.deleteEntity(appUrl, entityName, createLinkDifferentEntity);
  //     if (!response.equals("Entity Deleted")) {
  //       fail("Could not delete entity");
  //     }
  //   } else {
  //     fail("Could not edit entity");
  //   }
  // }

  // @Test
  // @Order(44)
  // void testCreateLinkFailure() throws IOException {
  //   System.out.println("Test (44): Create link fails due to invalid URL and name");
  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!editEntityResponse.equals("Could not edit entity")) {
  //     String linkName = "sample";
  //     String linkUrl = "example.com";
  //     try {
  //       api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //       fail("Create link did not throw an error for invalid url");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       assertEquals("400018", errorCode);
  //       assertTrue(
  //           errorMessage.equals("Enter a value that is within the expected pattern.")
  //               || errorMessage.equals("Enter a value that matches the expected pattern."),
  //           "Unexpected error message: " + errorMessage);
  //     }
  //     try {
  //       api.createLink(
  //           appUrl, entityName, facetName, createLinkEntity, linkName + "//", "https://" +
  // linkUrl);
  //       fail("Create link did not throw an error for invalid name");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       String expected =
  //           "\"sample//\" contains unsupported characters (‘/’ or ‘\\’). Rename and try again.";
  //       assertEquals("500", errorCode);
  //       assertEquals(
  //           expected.replaceAll("\\s+", " ").trim(), errorMessage.replaceAll("\\s+", "
  // ").trim());
  //     }
  //     try {
  //       api.createLink(appUrl, entityName, facetName, createLinkEntity, "", "");
  //       fail("Create link did not throw an error for empty name and url");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       String expected = "Provide the missing value.";
  //       assertEquals("409008", errorCode);
  //       assertEquals(expected, errorMessage);
  //     }
  //     try {
  //       api.createLink(
  //           appUrl, entityName, facetName, createLinkEntity, linkName, "https://" + linkUrl);
  //       fail("Create link did not throw an error for duplicate name");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       assertEquals("500", errorCode);
  //       assertEquals(
  //           "An object named \"sample\" already exists. Rename the object and try again.",
  //           errorMessage);
  //     }
  //     try {
  //       for (int i = 2; i < 5; i++) {
  //         api.createLink(
  //             appUrl, entityName, facetName, createLinkEntity, linkName + i, "https://" +
  // linkUrl);
  //       }
  //       fail("More than 5 links were created in the same entity");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       assertEquals("500", errorCode);
  //       assertEquals("Cannot upload more than 4 attachments.", errorMessage);
  //     }
  //     String response = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //     if (!response.equals("Saved")) {
  //       fail("Could not save entity");
  //     }
  //     response = api.deleteEntity(appUrl, entityName, createLinkEntity);
  //     if (!response.equals("Entity Deleted")) {
  //       fail("Could not delete entity");
  //     }
  //   } else {
  //     fail("Could not edit entity");
  //   }
  // }

  // @Test
  // @Order(45)
  // void testCreateLinkNoSDMRoles() throws IOException {
  //   System.out.println("Test (45): Create link fails due to no SDM roles assigned");
  //   String createLinkEntityNoSDMRoles =
  //       apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!createLinkEntityNoSDMRoles.equals("Could not edit entity")) {
  //     String linkName = "sample27";
  //     String linkUrl = "https://example.com";
  //     try {
  //       apiNoRoles.createLink(
  //           appUrl, entityName, facetName, createLinkEntityNoSDMRoles, linkName, linkUrl);
  //       fail("Link got created without SDM roles");
  //     } catch (IOException e) {
  //       String message = e.getMessage();
  //       int jsonStart = message.indexOf("{");
  //       String jsonPart = message.substring(jsonStart);
  //       JSONObject json = new JSONObject(jsonPart);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       assertEquals("500", errorCode);
  //       assertEquals(
  //           "You do not have the required permissions to upload attachments. Please contact your
  // administrator for access.",
  //           errorMessage);
  //     }
  //     String response =
  //         apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntityNoSDMRoles);
  //     if (!response.equals("Saved")) {
  //       fail("Could not save entity");
  //     }
  //     response = api.deleteEntity(appUrl, entityName, createLinkEntityNoSDMRoles);
  //     if (!response.equals("Entity Deleted")) {
  //       fail("Could not delete entity");
  //     }
  //   } else {
  //     fail("Could not edit entity");
  //   }
  // }

  // @Test
  // @Order(46)
  // void testDeleteLink() throws IOException {
  //   System.out.println("Test (46): Delete link in entity");
  //   List<String> attachments = new ArrayList<>();
  //   String createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!createLinkEntity.equals("Could not create entity")) {
  //     String linkName = "sample";
  //     String linkUrl = "https://www.example.com";
  //     String createLinkResponse =
  //         api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //     if (createLinkResponse.equals("Link created successfully")) {
  //       String saveEntityResponse =
  //           api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //       if (saveEntityResponse.equals("Saved")) {
  //         attachments =
  //             api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //                 .map(item -> (String) item.get("ID"))
  //                 .filter(Objects::nonNull)
  //                 .collect(Collectors.toList());
  //         String editEntityResponse =
  //             api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //         if (!editEntityResponse.equals("Entity in draft mode")) {
  //           fail("Could not edit entity");
  //         }
  //         String deleteLinkResponse =
  //             api.deleteAttachment(
  //                 appUrl, entityName, facetName, createLinkEntity, attachments.get(0));
  //         if (!deleteLinkResponse.equals("Deleted")) {
  //           fail("Could not delete created link");
  //         } else {
  //           saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //           if (!saveEntityResponse.equals("Saved")) {
  //             fail("Could not save entity");
  //           }
  //           attachments =
  //               api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //                   .map(item -> (String) item.get("ID"))
  //                   .filter(Objects::nonNull)
  //                   .collect(Collectors.toList());
  //           if (attachments.size() != 0) {
  //             fail("Link wasn't deleted");
  //           }
  //           String response = api.deleteEntity(appUrl, entityName, createLinkEntity);
  //           if (!response.equals("Entity Deleted")) {
  //             fail("Could not delete entity");
  //           }
  //         }
  //       } else {
  //         fail("Could not save entity");
  //       }
  //     } else {
  //       fail("Could not create link");
  //     }
  //   } else {
  //     fail("Could not create entity");
  //   }
  // }

  // @Test
  // @Order(47)
  // void testRenameLinkSuccess() throws IOException {
  //   System.out.println("Test (47): Rename link in entity");
  //   List<String> attachments = new ArrayList<>();

  //   createLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (createLinkEntity.equals("Could not create entity")) {
  //     fail("Could not create entity");
  //   }

  //   String linkName = "sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link");
  //   }

  //   String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }

  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }

  //   attachmentID9 = attachments.get(0);
  //   String renameLinkResponse =
  //       api.renameAttachment(
  //           appUrl, entityName, facetName, createLinkEntity, attachments.get(0),
  // "sampleRenamed");
  //   if (!renameLinkResponse.equals("Renamed")) fail("Could not Renamed created link");

  //   saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }
  // }

  // @Test
  // @Order(48)
  // void testRenameLinkDuplicate() throws IOException {
  //   System.out.println("Test (48): Rename link in entity fails due to duplicate error");
  //   List<String> attachments = new ArrayList<>();

  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }

  //   String linkName = "sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link");
  //   }

  //   String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }

  //   editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }

  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //           .filter(item -> !attachmentID9.equals(item.get("ID"))) // skip unwanted filename
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());
  //   attachmentID10 = attachments.get(0);
  //   api.renameAttachment(
  //       appUrl, entityName, facetName, createLinkEntity, attachments.get(0), "sampleRenamed");

  //   String saveError =
  //       saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //   String expectedWarning =
  //       "{\"error\":{\"code\":\"400\",\"message\":\"An object named \\\"sampleRenamed\\\" already
  // exists. Rename the object and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //   ObjectMapper mapper = new ObjectMapper();
  //   assertEquals(mapper.readTree(expectedWarning), mapper.readTree(saveError));

  //   String deleteEntityResponse = api.deleteEntityDraft(appUrl, entityName, createLinkEntity);
  //   if (!deleteEntityResponse.equals("Entity Draft Deleted")) {
  //     fail("Entity draft not deleted");
  //   }
  // }

  // @Test
  // @Order(49)
  // void testRenameLinkUnsupportedCharacters() throws IOException {
  //   System.out.println(
  //       "Test (49): Rename link in entity fails due to unsupported characters in name");
  //   List<String> attachments = new ArrayList<>();

  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }

  //   String linkName = "sample2";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, createLinkEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link");
  //   }

  //   String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // createLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }

  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, createLinkEntity).stream()
  //           // .filter(item -> "sample2".equals(item.get("filename"))) // skip unwanted filename
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());
  //   System.out.println("attachments: " + attachments);

  //   editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }

  //   api.renameAttachment(
  //       appUrl, entityName, facetName, createLinkEntity, attachments.get(0), "sampleRenamed//");
  //   String warning =
  //       saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, createLinkEntity);
  //   String expectedWarning =
  //       "{\"error\":{\"code\":\"400\",\"message\":\"\\\"sampleRenamed//\\\" contains unsupported
  // characters (‘/’ or ‘\\\\’). Rename and try again.\\n\\nTable: attachments\\nPage:
  // IntegrationTestEntity\"}}";
  //   ObjectMapper mapper = new ObjectMapper();
  //   assertEquals(mapper.readTree(expectedWarning), mapper.readTree(warning));

  //   String deleteEntityResponse = api.deleteEntity(appUrl, entityName, createLinkEntity);
  //   if (!deleteEntityResponse.equals("Entity Deleted")) {
  //     fail("Entity draft not deleted");
  //   }
  // }

  // @Test
  // @Order(50)
  // void testEditLinkSuccess() throws IOException {
  //   System.out.println("Test (50): Edit existing link in entity");

  //   List<String> attachments = new ArrayList<>();
  //   editLinkEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (editLinkEntity.equals("Could not create entity")) {
  //     fail("Could not create entity");
  //   }
  //   String linkName = "sample";
  //   String linkUrl = "https://www.example.com";

  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, editLinkEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link");
  //   }

  //   String saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }
  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }
  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (attachments.isEmpty()) {
  //     fail("Could not edit link");
  //   }
  //   String linkId = attachments.get(0);
  //   String updatedUrl = "https://editedexample.com";
  //   String editLinkResponse =
  //       api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
  //   if (!editLinkResponse.equals("Link edited successfully")) {
  //     fail("Could not edit link");
  //   }
  //   saveEntityResponse = api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!saveEntityResponse.equals("Saved")) {
  //     fail("Could not save entity");
  //   }
  //   String openAttachmentResponse;
  //   for (String attachment : attachments) {
  //     openAttachmentResponse =
  //         api.openAttachment(appUrl, entityName, facetName, editLinkEntity, attachment);
  //     if (!openAttachmentResponse.equals("Attachment opened successfully")) {
  //       fail("Could not open created link");
  //     }
  //   }
  // }

  // @Test
  // @Order(51)
  // void testEditLinkFailureInvalidURL() throws IOException {
  //   System.out.println("Test (51): Edit existing link with invalid url");
  //   Boolean testStatus = false;
  //   List<String> attachments = new ArrayList<>();

  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }
  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (attachments.isEmpty()) {
  //     fail("Could not edit link");
  //   }
  //   String linkId = attachments.get(0);
  //   String updatedUrl = "https://editedexample";
  //   try {

  //     api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
  //     fail("Create link did not throw an error for invalid url");
  //   } catch (IOException e) {
  //     String message = e.getMessage();
  //     int jsonStart = message.indexOf("{");
  //     String jsonPart = message.substring(jsonStart);
  //     JSONObject json = new JSONObject(jsonPart);
  //     String errorCode = json.getJSONObject("error").getString("code");
  //     String errorMessage = json.getJSONObject("error").getString("message");
  //     assertEquals("400018", errorCode);
  //     assertTrue(
  //         errorMessage.equals("Enter a value that is within the expected pattern.")
  //             || errorMessage.equals("Enter a value that matches the expected pattern."),
  //         "Unexpected error message: " + errorMessage);

  //     testStatus = true;
  //   }
  //   api.saveEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!testStatus) {
  //     fail("Could not edit link with an invalid URL");
  //   }
  // }

  // @Test
  // @Order(52)
  // void testEditLinkFailureEmptyURL() throws IOException {
  //   System.out.println("Test (52): Edit existing link with an empty url");
  //   Boolean testStatus = false;
  //   List<String> attachments = new ArrayList<>();

  //   String editEntityResponse = api.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }
  //   attachments =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (attachments.isEmpty()) {
  //     fail("Could not edit link");
  //   }
  //   String linkId = attachments.get(0);
  //   String updatedUrl = "";
  //   try {
  //     api.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
  //     fail("edit link did not throw an error for empty url");
  //   } catch (IOException e) {
  //     String message = e.getMessage();
  //     int jsonStart = message.indexOf("{");
  //     String jsonPart = message.substring(jsonStart);
  //     JSONObject json = new JSONObject(jsonPart);
  //     String errorCode = json.getJSONObject("error").getString("code");
  //     String errorMessage = json.getJSONObject("error").getString("message");
  //     String expected = "Provide the missing value.";
  //     assertEquals("409008", errorCode);
  //     assertEquals(expected, errorMessage);
  //     testStatus = true;
  //   }
  //   api.deleteEntityDraft(appUrl, entityName, editLinkEntity);
  //   if (!testStatus) {
  //     fail("Could not edit link with an empty URL");
  //   }
  // }

  // @Test
  // @Order(53)
  // void testEditLinkNoSDMRoles() throws IOException {
  //   System.out.println("Test (53): Edit link fails due to no SDM roles assigned");

  //   Boolean testStatus = false;
  //   List<String> attachments = new ArrayList<>();

  //   String editEntityResponse =
  //       apiNoRoles.editEntityDraft(appUrl, entityName, srvpath, editLinkEntity);
  //   if (!editEntityResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit entity");
  //   }
  //   attachments =
  //       apiNoRoles.fetchEntityMetadata(appUrl, entityName, facetName, editLinkEntity).stream()
  //           .map(item -> (String) item.get("ID"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (attachments.isEmpty()) {
  //     fail("Could not edit link");
  //   }
  //   String linkId = attachments.get(0);
  //   String updatedUrl = "https://www.example1.com";
  //   try {
  //     apiNoRoles.editLink(appUrl, entityName, facetName, editLinkEntity, linkId, updatedUrl);
  //     fail("Link got edited without SDM roles in facet: \" + facetName");
  //   } catch (IOException e) {
  //     String message = e.getMessage();
  //     int jsonStart = message.indexOf("{");
  //     String jsonPart = message.substring(jsonStart);
  //     JSONObject json = new JSONObject(jsonPart);
  //     String errorCode = json.getJSONObject("error").getString("code");
  //     String errorMessage = json.getJSONObject("error").getString("message");
  //     assertEquals("500", errorCode);
  //     assertEquals(
  //         "You do not have the required permissions to update attachments. Kindly contact the
  // admin",
  //         errorMessage);
  //     testStatus = true;
  //   }
  //   apiNoRoles.deleteEntity(appUrl, entityName, createLinkEntity);
  //   if (!testStatus) {
  //     fail("Link got edited without SDM roles");
  //   }
  //   api.deleteEntity(appUrl, entityName, editLinkEntity);
  // }

  // @Test
  // @Order(54)
  // void testCopyLinkSuccessNewEntity() throws IOException {
  //   System.out.println("Test (54): Copy link from one entity to another new entity");
  //   List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();

  //   copyLinkSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   copyLinkTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

  //   if (copyLinkSourceEntity.equals("Could not create entity")
  //       || copyLinkTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create source or target entity");
  //   }

  //   String linkName = "sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, copyLinkSourceEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link in source entity");
  //   }

  //   api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkSourceEntity);

  //   List<String> sourceObjectIds =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyLinkSourceEntity).stream()
  //           .map(item -> (String) item.get("objectId"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (sourceObjectIds.isEmpty()) {
  //     fail("Could not fetch object Id for link");
  //   }

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, sourceObjectIds);
  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy link: " + copyResponse);
  //   }

  //   // Wait for all uploads to complete before saving
  //   if (!waitForAllUploadsCompletion(copyLinkTargetEntity, 60)) {
  //     fail("Upload did not complete in time after copying link");
  //   }

  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!saveResponse.equals("Saved")) {
  //     fail("Could not save target entity after copying link");
  //   }

  //   attachmentsMetadata =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyLinkTargetEntity);
  //   Map<String, Object> copiedAttachment = attachmentsMetadata.get(0);
  //   String receivedType = (String) copiedAttachment.get("type");
  //   String receivedUrl = (String) copiedAttachment.get("linkUrl");

  //   String expectedType = "sap-icon://internet-browser";
  //   assertTrue(
  //       expectedType.equalsIgnoreCase(receivedType),
  //       "Attachment type mismatch. Expected '"
  //           + expectedType
  //           + "' but got '"
  //           + receivedType
  //           + "'.");

  //   assertEquals(linkUrl, receivedUrl, "Attachment URL mismatch.");

  //   System.out.println("Attachment type and URL validated successfully.");

  //   String attachmentId = (String) copiedAttachment.get("ID");
  //   String openAttachmentResponse =
  //       api.openAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, attachmentId);
  //   if (!openAttachmentResponse.equals("Attachment opened successfully")) {
  //     fail("Could not open the attachment");
  //   }

  //   String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copyLinkSourceEntity);
  //   String deleteTargetResponse = api.deleteEntity(appUrl, entityName, copyLinkTargetEntity);
  //   if (!deleteSourceResponse.equals("Entity Deleted")
  //       || !deleteTargetResponse.equals("Entity Deleted")) {
  //     fail("could not delete source or target entity");
  //   }
  // }

  // @Test
  // @Order(55)
  // void testCopyLinkUnsuccessfulNewEntity() throws IOException {
  //   System.out.println(
  //       "Test (55): Copy invalid type of link from one entity to another new entity");

  //   copyLinkSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   copyLinkTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

  //   if (copyLinkSourceEntity.equals("Could not create entity")
  //       || copyLinkTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create source or target entity");
  //   }

  //   api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkSourceEntity);
  //   List<String> invalidObjectIds = Collections.singletonList("incorrectObjectId");

  //   try {
  //     api.copyAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, invalidObjectIds);
  //     fail("Copy attachments did not throw error for invalid ID");
  //   } catch (IOException e) {
  //     System.out.println("Caught expected error: " + e.getMessage());
  //   }

  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!saveResponse.equals("Saved")) {
  //     fail("Could not save target entity after unsuccessful copy");
  //   }

  //   String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copyLinkSourceEntity);
  //   if (!deleteSourceResponse.equals("Entity Deleted")) {
  //     fail("Could not delete source entity");
  //   }
  // }

  // @Test
  // @Order(56)
  // void testCopyLinkFromNewEntityToExistingEntity() throws IOException {
  //   System.out.println("Test (56): Copy link from a new entity to an existing target entity");
  //   List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();

  //   copyLinkSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (copyLinkSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create new source entity");
  //   }

  //   String linkName = "Sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, copyLinkSourceEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link in new source entity");
  //   }

  //   String saveSourceResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save new source entity");
  //   }

  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!editResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit target entity draft");
  //   }

  //   List<String> sourceObjectIds =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyLinkSourceEntity).stream()
  //           .map(item -> (String) item.get("objectId"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (sourceObjectIds.isEmpty()) {
  //     fail("Could not fetch objectId from new source entity");
  //   }

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, sourceObjectIds);
  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy link from new source entity to existing target entity: " +
  // copyResponse);
  //   }

  //   // Wait for all uploads to complete before saving
  //   if (!waitForAllUploadsCompletion(copyLinkTargetEntity, 60)) {
  //     fail("Upload did not complete in time after copying link");
  //   }

  //   String saveTargetResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);

  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity after copying link");
  //   }

  //   attachmentsMetadata =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, copyLinkTargetEntity);
  //   Map<String, Object> copiedAttachment = attachmentsMetadata.get(0);
  //   String receivedType = (String) copiedAttachment.get("type");
  //   String receivedUrl = (String) copiedAttachment.get("linkUrl");

  //   String expectedType = "sap-icon://internet-browser";
  //   assertTrue(
  //       expectedType.equalsIgnoreCase(receivedType),
  //       "Attachment type mismatch. Expected '"
  //           + expectedType
  //           + "' but got '"
  //           + receivedType
  //           + "'.");

  //   assertEquals(linkUrl, receivedUrl, "Attachment URL mismatch.");

  //   System.out.println("Attachment type and URL validated successfully.");

  //   String attachmentId = (String) copiedAttachment.get("ID");
  //   assertNotNull(attachmentId, "Could not find 'ID' in the copied attachment metadata.");

  //   String openAttachmentResponse =
  //       api.openAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, attachmentId);
  //   if (!openAttachmentResponse.equals("Attachment opened successfully")) {
  //     fail("Could not open the attachment");
  //   }

  //   String deleteResponse = api.deleteEntity(appUrl, entityName, copyLinkSourceEntity);
  //   if (!deleteResponse.equals("Entity Deleted")) {
  //     fail("Could not delete new source entity");
  //   }
  // }

  // @Test
  // @Order(57)
  // void testCopyInvalidLinkFromNewEntityToExistingEntity() throws IOException {
  //   System.out.println(
  //       "Test (57): Copy invalid type of link from new entity to existing target entity");

  //   copyLinkSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (copyLinkSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create new source entity");
  //   }

  //   String linkName = "Sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, copyLinkSourceEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link in new source entity");
  //   }

  //   String saveSourceResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save new source entity");
  //   }

  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!editResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit target entity draft");
  //   }

  //   List<String> invalidObjectIds = Collections.singletonList("invalidObjectId123");

  //   try {
  //     api.copyAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, invalidObjectIds);
  //     fail("Copy did not throw error for invalid link ID");
  //   } catch (IOException e) {
  //     System.out.println("Caught expected error while copying invalid link: " + e.getMessage());
  //   }

  //   // No need to wait for upload completion as copy failed, but ensure clean state
  //   String saveTargetResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity after unsuccessful copy");
  //   }

  //   String deleteSourceResponse = api.deleteEntity(appUrl, entityName, copyLinkSourceEntity);
  //   String deleteTargetResponse = api.deleteEntity(appUrl, entityName, copyLinkTargetEntity);
  //   if (!deleteSourceResponse.equals("Entity Deleted")
  //       || !deleteTargetResponse.equals("Entity Deleted")) {
  //     fail("Could not delete new source entity or target entity");
  //   }
  // }

  // @Test
  // @Order(58)
  // void testCopyLinkSuccessNewEntityDraft() throws IOException {
  //   System.out.println("Test (58): Copy link from one entity to another new entity draft mode");

  //   copyLinkSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   copyLinkTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

  //   if (copyLinkSourceEntity.equals("Could not create entity")
  //       || copyLinkTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create source or target entity");
  //   }

  //   String linkName = "sample";
  //   String linkUrl = "https://www.example.com";
  //   String createLinkResponse =
  //       api.createLink(appUrl, entityName, facetName, copyLinkSourceEntity, linkName, linkUrl);
  //   if (!createLinkResponse.equals("Link created successfully")) {
  //     fail("Could not create link in source entity");
  //   }

  //   List<String> sourceObjectIds =
  //       api.fetchEntityMetadataDraft(appUrl, entityName, facetName,
  // copyLinkSourceEntity).stream()
  //           .map(item -> (String) item.get("objectId"))
  //           .filter(Objects::nonNull)
  //           .collect(Collectors.toList());

  //   if (sourceObjectIds.isEmpty()) {
  //     fail("Could not fetch object Id for link");
  //   }

  //   String copyResponse =
  //       api.copyAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, sourceObjectIds);
  //   if (!copyResponse.equals("Attachments copied successfully")) {
  //     fail("Could not copy link: " + copyResponse);
  //   }

  //   List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
  //   attachmentsMetadata =
  //       api.fetchEntityMetadataDraft(appUrl, entityName, facetName, copyLinkTargetEntity);
  //   Map<String, Object> copiedAttachment = attachmentsMetadata.get(0);
  //   String receivedType = (String) copiedAttachment.get("type");
  //   String receivedUrl = (String) copiedAttachment.get("linkUrl");

  //   String expectedType = "sap-icon://internet-browser";
  //   assertTrue(
  //       expectedType.equalsIgnoreCase(receivedType),
  //       "Attachment type mismatch. Expected '"
  //           + expectedType
  //           + "' but got '"
  //           + receivedType
  //           + "'.");

  //   assertEquals(linkUrl, receivedUrl, "Attachment URL mismatch.");

  //   System.out.println("Attachment type and URL validated successfully.");

  //   String attachmentId = (String) copiedAttachment.get("ID");
  //   assertNotNull(attachmentId, "Could not find 'ID' in the copied attachment metadata.");

  //   String openAttachmentResponse =
  //       api.openAttachment(appUrl, entityName, facetName, copyLinkTargetEntity, attachmentId);
  //   if (!openAttachmentResponse.equals("Attachment opened successfully")) {
  //     fail("Could not open the attachment");
  //   }

  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, copyLinkTargetEntity);
  //   if (!saveResponse.equals("Saved")) {
  //     fail("Could not save target entity after copying link");
  //   }
  //   api.deleteEntityDraft(appUrl, entityName, copyLinkSourceEntity);
  //   api.deleteEntity(appUrl, entityName, copyLinkTargetEntity);
  // }

  // @Test
  // @Order(59)
  // void testCopyAttachmentsSuccessNewEntityDraft() throws IOException {
  //   System.out.println(
  //       "Test (59): Copy attachments from one entity to another new entity draft mode");
  //   List<String> attachments = new ArrayList<>();
  //   copyAttachmentSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   copyAttachmentTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (!copyAttachmentSourceEntity.equals("Could not create entity")
  //       && !copyAttachmentTargetEntity.equals("Could not create entity")) {
  //     ClassLoader classLoader = getClass().getClassLoader();
  //     List<File> files = new ArrayList<>();
  //     files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //     files.add(new File(classLoader.getResource("sample1.pdf").getFile()));
  //     Map<String, Object> postData = new HashMap<>();
  //     postData.put("up__ID", entityID7);
  //     postData.put("mimeType", "application/pdf");
  //     postData.put("createdAt", new Date().toString());
  //     postData.put("createdBy", "test@test.com");
  //     postData.put("modifiedBy", "test@test.com");

  //     sourceObjectIds.clear();

  //     for (File file : files) {
  //       List<String> createResponse =
  //           api.createAttachment(
  //               appUrl, entityName, facetName, copyAttachmentSourceEntity, srvpath, postData,
  // file);
  //       if (createResponse.get(0).equals("Attachment created")) {
  //         attachments.add(createResponse.get(1));
  //       } else {
  //         fail("Could not create attachment");
  //       }
  //     }

  //     List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
  //     Map<String, Object> fetchAttachmentMetadataResponse;
  //     for (String attachment : attachments) {
  //       try {
  //         fetchAttachmentMetadataResponse =
  //             api.fetchMetadataDraft(
  //                 appUrl, entityName, facetName, copyAttachmentSourceEntity, attachment);
  //         attachmentsMetadata.add(fetchAttachmentMetadataResponse);
  //       } catch (IOException e) {
  //         fail("Could not fetch attachment metadata: " + e.getMessage());
  //       }
  //     }
  //     for (Map<String, Object> metadata : attachmentsMetadata) {
  //       if (metadata.containsKey("objectId")) {
  //         sourceObjectIds.add(metadata.get("objectId").toString());
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     }

  //     if (sourceObjectIds.size() == 2) {
  //       String copyResponse;
  //       copyResponse =
  //           api.copyAttachment(
  //               appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
  //       if (copyResponse.equals("Attachments copied successfully")) {
  //         // Wait for all uploads to complete before saving
  //         if (!waitForAllUploadsCompletion(copyAttachmentTargetEntity, 60)) {
  //           fail("Upload did not complete in time after copying attachments");
  //         }
  //         String saveEntityResponse =
  //             api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
  //         if (saveEntityResponse.equals("Saved")) {
  //           List<Map<String, Object>> fetchEntityMetadataResponse;
  //           fetchEntityMetadataResponse =
  //               api.fetchEntityMetadata(appUrl, entityName, facetName,
  // copyAttachmentTargetEntity);
  //           targetAttachmentIds =
  //               fetchEntityMetadataResponse.stream()
  //                   .map(item -> (String) item.get("ID"))
  //                   .filter(Objects::nonNull)
  //                   .collect(Collectors.toList());
  //           String readResponse;
  //           for (String targetAttachmentId : targetAttachmentIds) {
  //             readResponse =
  //                 api.readAttachment(
  //                     appUrl,
  //                     entityName,
  //                     facetName,
  //                     copyAttachmentTargetEntity,
  //                     targetAttachmentId);
  //             if (!readResponse.equals("OK")) {
  //               fail("Could not read copied attachment");
  //             }
  //           }
  //         } else {
  //           fail("Could not save entity after copying attachments: " + saveEntityResponse);
  //         }
  //       } else {
  //         fail("Could not copy attachments: " + copyResponse);
  //       }
  //     } else {
  //       fail("Could not fetch objects Ids for all attachments");
  //     }
  //   } else {
  //     fail("Could not create entities");
  //   }
  //   api.deleteEntityDraft(appUrl, entityName, copyAttachmentSourceEntity);
  //   api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
  // }

  // @Test
  // @Order(60)
  // void testViewChangelogForNewlyCreatedAttachment() throws IOException {
  //   System.out.println("Test (60): View changelog for newly created attachment");

  //   // Create a new entity for changelog test
  //   changelogEntityID = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   assertNotNull(changelogEntityID, "Failed to create changelog test entity");
  //   assertNotEquals("Could not create entity", changelogEntityID);

  //   // Prepare a sample file to upload
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.txt").getFile());
  //   assertTrue(file.exists(), "Sample file should exist");

  //   // Create attachment
  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", changelogEntityID);
  //   postData.put("mimeType", "text/plain");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, facetName, changelogEntityID, srvpath, postData, file);

  //   assertEquals(2, createResponse.size(), "Should return status and attachment ID");
  //   String status = createResponse.get(0);
  //   changelogAttachmentID = createResponse.get(1);

  //   assertEquals("Attachment created", status, "Attachment should be created successfully");
  //   assertNotNull(changelogAttachmentID, "Attachment ID should not be null");
  //   assertNotEquals("", changelogAttachmentID, "Attachment ID should not be empty");

  //   // Fetch changelog for the newly created attachment
  //   Map<String, Object> changelogResponse =
  //       api.fetchChangelog(appUrl, entityName, facetName, changelogEntityID,
  // changelogAttachmentID);

  //   assertNotNull(changelogResponse, "Changelog response should not be null");

  //   // Verify changelog structure
  //   assertEquals(false, changelogResponse.get("hasMoreItems"), "hasMoreItems should be false");
  //   assertEquals(
  //       "sample.txt", changelogResponse.get("filename"), "Filename should match uploaded file");
  //   assertNotNull(changelogResponse.get("objectId"), "ObjectId should not be null");
  //   assertEquals(1, changelogResponse.get("numItems"), "Should have 1 changelog entry");

  //   // Verify the changelog entry
  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogs =
  //       (List<Map<String, Object>>) changelogResponse.get("changeLogs");
  //   assertEquals(1, changeLogs.size(), "Should have exactly 1 changelog entry");

  //   Map<String, Object> logEntry = changeLogs.get(0);
  //   assertEquals("created", logEntry.get("operation"), "Operation should be 'created'");
  //   assertNotNull(logEntry.get("time"), "Time should not be null");
  //   assertNotNull(logEntry.get("user"), "User should not be null");
  //   assertFalse(
  //       logEntry.containsKey("changeDetail"), "Created operation should not have changeDetail");
  // }

  // @Test
  // @Order(61)
  // void testChangelogAfterModifyingNoteAndCustomProperty() throws IOException {
  //   System.out.println(
  //       "Test (61): Modify note field and custom property, then verify changelog shows created +
  // 3 updated entries");

  //   // Update attachment with notes field (entity is already in draft mode from test 60)
  //   String notesValue = "Test note for changelog verification";
  //   MediaType mediaType = MediaType.parse("application/json");
  //   String jsonPayload = "{\"note\": \"" + notesValue + "\"}";
  //   RequestBody updateNotesBody = RequestBody.create(jsonPayload, mediaType);

  //   String updateNotesResponse =
  //       api.updateSecondaryProperty(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           changelogEntityID,
  //           changelogAttachmentID,
  //           updateNotesBody);
  //   assertEquals("Updated", updateNotesResponse, "Should successfully update notes field");

  //   // Update attachment with custom property
  //   Integer customProperty2Value = 12345;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           "{\"customProperty2\": " + customProperty2Value + "}",
  //           MediaType.parse("application/json"));
  //   String updateCustomPropertyResponse =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, changelogEntityID, changelogAttachmentID, bodyInt);
  //   assertEquals(
  //       "Updated", updateCustomPropertyResponse, "Should successfully update custom property");

  //   // Save the entity
  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, changelogEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully");

  //   // Edit entity again to fetch changelog
  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, changelogEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Fetch changelog after modifications
  //   Map<String, Object> changelogResponse =
  //       api.fetchChangelog(appUrl, entityName, facetName, changelogEntityID,
  // changelogAttachmentID);

  //   assertNotNull(changelogResponse, "Changelog response should not be null");

  //   // Verify changelog content - should have 1 created + 3 updated (note, customProperty2, and
  //   // internal update)
  //   assertEquals(false, changelogResponse.get("hasMoreItems"), "hasMoreItems should be false");
  //   assertEquals(
  //       4,
  //       changelogResponse.get("numItems"),
  //       "Should have 4 changelog entries (1 created + 3 updated)");

  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogs =
  //       (List<Map<String, Object>>) changelogResponse.get("changeLogs");
  //   assertEquals(4, changeLogs.size(), "Should have exactly 4 changelog entries");

  //   // Verify first entry is 'created'
  //   Map<String, Object> createdEntry = changeLogs.get(0);
  //   assertEquals(
  //       "created", createdEntry.get("operation"), "First entry should be 'created' operation");

  //   // Verify remaining entries are 'updated'
  //   long updatedCount =
  //       changeLogs.stream().filter(log -> "updated".equals(log.get("operation"))).count();
  //   assertEquals(3, updatedCount, "Should have 3 'updated' operations");

  //   // Verify that changeDetail exists in updated entries for note field
  //   boolean hasNoteUpdate =
  //       changeLogs.stream()
  //           .filter(log -> "updated".equals(log.get("operation")))
  //           .anyMatch(
  //               log -> {
  //                 @SuppressWarnings("unchecked")
  //                 Map<String, Object> changeDetail = (Map<String, Object>)
  // log.get("changeDetail");
  //                 return changeDetail != null
  //                     && "cmis:description".equals(changeDetail.get("field"));
  //               });
  //   assertTrue(hasNoteUpdate, "Should have an update entry for note field (cmis:description)");
  //   assertTrue(hasNoteUpdate, "Should have an update entry for note field (cmis:description)");

  //   // Save the entity so test 62 can edit it
  //   String saveResponseFinal = api.saveEntityDraft(appUrl, entityName, srvpath,
  // changelogEntityID);
  //   assertEquals("Saved", saveResponseFinal, "Entity should be saved successfully");
  // }

  // @Test
  // @Order(62)
  // void testChangelogAfterRenamingAttachment() throws IOException {
  //   System.out.println(
  //       "Test (62): Rename attachment and verify changelog increases with rename entry");

  //   // Edit entity to put it in draft mode (entity was saved at end of test 61)
  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, changelogEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Rename the attachment
  //   String newFileName = "renamed_sample.txt";
  //   String renameResponse =
  //       api.renameAttachment(
  //           appUrl, entityName, facetName, changelogEntityID, changelogAttachmentID,
  // newFileName);
  //   assertEquals("Renamed", renameResponse, "Should successfully rename attachment");

  //   // Save entity after rename
  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, changelogEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully after rename");

  //   // Edit entity again and fetch changelog
  //   editResponse = api.editEntityDraft(appUrl, entityName, srvpath, changelogEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Fetch changelog after rename
  //   Map<String, Object> changelogAfterRename =
  //       api.fetchChangelog(appUrl, entityName, facetName, changelogEntityID,
  // changelogAttachmentID);

  //   assertNotNull(changelogAfterRename, "Changelog response should not be null after rename");

  //   // Verify changelog has increased (rename operation adds 1 entry for cmis:name change)
  //   // Expected: 1 created + 3 initial updates + 1 rename update = 5 total
  //   assertEquals(
  //       5, changelogAfterRename.get("numItems"), "Should have 5 changelog entries after rename");

  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogsAfterRename =
  //       (List<Map<String, Object>>) changelogAfterRename.get("changeLogs");
  //   assertEquals(
  //       5, changeLogsAfterRename.size(), "Should have exactly 5 changelog entries after rename");

  //   // Verify updated count is 4 (3 initial + 1 from rename operation)
  //   long updatedCountAfterRename =
  //       changeLogsAfterRename.stream()
  //           .filter(log -> "updated".equals(log.get("operation")))
  //           .count();
  //   assertEquals(4, updatedCountAfterRename, "Should have 4 'updated' operations after rename");

  //   // Verify filename change in changelog
  //   boolean hasFilenameUpdate =
  //       changeLogsAfterRename.stream()
  //           .filter(log -> "updated".equals(log.get("operation")))
  //           .anyMatch(
  //               log -> {
  //                 @SuppressWarnings("unchecked")
  //                 Map<String, Object> changeDetail = (Map<String, Object>)
  // log.get("changeDetail");
  //                 return changeDetail != null && "cmis:name".equals(changeDetail.get("field"));
  //               });
  //   assertTrue(hasFilenameUpdate, "Should have an update entry for filename (cmis:name)");

  //   // Cleanup - entity was saved after rename, so delete the active entity
  //   api.deleteEntity(appUrl, entityName, changelogEntityID);
  // }

  // @Test
  // @Order(63)
  // void testChangelogWithCustomPropertyEditSave() throws IOException {
  //   System.out.println(
  //       "Test (63): Create entity with custom property, save, edit and save again - verify
  // changelog remains at 3 entries");

  //   // Create a new entity
  //   String newEntityID = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   assertNotNull(newEntityID, "Failed to create new entity");
  //   assertNotEquals("Could not create entity", newEntityID);

  //   // Prepare a sample file to upload
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.pdf").getFile());
  //   assertTrue(file.exists(), "Sample file should exist");

  //   // Create attachment
  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", newEntityID);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(appUrl, entityName, facetName, newEntityID, srvpath, postData,
  // file);

  //   assertEquals(2, createResponse.size(), "Should return status and attachment ID");
  //   String status = createResponse.get(0);
  //   String attachmentID = createResponse.get(1);

  //   assertEquals("Attachment created", status, "Attachment should be created successfully");
  //   assertNotNull(attachmentID, "Attachment ID should not be null");
  //   assertNotEquals("", attachmentID, "Attachment ID should not be empty");

  //   // Add a custom property
  //   Integer customPropertyValue = 99999;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           "{\"customProperty2\": " + customPropertyValue + "}",
  //           MediaType.parse("application/json"));
  //   String updateCustomPropertyResponse =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, newEntityID, attachmentID, bodyInt);
  //   assertEquals(
  //       "Updated", updateCustomPropertyResponse, "Should successfully update custom property");

  //   // Save the entity
  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully");

  //   // Edit entity to fetch initial changelog
  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Fetch changelog after initial save
  //   Map<String, Object> changelogResponse =
  //       api.fetchChangelog(appUrl, entityName, facetName, newEntityID, attachmentID);

  //   assertNotNull(changelogResponse, "Changelog response should not be null");

  //   // Verify changelog has 3 entries: 1 created + 2 updated (cmis:secondaryObjectTypeIds +
  //   // customProperty2)
  //   assertEquals(3, changelogResponse.get("numItems"), "Should have 3 changelog entries
  // initially");

  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogs =
  //       (List<Map<String, Object>>) changelogResponse.get("changeLogs");
  //   assertEquals(3, changeLogs.size(), "Should have exactly 3 changelog entries");

  //   // Save entity again without any modifications
  //   saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully again");

  //   // Edit entity again and fetch changelog
  //   editResponse = api.editEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Fetch changelog after second save
  //   Map<String, Object> changelogAfterSecondSave =
  //       api.fetchChangelog(appUrl, entityName, facetName, newEntityID, attachmentID);

  //   assertNotNull(
  //       changelogAfterSecondSave, "Changelog response should not be null after second save");

  //   // Verify changelog still has only 3 entries (no new entries added)
  //   assertEquals(
  //       3,
  //       changelogAfterSecondSave.get("numItems"),
  //       "Should still have only 3 changelog entries after edit-save without modifications");

  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogsAfterSecondSave =
  //       (List<Map<String, Object>>) changelogAfterSecondSave.get("changeLogs");
  //   assertEquals(
  //       3,
  //       changeLogsAfterSecondSave.size(),
  //       "Should still have exactly 3 changelog entries after second save");

  //   // Clean up the entity
  //   api.deleteEntity(appUrl, entityName, newEntityID);
  // }

  // @Test
  // @Order(64)
  // void testChangelogForSavedAttachmentWithoutModification() throws IOException {
  //   System.out.println(
  //       "Test (64): Create entity, upload attachment, save, edit and save again - verify
  // changelog still has only 'created' entry");

  //   // Create a new entity
  //   String newEntityID = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   assertNotNull(newEntityID, "Failed to create new entity");
  //   assertNotEquals("Could not create entity", newEntityID);

  //   // Prepare a sample file to upload
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample.pdf").getFile());
  //   assertTrue(file.exists(), "Sample file should exist");

  //   // Create attachment
  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", newEntityID);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(appUrl, entityName, facetName, newEntityID, srvpath, postData,
  // file);

  //   assertEquals(2, createResponse.size(), "Should return status and attachment ID");
  //   String status = createResponse.get(0);
  //   String newAttachmentID = createResponse.get(1);

  //   assertEquals("Attachment created", status, "Attachment should be created successfully");
  //   assertNotNull(newAttachmentID, "Attachment ID should not be null");
  //   assertNotEquals("", newAttachmentID, "Attachment ID should not be empty");

  //   // Save the entity immediately without any modifications
  //   String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully");

  //   // Edit entity again without making any changes to the attachment
  //   String editResponse = api.editEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Save entity again without modifying the attachment
  //   saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Saved", saveResponse, "Entity should be saved successfully again");

  //   // Edit entity to fetch changelog
  //   editResponse = api.editEntityDraft(appUrl, entityName, srvpath, newEntityID);
  //   assertEquals("Entity in draft mode", editResponse, "Entity should be in draft mode");

  //   // Fetch changelog for the attachment
  //   Map<String, Object> changelogResponse =
  //       api.fetchChangelog(appUrl, entityName, facetName, newEntityID, newAttachmentID);

  //   assertNotNull(changelogResponse, "Changelog response should not be null");

  //   // Verify changelog content - should only have 'created' entry even after edit and save
  //   assertEquals(false, changelogResponse.get("hasMoreItems"), "hasMoreItems should be false");
  //   assertEquals(
  //       "sample.pdf", changelogResponse.get("filename"), "Filename should match uploaded file");
  //   assertNotNull(changelogResponse.get("objectId"), "ObjectId should not be null");
  //   assertEquals(1, changelogResponse.get("numItems"), "Should have only 1 changelog entry");

  //   // Verify the changelog entry
  //   @SuppressWarnings("unchecked")
  //   List<Map<String, Object>> changeLogs =
  //       (List<Map<String, Object>>) changelogResponse.get("changeLogs");
  //   assertEquals(1, changeLogs.size(), "Should have exactly 1 changelog entry");

  //   Map<String, Object> logEntry = changeLogs.get(0);
  //   assertEquals("created", logEntry.get("operation"), "Operation should be 'created'");
  //   assertNotNull(logEntry.get("time"), "Time should not be null");
  //   assertNotNull(logEntry.get("user"), "User should not be null");
  //   assertFalse(
  //       logEntry.containsKey("changeDetail"), "Created operation should not have changeDetail");

  //   // Clean up the new entity
  //   api.deleteEntity(appUrl, entityName, newEntityID);
  // }

  // @Test
  // @Order(65)
  // void testMoveAttachmentsWithSourceFacet() throws IOException {
  //   System.out.println(
  //       "Test (65): Move attachments from Source Entity to Target Entity with sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity: " + saveTargetBeforeMoveResponse);
  //   }

  //   // Move attachments from source to target with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   api.moveAttachment(
  //       appUrl,
  //       entityName,
  //       facetName,
  //       moveTargetEntity,
  //       moveSourceFolderId,
  //       moveObjectIds,
  //       targetFacet,
  //       sourceFacet);

  //   // All attachments moved to target entity in SDM & UI
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(
  //       sourceAttachmentIds.size(),
  //       targetMetadataAfterMove.size(),
  //       "Target entity should have all attachments after move");

  //   // Verify attachments can be read from target entity
  //   for (Map<String, Object> metadata : targetMetadataAfterMove) {
  //     String targetAttachmentId = (String) metadata.get("ID");
  //     String readResponse =
  //         api.readAttachment(appUrl, entityName, facetName, moveTargetEntity,
  // targetAttachmentId);
  //     if (!readResponse.equals("OK")) {
  //       fail("Could not read moved attachment from target entity");
  //     }
  //   }

  //   // All attachments removed from source entity in SDM & UI
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       0, sourceMetadataAfterMove.size(), "Source entity should have 0 attachments after move");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(66)
  // public void testMoveAttachmentsToEntityWithDuplicateWithSourceFacet() throws Exception {
  //   System.out.println(
  //       "Test (66): Move attachments to entity with duplicate attachment with sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (Exception e) {
  //       fail("Could not fetch metadata for attachment: " + attachmentId);
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch all objectIds from source entity");
  //   }

  //   // Create target entity and add attachment
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   Map<String, Object> targetPostData = new HashMap<>();
  //   targetPostData.put("up__ID", moveTargetEntity);
  //   targetPostData.put("mimeType", "application/pdf");
  //   targetPostData.put("createdAt", new Date().toString());
  //   targetPostData.put("createdBy", "test@test.com");
  //   targetPostData.put("modifiedBy", "test@test.com");

  //   File duplicateFile = new File(classLoader.getResource("sample.pdf").getFile());
  //   List<String> targetCreateResponse =
  //       api.createAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           srvpath,
  //           targetPostData,
  //           duplicateFile);

  //   if (!targetCreateResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create attachment on target entity");
  //   }

  //   // Save target entity to persist the attachment
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity before move: " + saveTargetBeforeMoveResponse);
  //   }

  //   // Fetch target metadata before move (target entity is now saved with 1 attachment)
  //   List<Map<String, Object>> targetMetadataBeforeMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   int targetCountBeforeMove = targetMetadataBeforeMove.size();

  //   // Move attachments from source to target with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   api.moveAttachment(
  //       appUrl,
  //       entityName,
  //       facetName,
  //       moveTargetEntity,
  //       moveSourceFolderId,
  //       moveObjectIds,
  //       targetFacet,
  //       sourceFacet);

  //   // Verify target has duplicate skipped, other attachments moved
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);

  //   // Expected: original attachments + non-duplicate moved attachments
  //   int expectedTargetCount = targetCountBeforeMove + (sourceAttachmentIds.size() - 1);
  //   assertEquals(
  //       expectedTargetCount,
  //       targetMetadataAfterMove.size(),
  //       "Target should have duplicate skipped, other attachments moved");

  //   // Verify source entity has only the duplicate attachment remaining
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   // Calculate expected source count: number of duplicates that couldn't be moved
  //   int expectedSourceCount =
  //       sourceAttachmentIds.size() - (targetMetadataAfterMove.size() - targetCountBeforeMove);
  //   assertEquals(
  //       expectedSourceCount,
  //       sourceMetadataAfterMove.size(),
  //       "Source should have duplicate attachment remaining");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(67)
  // public void testMoveAttachmentsWithNotesAndSecondaryProperties() throws Exception {
  //   System.out.println(
  //       "Test (67): Move attachments with notes and secondary properties with sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Add notes to attachments
  //   String notesValue = "Test note for verification";
  //   MediaType mediaType = MediaType.parse("application/json");
  //   String jsonPayload = "{\"note\": \"" + notesValue + "\"}";
  //   RequestBody updateNotesBody = RequestBody.create(jsonPayload, mediaType);

  //   for (String attachmentId : sourceAttachmentIds) {
  //     String updateNotesResponse =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, moveSourceEntity, attachmentId, updateNotesBody);
  //     if (!updateNotesResponse.equals("Updated")) {
  //       fail("Could not update notes for attachment: " + attachmentId);
  //     }
  //   }

  //   // Add custom property to attachments
  //   Integer customProperty2Value = 54321;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           "{\"customProperty2\": " + customProperty2Value + "}",
  //           MediaType.parse("application/json"));

  //   for (String attachmentId : sourceAttachmentIds) {
  //     String updateCustomPropertyResponse =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, moveSourceEntity, attachmentId, bodyInt);
  //     if (!updateCustomPropertyResponse.equals("Updated")) {
  //       fail("Could not update custom property for attachment: " + attachmentId);
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (Exception e) {
  //       fail("Could not fetch metadata for attachment: " + attachmentId);
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch all objectIds from source entity");
  //   }

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity before move: " + saveTargetBeforeMoveResponse);
  //   }

  //   // Move attachments from source to target with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           sourceFacet);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify all attachments moved to target
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(
  //       sourceAttachmentIds.size(),
  //       targetMetadataAfterMove.size(),
  //       "Target entity should have all attachments after move");

  //   // Verify notes and secondary properties are preserved
  //   for (Map<String, Object> metadata : targetMetadataAfterMove) {
  //     String targetAttachmentId = (String) metadata.get("ID");
  //     assertNotNull(targetAttachmentId, "Target attachment ID should not be null");

  //     Map<String, Object> detailedMetadata =
  //         api.fetchMetadata(appUrl, entityName, facetName, moveTargetEntity, targetAttachmentId);

  //     // Verify notes are preserved
  //     if (detailedMetadata.containsKey("note")) {
  //       assertEquals(
  //           notesValue,
  //           detailedMetadata.get("note"),
  //           "Notes should be preserved after move for attachment: " + targetAttachmentId);
  //     } else {
  //       fail("Notes property missing after move for attachment: " + targetAttachmentId);
  //     }

  //     // Verify custom property is preserved
  //     if (detailedMetadata.containsKey("customProperty2")) {
  //       assertEquals(
  //           customProperty2Value,
  //           detailedMetadata.get("customProperty2"),
  //           "Custom property should be preserved after move for attachment: " +
  // targetAttachmentId);
  //     } else {
  //       fail("Custom property missing after move for attachment: " + targetAttachmentId);
  //     }
  //   }

  //   // Verify source entity has no attachments (all moved with sourceFacet)
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(0, sourceMetadataAfterMove.size(), "Source entity has no attachments after
  // move");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(68)
  // public void testMoveAttachmentsWithoutSourceFacet() throws Exception {
  //   System.out.println(
  //       "Test (68): Move valid attachments from Source Entity to Target Entity without
  // sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID from first attachment
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity before move");
  //   }

  //   // Move attachments without sourceFacet (pass null for sourceFacet parameter)
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           null);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify attachments are in target entity
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(
  //       moveObjectIds.size(),
  //       targetMetadataAfterMove.size(),
  //       "Target entity should have all moved attachments");

  //   // Verify attachments can be read from target entity
  //   for (Map<String, Object> metadata : targetMetadataAfterMove) {
  //     String targetAttachmentId = (String) metadata.get("ID");
  //     String readResponse =
  //         api.readAttachment(appUrl, entityName, facetName, moveTargetEntity,
  // targetAttachmentId);
  //     if (!readResponse.equals("OK")) {
  //       fail("Could not read moved attachment from target entity");
  //     }
  //   }

  //   // Expected Behavior: Attachments remain in source entity UI (without sourceFacet)
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       moveObjectIds.size(),
  //       sourceMetadataAfterMove.size(),
  //       "Source entity should still have attachments in UI when sourceFacet is not specified");

  //   // Verify the same objectIds are still visible in source
  //   for (Map<String, Object> metadata : sourceMetadataAfterMove) {
  //     String objectId = (String) metadata.get("objectId");
  //     assertTrue(
  //         moveObjectIds.contains(objectId),
  //         "Source entity should still show attachment with objectId: " + objectId);
  //   }

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(69)
  // public void testMoveAttachmentsToEntityWithDuplicateWithoutSourceFacet() throws Exception {
  //   System.out.println(
  //       "Test (69): Move attachments into existing Target Entity when duplicate exists without
  // sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID from first attachment
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       } else {
  //         fail("Attachment metadata does not contain objectId");
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   // Create target entity and add duplicate attachment (sample.pdf)
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Add the same first file (sample.pdf) to target entity to create duplicate
  //   Map<String, Object> targetPostData = new HashMap<>();
  //   targetPostData.put("up__ID", moveTargetEntity);
  //   targetPostData.put("mimeType", "application/pdf");
  //   targetPostData.put("createdAt", new Date().toString());
  //   targetPostData.put("createdBy", "test@test.com");
  //   targetPostData.put("modifiedBy", "test@test.com");

  //   List<String> createTargetResponse =
  //       api.createAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           srvpath,
  //           targetPostData,
  //           files.get(0)); // Add same file (sample.pdf)
  //   if (!createTargetResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create duplicate attachment in target entity");
  //   }

  //   // Save target entity before move
  //   String saveTargetResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity: " + saveTargetResponse);
  //   }

  //   // Get initial target metadata count
  //   List<Map<String, Object>> targetMetadataBeforeMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   int initialTargetCount = targetMetadataBeforeMove.size();

  //   // Step 3: Move attachments without sourceFacet (duplicate should be skipped)
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           null);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Expected Behavior - Verify duplicate was skipped, other attachments moved
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);

  //   int nonDuplicateCount = moveObjectIds.size() - 1;
  //   int expectedTargetCount = initialTargetCount + nonDuplicateCount;

  //   assertEquals(
  //       expectedTargetCount,
  //       targetMetadataAfterMove.size(),
  //       "Target entity should have initial attachments plus non-duplicate moved attachments");

  //   // Verify at least one non-duplicate attachment was moved
  //   assertTrue(
  //       targetMetadataAfterMove.size() > initialTargetCount,
  //       "Target should have more attachments after move (non-duplicates added)");

  //   // Verify all attachments still remain in source entity UI (without sourceFacet)
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       moveObjectIds.size(),
  //       sourceMetadataAfterMove.size(),
  //       "Source entity should still have all attachments in UI when sourceFacet is not
  // specified");

  //   // Verify all original objectIds are still visible in source
  //   List<String> sourceObjectIds = new ArrayList<>();
  //   for (Map<String, Object> metadata : sourceMetadataAfterMove) {
  //     sourceObjectIds.add((String) metadata.get("objectId"));
  //   }
  //   for (String objectId : moveObjectIds) {
  //     assertTrue(
  //         sourceObjectIds.contains(objectId),
  //         "Source entity should still show attachment with objectId: " + objectId);
  //   }

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(70)
  // public void testMoveAttachmentsWithNotesAndSecondaryPropertiesWithoutSourceFacet()
  //     throws Exception {
  //   System.out.println(
  //       "Test (70): Move attachments with notes and secondary properties without sourceFacet");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Add notes to attachments
  //   String notesValue = "Test note for migration verification";
  //   MediaType mediaType = MediaType.parse("application/json");
  //   String jsonPayload = "{\"note\": \"" + notesValue + "\"}";
  //   RequestBody updateNotesBody = RequestBody.create(jsonPayload, mediaType);

  //   for (String attachmentId : sourceAttachmentIds) {
  //     String updateNotesResponse =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, moveSourceEntity, attachmentId, updateNotesBody);
  //     if (!updateNotesResponse.equals("Updated")) {
  //       fail("Could not update notes for attachment: " + attachmentId);
  //     }
  //   }

  //   // Add custom property to attachments
  //   Integer customProperty2Value = 54321;
  //   RequestBody bodyInt =
  //       RequestBody.create(
  //           "{\"customProperty2\": " + customProperty2Value + "}",
  //           MediaType.parse("application/json"));

  //   for (String attachmentId : sourceAttachmentIds) {
  //     String updateCustomPropertyResponse =
  //         api.updateSecondaryProperty(
  //             appUrl, entityName, facetName, moveSourceEntity, attachmentId, bodyInt);
  //     if (!updateCustomPropertyResponse.equals("Updated")) {
  //       fail("Could not update custom property for attachment: " + attachmentId);
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (Exception e) {
  //       fail("Could not fetch metadata for attachment: " + attachmentId);
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch all objectIds from source entity");
  //   }

  //   // Get source attachment count before move
  //   List<Map<String, Object>> sourceMetadataBeforeMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   int sourceCountBeforeMove = sourceMetadataBeforeMove.size();

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity before move");
  //   }

  //   // Get target attachment count before move
  //   List<Map<String, Object>> targetMetadataBeforeMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   int targetCountBeforeMove = targetMetadataBeforeMove.size();

  //   // Move attachments from source to target WITHOUT sourceFacet
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           null);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify expected number of attachments moved to target
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   int expectedTargetCount = targetCountBeforeMove + sourceAttachmentIds.size();
  //   assertEquals(
  //       expectedTargetCount,
  //       targetMetadataAfterMove.size(),
  //       "Target entity should have " + expectedTargetCount + " attachments after move");

  //   // Verify notes and secondary properties are preserved
  //   for (Map<String, Object> metadata : targetMetadataAfterMove) {
  //     String targetAttachmentId = (String) metadata.get("ID");
  //     assertNotNull(targetAttachmentId, "Target attachment ID should not be null");

  //     Map<String, Object> detailedMetadata =
  //         api.fetchMetadata(appUrl, entityName, facetName, moveTargetEntity, targetAttachmentId);

  //     // Verify notes are preserved
  //     if (detailedMetadata.containsKey("note")) {
  //       assertEquals(
  //           notesValue,
  //           detailedMetadata.get("note"),
  //           "Notes should be preserved after move for attachment: " + targetAttachmentId);
  //     } else {
  //       fail("Notes property missing after move for attachment: " + targetAttachmentId);
  //     }

  //     // Verify custom property is preserved
  //     if (detailedMetadata.containsKey("customProperty2")) {
  //       assertEquals(
  //           customProperty2Value,
  //           detailedMetadata.get("customProperty2"),
  //           "Custom property should be preserved after move for attachment: " +
  // targetAttachmentId);
  //     } else {
  //       fail("Custom property missing after move for attachment: " + targetAttachmentId);
  //     }
  //   }

  //   // Verify source entity still has all attachments (without sourceFacet)
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       sourceCountBeforeMove,
  //       sourceMetadataAfterMove.size(),
  //       "Source entity should still have "
  //           + sourceCountBeforeMove
  //           + " attachments (without sourceFacet)");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(71)
  // public void testMoveAttachmentsWithInvalidOrUndefinedSecondaryProperties() throws Exception {
  //   System.out.println(
  //       "Test (71): Move attachments with invalid or undefined secondary properties");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));
  //   files.add(new File(classLoader.getResource("WDIRSCodeList.csv").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Add valid secondary properties to first attachment (customProperty2)
  //   String validAttachmentId = sourceAttachmentIds.get(0);
  //   Integer validCustomProperty2Value = 12345;
  //   RequestBody validPropertyBody =
  //       RequestBody.create(
  //           "{\"customProperty2\": " + validCustomProperty2Value + "}",
  //           MediaType.parse("application/json"));

  //   String validPropertyResponse =
  //       api.updateSecondaryProperty(
  //           appUrl, entityName, facetName, moveSourceEntity, validAttachmentId,
  // validPropertyBody);
  //   if (!validPropertyResponse.equals("Updated")) {
  //     fail("Could not update valid property for attachment: " + validAttachmentId);
  //   }

  //   // add invalid secondary properties to second attachment (non-existent property)
  //   String invalidAttachmentId = sourceAttachmentIds.get(1);
  //   RequestBody invalidPropertyBody =
  //       RequestBody.create(
  //           "{\"nonExistentProperty\": \"invalid\"}", MediaType.parse("application/json"));

  //   api.updateSecondaryProperty(
  //       appUrl, entityName, facetName, moveSourceEntity, invalidAttachmentId,
  // invalidPropertyBody);

  //   // add undefined properties to third attachment
  //   String undefinedAttachmentId = sourceAttachmentIds.get(2);
  //   RequestBody undefinedPropertyBody =
  //       RequestBody.create(
  //           "{\"undefinedField\": \"test\", \"anotherUndefined\": 999}",
  //           MediaType.parse("application/json"));

  //   api.updateSecondaryProperty(
  //       appUrl,
  //       entityName,
  //       facetName,
  //       moveSourceEntity,
  //       undefinedAttachmentId,
  //       undefinedPropertyBody);

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (Exception e) {
  //       fail("Could not fetch metadata for attachment: " + attachmentId);
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch all objectIds from source entity");
  //   }

  //   // Get source attachment count before move
  //   List<Map<String, Object>> sourceMetadataBeforeMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   int sourceCountBeforeMove = sourceMetadataBeforeMove.size();

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse68 =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse68.equals("Saved")) {
  //     fail("Could not save target entity before move: " + saveTargetBeforeMoveResponse68);
  //   }

  //   // Move attachments from source to target with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           sourceFacet);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify attachments moved to target
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);

  //   assertTrue(
  //       targetMetadataAfterMove.size() > 0, "Target entity should have attachments after move");
  //   assertEquals(
  //       sourceCountBeforeMove,
  //       targetMetadataAfterMove.size(),
  //       "All attachments should move (invalid properties are ignored)");

  //   // Verify only allowed properties are populated in target
  //   for (Map<String, Object> metadata : targetMetadataAfterMove) {
  //     String targetAttachmentId = (String) metadata.get("ID");
  //     assertNotNull(targetAttachmentId, "Target attachment ID should not be null");

  //     // Fetch detailed metadata to verify properties
  //     Map<String, Object> detailedMetadata =
  //         api.fetchMetadata(appUrl, entityName, facetName, moveTargetEntity, targetAttachmentId);

  //     // Check if this is the attachment with valid customProperty2
  //     if (detailedMetadata.containsKey("customProperty2")
  //         && detailedMetadata.get("customProperty2") != null) {
  //       assertEquals(
  //           validCustomProperty2Value,
  //           detailedMetadata.get("customProperty2"),
  //           "Valid customProperty2 should be preserved");
  //     }
  //   }

  //   // Verify source entity has no attachments
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       0,
  //       sourceMetadataAfterMove.size(),
  //       "Source entity should have no attachments after move with sourceFacet");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(72)
  // public void testMoveAttachmentsFromSourceEntityInDraftMode() throws Exception {
  //   System.out.println(
  //       "Test (72): Move attachments from Source Entity when Source Entity is in draft mode");

  //   // Create source entity and keep it in draft mode
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));
  //   files.add(new File(classLoader.getResource("WDIRSCodeList.csv").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Verify attachments are added to source entity
  //   int sourceCountBeforeMove = sourceAttachmentIds.size();
  //   assertTrue(sourceCountBeforeMove > 0, "Source entity should have attachments before move");
  //   assertEquals(
  //       files.size(), sourceCountBeforeMove, "Source should have " + files.size() + "
  // attachments");

  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID from first attachment
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   assertNotNull(moveSourceFolderId, "Source folder ID should not be null");

  //   String editSourceResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!editSourceResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit source entity back to draft mode");
  //   }

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveTargetEntity);
  //   if (!saveTargetResponse.equals("Saved")) {
  //     fail("Could not save target entity: " + saveTargetResponse);
  //   }

  //   // Move attachments from draft source to target using sourceFacet
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           null);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify attachments moved to target
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertTrue(
  //       targetMetadataAfterMove.size() > 0, "Target entity should have attachments after move");
  //   assertEquals(
  //       sourceCountBeforeMove,
  //       targetMetadataAfterMove.size(),
  //       "Target should have " + sourceCountBeforeMove + " attachments after move");

  //   // Verify all expected attachments are in target
  //   Set<String> targetFileNames =
  //       targetMetadataAfterMove.stream()
  //           .map(m -> (String) m.get("fileName"))
  //           .collect(java.util.stream.Collectors.toSet());

  //   for (File file : files) {
  //     assertTrue(
  //         targetFileNames.contains(file.getName()),
  //         "Target should contain attachment: " + file.getName());
  //   }

  //   // Now save the source entity
  //   String saveSourceAfterMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveSourceEntity);
  //   if (!saveSourceAfterMoveResponse.equals("Saved")) {
  //     fail("Could not save source entity after move: " + saveSourceAfterMoveResponse);
  //   }

  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       sourceCountBeforeMove,
  //       sourceMetadataAfterMove.size(),
  //       "Source entity in draft mode retains attachments after move (copy behavior)");

  //   Set<String> sourceFileNamesAfterMove =
  //       sourceMetadataAfterMove.stream()
  //           .map(m -> (String) m.get("fileName"))
  //           .collect(java.util.stream.Collectors.toSet());

  //   for (File file : files) {
  //     assertTrue(
  //         sourceFileNamesAfterMove.contains(file.getName()),
  //         "Source (draft) should still contain attachment: " + file.getName());
  //   }

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(73)
  // public void testEditAttachmentFileNameAndMoveToTarget() throws Exception {
  //   System.out.println(
  //       "Test (73): Edit attachment file name in Source Entity and move it to Target Entity");

  //   // Create source entity and add attachment
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Add attachment with original name (sample.txt)
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File originalFile = new File(classLoader.getResource("sample.txt").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "text/plain");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, originalFile);
  //   if (!createResponse.get(0).equals("Attachment created")) {
  //     fail("Could not create attachment in source entity");
  //   }

  //   String attachmentId = createResponse.get(1);
  //   assertNotNull(attachmentId, "Attachment ID should not be null");

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Verify original filename
  //   List<Map<String, Object>> metadataBeforeRename =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(1, metadataBeforeRename.size(), "Source should have 1 attachment");
  //   assertEquals(
  //       "sample.txt",
  //       metadataBeforeRename.get(0).get("fileName"),
  //       "Original filename should be sample.txt");

  //   // Edit source entity back to draft mode
  //   String editSourceResponse = api.editEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!editSourceResponse.equals("Entity in draft mode")) {
  //     fail("Could not edit source entity to draft mode");
  //   }

  //   // Rename the attachment to testEdited.txt
  //   String newFileName = "testEdited.txt";
  //   String renameResponse =
  //       api.renameAttachment(
  //           appUrl, entityName, facetName, moveSourceEntity, attachmentId, newFileName);
  //   assertEquals("Renamed", renameResponse, "Attachment should be renamed successfully");

  //   // Save source entity after rename
  //   saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath, moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity after rename: " + saveSourceResponse);
  //   }

  //   // Verify renamed filename in source
  //   List<Map<String, Object>> metadataAfterRename =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(1, metadataAfterRename.size(), "Source should still have 1 attachment");
  //   assertEquals(
  //       newFileName,
  //       metadataAfterRename.get(0).get("fileName"),
  //       "Filename should be updated to " + newFileName);

  //   // Get objectId and folderId for move operation
  //   Map<String, Object> metadata =
  //       api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //   String objectId = metadata.get("objectId").toString();
  //   moveSourceFolderId = metadata.get("folderId").toString();
  //   assertNotNull(objectId, "Object ID should not be null");
  //   assertNotNull(moveSourceFolderId, "Folder ID should not be null");

  //   moveObjectIds.clear();
  //   moveObjectIds.add(objectId);

  //   // Create target entity
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity");
  //   }

  //   // Save target before move
  //   String saveTargetBeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTargetBeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity before move");
  //   }

  //   // Move attachment from source to target with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           sourceFacet);

  //   if (moveResult == null) {
  //     fail("Move operation returned null result");
  //   }

  //   // Verify attachment moved to target with renamed filename
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(1, targetMetadataAfterMove.size(), "Target should have 1 attachment after
  // move");
  //   assertEquals(
  //       newFileName,
  //       targetMetadataAfterMove.get(0).get("fileName"),
  //       "Target should have attachment with renamed filename: " + newFileName);

  //   // Verify attachment removed from source
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       0,
  //       sourceMetadataAfterMove.size(),
  //       "Source entity should have no attachments after move with sourceFacet");

  //   // Clean up - delete both entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(74)
  // public void testChainMoveAttachmentsFromSourceToTarget1ToTarget2() throws Exception {
  //   System.out.println(
  //       "Test (74): Move attachments from Source Entity to Target Entity 1 and then to Target
  // Entity 2");

  //   // Create source entity and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Get count of attachments in source
  //   int sourceCountInitial = sourceAttachmentIds.size();
  //   assertTrue(sourceCountInitial > 0, "Source should have attachments");

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID from first attachment
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   assertNotNull(moveSourceFolderId, "Source folder ID should not be null");

  //   // Create Target Entity 1
  //   moveTargetEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity 1");
  //   }

  //   // Save target1 before move
  //   String saveTarget1BeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity);
  //   if (!saveTarget1BeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity 1 before move");
  //   }

  //   // Move attachments from source to Target Entity 1 with sourceFacet
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult1 =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity,
  //           moveSourceFolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           sourceFacet);

  //   if (moveResult1 == null) {
  //     fail("Move operation from source to target 1 returned null result");
  //   }

  //   // Verify attachments moved to Target Entity 1
  //   List<Map<String, Object>> target1MetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertTrue(
  //       target1MetadataAfterMove.size() > 0, "Target entity 1 should have attachments after
  // move");
  //   assertEquals(
  //       sourceCountInitial,
  //       target1MetadataAfterMove.size(),
  //       "Target 1 should have " + sourceCountInitial + " attachments");

  //   // Verify all expected files are in Target Entity 1
  //   Set<String> target1FileNames =
  //       target1MetadataAfterMove.stream()
  //           .map(m -> (String) m.get("fileName"))
  //           .collect(java.util.stream.Collectors.toSet());

  //   for (File file : files) {
  //     assertTrue(
  //         target1FileNames.contains(file.getName()),
  //         "Target 1 should contain attachment: " + file.getName());
  //   }

  //   // Verify attachments removed from source
  //   List<Map<String, Object>> sourceMetadataAfterFirstMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       0,
  //       sourceMetadataAfterFirstMove.size(),
  //       "Source entity should have no attachments after move to target 1");

  //   // Create Target Entity 2
  //   String moveTargetEntity2 = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity2.equals("Could not create entity")) {
  //     fail("Could not create target entity 2");
  //   }

  //   // Save target2 before move
  //   String saveTarget2BeforeMoveResponse =
  //       api.saveEntityDraft(appUrl, entityName, srvpath, moveTargetEntity2);
  //   if (!saveTarget2BeforeMoveResponse.equals("Saved")) {
  //     fail("Could not save target entity 2 before move");
  //   }

  //   // Get new object IDs and folder ID from Target Entity 1 for second move
  //   List<String> target1AttachmentIds = new ArrayList<>();
  //   for (Map<String, Object> metadata : target1MetadataAfterMove) {
  //     String attachmentId = metadata.get("ID").toString();
  //     target1AttachmentIds.add(attachmentId);
  //   }

  //   moveObjectIds.clear();
  //   String target1FolderId = null;
  //   for (String attachmentId : target1AttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveTargetEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get folder ID from first attachment
  //         if (target1FolderId == null && metadata.containsKey("folderId")) {
  //           target1FolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata from target 1: " + e.getMessage());
  //     }
  //   }

  //   assertNotNull(target1FolderId, "Target 1 folder ID should not be null");

  //   // Move attachments from Target Entity 1 to Target Entity 2 with sourceFacet
  //   Map<String, Object> moveResult2 =
  //       api.moveAttachment(
  //           appUrl,
  //           entityName,
  //           facetName,
  //           moveTargetEntity2,
  //           target1FolderId,
  //           moveObjectIds,
  //           targetFacet,
  //           sourceFacet);

  //   if (moveResult2 == null) {
  //     fail("Move operation from target 1 to target 2 returned null result");
  //   }

  //   // Verify attachments moved to Target Entity 2
  //   List<Map<String, Object>> target2MetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity2);
  //   assertTrue(
  //       target2MetadataAfterMove.size() > 0, "Target entity 2 should have attachments after
  // move");
  //   assertEquals(
  //       sourceCountInitial,
  //       target2MetadataAfterMove.size(),
  //       "Target 2 should have " + sourceCountInitial + " attachments");

  //   // Verify all expected files are in Target Entity 2
  //   Set<String> target2FileNames =
  //       target2MetadataAfterMove.stream()
  //           .map(m -> (String) m.get("fileName"))
  //           .collect(java.util.stream.Collectors.toSet());

  //   for (File file : files) {
  //     assertTrue(
  //         target2FileNames.contains(file.getName()),
  //         "Target 2 should contain attachment: " + file.getName());
  //   }

  //   // Verify attachments removed from Target Entity 1
  //   List<Map<String, Object>> target1MetadataAfterSecondMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(
  //       0,
  //       target1MetadataAfterSecondMove.size(),
  //       "Target entity 1 should have no attachments after move to target 2");

  //   // Clean up - delete all three entities
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity2);
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(75)
  // public void testMoveAttachmentsWithoutSDMRole() throws Exception {
  //   System.out.println("Test (75): Move attachments when user does not have SDM Role");

  //   // Create source entity with SDM role and add attachments
  //   moveSourceEntity = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveSourceEntity.equals("Could not create entity")) {
  //     fail("Could not create source entity");
  //   }

  //   // Prepare sample files
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   List<File> files = new ArrayList<>();
  //   files.add(new File(classLoader.getResource("sample.pdf").getFile()));
  //   files.add(new File(classLoader.getResource("sample.txt").getFile()));

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", moveSourceEntity);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Create attachments in source entity with SDM role
  //   List<String> sourceAttachmentIds = new ArrayList<>();
  //   for (File file : files) {
  //     List<String> createResponse =
  //         api.createAttachment(
  //             appUrl, entityName, facetName, moveSourceEntity, srvpath, postData, file);
  //     if (createResponse.get(0).equals("Attachment created")) {
  //       sourceAttachmentIds.add(createResponse.get(1));
  //     } else {
  //       fail("Could not create attachment in source entity");
  //     }
  //   }

  //   // Save source entity with SDM role
  //   String saveSourceResponse = api.saveEntityDraft(appUrl, entityName, srvpath,
  // moveSourceEntity);
  //   if (!saveSourceResponse.equals("Saved")) {
  //     fail("Could not save source entity: " + saveSourceResponse);
  //   }

  //   // Get count of attachments in source
  //   int sourceCountInitial = sourceAttachmentIds.size();
  //   assertTrue(sourceCountInitial > 0, "Source should have attachments");

  //   // Fetch object IDs from source entity
  //   moveObjectIds.clear();
  //   for (String attachmentId : sourceAttachmentIds) {
  //     try {
  //       Map<String, Object> metadata =
  //           api.fetchMetadata(appUrl, entityName, facetName, moveSourceEntity, attachmentId);
  //       if (metadata.containsKey("objectId")) {
  //         moveObjectIds.add(metadata.get("objectId").toString());
  //         // Get source folder ID from first attachment
  //         if (moveSourceFolderId == null && metadata.containsKey("folderId")) {
  //           moveSourceFolderId = metadata.get("folderId").toString();
  //         }
  //       }
  //     } catch (IOException e) {
  //       fail("Could not fetch attachment metadata: " + e.getMessage());
  //     }
  //   }

  //   if (moveObjectIds.size() != sourceAttachmentIds.size()) {
  //     fail("Could not fetch object IDs for all attachments");
  //   }

  //   assertNotNull(moveSourceFolderId, "Source folder ID should not be null");

  //   // Create target entity with no SDM role
  //   moveTargetEntity = apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (moveTargetEntity.equals("Could not create entity")) {
  //     fail("Could not create target entity with no SDM role");
  //   }

  //   // Try to move attachments from source to target using user without SDM role
  //   String sourceFacet = serviceName + "." + entityName + "." + facetName;
  //   String targetFacet = serviceName + "." + entityName + "." + facetName;
  //   Map<String, Object> moveResult = null;
  //   boolean moveOperationFailed = false;
  //   String errorMessage = null;

  //   try {
  //     moveResult =
  //         apiNoRoles.moveAttachment(
  //             appUrl,
  //             entityName,
  //             facetName,
  //             moveTargetEntity,
  //             moveSourceFolderId,
  //             moveObjectIds,
  //             targetFacet,
  //             sourceFacet);

  //     if (moveResult == null) {
  //       moveOperationFailed = true;
  //       errorMessage = "Move operation returned null";
  //     } else if (moveResult.containsKey("error")) {
  //       moveOperationFailed = true;
  //       errorMessage = moveResult.get("error").toString();
  //     }
  //   } catch (Exception e) {
  //     moveOperationFailed = true;
  //     errorMessage = e.getMessage();
  //   }

  //   // Verify move operation failed
  //   assertTrue(moveOperationFailed, "Move operation should fail when user does not have SDM
  // role");
  //   assertNotNull(errorMessage, "Error message should be present when move operation fails");
  //   System.out.println("Move operation failed as expected. Error: " + errorMessage);

  //   // Verify attachments are still in source entity (not moved)
  //   List<Map<String, Object>> sourceMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveSourceEntity);
  //   assertEquals(
  //       sourceCountInitial,
  //       sourceMetadataAfterMove.size(),
  //       "Source should still have all attachments after failed move");

  //   // Verify target entity has no attachments
  //   List<Map<String, Object>> targetMetadataAfterMove =
  //       api.fetchEntityMetadata(appUrl, entityName, facetName, moveTargetEntity);
  //   assertEquals(
  //       0, targetMetadataAfterMove.size(), "Target should have no attachments after failed
  // move");

  //   // Clean up - delete both entities using SDM role
  //   api.deleteEntity(appUrl, entityName, moveTargetEntity);
  //   api.deleteEntity(appUrl, entityName, moveSourceEntity);
  // }

  // @Test
  // @Order(76)
  // void testUploadAttachmentExceedingMaximumFileSize() throws IOException {
  //   System.out.println(
  //       "Test (76) : Upload attachment exceeding maximum file size in references facet");

  //   // Create a new entity
  //   String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
  //   if (response.equals("Could not create entity")) {
  //     fail("Could not create entity");
  //   }
  //   String testEntityID = response;

  //   // Load the 150MB sample file
  //   ClassLoader classLoader = getClass().getClassLoader();
  //   File file = new File(classLoader.getResource("sample32mb.pdf").getFile());

  //   Map<String, Object> postData = new HashMap<>();
  //   postData.put("up__ID", testEntityID);
  //   postData.put("mimeType", "application/pdf");
  //   postData.put("createdAt", new Date().toString());
  //   postData.put("createdBy", "test@test.com");
  //   postData.put("modifiedBy", "test@test.com");

  //   // Try to upload to the 'references' facet which has the 100MB limit
  //   String referencesFacet = "references";
  //   List<String> createResponse =
  //       api.createAttachment(
  //           appUrl, entityName, referencesFacet, testEntityID, srvpath, postData, file);
  //   String check = createResponse.get(0);

  //   // The upload should fail with AttachmentSizeExceeded error
  //   if (!check.equals("Attachment created")) {
  //     try {
  //       JSONObject json = new JSONObject(check);
  //       String errorCode = json.getJSONObject("error").getString("code");
  //       String errorMessage = json.getJSONObject("error").getString("message");
  //       assertEquals("413", errorCode);
  //       assertEquals("File size exceeds the limit of 30MB.", errorMessage);
  //     } catch (Exception e) {
  //       fail("Failed to parse error response: " + e.getMessage());
  //     }
  //   } else {
  //     fail("Attachment got created with file size exceeding maximum limit");
  //   }

  //   // delete the test entity draft
  //   api.deleteEntityDraft(appUrl, entityName, testEntityID);
  // }
}
