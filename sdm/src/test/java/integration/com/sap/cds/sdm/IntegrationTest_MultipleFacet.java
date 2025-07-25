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
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_MultipleFacet {
  private static String token;
  private static String tokenNoRoles;
  private static String entityID;
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String[] ID = {"attachmentID1", "referenceID1", "footnoteID1"};
  private static String[] ID2 = {"attachmentID2", "referenceID2", "footnoteID2"};
  private static String[] ID3 = {"attachmentID3", "referenceID3", "footnoteID3"};
  private static String[] ID4 = {"attachmentID4", "referenceID4", "footnoteID4"};
  private static String[] ID5 = {"attachmentID5", "referenceID5", "footnoteID5"};
  private static String entityID2;
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
  private static int counter;
  private static IntegrationTestUtils integrationTestUtils;
  private static String copyAttachmentSourceEntity;
  private static String copyAttachmentTargetEntity;
  private static String copyAttachmentTargetEntityEmpty;
  private static List<String> sourceObjectIds = new ArrayList<>();
  private static List<String> targetAttachmentIds = new ArrayList<>();

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

  private String CreateandReturnFacetID(
      String appUrl,
      String serviceName,
      String entityName,
      String facet,
      String newentityId,
      Map<String, Object> postData,
      File file)
      throws IOException {
    String ID = null;
    List<String> FacetResponse =
        api.createAttachment(appUrl, entityName, facet, newentityId, srvpath, postData, file);
    String check = FacetResponse.get(0);
    if (check.equals("Attachment created")) {
      ID = FacetResponse.get(1);
      return ID;
    }
    return ID;
  }

  private boolean verifyDraftAndSave(
      String appUrl, String serviceName, String entityName, String entityID, String[] ID)
      throws IOException {
    String response[] = {"response1", "response2", "response3"};
    int Counter = -1;
    boolean status = false;

    for (int i = 0; i < facet.length; i++) {
      response[i] = api.readAttachmentDraft(appUrl, entityName, facet[i], entityID, ID[i]);
      if ("OK".equals(response[i])) Counter++;
    }
    if (Counter >= 2) {
      String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if ("Saved".equals(saveResponse)) {
        for (int i = 0; i < facet.length; i++) {
          response[i] = api.readAttachment(appUrl, entityName, facet[i], entityID, ID[i]);
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
          "{\"error\":{\"code\":\"500\",\"message\":\"sample.pdf already exists.\"}}";
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

  private boolean renameAndCheck(String facet, String id, String eId, String newName) {
    String result;
    String type = facet;
    switch (type.toLowerCase()) {
      case "attachments":
        result = api.renameAttachment(appUrl, entityName, facet, eId, id, newName);
        break;
      case "references":
        result = api.renameAttachment(appUrl, entityName, facet, eId, id, newName);
        break;
      case "footnotes":
        result = api.renameAttachment(appUrl, entityName, facet, eId, id, newName);
        break;
      default:
        System.out.println("Unknown type: " + type);
        return false;
    }
    boolean renamed = "Renamed".equals(result);
    return renamed;
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
      if (response.equals("Saved")) {
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
    if (response.equals("Entity in draft mode")) {
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (response.equals("Saved")) {
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
  void testUploadSinglePDF() throws IOException {
    System.out.println("Test (3) : Upload attachment, reference, and footnote PDF");
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
    if (response.equals("Entity in draft mode")) {
      // Creation of attachment, reference and footnote
      for (int i = 0; i < facet.length; i++) {
        ID[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID, postData, file);
      }
      testStatus = verifyDraftAndSave(appUrl, serviceName, entityName, entityID, ID);
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf " + response);
    }
  }

  @Test
  @Order(4)
  void testUploadSingleTXT() throws IOException {
    System.out.println("Test (4) : Upload attachment, reference, and footnote TXT");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "text/plain");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      // Creation of attachment, reference and footnote
      for (int i = 0; i < facet.length; i++) {
        ID2[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID, postData, file);
      }
      testStatus = verifyDraftAndSave(appUrl, serviceName, entityName, entityID, ID2);
    }
    if (!testStatus) {
      fail("Could not upload sample.txt " + response);
    }
  }

  @Test
  @Order(5)
  void testUploadSingleEXE() throws IOException {
    System.out.println("Test (5) : Upload attachment, reference, and footnote EXE");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.exe").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/octet-stream"); // Common mime-type for executables
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      // Creation of attachment, reference and footnote
      for (int i = 0; i < facet.length; i++) {
        ID3[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID, postData, file);
      }
      testStatus = verifyDraftAndSave(appUrl, serviceName, entityName, entityID, ID3);
    }
    if (!testStatus) {
      fail("Could not upload sample.exe " + response);
    }
  }

  @Test
  @Order(6)
  void testUploadPDFDuplicate() throws IOException {
    System.out.println("Test (6) : Upload duplicate PDF as attachment, reference, and footnote");
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if ("Entity in draft mode".equals(response)) {
      Boolean allFacetsFailedCorrectly = true;
      for (int i = 0; i < facet.length; i++) {
        List<String> facetResponse =
            api.createAttachment(appUrl, entityName, facet[i], entityID, srvpath, postData, file);
        allFacetsFailedCorrectly &= checkDuplicateCreation(facet[i], facetResponse);
      }
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (!allFacetsFailedCorrectly) {
        fail("One or more facets were incorrectly accepted as new.");
      }
    } else {
      fail("Entity could not be edited to draft mode.");
    }
  }

  @Test
  @Order(7)
  void testUploadSinglePDFWithAttachmentReferenceFootnote() throws IOException {
    System.out.println(
        "Test (7) : Upload duplicate PDF in different entity with attachment, reference, and footnote");
    Boolean testStatus = false;
    // Create a new entity draft
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      entityID2 = response;
      response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID2);

      if ("Saved".equals(response)) {
        response = api.checkEntity(appUrl, entityName, entityID2);
        if ("Entity exists".equals(response)) {
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

    // Edit entity to draft mode
    response = api.editEntityDraft(appUrl, entityName, srvpath, entityID2);
    if ("Entity in draft mode".equals(response)) {
      // Create attachment, reference, and footnote
      for (int i = 0; i < facet.length; i++) {
        ID4[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID2, postData, file);
      }
      // Verify and save
      testStatus = verifyDraftAndSave(appUrl, serviceName, entityName, entityID2, ID4);
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf as an attachment, reference, or footnote: " + response);
    }
  }

  @Test
  @Order(8)
  void testRenameEntities() {
    System.out.println("Test (8) : Rename single attachment, reference, and footnote");
    Boolean testStatus = true;

    try {
      String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);

      if ("Entity in draft mode".equals(response)) {
        String[] name = {"sample123", "reference123", "footnote123"};
        for (int i = 0; i < facet.length; i++) {
          // Read the facet to ensure it exists
          response = api.renameAttachment(appUrl, entityName, facet[i], entityID, ID[i], name[i]);
          if (!"Renamed".equals(response)) {
            testStatus = false;
            System.out.println(facet[i] + " was not renamed: " + response);
          }
        }
        // Save entity draft if everything is renamed
        if (testStatus) {
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          if (!"Saved".equals(response)) {
            testStatus = false;
            System.out.println("Entity draft was not saved: " + response);
          }
        } else {
          // Attempt save despite potential rename failures
          api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        }
      } else {
        testStatus = false;
        System.out.println("Entity was not put into draft mode: " + response);
      }
    } catch (Exception e) {
      testStatus = false;
      System.out.println("Exception during renaming entities: " + e.getMessage());
    }

    if (!testStatus) {
      fail("There was an error during the rename test process.");
    }
  }

  @Test
  @Order(9)
  void testCreateEntitiesWithUnsupportedCharacter() throws IOException {
    System.out.println("Test (9): Create attachments with unsupported characters");
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

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (!"Entity in draft mode".equals(response)) {
      fail("Entity not in draft mode: " + response);
      return;
    }

    for (int i = 0; i < facet.length; i++) {
      postData.put("up__ID", entityID);
      List<String> createResponse =
          api.createAttachment(appUrl, entityName, facet[i], entityID, srvpath, postData, tempFile);

      String check = createResponse.get(0);
      if (!"Attachment created".equals(check)) {
        System.out.println("Failed to create attachment for facet: " + facet[i]);
        continue;
      }

      String restrictedName = "a/\\bc.pdf";
      response =
          api.renameAttachment(appUrl, entityName, facet[i], entityID, ID4[i], restrictedName);
      System.out.println("Rename response for " + facet[i] + ": " + response);
    }

    response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);

    String expected =
        "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported "
            + "characters (/, \\\\). \\n\\n\\t\\u2022 a/\\bc.pdf\\n\\nRename the files and try again.\",\"numericSeverity"
            + "\":3},{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters "
            + "(/, \\\\). \\n\\n\\t\\u2022 a/\\bc.pdf\\n\\nRename the files and try again.\",\"numericSeverity\":3},{\"code\":\"<none>\","
            + "\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters (/, \\\\). \\n\\n\\t\\u2022 "
            + "a/\\bc.pdf\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
    if (response.equals(expected)) {
      testStatus = true;
    }

    if (!testStatus) {
      fail("Facets renamed with restricted characters were not correctly rejected.");
    }
  }

  @Test
  @Order(10)
  void testRenameEntitiesWithUnsupportedCharacter() {
    System.out.println("Test (10) : Rename attachments with unsupported characters");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String[] name = {"sample/1234", "reference1/234", "footnote1/234"};
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response = api.renameAttachment(appUrl, entityName, facet[i], entityID, ID3[i], name[i]);
        if (response.equals("Renamed")) counter++;
      }
      if (counter >= 2) {
        counter = -1; // Reset counter for the next check
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters (/, \\\\). \\n\\n\\t\\u2022 sample/1234\\n\\nRename the files and try again.\",\"numericSeverity\":3},"
                + "{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters (/, \\\\). \\n\\n\\t\\u2022 reference1/234\\n\\nRename the files and try again.\",\"numericSeverity\":3},"
                + "{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters (/, \\\\). \\n\\n\\t\\u2022 footnote1/234\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
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
  @Order(11)
  void testRenameMultipleEntityComponents() {
    System.out.println("Test (11) : Rename multiple attachments, references, and footnotes");
    boolean testStatus = true;
    String draftResponse = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (!"Entity in draft mode".equals(draftResponse)) {
      fail("Entity is not in draft mode.");
      return;
    }
    String[] name = {"sample1234", "reference1234", "footnote1234"};
    String[] name2 = {"sample12345", "reference12345", "footnote12345"};
    for (int i = 0; i < facet.length; i++) {
      // Read the facet to ensure it exists
      testStatus &= renameAndCheck(facet[i], ID2[i], entityID, name[i]);
      testStatus &= renameAndCheck(facet[i], ID3[i], entityID, name2[i]);
    }
    // Save the draft if all renames succeeded
    if (testStatus) {
      String saveResponse = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      if (!"Saved".equals(saveResponse)) {
        fail("Entity draft was not saved after renaming.");
      }
    } else {
      // Save draft even if renaming failed to preserve state
      api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      fail("One or more components were not renamed.");
    }
  }

  @Test
  @Order(12)
  void testRenameSingleDuplicate() {
    System.out.println("Test (12) : Rename duplicates for attachment, reference, and footnote");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    String[] name = {"sample1234", "reference1234", "footnote1234"};
    String[] name2 = {"sample123456", "reference123456", "footnote123456"};
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response = api.renameAttachment(appUrl, entityName, facet[i], entityID, ID3[i], name[i]);
        if (response.equals("Renamed")) counter++;
      }
      if (counter >= 2) {
        counter = -1; // Reset counter for the next check
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            String.format(
                "{\"error\":{\"code\":\"400\",\"message\":\"The file(s) %s have been added multiple times. Please rename and try again.\","
                    + "\"details\":["
                    + "{\"code\":\"<none>\",\"message\":\"The file(s) %s have been added multiple times. Please rename and try again.\",\"@Common.numericSeverity\":4},"
                    + "{\"code\":\"<none>\",\"message\":\"The file(s) %s have been added multiple times. Please rename and try again.\",\"@Common.numericSeverity\":4}"
                    + "]}}",
                name[0], name[1], name[2]);
        if (response.equals(expected)) {
          for (int i = 0; i < facet.length; i++) {
            // Attempt to rename again with a different name
            response =
                api.renameAttachment(appUrl, entityName, facet[i], entityID, ID3[i], name2[i]);
            if (response.equals("Renamed")) counter++;
          }
        }
        if (counter >= 2) {
          // If all renames were successful, save the draft
          response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            testStatus = true;
          }
        } else {
          testStatus = false;
          fail("Attachment was renamed");
        }
      } else {
        api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      }
    }
  }

  @Test
  @Order(13)
  void testRenameMultipleEntitiesWithOneUnsupportedCharacter() {
    System.out.println(
        "Test (13) : Rename multiple files out of which one file name contains unsupported characters");
    boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);

    String[] names = {"summary_1234", "reference_4567", "note/invalid"};

    if (response.equals("Entity in draft mode")) {
      int successCount = 0;
      for (int i = 0; i < facet.length; i++) {
        response = api.renameAttachment(appUrl, entityName, facet[i], entityID, ID3[i], names[i]);
        if (response.equals("Renamed")) successCount++;
      }

      if (successCount >= 2) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
        String expected =
            "[{\"code\":\"<none>\",\"message\":\"Rename unsuccessful. The following filename(s) contain unsupported characters"
                + " (/, \\\\). \\n\\n\\t\\u2022 note/invalid\\n\\nRename the files and try again.\",\"numericSeverity\":3}]";
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
  void testRenameEntitiesWithoutSDMRole() throws IOException {
    //    System.out.println("Test (14) : Rename attachments where user don't have SDM-Roles");
    //    boolean testStatus = true;
    //    try {
    //      String apiResponse = apiNoRoles.editEntityDraft(appUrl, entityName, srvpath, entityID);
    //      if ("Entity in draft mode".equals(apiResponse)) {
    //        String[] name = {"sample456", "reference456", "footnote456"};
    //        for (int i = 0; i < facet.length; i++) {
    //          apiResponse =
    //              apiNoRoles.renameAttachment(appUrl, entityName, facet[i], entityID, ID[i],
    // name[i]);
    //          if (!"Renamed".equals(apiResponse)) {
    //            testStatus = false;
    //            System.out.println(facet[i] + " was not renamed: " + apiResponse);
    //          }
    //        }
    //        if (testStatus) {
    //          apiResponse = apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
    //          System.out.println("response is " + apiResponse);
    //          String expected =
    //              "[{\"code\":\"<none>\",\"message\":\"Could not update the following
    // files.\\n\\n\\t\\u2022 sample123\\n\\nYou do not have the required permissions to update
    // attachments. Kindly contact the admin\",\"numericSeverity\":3},"
    //                  + "{\"code\":\"<none>\",\"message\":\"Could not update the following
    // files.\\n\\n\\t\\u2022 reference123\\n\\nYou do not have the required permissions to update
    // attachments. Kindly contact the admin\",\"numericSeverity\":3},"
    //                  + "{\"code\":\"<none>\",\"message\":\"Could not update the following
    // files.\\n\\n\\t\\u2022 footnote123\\n\\nYou do not have the required permissions to update
    // attachments. Kindly contact the admin\",\"numericSeverity\":3}]";
    //          if (!apiResponse.equals(expected)) {
    //            testStatus = false;
    //            System.out.println("Entity draft not saved: " + apiResponse);
    //          }
    //        } else {
    //          apiNoRoles.saveEntityDraft(appUrl, entityName, srvpath, entityID);
    //        }
    //      }
    //    } catch (Exception e) {
    //      testStatus = false;
    //      System.out.println("Exception during renaming entities: " + e.getMessage());
    //    }
    //    if (!testStatus) {
    //      fail("Attachment got renamed without SDM roles.");
    //    }
  }

  @Test
  @Order(15)
  void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (15) : Delete single attachment, reference, and footnote");
    Boolean testStatus = false;
    counter = -1;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response = api.deleteAttachment(appUrl, entityName, facet[i], entityID, ID[i]);
        if (response.equals("Deleted")) counter++;
      }
      if (counter >= 2) response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
      counter = -1; // Reset counter for the next check
      if (response.equals("Saved")) {
        for (int i = 0; i < facet.length; i++) {
          response = api.readAttachment(appUrl, entityName, facet[i], entityID, ID[i]);
          if (response.equals("Could not read Attachment")) counter++;
        }
        if (counter >= 2) testStatus = true;
        else fail("Could not read deleted facets");
      } else {
        fail("Could not save entity after deletion");
      }
    }
  }

  @Test
  @Order(16)
  void testDeleteMultipleAttachmentsReferencesFootnotes() throws IOException {
    System.out.println("Test (16) : Delete multiple attachments, references, and footnotes");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        String response1 = api.deleteAttachment(appUrl, entityName, facet[i], entityID, ID2[i]);
        String response2 = api.deleteAttachment(appUrl, entityName, facet[i], entityID, ID3[i]);
        if (response1.equals("Deleted") && response2.equals("Deleted")) counter++;
      }
    }
    if (counter >= 2) response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID);
    if (response.equals("Saved")) {
      for (int i = 0; i < facet.length; i++) {
        String response1 = api.readAttachment(appUrl, entityName, facet[i], entityID, ID2[i]);
        String response2 = api.readAttachment(appUrl, entityName, facet[i], entityID, ID3[i]);
        if (response1.equals("Could not read " + facet[i])
            && response2.equals("Could not read " + facet[i])) {
          counter++;
        }
      }
      if (counter >= 2) testStatus = true;
      else fail("Could not read deleted facets");
    } else fail("Could not save entity after deletion");
  }

  @Test
  @Order(17)
  void testDeleteEntity() {
    System.out.println("Test (17) : Delete entity");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, entityName, entityID);
    String response2 = api.deleteEntity(appUrl, entityName, entityID2);
    if (response.equals("Entity Deleted") && response2.equals("Entity Deleted")) testStatus = true;
    if (!testStatus) fail("Could not delete entity");
  }

  @Test
  @Order(18)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println("Test (18) : Rename & Update secondary property before entity is saved");
    System.out.println("Creating entity");

    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (!response.equals("Could not create entity")) {
      entityID3 = response;

      System.out.println("Creating attachment, reference, and footnote");

      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      for (int i = 0; i < facet.length; i++) {
        ID[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      System.out.println("Attachments, References, and Footnotes created");

      // Use valid dropdown value for customProperty1
      Integer secondaryPropertyInt = 1234;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      String[] name = {"sample1234.pdf", "reference1234.pdf", "footnote1234.pdf"};

      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name[i]);

        // Update customProperty1 (String - dropdown value)
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);

        // Update customProperty2 (Integer)
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);

        // Update customProperty5 (DateTime)
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);

        // Update customProperty6 (Boolean)
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) {
          counter++;
        }
      }

      if (counter >= 2) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
      }
      if (response.equals("Saved")) {
        testStatus = true;
      }
    }

    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(19)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_single() {
    System.out.println("Test (19): Rename & Update secondary property after entity is saved");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    System.out.println("Editing entity");

    if (response.equals("Entity in draft mode")) {
      // Sample secondary properties
      String name[] = {"sample.pdf", "reference_sample.pdf", "footnote_sample.pdf"};
      Integer secondaryPropertyInt = 42;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      System.out.println("Renaming and updating secondary properties for attachment");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name[i]);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2) response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
      if (response.equals("Saved")) {
        testStatus = true;
        System.out.println("Renamed & updated Secondary properties for attachment");
      }
      // Clean up
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) fail("Could not delete entity");
    }
    if (!testStatus) fail("Could not update secondary properties after entity is saved");
  }

  @Test
  @Order(20)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println(
        "Test (20): Rename & Update invalid secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      for (int i = 0; i < facet.length; i++) {
        ID[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }
      // Prepare test data
      String name1 = "sample1234.pdf";
      Integer secondaryPropertyInt = 1234;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testid";

      for (int i = 0; i < facet.length; i++) {
        // Rename and update secondary properties
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for invalid ID
        String updateSecondaryPropertyResponse4 =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], invalidProperty);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2) response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
      for (int i = 0; i < facet.length; i++) {
        Map<String, Object> FacetMetadata =
            api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID[i]);
        assertEquals("sample.pdf", FacetMetadata.get("fileName"));
        assertNull(FacetMetadata.get("customProperty3"));
        assertNull(FacetMetadata.get("customProperty4"));
        assertNull(FacetMetadata.get("customProperty1_code"));
        assertNull(FacetMetadata.get("customProperty2"));
        assertNull(FacetMetadata.get("customProperty6"));
        assertNull(FacetMetadata.get("customProperty5"));
      }
      String expectedResponse =
          "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
              + //
              "\\n"
              + //
              "\\t\\u2022 id1\\n"
              + //
              "\\n"
              + //
              "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
              + //
              "\\n"
              + //
              "\\t\\u2022 id1\\n"
              + //
              "\\n"
              + //
              "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
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
        System.out.println("Rename & update secondary properties for attachment is unsuccessfull");
      }
    }
    if (!testStatus)
      fail(
          "Could not update secondary property before entity is saved for attachment, reference, or footnote");
  }

  @Test
  @Order(21)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_single() throws IOException {
    System.out.println(
        "Test (21): Rename & Update invalid secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response.equals("Entity in draft mode")) {
      String name1 = "sample.pdf";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testidinvalid";

      for (int i = 0; i < facet.length; i++) {
        // Rename and update secondary properties
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);
        // Update secondary properties for Drop down
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for invalid ID
        String updateSecondaryPropertyResponse4 =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], invalidProperty);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2) response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
      for (int i = 0; i < facet.length; i++) {
        Map<String, Object> FacetMetadata =
            api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID[i]);
        assertEquals("sample.pdf", FacetMetadata.get("fileName"));
        assertNull(FacetMetadata.get("customProperty3"));
        assertNull(FacetMetadata.get("customProperty4"));
        assertNull(FacetMetadata.get("customProperty1_code"));
        assertNull(FacetMetadata.get("customProperty2"));
        assertNull(FacetMetadata.get("customProperty6"));
        assertNull(FacetMetadata.get("customProperty5"));
      }
      String expectedResponse =
          "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
              + //
              "\\n"
              + //
              "\\t\\u2022 id1\\n"
              + //
              "\\n"
              + //
              "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
              + //
              "\\n"
              + //
              "\\t\\u2022 id1\\n"
              + //
              "\\n"
              + //
              "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
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
            "Rename & update secondary properties for attachment, reference, footnote is unsuccessfull");
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    }
    if (!testStatus)
      fail(
          "Could not update secondary property after entity is saved for attachment, reference, or footnote");
  }

  @Test
  @Order(22)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (22): Rename & Update valid secondary properties for multiple facets before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;

      System.out.println("Entity created");
      ClassLoader classLoader = getClass().getClassLoader();

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      System.out.println("Creating attachment, reference, and footnote PDF");
      File file = new File(classLoader.getResource("sample.pdf").getFile());
      for (int i = 0; i < facet.length; i++) {
        ID[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      System.out.println("Creating attachment, reference, and footnote TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      postData.put("mimeType", "application/txt");
      for (int i = 0; i < facet.length; i++) {
        ID2[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      System.out.println("Creating attachment, reference, and footnote EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      postData.put("mimeType", "application/exe");
      for (int i = 0; i < facet.length; i++) {
        ID3[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }
      Boolean Updated1[] = new Boolean[3];
      Boolean Updated2[] = new Boolean[3];
      Boolean Updated3[] = new Boolean[3];
      String name1 = "sample1234.pdf";
      Integer secondaryPropertyInt1 = 1234;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      // PDF
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) {
          Updated1[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " PDF");
        }
      }

      // TXT
      System.out.println("Renaming and updating secondary properties for TXT");
      for (int i = 0; i < facet.length; i++) {
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID2[i], bodyBool);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }

      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        // Update secondary properties for String
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID3[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID3[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID3[i], bodyDate);

        if (updateSecondaryPropertyResponseEXE1.equals("Updated")
            && updateSecondaryPropertyResponseEXE2.equals("Updated")
            && updateSecondaryPropertyResponseEXE3.equals("Updated")) {
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
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println("Renamed & updated Secondary properties");
        }
      }
    }
    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(23)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_multipleAttachments() {
    System.out.println(
        "Test (23): Rename & Update  valid secondary properties for multiple facets after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response.equals("Entity in draft mode")) {
      Boolean Updated1[] = new Boolean[3];
      Boolean Updated2[] = new Boolean[3];
      Boolean Updated3[] = new Boolean[3];

      String name1 = "sample1.pdf";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);
        // Update secondary properties for Drop down
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) {
          Updated1[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " PDF");
        }
      }

      // TXT
      System.out.println("Renaming and updating secondary properties for TXT");
      for (int i = 0; i < facet.length; i++) {
        //  Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID2[i], bodyBool);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }

      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        // Update secondary properties for Drop down
        String dropdownValue = integrationTestUtils.getDropDownValue();
        String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID3[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID3[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID3[i], bodyDate);

        if (updateSecondaryPropertyResponseEXE1.equals("Updated")
            && updateSecondaryPropertyResponseEXE2.equals("Updated")
            && updateSecondaryPropertyResponseEXE3.equals("Updated")) {
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
  @Order(24)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (24): Rename & Update invalid and valid secondary properties for multiple facets before entity is saved");
    System.out.println("Creating entity");

    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (!"Could not create entity".equals(response)) {
      entityID3 = response;
      System.out.println("Entity created");

      ClassLoader classLoader = getClass().getClassLoader();
      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      // Create PDF attachments
      postData.put("mimeType", "application/pdf");
      File file = new File(classLoader.getResource("sample.pdf").getFile());
      for (int i = 0; i < facet.length; i++) {
        ID[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      // Create TXT attachments
      postData.put("mimeType", "application/txt");
      file = new File(classLoader.getResource("sample.txt").getFile());
      for (int i = 0; i < facet.length; i++) {
        ID2[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      // Create EXE attachments
      postData.put("mimeType", "application/exe");
      file = new File(classLoader.getResource("sample.exe").getFile());
      for (int i = 0; i < facet.length; i++) {
        ID3[i] =
            CreateandReturnFacetID(
                appUrl, serviceName, entityName, facet[i], entityID3, postData, file);
      }

      Boolean[] Updated1 = new Boolean[3];
      Boolean[] Updated2 = new Boolean[3];
      Boolean[] Updated3 = new Boolean[3];

      String name1 = "sample1234.pdf";
      String dropdownValue =
          integrationTestUtils.getDropDownValue(); // returns a plain string like "option-123"
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";
      Integer secondaryPropertyInt1 = 1234;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";

      // Update PDF properties
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String renameResp =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);

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
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        String upd2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        String upd3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        String upd4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);
        String updInvalid =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], invalidPropertyPDF);

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
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID2[i], bodyBool);
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
                appUrl, entityName, facet[i], entityID3, ID3[i], bodyDropdownExe);
        String upd2 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID3[i], bodyIntExe);

        if ("Updated".equals(upd1) && "Updated".equals(upd2)) {
          Updated3[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " EXE");
        }
      }

      if (Arrays.stream(Updated1).allMatch(Boolean.TRUE::equals)
          && Arrays.stream(Updated2).allMatch(Boolean.TRUE::equals)
          && Arrays.stream(Updated3).allMatch(Boolean.TRUE::equals)) {

        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        String[] expectedNames = {"sample.pdf", "sample.txt", "sample.exe"};

        // Verify PDF metadata
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> metadata =
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID[i]);
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
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID2[i]);
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
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID3[i]);
          assertEquals(expectedNames[2], metadata.get("fileName"));
          assertNull(metadata.get("customProperty3"));
          assertNull(metadata.get("customProperty4"));
          assertEquals(
              dropdownValueExe,
              metadata.get("customProperty1_code")); // Adjust expected value if needed
          assertEquals(1234, metadata.get("customProperty2"));
        }

        String expectedResponse =
            "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
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
              "Rename & update unsuccessful for invalid properties and successful for valid attachments");
        }
      }
    }

    if (!testStatus) {
      fail("Could not update secondary property before entity is saved");
    }
  }

  @Test
  @Order(25)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (25): Rename & Update invalid and valid secondary properties for multiple attachments after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, entityName, srvpath, entityID3);
    if (response.equals("Entity in draft mode")) {
      Boolean Updated1[] = new Boolean[3];
      Boolean Updated2[] = new Boolean[3];
      Boolean Updated3[] = new Boolean[3];
      String name1 = "sample.pdf";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";
      String dropdownValue = integrationTestUtils.getDropDownValue();
      System.out.println("drop down value is: " + dropdownValue);
      String jsonDropdown = "{ \"customProperty1_code\" : \"" + dropdownValue + "\" }";

      // PDF
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameAttachment(appUrl, entityName, facet[i], entityID3, ID[i], name1);
        // Update secondary properties for String
        RequestBody bodyDropdown =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], bodyDropdown);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt1 + "\n}"));
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyInt);
        // Update secondary properties for LocalDateTime
        RequestBody bodyDate =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty5\" : \"" + secondaryPropertyDateTime1 + "\"\n}"));
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyDate);
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + true + "\n}"));
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID[i], bodyBool);
        // Update invalid secondary property
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID[i], invalidPropertyPDF);

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
        // Update secondary properties for Boolean
        RequestBody bodyBool =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8("{\n    \"customProperty6\" : " + false + "\n}"));
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID2[i], bodyBool);
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
        // Update secondary properties for String
        System.out.println("drop down value is: " + dropdownValue1);
        String jsonDropdown1 = "{ \"customProperty1_code\" : \"" + dropdownValue1 + "\" }";
        RequestBody bodyDropdown1 =
            RequestBody.create(MediaType.parse("application/json"), jsonDropdown1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl, entityName, facet[i], entityID3, ID3[i], bodyDropdown1);
        // Update secondary properties for Integer
        RequestBody bodyInt =
            RequestBody.create(
                MediaType.parse("application/json"),
                ByteString.encodeUtf8(
                    "{\n    \"customProperty2\" : " + secondaryPropertyInt3 + "\n}"));
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(appUrl, entityName, facet[i], entityID3, ID3[i], bodyInt);

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
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID3);
        String name[] = {"sample.pdf", "sample.txt", "sample.exe"};
        // for PDF
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID[i]);
          assertEquals(name[0], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("customProperty3"));
          assertNull(FacetMetadata.get("customProperty4"));
          assertNull(FacetMetadata.get("customProperty1_code"));
          assertNull(FacetMetadata.get("customProperty2"));
          assertNull(FacetMetadata.get("customProperty6"));
          assertNull(FacetMetadata.get("customProperty5"));
        }
        // for TXT
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID2[i]);
          assertEquals(name[1], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("customProperty3"));
          assertNull(FacetMetadata.get("customProperty4"));
          assertNull(FacetMetadata.get("customProperty1_code"));
          assertNull(FacetMetadata.get("customProperty2"));
          assertFalse((Boolean) FacetMetadata.get("customProperty6"));
          assertNull(FacetMetadata.get("customProperty5"));
        }
        // for EXE
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchMetadata(appUrl, entityName, facet[i], entityID3, ID3[i]);
          assertEquals(name[2], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("customProperty3"));
          assertNull(FacetMetadata.get("customProperty4"));
          assertEquals(dropdownValue1, FacetMetadata.get("customProperty1_code"));
          assertEquals(12, FacetMetadata.get("customProperty2"));
        }

        String expectedResponse =
            "[{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
                + //
                "\\n"
                + //
                "\\t\\u2022 id1\\n"
                + //
                "\\n"
                + //
                "Please contact your administrator for assistance with any necessary adjustments.\",\"numericSeverity\":3},{\"code\":\"<none>\",\"message\":\"The following secondary properties are not supported.\\n"
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
  @Order(26)
  void testNAttachments_NewEntity() throws IOException {
    System.out.println(
        "Test (26): Creating new entity and checking only max 4 attachments are allowed to be uploaded");
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
          api.createAttachment(appUrl, entityName, facet[0], entityID4, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")) {
        ID[0] = createResponse1.get(1);
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
          api.createAttachment(appUrl, entityName, facet[0], entityID4, srvpath, postData2, file);
      if (createResponse2.get(0).equals("Attachment created")) {
        ID2[0] = createResponse2.get(1);
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
          api.createAttachment(appUrl, entityName, facet[0], entityID4, srvpath, postData3, file);
      if (createResponse3.get(0).equals("Attachment created")) {
        ID[0] = createResponse3.get(1);
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
          api.createAttachment(appUrl, entityName, facet[0], entityID4, srvpath, postData3, file);
      if (createResponse4.get(0).equals("Attachment created")) {
        ID4[0] = createResponse4.get(1);
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
          api.createAttachment(appUrl, entityName, facet[0], entityID4, srvpath, postData3, file);
      if (createResponse5.get(0).equals("Only 4 attachments allowed.")) {
        testStatus = true;
        ID5[0] = createResponse5.get(1);
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
  @Order(27)
  void testUploadNAttachments() throws IOException {
    System.out.println("Test (27): Upload maximum 4 attachments in an exsisting entity");

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
                appUrl, entityName, facet[0], entityID4, srvpath, postData, tempFile);

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
  @Order(28)
  void testDiscardDraftWithoutAttachments() {
    System.out.println("Test (28) : Discard draft without adding attachments");
    Boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID6 = response;
      response = api.deleteEntityDraft(appUrl, entityName, entityID6);
      if (response.equals("Entity Deleted")) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Draft was not discarded properly");
    }
  }

  @Test
  @Order(29)
  void testDiscardDraftWithAttachments() throws IOException {
    System.out.println("Test (29): Discard draft with attachments, references, and footnotes");
    boolean testStatus = false;

    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!"Could not create entity".equals(response)) {
      entityID6 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID6);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");
      for (int i = 0; i < facet.length; i++) {
        List<String> createResponse =
            api.createAttachment(appUrl, entityName, facet[i], entityID6, srvpath, postData, file);
        if ("Attachment created".equals(createResponse.get(0))) {
          System.out.println("Attachment created in facet: " + facet[i]);
        } else {
          System.out.println("Attachment creation failed in facet: " + facet[i]);
        }
      }
      response = api.deleteEntityDraft(appUrl, entityName, entityID6);
      if ("Entity Deleted".equals(response)) {
        testStatus = true;
      }
    }
    if (!testStatus) {
      fail("Draft with attachments was not discarded properly");
    }
  }

  @Test
  @Order(30)
  void testDraftUpdateUploadTwoDeleteOneAndCreate() throws IOException {
    System.out.println("Test (30): Upload to all facets, delete one, and create entity");

    boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, entityName, entityName2, srvpath);

    if (!"Could not create entity".equals(response)) {
      entityID5 = response;
      ClassLoader classLoader = getClass().getClassLoader();

      File file1 =
          new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());
      File file2 =
          new File(Objects.requireNonNull(classLoader.getResource("sample.txt")).getFile());

      Map<String, Object> postData1 = new HashMap<>();
      postData1.put("up__ID", entityID5);
      postData1.put("mimeType", "application/pdf");
      postData1.put("createdAt", new Date().toString());
      postData1.put("createdBy", "test@test.com");
      postData1.put("modifiedBy", "test@test.com");

      Map<String, Object> postData2 = new HashMap<>(postData1);
      postData2.put("up__ID", entityID5);
      postData2.put("mimeType", "text/plain");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      boolean allCreated = true;
      for (int i = 0; i < facet.length; i++) {
        List<String> response1 =
            api.createAttachment(
                appUrl, entityName, facet[i], entityID5, srvpath, postData1, file1);
        List<String> response2 =
            api.createAttachment(
                appUrl, entityName, facet[i], entityID5, srvpath, postData2, file2);

        if (response1.get(0).equals("Attachment created")
            && response2.get(0).equals("Attachment created")) {
          ID4[i] = response1.get(1); // to keep one
          ID5[i] = response2.get(1); // will delete this one
        } else {
          allCreated = false;
          break;
        }

        String deleteResponse =
            api.deleteAttachment(appUrl, entityName, facet[i], entityID5, ID5[i]);
        if (!"Deleted".equals(deleteResponse)) {
          allCreated = false;
          break;
        }
      }

      if (allCreated) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);
        if ("Saved".equals(response)) {
          testStatus = true;
        }
      }
    }

    if (!testStatus) {
      fail("Failed to upload multiple facet entries, delete one per facet and create entity");
    }
  }

  @Test
  @Order(31)
  void testUpdateEntityDraft() throws IOException {
    System.out.println("Test (31): Update entity draft with new facet content");
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
    if ("Entity in draft mode".equals(response)) {
      boolean allCreated = true;

      for (int i = 0; i < facet.length; i++) {
        List<String> createResponse =
            api.createAttachment(
                appUrl, entityName, facet[i], entityID5, srvpath, postData, tempFile);
        if (!"Attachment created".equals(createResponse.get(0))) {
          allCreated = false;
        }
      }

      if (allCreated) {
        response = api.saveEntityDraft(appUrl, entityName, srvpath, entityID5);
        if ("Saved".equals(response)) {
          testStatus = true;
        }
      }
    }
    api.deleteEntity(appUrl, entityName, entityID5);
    if (!testStatus) {
      fail("Failed to update draft with new attachments for all facets");
    }
  }

  @Test
  @Order(32)
  void testUploadAttachmentWithoutSDMRole() throws IOException {
    System.out.println("Test (32): Upload attachment across facets without SDM role");
    boolean testStatus = true;

    String response = apiNoRoles.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (!response.equals("Could not create entity")) {
      entityID7 = response;
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(Objects.requireNonNull(classLoader.getResource("sample.pdf")).getFile());

      File tempFile = new File(System.getProperty("java.io.tmpdir"), "sample3.pdf");
      Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID7);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      for (int i = 0; i < facet.length; i++) {
        List<String> createResponse =
            apiNoRoles.createAttachment(
                appUrl, entityName, facet[i], entityID7, srvpath, postData, tempFile);
        String check = createResponse.get(0);
        String expectedError =
            "{\"error\":{\"code\":\"500\",\"message\":\"You do not have the required permissions to upload attachments. Please contact your administrator for access.\"}}";
        if (!expectedError.equals(check)) {
          testStatus = false;
        }
      }
    }

    if (!testStatus) {
      fail("Attachment uploaded without SDM role for one or more facets");
    }
  }

  @Test
  @Order(33)
  void testCopyAttachmentsSuccessNewEntity() throws IOException {
    System.out.println("Test (33): Copy attachments from one entity to another new entity");
    List<List<String>> attachments = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      attachments.add(new ArrayList<>());
    }
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

      for (int i = 0; i < facet.length; i++) {
        for (File file : files) {
          List<String> createResponse =
              api.createAttachment(
                  appUrl,
                  entityName,
                  facet[i],
                  copyAttachmentSourceEntity,
                  srvpath,
                  postData,
                  file);
          if (createResponse.get(0).equals("Attachment created")) {
            attachments.get(i).add(createResponse.get(1));
          } else {
            fail("Could not create attachment");
          }
        }
      }
      api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
      List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
      Map<String, Object> fetchAttachmentMetadataResponse;
      for (int i = 0; i < attachments.size(); i++) {
        for (String attachment : attachments.get(i)) {
          try {
            fetchAttachmentMetadataResponse =
                api.fetchMetadata(
                    appUrl, entityName, facet[i], copyAttachmentSourceEntity, attachment);
            attachmentsMetadata.add(fetchAttachmentMetadataResponse);
          } catch (IOException e) {
            fail("Could not fetch attachment metadata: " + e.getMessage());
          }
        }
      }
      for (Map<String, Object> metadata : attachmentsMetadata) {
        if (metadata.containsKey("objectId")) {
          sourceObjectIds.add(metadata.get("objectId").toString());
        } else {
          fail("Attachment metadata does not contain objectId");
        }
      }

      if (sourceObjectIds.size() == 6) {
        String copyResponse;
        int i = 0;
        for (String facetName : facet) {
          if (i != 0) {
            String editResponse =
                api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
            if (!editResponse.equals("Entity in draft mode")) {
              fail("Could not edit target entity draft");
            }
          }
          copyResponse =
              api.copyAttachment(
                  appUrl,
                  entityName,
                  facetName,
                  copyAttachmentTargetEntity,
                  sourceObjectIds.subList(i, Math.min(i + 2, sourceObjectIds.size())));
          i += 2;
          if (copyResponse.equals("Attachments copied successfully")) {
            String saveEntityResponse =
                api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
            if (saveEntityResponse.equals("Saved")) {
              List<Map<String, Object>> fetchEntityMetadataResponse;
              fetchEntityMetadataResponse =
                  api.fetchEntityMetadata(
                      appUrl, entityName, facetName, copyAttachmentTargetEntity);
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
    System.out.println(
        "Test (34): Copy incorrect attachments from one entity to another new entity");
    String editResponse1 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
    copyAttachmentTargetEntityEmpty =
        api.createEntityDraft(appUrl, entityName, entityName2, srvpath);
    if (editResponse1.equals("Entity in draft mode")
        && !copyAttachmentTargetEntityEmpty.equals("Could not create entity")) {
      if (sourceObjectIds.size() == 6) {
        int i = 0;
        for (String facet : facet) {
          try {
            List<String> currentFacetObjectIds =
                sourceObjectIds.subList(i, Math.min(i + 2, sourceObjectIds.size()));
            currentFacetObjectIds.add("incorrectObjectId");
            if (currentFacetObjectIds.size() != 3) {
              fail("Not enough object IDs to copy attachments for facet: " + facet);
            }
            api.copyAttachment(
                appUrl, entityName, facet, copyAttachmentTargetEntityEmpty, currentFacetObjectIds);
            fail("Copy attachments did not throw an error");
          } catch (IOException e) {
            i += 2;
          }
        }
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
    List<List<String>> attachments = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      attachments.add(new ArrayList<>());
    }
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
      for (int i = 0; i < facet.length; i++) {
        for (File file : files) {
          List<String> createResponse =
              api.createAttachment(
                  appUrl,
                  entityName,
                  facet[i],
                  copyAttachmentSourceEntity,
                  srvpath,
                  postData,
                  file);
          if (createResponse.get(0).equals("Attachment created")) {
            attachments.get(i).add(createResponse.get(1));
          } else {
            fail("Could not create attachment");
          }
        }
      }
      api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
      List<Map<String, Object>> attachmentsMetadata = new ArrayList<>();
      Map<String, Object> fetchAttachmentMetadataResponse;
      for (int i = 0; i < attachments.size(); i++) {
        for (String attachment : attachments.get(i)) {
          try {
            fetchAttachmentMetadataResponse =
                api.fetchMetadata(
                    appUrl, entityName, facet[i], copyAttachmentSourceEntity, attachment);
            attachmentsMetadata.add(fetchAttachmentMetadataResponse);
          } catch (IOException e) {
            fail("Could not fetch attachment metadata: " + e.getMessage());
          }
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

      if (sourceObjectIds.size() == 6) {
        String copyResponse;
        int i = 0;
        for (String facetName : facet) {
          if (i != 0) {
            String editResponse =
                api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
            if (!editResponse.equals("Entity in draft mode")) {
              fail("Could not edit target entity draft");
            }
          }
          List<String> currentFacetObjectIds =
              sourceObjectIds.subList(i, Math.min(i + 2, sourceObjectIds.size()));
          if (currentFacetObjectIds.size() != 2) {
            fail("Not enough object IDs to copy attachments for facet: " + facet);
          }
          copyResponse =
              api.copyAttachment(
                  appUrl, entityName, facetName, copyAttachmentTargetEntity, currentFacetObjectIds);
          i += 2;
          if (copyResponse.equals("Attachments copied successfully")) {
            String saveEntityResponse =
                api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
            if (saveEntityResponse.equals("Saved")) {
              List<Map<String, Object>> fetchEntityMetadataResponse;
              fetchEntityMetadataResponse =
                  api.fetchEntityMetadata(
                      appUrl, entityName, facetName, copyAttachmentTargetEntity);
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
            } else {
              fail("Could not save entity after copying attachments: " + saveEntityResponse);
            }
          } else {
            fail("Could not copy attachments: " + copyResponse);
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
  @Order(36)
  void testCopyAttachmentsUnsuccessfulExistingEntity() throws IOException {
    System.out.println("Test (36): Copy attachments from one entity to another new entity");
    String editResponse1 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
    String editResponse2 =
        api.editEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
    if (editResponse1.equals("Entity in draft mode")
        && editResponse2.equals("Entity in draft mode")) {
      if (sourceObjectIds.size() == 6) {
        int i = 0;
        for (String facetName : facet) {
          List<String> currentFacetObjectIds =
              sourceObjectIds.subList(i, Math.min(i + 2, sourceObjectIds.size()));
          currentFacetObjectIds.add("incorrectObjectId");
          if (currentFacetObjectIds.size() != 3) {
            fail("Not enough object IDs to copy attachments for facet: " + facet);
          }
          try {
            api.copyAttachment(
                appUrl, entityName, facetName, copyAttachmentTargetEntity, sourceObjectIds);
            fail("Copy attachments did not throw an error");
          } catch (IOException e) {
            i += 2;
          }
        }
        api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentSourceEntity);
        api.saveEntityDraft(appUrl, entityName, srvpath, copyAttachmentTargetEntity);
        api.deleteEntity(appUrl, entityName, copyAttachmentTargetEntity);
        api.deleteEntity(appUrl, entityName, copyAttachmentSourceEntity);
      } else {
        fail("Could not fetch objects Ids for all attachments");
      }
    } else {
      fail("Could not edit entities");
    }
  }
}
