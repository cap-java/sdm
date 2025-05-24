package integration.com.sap.cds.sdm.multiple;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import integration.com.sap.cds.sdm.Credentials;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttachmentsSDMTest {
  private static String token;
  private static String entityID;
  private static String entityID2;
  private static String entityID3;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String serviceName = "UserService";
  private static String entityName = "Notebooks";
  private static String srvpath = "UserService";
  private static Api api;
  private static String attachmentID1 = "", referenceID1 = "", footnoteID1 = "";
  private static String attachmentID2 = "", referenceID2 = "", footnoteID2 = "";
  private static String attachmentID3 = "", referenceID3 = "", footnoteID3 = "";
  private static String attachmentID4 = "", referenceID4 = "", footnoteID4 = "";

  @BeforeAll
  static void setup() throws IOException {
    // Define your clientId and clientSecret
    Properties credentialsProperties = Credentials.getCredentials();
    String clientId = credentialsProperties.getProperty("clientID");
    String clientSecret = credentialsProperties.getProperty("clientSecret");
    appUrl = credentialsProperties.getProperty("appUrl");
    authUrl = credentialsProperties.getProperty("authUrl");
    username = credentialsProperties.getProperty("username");
    password = credentialsProperties.getProperty("password");

    // Encode clientId:clientSecret to Base64
    String credentials = clientId + ":" + clientSecret;
    String basicAuth =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    OkHttpClient client = new OkHttpClient().newBuilder().build();
    MediaType mediaType = MediaType.parse("text/plain");
    RequestBody body = RequestBody.create(mediaType, "");
    Request request =
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
    api = new Api(config);
  }

  private boolean verifyDraftAndSave(
      String appUrl,
      String serviceName,
      String entityName,
      String entityID,
      String attachmentID,
      String referenceID,
      String footnoteID)
      throws IOException {
    boolean status = false;

    String responseAttach =
        api.readAttachmentDraft(appUrl, serviceName, entityName, entityID, attachmentID);
    String responseRef =
        api.readReferenceDraft(appUrl, serviceName, entityName, entityID, referenceID);
    String responseFootNote =
        api.readFootnoteDraft(appUrl, serviceName, entityName, entityID, footnoteID);

    if ("OK".equals(responseAttach) && "OK".equals(responseRef) && "OK".equals(responseFootNote)) {
      String saveResponse = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if ("Saved".equals(saveResponse)) {
        String readAttach =
            api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID);
        String readRef = api.readReference(appUrl, serviceName, entityName, entityID, referenceID);
        String readFootNote =
            api.readFootnote(appUrl, serviceName, entityName, entityID, footnoteID);

        if ("OK".equals(readAttach) && "OK".equals(readRef) && "OK".equals(readFootNote)) {
          status = true;
        }
      }
    }
    return status;
  }

  private boolean checkDuplicateCreation(String facetType, List<String> createResponse)
      throws IOException {
    String creationCheck = createResponse.get(0);
    boolean wasCreated =
        (facetType + " created").equals(creationCheck); // Evaluating creation status
    if (wasCreated) {
      System.out.println(
          facetType + " was created when it should have been rejected as a duplicate.");
      return false;
    } else {
      String expectedJson =
          "{\"error\":{\"code\":\"500\",\"message\":\"sample.pdf already exists.\"}}";
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode actualJsonNode = objectMapper.readTree(creationCheck);
      JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
      if (expectedJsonNode.equals(actualJsonNode)) {
        System.out.println(facetType + " correctly failed due to duplicate upload.");
        return true;
      } else {
        System.out.println(facetType + " failed but with an unexpected error: " + creationCheck);
        return false;
      }
    }
  }

  private boolean renameAndCheck(String type, String id, String newName) {
    String result;
    switch (type.toLowerCase()) {
      case "attachment":
        result = api.renameAttachment(appUrl, serviceName, entityID, id, newName);
        break;
      case "reference":
        result = api.renameReference(appUrl, serviceName, entityID, id, newName);
        break;
      case "footnote":
        result = api.renameFootnote(appUrl, serviceName, entityID, id, newName);
        break;
      default:
        System.out.println("Unknown type: " + type);
        return false;
    }
    boolean renamed = "Renamed".equals(result);
    return renamed;
  }

  private boolean renameEntityAndCheckForDuplicates(
      String type, String id, String duplicateName, String newName) {
    boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

    if ("Entity in draft mode".equals(response)) {
      response = renameEntity(type, id, duplicateName);

      if ("Renamed".equals(response)) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        String expectedErrorMessage =
            String.format(
                "{\"error\":{\"code\":\"400\",\"message\":\"The file(s) %s have been added multiple times. Please rename and try again.\"}}",
                duplicateName);

        if (expectedErrorMessage.equals(response)) {
          response = renameEntity(type, id, newName);

          if ("Renamed".equals(response)) {
            response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

            if ("Saved".equals(response)) {
              testStatus = true;
            }
          }
        }
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }

    return testStatus;
  }

  private String renameEntity(String type, String id, String newName) {
    switch (type.toLowerCase()) {
      case "attachment":
        return api.renameAttachment(appUrl, serviceName, entityID, id, newName);
      case "reference":
        return api.renameReference(appUrl, serviceName, entityID, id, newName);
      case "footnote":
        return api.renameFootnote(appUrl, serviceName, entityID, id, newName);
      default:
        throw new IllegalArgumentException("Invalid type: " + type);
    }
  }

  @Test
  @Order(1)
  void testCreateEntityAndCheck() {
    System.out.println("Test (1) : Create entity and check if it exists");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      // Creation of attachment, reference and footnote
      List<String> attachmentResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> referenceResponse =
          api.createReference(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> footnoteResponse =
          api.createFootnote(appUrl, serviceName, entityName, entityID, srvpath, postData, file);

      // checking for response for attachment, reference and footnote
      String check1 = attachmentResponse.get(0);
      String check2 = referenceResponse.get(0);
      String check3 = footnoteResponse.get(0);
      if (check1.equals("Attachment created")
          && check2.equals("Reference created")
          && check3.equals("Footnote created")) {
        attachmentID1 = attachmentResponse.get(1);
        referenceID1 = referenceResponse.get(1);
        footnoteID1 = footnoteResponse.get(1);

        testStatus =
            verifyDraftAndSave(
                appUrl,
                serviceName,
                entityName,
                entityID,
                attachmentID1,
                referenceID1,
                footnoteID1);
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf as an attachments" + response);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if ("Entity in draft mode".equals(response)) {
      // Creation of attachment, reference, and footnote
      List<String> attachmentResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> referenceResponse =
          api.createReference(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> footnoteResponse =
          api.createFootnote(appUrl, serviceName, entityName, entityID, srvpath, postData, file);

      // Checking for response for attachment, reference, and footnote
      String check1 = attachmentResponse.get(0);
      String check2 = referenceResponse.get(0);
      String check3 = footnoteResponse.get(0);
      if ("Attachment created".equals(check1)
          && "Reference created".equals(check2)
          && "Footnote created".equals(check3)) {
        attachmentID2 = attachmentResponse.get(1);
        referenceID2 = referenceResponse.get(1);
        footnoteID2 = footnoteResponse.get(1);

        testStatus =
            verifyDraftAndSave(
                appUrl,
                serviceName,
                entityName,
                entityID,
                attachmentID2,
                referenceID2,
                footnoteID2);
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.txt as an attachment, reference, or footnote: " + response);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if ("Entity in draft mode".equals(response)) {
      // Creation of attachment, reference, and footnote
      List<String> attachmentResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> referenceResponse =
          api.createReference(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      List<String> footnoteResponse =
          api.createFootnote(appUrl, serviceName, entityName, entityID, srvpath, postData, file);

      // Checking for response for attachment, reference, and footnote
      String check1 = attachmentResponse.get(0);
      String check2 = referenceResponse.get(0);
      String check3 = footnoteResponse.get(0);
      if ("Attachment created".equals(check1)
          && "Reference created".equals(check2)
          && "Footnote created".equals(check3)) {
        attachmentID3 = attachmentResponse.get(1);
        referenceID3 = referenceResponse.get(1);
        footnoteID3 = footnoteResponse.get(1);

        testStatus =
            verifyDraftAndSave(
                appUrl,
                serviceName,
                entityName,
                entityID,
                attachmentID3,
                referenceID3,
                footnoteID3);
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.exe as an attachment, reference, or footnote: " + response);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if ("Entity in draft mode".equals(response)) {
      Boolean allFacetsFailedCorrectly = true;

      // Attempt duplicate uploads
      allFacetsFailedCorrectly &=
          checkDuplicateCreation(
              "Attachment",
              api.createAttachment(
                  appUrl, serviceName, entityName, entityID, srvpath, postData, file));
      allFacetsFailedCorrectly &=
          checkDuplicateCreation(
              "Reference",
              api.createReference(
                  appUrl, serviceName, entityName, entityID, srvpath, postData, file));
      allFacetsFailedCorrectly &=
          checkDuplicateCreation(
              "Footnote",
              api.createFootnote(
                  appUrl, serviceName, entityName, entityID, srvpath, postData, file));

      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

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
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (!"Could not create entity".equals(response)) {
      entityID2 = response;
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID2);

      if ("Saved".equals(response)) {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID2);
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
    response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID2);
    if ("Entity in draft mode".equals(response)) {
      // Create attachment, reference, and footnote
      List<String> attachmentResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID2, srvpath, postData, file);
      List<String> referenceResponse =
          api.createReference(appUrl, serviceName, entityName, entityID2, srvpath, postData, file);
      List<String> footnoteResponse =
          api.createFootnote(appUrl, serviceName, entityName, entityID2, srvpath, postData, file);

      // Check creation responses
      String check1 = attachmentResponse.get(0);
      String check2 = referenceResponse.get(0);
      String check3 = footnoteResponse.get(0);
      if ("Attachment created".equals(check1)
          && "Reference created".equals(check2)
          && "Footnote created".equals(check3)) {
        attachmentID4 = attachmentResponse.get(1);
        referenceID4 = referenceResponse.get(1);
        footnoteID4 = footnoteResponse.get(1);

        // Verify and save
        testStatus =
            verifyDraftAndSave(
                appUrl,
                serviceName,
                entityName,
                entityID2,
                attachmentID4,
                referenceID4,
                footnoteID4);
      }
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
      String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

      if ("Entity in draft mode".equals(response)) {
        // Rename attachment
        response = api.renameAttachment(appUrl, serviceName, entityID, attachmentID1, "sample123");
        if (!"Renamed".equals(response)) {
          testStatus = false;
          System.out.println("Attachment was not renamed: " + response);
        }

        // Rename reference
        response = api.renameReference(appUrl, serviceName, entityID, referenceID1, "reference123");
        if (!"Renamed".equals(response)) {
          testStatus = false;
          System.out.println("Reference was not renamed: " + response);
        }

        // Rename footnote
        response = api.renameFootnote(appUrl, serviceName, entityID, footnoteID1, "footnote123");
        if (!"Renamed".equals(response)) {
          testStatus = false;
          System.out.println("Footnote was not renamed: " + response);
        }

        // Save entity draft if everything is renamed
        if (testStatus) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (!"Saved".equals(response)) {
            testStatus = false;
            System.out.println("Entity draft was not saved: " + response);
          }
        } else {
          // Attempt save despite potential rename failures
          api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
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
  void testRenameMultipleEntityComponents() {
    System.out.println("Test (9) : Rename multiple attachments, references, and footnotes");
    boolean testStatus = true;

    String draftResponse = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

    if (!"Entity in draft mode".equals(draftResponse)) {
      fail("Entity is not in draft mode.");
      return;
    }

    // Rename Attachments
    testStatus &= renameAndCheck("attachment", attachmentID2, "sample1234");
    testStatus &= renameAndCheck("attachment", attachmentID3, "sample12345");

    // Rename References
    testStatus &= renameAndCheck("reference", referenceID2, "reference1234");
    testStatus &= renameAndCheck("reference", referenceID3, "reference12345");

    // Rename Footnotes
    testStatus &= renameAndCheck("footnote", footnoteID2, "footnote1234");
    testStatus &= renameAndCheck("footnote", footnoteID3, "footnote12345");

    // Save the draft if all renames succeeded
    if (testStatus) {
      String saveResponse = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (!"Saved".equals(saveResponse)) {
        fail("Entity draft was not saved after renaming.");
      }
    } else {
      // Save draft even if renaming failed to preserve state
      api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      fail("One or more components were not renamed.");
    }
  }

  @Test
  @Order(10)
  void testRenameDuplicateEntities() {
    System.out.println("Test (10) : Rename duplicates for attachment, reference, and footnote");

    // Running duplicate rename tests for each entity component
    boolean attachmentTestStatus =
        renameEntityAndCheckForDuplicates("attachment", attachmentID3, "sample123", "sample123456");
    boolean referenceTestStatus =
        renameEntityAndCheckForDuplicates(
            "reference", referenceID3, "reference123", "reference123456");
    boolean footnoteTestStatus =
        renameEntityAndCheckForDuplicates("footnote", footnoteID3, "footnote123", "footnote123456");

    if (!attachmentTestStatus || !referenceTestStatus || !footnoteTestStatus) {
      fail("One or more entities did not properly handle duplicate renaming.");
    }
  }

  @Test
  @Order(11)
  void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (11) : Delete single attachment, reference, and footnote");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String response2 = "", response3 = "";
    if (response == "Entity in draft mode") {
      response = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID1);
      response2 = api.deleteReference(appUrl, serviceName, entityID, referenceID1);
      response3 = api.deleteFootnote(appUrl, serviceName, entityID, footnoteID1);
      if (response == "Deleted" && response2 == "Deleted" && response3 == "Deleted") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response == "Saved") {
          response = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID1);
          response2 = api.readReference(appUrl, serviceName, entityName, entityID, referenceID1);
          response3 = api.readFootnote(appUrl, serviceName, entityName, entityID, footnoteID1);
          if (response.equals("Could not read attachment")
              && response2.equals("Could not read reference")
              && response3.equals("Could not read footnote")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not delete attachment, reference or footnote");
    }
  }

  @Test
  @Order(12)
  void testDeleteMultipleAttachmentsReferencesFootnotes() throws IOException {
    System.out.println("Test (12) : Delete multiple attachments, references, and footnotes");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      // Delete multiple attachments
      String response1 = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID2);
      String response2 = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID3);

      // Delete multiple references
      String response3 = api.deleteReference(appUrl, serviceName, entityID, referenceID2);
      String response4 = api.deleteReference(appUrl, serviceName, entityID, referenceID3);

      // Delete multiple footnotes
      String response5 = api.deleteFootnote(appUrl, serviceName, entityID, footnoteID2);
      String response6 = api.deleteFootnote(appUrl, serviceName, entityID, footnoteID3);

      if (response1.equals("Deleted")
          && response2.equals("Deleted")
          && response3.equals("Deleted")
          && response4.equals("Deleted")
          && response5.equals("Deleted")
          && response6.equals("Deleted")) {

        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          response1 = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID2);
          response2 = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID3);

          response3 = api.readReference(appUrl, serviceName, entityName, entityID, referenceID2);
          response4 = api.readReference(appUrl, serviceName, entityName, entityID, referenceID3);

          response5 = api.readFootnote(appUrl, serviceName, entityName, entityID, footnoteID2);
          response6 = api.readFootnote(appUrl, serviceName, entityName, entityID, footnoteID3);

          if (response1.equals("Could not read attachment")
              && response2.equals("Could not read attachment")
              && response3.equals("Could not read reference")
              && response4.equals("Could not read reference")
              && response5.equals("Could not read footnote")
              && response6.equals("Could not read footnote")) {
            testStatus = true;
          }
        }
      }
    }

    if (!testStatus) {
      fail("Could not delete attachments, references, or footnotes");
    }
  }

  @Test
  @Order(13)
  void testDeleteEntity() {
    System.out.println("Test (13) : Delete entity");
    Boolean testStatus = false;

    String response = api.deleteEntity(appUrl, serviceName, entityName, entityID);
    String response2 = api.deleteEntity(appUrl, serviceName, entityName, entityID2);

    if (response.equals("Entity Deleted") && response2.equals("Entity Deleted")) {
      testStatus = true;
    }

    if (!testStatus) {
      fail("Could not delete entity");
    }
  }

  @Test
  @Order(14)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println("Test (14) : Rename & Update secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;
      System.out.println("Entity created");
      System.out.println("Creating attachment, reference, and footnote");
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);
      String check = createResponse.get(0);

      List<String> createResponse2 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);
      String check2 = createResponse2.get(0);

      List<String> createResponse3 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);
      String check3 = createResponse3.get(0);

      if (check.equals("Attachment created")
          && check2.equals("Reference created")
          && check3.equals("Footnote created")) {
        attachmentID1 = createResponse.get(1);
        referenceID1 = createResponse2.get(1);
        footnoteID1 = createResponse3.get(1);
        System.out.println("Attachments, References, and Footnotes created");

        // Rename and update secondary properties
        String secondaryPropertyString = "sample12345";
        Integer secondaryPropertyInt = 1234;
        LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

        // Renaming and updating secondary properties for Attachment
        String name1 = "sample1234.pdf";
        String response1 =
            api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, true);

        // Renaming and updating secondary properties for Reference
        String referenceName = "reference1234.pdf";
        String referenceResponse1 =
            api.renameReference(appUrl, serviceName, entityID3, referenceID1, referenceName);
        String referenceUpdateSecondaryPropertyResponse1 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString);
        String referenceUpdateSecondaryPropertyResponse2 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt);
        String referenceUpdateSecondaryPropertyResponse3 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime);
        String referenceUpdateSecondaryPropertyResponse4 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, true);

        // Renaming and updating secondary properties for Footnote
        String footnoteName = "footnote1234.pdf";
        String footnoteResponse1 =
            api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, footnoteName);
        String footnoteUpdateSecondaryPropertyResponse1 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString);
        String footnoteUpdateSecondaryPropertyResponse2 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt);
        String footnoteUpdateSecondaryPropertyResponse3 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime);
        String footnoteUpdateSecondaryPropertyResponse4 =
            api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);

        // Check that all properties are updated correctly
        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")
            && referenceResponse1.equals("Renamed")
            && referenceUpdateSecondaryPropertyResponse1.equals("Updated")
            && referenceUpdateSecondaryPropertyResponse2.equals("Updated")
            && referenceUpdateSecondaryPropertyResponse3.equals("Updated")
            && referenceUpdateSecondaryPropertyResponse4.equals("Updated")
            && footnoteResponse1.equals("Renamed")
            && footnoteUpdateSecondaryPropertyResponse1.equals("Updated")
            && footnoteUpdateSecondaryPropertyResponse2.equals("Updated")
            && footnoteUpdateSecondaryPropertyResponse3.equals("Updated")
            && footnoteUpdateSecondaryPropertyResponse4.equals("Updated")) {
          // Save the entity draft after updates
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
          if (response.equals("Saved")) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println(
                "Renamed & updated Secondary properties for attachment, reference, and footnote");
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
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_single() {
    System.out.println("Test (15): Rename & Update secondary property after entity is saved");

    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    System.out.println("Editing entity");

    if (response.equals("Entity in draft mode")) {
      // Sample secondary properties
      String name1 = "sample.pdf";
      String referenceName = "reference_sample.pdf";
      String footnoteName = "footnote_sample.pdf";
      String secondaryPropertyString = "sampleString";
      Integer secondaryPropertyInt = 42;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      System.out.println("Renaming and updating secondary properties for attachment");
      String response1 = api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString);
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt);
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime);
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, true);

      System.out.println("Renaming and updating secondary properties for reference");
      String referenceResponse =
          api.renameReference(appUrl, serviceName, entityID3, referenceID1, referenceName);
      String referenceUpdate1 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString);
      String referenceUpdate2 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt);
      String referenceUpdate3 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime);
      String referenceUpdate4 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID1, true);

      System.out.println("Renaming and updating secondary properties for footnote");
      String footnoteResponse =
          api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, footnoteName);
      String footnoteUpdate1 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString);
      String footnoteUpdate2 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt);
      String footnoteUpdate3 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime);
      String footnoteUpdate4 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);

      // All conditions must be met
      if (response1.equals("Renamed")
          && updateSecondaryPropertyResponse1.equals("Updated")
          && updateSecondaryPropertyResponse2.equals("Updated")
          && updateSecondaryPropertyResponse3.equals("Updated")
          && updateSecondaryPropertyResponse4.equals("Updated")
          && referenceResponse.equals("Renamed")
          && referenceUpdate1.equals("Updated")
          && referenceUpdate2.equals("Updated")
          && referenceUpdate3.equals("Updated")
          && referenceUpdate4.equals("Updated")
          && footnoteResponse.equals("Renamed")
          && footnoteUpdate1.equals("Updated")
          && footnoteUpdate2.equals("Updated")
          && footnoteUpdate3.equals("Updated")
          && footnoteUpdate4.equals("Updated")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        if (response.equals("Saved")) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Renamed & updated Secondary properties for attachment, reference, and footnote");
        }
      }

      // Clean up
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    }

    if (!testStatus) {
      fail("Could not update secondary properties after entity is saved");
    }
  }

  @Test
  @Order(16)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println(
        "Test (16): Rename & Update invalid secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;
      System.out.println("Entity created");

      // Create attachment, reference, and footnote
      ClassLoader classLoader = getClass().getClassLoader();
      File file = new File(classLoader.getResource("sample.pdf").getFile());

      Map<String, Object> postData = new HashMap<>();
      postData.put("up__ID", entityID3);
      postData.put("mimeType", "application/pdf");
      postData.put("createdAt", new Date().toString());
      postData.put("createdBy", "test@test.com");
      postData.put("modifiedBy", "test@test.com");

      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);
      List<String> createResponse2 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);
      List<String> createResponse3 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData, file);

      String check = createResponse.get(0);
      String check2 = createResponse2.get(0);
      String check3 = createResponse3.get(0);

      if (check.equals("Attachment created")
          && check2.equals("Reference created")
          && check3.equals("Footnote created")) {
        attachmentID1 = createResponse.get(1);
        referenceID1 = createResponse2.get(1);
        footnoteID1 = createResponse3.get(1);
        System.out.println("Attachments, References, and Footnotes created");

        // Prepare test data
        String name1 = "sample1234.pdf";
        String secondaryPropertyString = "sample12345";
        Integer secondaryPropertyInt = 1234;
        LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
        String invalidProperty = "testid";

        // Rename and update invalid secondary properties for attachment
        System.out.println("Renaming and updating invalid secondary properties for attachment");
        String response1 =
            api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, true);
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, invalidProperty);

        // Rename and update invalid secondary properties for reference
        String response2 = api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
        String updateSecondaryPropertyResponse6 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString);
        String updateSecondaryPropertyResponse7 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt);
        String updateSecondaryPropertyResponse8 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse9 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, true);
        String updateSecondaryPropertyResponse10 =
            api.updateInvalidSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, invalidProperty);

        // Rename and update invalid secondary properties for footnote
        String response3 = api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, name1);
        String updateSecondaryPropertyResponse11 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString);
        String updateSecondaryPropertyResponse12 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt);
        String updateSecondaryPropertyResponse13 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse14 =
            api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);
        String updateSecondaryPropertyResponse15 =
            api.updateInvalidSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, invalidProperty);

        // Check if all responses are as expected
        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")
            && updateSecondaryPropertyResponse5.equals("Updated")
            && response2.equals("Renamed")
            && updateSecondaryPropertyResponse6.equals("Updated")
            && updateSecondaryPropertyResponse7.equals("Updated")
            && updateSecondaryPropertyResponse8.equals("Updated")
            && updateSecondaryPropertyResponse9.equals("Updated")
            && updateSecondaryPropertyResponse10.equals("Updated")
            && response3.equals("Renamed")
            && updateSecondaryPropertyResponse11.equals("Updated")
            && updateSecondaryPropertyResponse12.equals("Updated")
            && updateSecondaryPropertyResponse13.equals("Updated")
            && updateSecondaryPropertyResponse14.equals("Updated")
            && updateSecondaryPropertyResponse15.equals("Updated")) {

          // Save entity draft and check metadata
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);

          // Fetch metadata for attachment, reference, and footnote
          Map<String, Object> attachmentMetadata =
              api.fetchAttachmentMetadata(
                  appUrl, serviceName, entityName, entityID3, attachmentID1);
          Map<String, Object> referenceMetadata =
              api.fetchReferenceMetadata(appUrl, serviceName, entityName, entityID3, referenceID1);
          Map<String, Object> footnoteMetadata =
              api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID1);

          // Assertions
          assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
          assertEquals("sample.pdf", referenceMetadata.get("fileName"));
          assertEquals("sample.pdf", footnoteMetadata.get("fileName"));

          // Assert null for unexpected metadata keys
          assertNull(attachmentMetadata.get("abc___myId1"));
          assertNull(referenceMetadata.get("abc___myId1"));
          assertNull(footnoteMetadata.get("abc___myId1"));

          assertNull(attachmentMetadata.get("abc___myId2"));
          assertNull(referenceMetadata.get("abc___myId2"));
          assertNull(footnoteMetadata.get("abc___myId2"));

          assertNull(attachmentMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(referenceMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(footnoteMetadata.get("Working___DocumentInfoRecordString"));

          assertNull(attachmentMetadata.get("Working___DocumentInfoRecordInt"));
          assertNull(referenceMetadata.get("Working___DocumentInfoRecordInt"));
          assertNull(footnoteMetadata.get("Working___DocumentInfoRecordInt"));

          assertNull(attachmentMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(referenceMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(footnoteMetadata.get("Working___DocumentInfoRecordBoolean"));

          assertNull(attachmentMetadata.get("Working___DocumentInfoRecordDate"));
          assertNull(referenceMetadata.get("Working___DocumentInfoRecordDate"));
          assertNull(footnoteMetadata.get("Working___DocumentInfoRecordDate"));

          if (response.equals("Saved")) {
            System.out.println("Entity saved");
            testStatus = true;
            System.out.println(
                "Rename & update secondary properties for attachment is unsuccessfull");
          }
        }
      }
    }

    if (!testStatus) {
      fail(
          "Could not update secondary property before entity is saved for attachment, reference, or footnote");
    }
  }

  @Test
  @Order(17)
  void testUpdateInvalidSecondaryProperty_afterEntityIsSaved_single() throws IOException {
    System.out.println(
        "Test (17): Rename & Update invalid secondary property after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    if (response.equals("Entity in draft mode")) {
      String name1 = "sample.pdf";
      String secondaryPropertyString = "sample";
      Integer secondaryPropertyInt = 12;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testidinvalid";

      // === ATTACHMENT ===
      System.out.println("Renaming and updating invalid secondary properties for attachment");
      String response1 = api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
      String updateSecondaryPropertyResponse1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString);
      String updateSecondaryPropertyResponse2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt);
      String updateSecondaryPropertyResponse3 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime);
      String updateSecondaryPropertyResponse4 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, true);
      String updateSecondaryPropertyResponse5 =
          api.updateInvalidSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, invalidProperty);

      // === REFERENCE ===
      System.out.println("Renaming and updating invalid secondary properties for reference");
      String response2 = api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
      String updateSecondaryPropertyResponse6 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString);
      String updateSecondaryPropertyResponse7 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt);
      String updateSecondaryPropertyResponse8 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime);
      String updateSecondaryPropertyResponse9 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID1, true);
      String updateSecondaryPropertyResponse10 =
          api.updateInvalidSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, invalidProperty);

      // === FOOTNOTE ===
      System.out.println("Renaming and updating invalid secondary properties for footnote");
      String response3 = api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, name1);
      String updateSecondaryPropertyResponse11 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString);
      String updateSecondaryPropertyResponse12 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt);
      String updateSecondaryPropertyResponse13 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime);
      String updateSecondaryPropertyResponse14 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);
      String updateSecondaryPropertyResponse15 =
          api.updateInvalidSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, invalidProperty);

      // Validation
      if (response1.equals("Renamed")
          && updateSecondaryPropertyResponse1.equals("Updated")
          && updateSecondaryPropertyResponse2.equals("Updated")
          && updateSecondaryPropertyResponse3.equals("Updated")
          && updateSecondaryPropertyResponse4.equals("Updated")
          && updateSecondaryPropertyResponse5.equals("Updated")
          && response2.equals("Renamed")
          && updateSecondaryPropertyResponse6.equals("Updated")
          && updateSecondaryPropertyResponse7.equals("Updated")
          && updateSecondaryPropertyResponse8.equals("Updated")
          && updateSecondaryPropertyResponse9.equals("Updated")
          && updateSecondaryPropertyResponse10.equals("Updated")
          && response3.equals("Renamed")
          && updateSecondaryPropertyResponse11.equals("Updated")
          && updateSecondaryPropertyResponse12.equals("Updated")
          && updateSecondaryPropertyResponse13.equals("Updated")
          && updateSecondaryPropertyResponse14.equals("Updated")
          && updateSecondaryPropertyResponse15.equals("Updated")) {

        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);

        Map<String, Object> attachmentMetadata =
            api.fetchAttachmentMetadata(appUrl, serviceName, entityName, entityID3, attachmentID1);
        Map<String, Object> referenceMetadata =
            api.fetchReferenceMetadata(appUrl, serviceName, entityName, entityID3, referenceID1);
        Map<String, Object> footnoteMetadata =
            api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID1);

        // Validate fileName
        assertEquals("sample.pdf", attachmentMetadata.get("fileName"));
        assertEquals("sample.pdf", referenceMetadata.get("fileName"));
        assertEquals("sample.pdf", footnoteMetadata.get("fileName"));

        // Validate absence of invalid secondary properties
        assertNull(attachmentMetadata.get("abc___myId1"));
        assertNull(referenceMetadata.get("abc___myId1"));
        assertNull(footnoteMetadata.get("abc___myId1"));

        assertNull(attachmentMetadata.get("abc___myId2"));
        assertNull(referenceMetadata.get("abc___myId2"));
        assertNull(footnoteMetadata.get("abc___myId2"));

        assertNull(attachmentMetadata.get("Working___DocumentInfoRecordString"));
        assertNull(referenceMetadata.get("Working___DocumentInfoRecordString"));
        assertNull(footnoteMetadata.get("Working___DocumentInfoRecordString"));

        assertNull(attachmentMetadata.get("Working___DocumentInfoRecordInt"));
        assertNull(referenceMetadata.get("Working___DocumentInfoRecordInt"));
        assertNull(footnoteMetadata.get("Working___DocumentInfoRecordInt"));

        assertNull(attachmentMetadata.get("Working___DocumentInfoRecordBoolean"));
        assertNull(referenceMetadata.get("Working___DocumentInfoRecordBoolean"));
        assertNull(footnoteMetadata.get("Working___DocumentInfoRecordBoolean"));

        assertNull(attachmentMetadata.get("Working___DocumentInfoRecordDate"));
        assertNull(referenceMetadata.get("Working___DocumentInfoRecordDate"));
        assertNull(footnoteMetadata.get("Working___DocumentInfoRecordDate"));

        if (response.equals("Saved")) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update secondary properties for attachment, reference, footnote is unsuccessfull");
        }
      }

      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    }

    if (!testStatus) {
      fail(
          "Could not update secondary property after entity is saved for attachment, reference, or footnote");
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
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;

      System.out.println("Entity created");

      System.out.println("Creating attachment, reference, and footnote PDF");
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
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse2 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse3 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      if (createResponse1.get(0).equals("Attachment created")
          && createResponse2.get(0).equals("Reference created")
          && createResponse3.get(0).equals("Footnote created")) {
        attachmentID1 = createResponse1.get(1);
        referenceID1 = createResponse2.get(1);
        footnoteID1 = createResponse3.get(1);
      }

      System.out.println("Creating attachment, reference, and footnote TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID3);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse4 =
          api.createAttachment(
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse5 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse6 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      if (createResponse4.get(0).equals("Attachment created")
          && createResponse5.get(0).equals("Reference created")
          && createResponse6.get(0).equals("Footnote created")) {
        attachmentID2 = createResponse4.get(1);
        referenceID2 = createResponse5.get(1);
        footnoteID2 = createResponse6.get(1);
      }

      System.out.println("Creating attachment, reference, and footnote EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      Map<String, Object> postData3 = new HashMap<>();
      postData3.put("up__ID", entityID3);
      postData3.put("mimeType", "application/exe");
      postData3.put("createdAt", new Date().toString());
      postData3.put("createdBy", "test@test.com");
      postData3.put("modifiedBy", "test@test.com");

      List<String> createResponse7 =
          api.createAttachment(
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse8 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse9 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      if (createResponse7.get(0).equals("Attachment created")
          && createResponse8.get(0).equals("Reference created")
          && createResponse9.get(0).equals("Footnote created")) {
        attachmentID3 = createResponse7.get(1);
        referenceID3 = createResponse8.get(1);
        footnoteID3 = createResponse9.get(1);
      }

      String check1 = createResponse1.get(0);
      String check2 = createResponse2.get(0);
      String check3 = createResponse3.get(0);
      String check4 = createResponse4.get(0);
      String check5 = createResponse5.get(0);
      String check6 = createResponse6.get(0);
      String check7 = createResponse7.get(0);
      String check8 = createResponse8.get(0);
      String check9 = createResponse9.get(0);
      if (check1.equals("Attachment created")
          && check2.equals("Reference created")
          && check3.equals("Footnote created")
          && check4.equals("Attachment created")
          && check5.equals("Reference created")
          && check6.equals("Footnote created")
          && check7.equals("Attachment created")
          && check8.equals("Reference created")
          && check9.equals("Footnote created")) {

        Boolean attachment1Updated = false, reference1Updated = false, footnote1Updated = false;
        Boolean attachment2Updated = false, reference2Updated = false, footnote2Updated = false;
        Boolean attachment3Updated = false, reference3Updated = false, footnote3Updated = false;

        String name1 = "sample1234.pdf";
        String secondaryPropertyString1 = "sample12345";
        Integer secondaryPropertyInt1 = 1234;
        LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();

        // PDF
        System.out.println("Renaming and updating secondary properties for attachment PDF");
        String responsePDF1 =
            api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
        String updateSecondaryPropertyResponsePDF1 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString1);
        String updateSecondaryPropertyResponsePDF2 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt1);
        String updateSecondaryPropertyResponsePDF3 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime1);
        String updateSecondaryPropertyResponsePDF4 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID1, true);

        if (responsePDF1 == "Renamed"
            && updateSecondaryPropertyResponsePDF1 == "Updated"
            && updateSecondaryPropertyResponsePDF2 == "Updated"
            && updateSecondaryPropertyResponsePDF3 == "Updated"
            && updateSecondaryPropertyResponsePDF4 == "Updated") {
          System.out.println("Renamed & updated Secondary properties for attachment PDF");
          attachment1Updated = true;
        }

        // === Update Secondary Properties for Reference 1 ===
        System.out.println("Renaming and updating secondary properties for reference PDF");
        String renameReference1 =
            api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
        String updateRefStr1 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString1);
        String updateRefInt1 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt1);
        String updateRefDate1 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime1);
        String updateRefBool1 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID1, true);

        if (renameReference1.equals("Renamed")
            && updateRefStr1.equals("Updated")
            && updateRefInt1.equals("Updated")
            && updateRefDate1.equals("Updated")
            && updateRefBool1.equals("Updated")) {
          System.out.println("Renamed & updated secondary properties for reference PDF");
          reference1Updated = true;
        }

        // === Update Footnote 1 ===
        System.out.println("Renaming and updating secondary properties for footnote PDF");
        String renameFootnote1 =
            api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, name1);
        String updateFootStr1 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString1);
        String updateFootInt1 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt1);
        String updateFootDate1 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime1);
        String updateFootBool1 =
            api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);

        if (renameFootnote1.equals("Renamed")
            && updateFootStr1.equals("Updated")
            && updateFootInt1.equals("Updated")
            && updateFootDate1.equals("Updated")
            && updateFootBool1.equals("Updated")) {
          System.out.println("Renamed & updated secondary properties for footnote PDF");
          footnote1Updated = true;
        }

        // TEXT
        System.out.println("Updating secondary properties for attachment TXT");
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID2, true);
        if (updateSecondaryPropertyResponseTXT1 == "Updated") {
          System.out.println("Updated Secondary properties for attachment TXT");
          attachment2Updated = true;
        }

        System.out.println("Updating secondary properties for reference TXT");
        String updateRefBool2 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID2, true);
        if (updateRefBool2.equals("Updated")) {
          reference2Updated = true;
          System.out.println("Updated secondary property for reference TXT");
        }

        System.out.println("Updating secondary properties for footnote TXT");
        String updateFootBool2 =
            api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID2, true);

        if (updateFootBool2.equals("Updated")) {
          footnote2Updated = true;
          System.out.println("Updated secondary property for footnote TXT");
        }

        // EXE
        String secondaryPropertyString3 = "sample12345";
        Integer secondaryPropertyInt3 = 1234;
        LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
        System.out.println("Updating secondary properties for attachment EXE");

        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyString3);
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyInt3);
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryAttachmentProperty(
                appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyDateTime3);

        if (updateSecondaryPropertyResponseEXE1 == "Updated"
            && updateSecondaryPropertyResponseEXE2 == "Updated"
            && updateSecondaryPropertyResponseEXE3 == "Updated") {
          System.out.println("Updated Secondary properties for attachment EXE");
          attachment3Updated = true;
        }

        System.out.println("Updating secondary properties for reference EXE");
        String updateRefStr3 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID3, secondaryPropertyString3);
        String updateRefInt3 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID3, secondaryPropertyInt3);
        String updateRefDate3 =
            api.updateSecondaryReferenceProperty(
                appUrl, serviceName, entityID3, referenceID3, secondaryPropertyDateTime3);

        if (updateRefStr3.equals("Updated")
            && updateRefInt3.equals("Updated")
            && updateRefDate3.equals("Updated")) {
          reference3Updated = true;
          System.out.println("Updated secondary properties for reference EXE");
        }

        System.out.println("Updating secondary properties for footnote EXE");
        String updateFootStr3 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyString3);
        String updateFootInt3 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyInt3);
        String updateFootDate3 =
            api.updateSecondaryFootnoteProperty(
                appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyDateTime3);

        if (updateFootStr3.equals("Updated")
            && updateFootInt3.equals("Updated")
            && updateFootDate3.equals("Updated")) {
          footnote3Updated = true;
          System.out.println("Updated secondary properties for footnote EXE");
        }

        if (attachment1Updated
            && attachment2Updated
            && attachment3Updated
            && reference1Updated
            && reference2Updated
            && reference3Updated
            && footnote1Updated
            && footnote2Updated
            && footnote3Updated) {
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
      Boolean attachment1Updated = false, reference1Updated = false, footnote1Updated = false;
      Boolean attachment2Updated = false, reference2Updated = false, footnote2Updated = false;
      Boolean attachment3Updated = false, reference3Updated = false, footnote3Updated = false;

      String name1 = "sample1.pdf";
      String secondaryPropertyString1 = "sample1";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for attachment PDF");
      String responsePDF1 =
          api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, true);

      if (responsePDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF1 == "Updated"
          && updateSecondaryPropertyResponsePDF2 == "Updated"
          && updateSecondaryPropertyResponsePDF3 == "Updated"
          && updateSecondaryPropertyResponsePDF4 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for attachment PDF");
        attachment1Updated = true;
      }

      System.out.println("Renaming and updating secondary properties for reference PDF");
      String response2PDF1 =
          api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
      String updateSecondaryPropertyResponsePDF5 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF6 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF7 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF8 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID1, true);

      if (response2PDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF5 == "Updated"
          && updateSecondaryPropertyResponsePDF6 == "Updated"
          && updateSecondaryPropertyResponsePDF7 == "Updated"
          && updateSecondaryPropertyResponsePDF8 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for reference PDF");
        reference1Updated = true;
      }

      System.out.println("Renaming and updating secondary properties for footnote PDF");
      String response3PDF1 =
          api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
      String updateSecondaryPropertyResponsePDF9 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF10 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF11 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF12 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);

      if (response3PDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF9 == "Updated"
          && updateSecondaryPropertyResponsePDF10 == "Updated"
          && updateSecondaryPropertyResponsePDF11 == "Updated"
          && updateSecondaryPropertyResponsePDF12 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for footnote PDF");
        footnote1Updated = true;
      }

      System.out.println("Updating secondary properties for attachment TXT");
      String updateSecondaryPropertyResponseTXT1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID2, false);
      if (updateSecondaryPropertyResponseTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for attachment TXT");
        attachment2Updated = true;
      }

      System.out.println("Updating secondary properties for reference TXT");
      String updateSecondaryReferenceResponseTXT1 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID2, false);

      if (updateSecondaryReferenceResponseTXT1.equals("Updated")) {
        System.out.println("Updated Secondary properties for reference TXT");
        reference2Updated = true;
      }

      System.out.println("Updating secondary properties for footnote TXT");
      String updateSecondaryFootnoteResponseTXT1 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID2, false);

      if (updateSecondaryFootnoteResponseTXT1.equals("Updated")) {
        System.out.println("Updated Secondary properties for footnote TXT");
        footnote2Updated = true;
      }

      String secondaryPropertyString3 = "sample3";
      Integer secondaryPropertyInt3 = 123;
      LocalDateTime secondaryPropertyDateTime3 = LocalDateTime.now();
      System.out.println("Updating secondary properties for attachment EXE");

      String updateSecondaryPropertyResponseEXE1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyString3);
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyInt3);
      String updateSecondaryPropertyResponseEXE3 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyDateTime3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated"
          && updateSecondaryPropertyResponseEXE3 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      System.out.println("Updating secondary properties for reference EXE");

      String updateSecondaryReferenceResponseEXE1 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID3, secondaryPropertyString3);
      String updateSecondaryReferenceResponseEXE2 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID3, secondaryPropertyInt3);
      String updateSecondaryReferenceResponseEXE3 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID3, secondaryPropertyDateTime3);

      if (updateSecondaryReferenceResponseEXE1.equals("Updated")
          && updateSecondaryReferenceResponseEXE2.equals("Updated")
          && updateSecondaryReferenceResponseEXE3.equals("Updated")) {
        System.out.println("Updated Secondary properties for reference EXE");
        reference3Updated = true;
      }

      System.out.println("Updating secondary properties for footnote EXE");

      String updateSecondaryFootnoteResponseEXE1 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyString3);
      String updateSecondaryFootnoteResponseEXE2 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyInt3);
      String updateSecondaryFootnoteResponseEXE3 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyDateTime3);

      if (updateSecondaryFootnoteResponseEXE1.equals("Updated")
          && updateSecondaryFootnoteResponseEXE2.equals("Updated")
          && updateSecondaryFootnoteResponseEXE3.equals("Updated")) {
        System.out.println("Updated Secondary properties for footnote EXE");
        footnote3Updated = true;
      }

      if (attachment1Updated
          && attachment2Updated
          && attachment3Updated
          && reference1Updated
          && reference2Updated
          && reference3Updated
          && footnote1Updated
          && footnote2Updated
          && footnote3Updated) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        if ("Saved".equals(response)) {
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
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (response != "Could not create entity") {
      entityID3 = response;

      System.out.println("Entity created");

      System.out.println("Creating attachment, reference, and footnote PDF");
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
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse2 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse3 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);

      if (createResponse1.get(0).equals("Attachment created")
          && createResponse2.get(0).equals("Reference created")
          && createResponse3.get(0).equals("Footnote created")) {
        attachmentID1 = createResponse1.get(1);
        referenceID1 = createResponse2.get(1);
        footnoteID1 = createResponse3.get(1);
      }

      System.out.println("Creating attachment, reference, and footnote TXT");
      file = new File(classLoader.getResource("sample.txt").getFile());
      Map<String, Object> postData2 = new HashMap<>();
      postData2.put("up__ID", entityID3);
      postData2.put("mimeType", "application/txt");
      postData2.put("createdAt", new Date().toString());
      postData2.put("createdBy", "test@test.com");
      postData2.put("modifiedBy", "test@test.com");

      List<String> createResponse4 =
          api.createAttachment(
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse5 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse6 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);

      if (createResponse4.get(0).equals("Attachment created")
          && createResponse5.get(0).equals("Reference created")
          && createResponse6.get(0).equals("Footnote created")) {
        attachmentID2 = createResponse4.get(1);
        referenceID2 = createResponse5.get(1);
        footnoteID2 = createResponse6.get(1);
      }

      System.out.println("Creating attachment, reference, and footnote EXE");
      file = new File(classLoader.getResource("sample.exe").getFile());
      Map<String, Object> postData3 = new HashMap<>();
      postData3.put("up__ID", entityID3);
      postData3.put("mimeType", "application/exe");
      postData3.put("createdAt", new Date().toString());
      postData3.put("createdBy", "test@test.com");
      postData3.put("modifiedBy", "test@test.com");

      List<String> createResponse7 =
          api.createAttachment(
              appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse8 =
          api.createReference(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);
      List<String> createResponse9 =
          api.createFootnote(appUrl, serviceName, entityName, entityID3, srvpath, postData1, file);

      if (createResponse7.get(0).equals("Attachment created")
          && createResponse8.get(0).equals("Reference created")
          && createResponse9.get(0).equals("Footnote created")) {
        attachmentID3 = createResponse7.get(1);
        referenceID3 = createResponse8.get(1);
        footnoteID3 = createResponse9.get(1);
      }

      String check1 = createResponse1.get(0),
          check2 = createResponse2.get(0),
          check3 = createResponse3.get(0);
      String check4 = createResponse4.get(0),
          check5 = createResponse5.get(0),
          check6 = createResponse6.get(0);
      String check7 = createResponse7.get(0),
          check8 = createResponse8.get(0),
          check9 = createResponse9.get(0);
      if (check1.equals("Attachment created")
          && check2.equals("Reference created")
          && check3.equals("Footnote created")
          && check4.equals("Attachment created")
          && check5.equals("Reference created")
          && check6.equals("Footnote created")
          && check7.equals("Attachment created")
          && check8.equals("Reference created")
          && check9.equals("Footnote created")) {
        {
          Boolean attachment1Updated = false,
              attachment2Updated = false,
              attachment3Updated = false;
          Boolean reference1Updated = false, reference2Updated = false, reference3Updated = false;
          Boolean footnote1Updated = false, footnote2Updated = false, footnote3Updated = false;

          String name1 = "sample1234.pdf";
          String secondaryPropertyString1 = "sample12345";
          Integer secondaryPropertyInt1 = 1234;
          LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
          String invalidPropertyPDF = "testidinvalidPDF";
          System.out.println(
              "Renaming and updating invalid secondary properties for attachment PDF");
          String responsePDF1 =
              api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
          String updateSecondaryPropertyResponsePDF1 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString1);
          String updateSecondaryPropertyResponsePDF2 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt1);
          String updateSecondaryPropertyResponsePDF3 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime1);
          String updateSecondaryPropertyResponsePDF4 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID1, true);
          String updateSecondaryPropertyResponsePDF5 =
              api.updateInvalidSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID1, invalidPropertyPDF);
          if (responsePDF1 == "Renamed"
              && updateSecondaryPropertyResponsePDF1 == "Updated"
              && updateSecondaryPropertyResponsePDF2 == "Updated"
              && updateSecondaryPropertyResponsePDF3 == "Updated"
              && updateSecondaryPropertyResponsePDF4 == "Updated"
              && updateSecondaryPropertyResponsePDF5 == "Updated") {
            System.out.println("Renamed & updated Secondary properties for attachment PDF");
            attachment1Updated = true;
          }

          System.out.println(
              "Renaming and updating invalid secondary properties for reference PDF");

          String responseReferencePDF1 =
              api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
          String updateSecondaryPropertyResponseRef1 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString1);
          String updateSecondaryPropertyResponseRef2 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt1);
          String updateSecondaryPropertyResponseRef3 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime1);
          String updateSecondaryPropertyResponseRef4 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID1, true);
          String updateSecondaryPropertyResponseRef5 =
              api.updateInvalidSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID1, invalidPropertyPDF);

          if ("Renamed".equals(responseReferencePDF1)
              && "Updated".equals(updateSecondaryPropertyResponseRef1)
              && "Updated".equals(updateSecondaryPropertyResponseRef2)
              && "Updated".equals(updateSecondaryPropertyResponseRef3)
              && "Updated".equals(updateSecondaryPropertyResponseRef4)
              && "Updated".equals(updateSecondaryPropertyResponseRef5)) {
            System.out.println("Renamed & updated Secondary properties for reference PDF");
            reference1Updated = true;
          }

          System.out.println("Renaming and updating invalid secondary properties for footnote PDF");

          String responseFootnotePDF1 =
              api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, name1);
          String updateSecondaryPropertyResponseFoot1 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString1);
          String updateSecondaryPropertyResponseFoot2 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt1);
          String updateSecondaryPropertyResponseFoot3 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime1);
          String updateSecondaryPropertyResponseFoot4 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID1, true);
          String updateSecondaryPropertyResponseFoot5 =
              api.updateInvalidSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID1, invalidPropertyPDF);

          if ("Renamed".equals(responseFootnotePDF1)
              && "Updated".equals(updateSecondaryPropertyResponseFoot1)
              && "Updated".equals(updateSecondaryPropertyResponseFoot2)
              && "Updated".equals(updateSecondaryPropertyResponseFoot3)
              && "Updated".equals(updateSecondaryPropertyResponseFoot4)
              && "Updated".equals(updateSecondaryPropertyResponseFoot5)) {
            System.out.println("Renamed & updated Secondary properties for footnote PDF");
            footnote1Updated = true;
          }

          // TXT
          System.out.println("Updating valid secondary properties for attachment TXT");
          String updateSecondaryPropertyResponseTXT1 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID2, true);
          if ("Updated".equals(updateSecondaryPropertyResponseTXT1)) {
            System.out.println("Updated Secondary properties for attachment TXT");
            attachment2Updated = true;
          }

          System.out.println("Updating valid secondary properties for reference TXT");
          String updateSecondaryReferenceResponseTXT1 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID2, true);
          if ("Updated".equals(updateSecondaryReferenceResponseTXT1)) {
            System.out.println("Updated Secondary properties for reference TXT");
            reference2Updated = true;
          }

          System.out.println("Updating valid secondary properties for footnote TXT");
          String updateSecondaryFootnoteResponseTXT1 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID2, true);
          if ("Updated".equals(updateSecondaryFootnoteResponseTXT1)) {
            System.out.println("Updated Secondary properties for footnote TXT");
            footnote2Updated = true;
          }

          // EXE
          String secondaryPropertyString3 = "sample12345";
          Integer secondaryPropertyInt3 = 1234;
          System.out.println("Updating valid secondary properties for attachment EXE");

          String updateSecondaryPropertyResponseEXE1 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyString3);
          String updateSecondaryPropertyResponseEXE2 =
              api.updateSecondaryAttachmentProperty(
                  appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyInt3);

          if ("Updated".equals(updateSecondaryPropertyResponseEXE1)
              && "Updated".equals(updateSecondaryPropertyResponseEXE2)) {
            System.out.println("Updated Secondary properties for attachment EXE");
            attachment3Updated = true;
          }

          System.out.println("Updating valid secondary properties for reference EXE");

          String updateSecondaryReferenceResponseEXE1 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID3, secondaryPropertyString3);
          String updateSecondaryReferenceResponseEXE2 =
              api.updateSecondaryReferenceProperty(
                  appUrl, serviceName, entityID3, referenceID3, secondaryPropertyInt3);

          if ("Updated".equals(updateSecondaryReferenceResponseEXE1)
              && "Updated".equals(updateSecondaryReferenceResponseEXE2)) {
            System.out.println("Updated Secondary properties for reference EXE");
            reference3Updated = true;
          }

          System.out.println("Updating valid secondary properties for footnote EXE");

          String updateSecondaryFootnoteResponseEXE1 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyString3);
          String updateSecondaryFootnoteResponseEXE2 =
              api.updateSecondaryFootnoteProperty(
                  appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyInt3);

          if ("Updated".equals(updateSecondaryFootnoteResponseEXE1)
              && "Updated".equals(updateSecondaryFootnoteResponseEXE2)) {
            System.out.println("Updated Secondary properties for footnote EXE");
            footnote3Updated = true;
          }

          if (attachment1Updated
              && attachment2Updated
              && attachment3Updated
              && reference1Updated
              && reference2Updated
              && reference3Updated
              && footnote1Updated
              && footnote2Updated
              && footnote3Updated) {
            response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
            // Fetching and asserting Attachment PDF metadata
            Map<String, Object> attachmentMetadataPDF =
                api.fetchAttachmentMetadata(
                    appUrl, serviceName, entityName, entityID3, attachmentID1);
            assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
            assertNull(attachmentMetadataPDF.get("abc___myId1"));
            assertNull(attachmentMetadataPDF.get("abc___myId2"));
            assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordString"));
            assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordInt"));
            assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
            assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Attachment TXT metadata
            Map<String, Object> attachmentMetadataTXT =
                api.fetchAttachmentMetadata(
                    appUrl, serviceName, entityName, entityID3, attachmentID2);
            assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
            assertNull(attachmentMetadataTXT.get("abc___myId1"));
            assertNull(attachmentMetadataTXT.get("abc___myId2"));
            assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordString"));
            assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordInt"));
            assertTrue((Boolean) attachmentMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
            assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Attachment EXE metadata
            Map<String, Object> attachmentMetadataEXE =
                api.fetchAttachmentMetadata(
                    appUrl, serviceName, entityName, entityID3, attachmentID3);
            assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
            assertNull(attachmentMetadataEXE.get("abc___myId1"));
            assertNull(attachmentMetadataEXE.get("abc___myId2"));
            assertEquals(
                "sample12345", attachmentMetadataEXE.get("Working___DocumentInfoRecordString"));
            assertEquals(1234, attachmentMetadataEXE.get("Working___DocumentInfoRecordInt"));

            // Fetching and asserting Reference PDF metadata
            Map<String, Object> referenceMetadataPDF =
                api.fetchReferenceMetadata(
                    appUrl, serviceName, entityName, entityID3, referenceID1);
            assertEquals("sample.pdf", referenceMetadataPDF.get("fileName"));
            assertNull(referenceMetadataPDF.get("abc___myId1"));
            assertNull(referenceMetadataPDF.get("abc___myId2"));
            assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordString"));
            assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordInt"));
            assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
            assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Reference TXT metadata
            Map<String, Object> referenceMetadataTXT =
                api.fetchReferenceMetadata(
                    appUrl, serviceName, entityName, entityID3, referenceID2);
            assertEquals("sample.txt", referenceMetadataTXT.get("fileName"));
            assertNull(referenceMetadataTXT.get("abc___myId1"));
            assertNull(referenceMetadataTXT.get("abc___myId2"));
            assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordString"));
            assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordInt"));
            assertTrue((Boolean) referenceMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
            assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Reference EXE metadata
            Map<String, Object> referenceMetadataEXE =
                api.fetchReferenceMetadata(
                    appUrl, serviceName, entityName, entityID3, referenceID3);
            assertEquals("sample.exe", referenceMetadataEXE.get("fileName"));
            assertNull(referenceMetadataEXE.get("abc___myId1"));
            assertNull(referenceMetadataEXE.get("abc___myId2"));
            assertEquals(
                "sample12345", referenceMetadataEXE.get("Working___DocumentInfoRecordString"));
            assertEquals(1234, referenceMetadataEXE.get("Working___DocumentInfoRecordInt"));

            // Fetching and asserting Footnote PDF metadata
            Map<String, Object> footnoteMetadataPDF =
                api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID1);
            assertEquals("sample.pdf", footnoteMetadataPDF.get("fileName"));
            assertNull(footnoteMetadataPDF.get("abc___myId1"));
            assertNull(footnoteMetadataPDF.get("abc___myId2"));
            assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordString"));
            assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordInt"));
            assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
            assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Footnote TXT metadata
            Map<String, Object> footnoteMetadataTXT =
                api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID2);
            assertEquals("sample.txt", footnoteMetadataTXT.get("fileName"));
            assertNull(footnoteMetadataTXT.get("abc___myId1"));
            assertNull(footnoteMetadataTXT.get("abc___myId2"));
            assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordString"));
            assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordInt"));
            assertTrue((Boolean) footnoteMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
            assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordDate"));

            // Fetching and asserting Footnote EXE metadata
            Map<String, Object> footnoteMetadataEXE =
                api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID3);
            assertEquals("sample.exe", footnoteMetadataEXE.get("fileName"));
            assertNull(footnoteMetadataEXE.get("abc___myId1"));
            assertNull(footnoteMetadataEXE.get("abc___myId2"));
            assertEquals(
                "sample12345", footnoteMetadataEXE.get("Working___DocumentInfoRecordString"));
            assertEquals(1234, footnoteMetadataEXE.get("Working___DocumentInfoRecordInt"));

            if ("Saved".equals(response)) {
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
      Boolean attachment1Updated = false, attachment2Updated = false, attachment3Updated = false;
      Boolean reference1Updated = false, reference2Updated = false, reference3Updated = false;
      Boolean footnote1Updated = false, footnote2Updated = false, footnote3Updated = false;

      String name1 = "sample.pdf";
      String secondaryPropertyString1 = "sample";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";
      System.out.println("Renaming and updating invalid secondary properties for attachment PDF");
      String responsePDF1 =
          api.renameAttachment(appUrl, serviceName, entityID3, attachmentID1, name1);
      String updateSecondaryPropertyResponsePDF1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF3 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF4 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, true);
      String updateSecondaryPropertyResponsePDF5 =
          api.updateInvalidSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID1, invalidPropertyPDF);
      if (responsePDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF1 == "Updated"
          && updateSecondaryPropertyResponsePDF2 == "Updated"
          && updateSecondaryPropertyResponsePDF3 == "Updated"
          && updateSecondaryPropertyResponsePDF4 == "Updated"
          && updateSecondaryPropertyResponsePDF5 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for attachment PDF");
        attachment1Updated = true;
      }

      System.out.println("Renaming and updating invalid secondary properties for reference PDF");
      String response2PDF1 =
          api.renameReference(appUrl, serviceName, entityID3, referenceID1, name1);
      String updateSecondaryPropertyResponsePDF6 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF7 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF8 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF9 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID1, true);
      String updateSecondaryPropertyResponsePDF10 =
          api.updateInvalidSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID1, invalidPropertyPDF);
      if (response2PDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF6 == "Updated"
          && updateSecondaryPropertyResponsePDF7 == "Updated"
          && updateSecondaryPropertyResponsePDF8 == "Updated"
          && updateSecondaryPropertyResponsePDF9 == "Updated"
          && updateSecondaryPropertyResponsePDF10 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for reference PDF");
        reference1Updated = true;
      }

      System.out.println("Renaming and updating invalid secondary properties for footnote PDF");
      String response3PDF1 = api.renameFootnote(appUrl, serviceName, entityID3, footnoteID1, name1);
      String updateSecondaryPropertyResponsePDF11 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyString1);
      String updateSecondaryPropertyResponsePDF12 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyInt1);
      String updateSecondaryPropertyResponsePDF13 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, secondaryPropertyDateTime1);
      String updateSecondaryPropertyResponsePDF14 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID1, true);
      String updateSecondaryPropertyResponsePDF15 =
          api.updateInvalidSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID1, invalidPropertyPDF);
      if (response3PDF1 == "Renamed"
          && updateSecondaryPropertyResponsePDF11 == "Updated"
          && updateSecondaryPropertyResponsePDF12 == "Updated"
          && updateSecondaryPropertyResponsePDF13 == "Updated"
          && updateSecondaryPropertyResponsePDF14 == "Updated"
          && updateSecondaryPropertyResponsePDF15 == "Updated") {
        System.out.println("Renamed & updated Secondary properties for footnote PDF");
        footnote1Updated = true;
      }

      System.out.println("Updating valid secondary properties for attachment TXT");
      String updateSecondaryPropertyResponseTXT1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID2, false);
      if (updateSecondaryPropertyResponseTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for attachment TXT");
        attachment2Updated = true;
      }

      System.out.println("Updating valid secondary properties for reference TXT");
      String updateSecondaryPropertyResponseRefTXT1 =
          api.updateSecondaryReferenceProperty(appUrl, serviceName, entityID3, referenceID2, false);
      if (updateSecondaryPropertyResponseRefTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for reference TXT");
        reference2Updated = true;
      }

      System.out.println("Updating valid secondary properties for footnote TXT");
      String updateSecondaryPropertyResponseFootnoteTXT1 =
          api.updateSecondaryFootnoteProperty(appUrl, serviceName, entityID3, footnoteID2, false);
      if (updateSecondaryPropertyResponseFootnoteTXT1 == "Updated") {
        System.out.println("Updated Secondary properties for footnote TXT");
        footnote2Updated = true;
      }

      String secondaryPropertyString3 = "sample";
      Integer secondaryPropertyInt3 = 12;
      System.out.println("Updating valid secondary properties for attachment EXE");

      String updateSecondaryPropertyResponseEXE1 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyString3);
      String updateSecondaryPropertyResponseEXE2 =
          api.updateSecondaryAttachmentProperty(
              appUrl, serviceName, entityID3, attachmentID3, secondaryPropertyInt3);

      if (updateSecondaryPropertyResponseEXE1 == "Updated"
          && updateSecondaryPropertyResponseEXE2 == "Updated") {
        System.out.println("Updated Secondary properties for attachment EXE");
        attachment3Updated = true;
      }

      System.out.println("Updating valid secondary properties for reference EXE");
      String updateSecondaryPropertyResponseRefEXE1 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID3, secondaryPropertyString3);
      String updateSecondaryPropertyResponseRefEXE2 =
          api.updateSecondaryReferenceProperty(
              appUrl, serviceName, entityID3, referenceID3, secondaryPropertyInt3);

      if (updateSecondaryPropertyResponseRefEXE1 == "Updated"
          && updateSecondaryPropertyResponseRefEXE2 == "Updated") {
        System.out.println("Updated Secondary properties for reference EXE");
        reference3Updated = true;
      }

      System.out.println("Updating valid secondary properties for footnote EXE");
      String updateSecondaryPropertyResponseFootnoteEXE1 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyString3);
      String updateSecondaryPropertyResponseFootnoteEXE2 =
          api.updateSecondaryFootnoteProperty(
              appUrl, serviceName, entityID3, footnoteID3, secondaryPropertyInt3);

      if (updateSecondaryPropertyResponseFootnoteEXE1 == "Updated"
          && updateSecondaryPropertyResponseFootnoteEXE2 == "Updated") {
        System.out.println("Updated Secondary properties for footnote EXE");
        footnote3Updated = true;
      }

      if (attachment1Updated
          && attachment2Updated
          && attachment3Updated
          && reference1Updated
          && reference2Updated
          && reference3Updated
          && footnote1Updated
          && footnote2Updated
          && footnote3Updated) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);

        // Fetching and asserting Attachment PDF metadata
        Map<String, Object> attachmentMetadataPDF =
            api.fetchAttachmentMetadata(appUrl, serviceName, entityName, entityID3, attachmentID1);
        assertEquals("sample.pdf", attachmentMetadataPDF.get("fileName"));
        assertNull(attachmentMetadataPDF.get("abc___myId1"));
        assertNull(attachmentMetadataPDF.get("abc___myId2"));
        assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordString"));
        assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordInt"));
        assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
        assertNull(attachmentMetadataPDF.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Attachment TXT metadata
        Map<String, Object> attachmentMetadataTXT =
            api.fetchAttachmentMetadata(appUrl, serviceName, entityName, entityID3, attachmentID2);
        assertEquals("sample.txt", attachmentMetadataTXT.get("fileName"));
        assertNull(attachmentMetadataTXT.get("abc___myId1"));
        assertNull(attachmentMetadataTXT.get("abc___myId2"));
        assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordString"));
        assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordInt"));
        assertFalse((Boolean) attachmentMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
        assertNull(attachmentMetadataTXT.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Attachment EXE metadata
        Map<String, Object> attachmentMetadataEXE =
            api.fetchAttachmentMetadata(appUrl, serviceName, entityName, entityID3, attachmentID3);
        assertEquals("sample.exe", attachmentMetadataEXE.get("fileName"));
        assertNull(attachmentMetadataEXE.get("abc___myId1"));
        assertNull(attachmentMetadataEXE.get("abc___myId2"));
        assertEquals("sample", attachmentMetadataEXE.get("Working___DocumentInfoRecordString"));
        assertEquals(12, attachmentMetadataEXE.get("Working___DocumentInfoRecordInt"));

        // Fetching and asserting Reference PDF metadata
        Map<String, Object> referenceMetadataPDF =
            api.fetchReferenceMetadata(appUrl, serviceName, entityName, entityID3, referenceID1);
        assertEquals("sample.pdf", referenceMetadataPDF.get("fileName"));
        assertNull(referenceMetadataPDF.get("abc___myId1"));
        assertNull(referenceMetadataPDF.get("abc___myId2"));
        assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordString"));
        assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordInt"));
        assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
        assertNull(referenceMetadataPDF.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Reference TXT metadata
        Map<String, Object> referenceMetadataTXT =
            api.fetchReferenceMetadata(appUrl, serviceName, entityName, entityID3, referenceID2);
        assertEquals("sample.txt", referenceMetadataTXT.get("fileName"));
        assertNull(referenceMetadataTXT.get("abc___myId1"));
        assertNull(referenceMetadataTXT.get("abc___myId2"));
        assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordString"));
        assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordInt"));
        assertFalse((Boolean) referenceMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
        assertNull(referenceMetadataTXT.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Reference EXE metadata
        Map<String, Object> referenceMetadataEXE =
            api.fetchReferenceMetadata(appUrl, serviceName, entityName, entityID3, referenceID3);
        assertEquals("sample.exe", referenceMetadataEXE.get("fileName"));
        assertNull(referenceMetadataEXE.get("abc___myId1"));
        assertNull(referenceMetadataEXE.get("abc___myId2"));
        assertEquals("sample", referenceMetadataEXE.get("Working___DocumentInfoRecordString"));
        assertEquals(12, referenceMetadataEXE.get("Working___DocumentInfoRecordInt"));

        // Fetching and asserting Footnote PDF metadata
        Map<String, Object> footnoteMetadataPDF =
            api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID1);
        assertEquals("sample.pdf", footnoteMetadataPDF.get("fileName"));
        assertNull(footnoteMetadataPDF.get("abc___myId1"));
        assertNull(footnoteMetadataPDF.get("abc___myId2"));
        assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordString"));
        assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordInt"));
        assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordBoolean"));
        assertNull(footnoteMetadataPDF.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Footnote TXT metadata
        Map<String, Object> footnoteMetadataTXT =
            api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID2);
        assertEquals("sample.txt", footnoteMetadataTXT.get("fileName"));
        assertNull(footnoteMetadataTXT.get("abc___myId1"));
        assertNull(footnoteMetadataTXT.get("abc___myId2"));
        assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordString"));
        assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordInt"));
        assertFalse((Boolean) footnoteMetadataTXT.get("Working___DocumentInfoRecordBoolean"));
        assertNull(footnoteMetadataTXT.get("Working___DocumentInfoRecordDate"));

        // Fetching and asserting Footnote EXE metadata
        Map<String, Object> footnoteMetadataEXE =
            api.fetchFootnoteMetadata(appUrl, serviceName, entityName, entityID3, footnoteID3);
        assertEquals("sample.exe", footnoteMetadataEXE.get("fileName"));
        assertNull(footnoteMetadataEXE.get("abc___myId1"));
        assertNull(footnoteMetadataEXE.get("abc___myId2"));
        assertEquals("sample", footnoteMetadataEXE.get("Working___DocumentInfoRecordString"));
        assertEquals(12, footnoteMetadataEXE.get("Working___DocumentInfoRecordInt"));

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
}
