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
class IntegrationTest_SingleFacet_Virus {
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
}
