package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_MultipleFacet {
  private static String token;
  private static String entityID;
  private static String[] facet = {"attachments", "references", "footnotes"};
  private static String[] ID = {"attachmentID1", "referenceID1", "footnoteID1"};
  private static String[] ID2 = {"attachmentID2", "referenceID2", "footnoteID2"};
  private static String[] ID3 = {"attachmentID3", "referenceID3", "footnoteID3"};
  private static String[] ID4 = {"attachmentID4", "referenceID4", "footnoteID4"};
  private static String entityID2;
  private static String entityID3;
  private static String appUrl;
  private static String authUrl;
  private static String username;
  private static String password;
  private static String serviceName = "UserService";
  private static String entityName = "Notebooks";
  private static String entityName2 = "writer";
  private static String srvpath = "UserService";
  private static Api api;
  private static int counter;

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
        api.createFacet(
            appUrl, serviceName, entityName, facet, newentityId, srvpath, postData, file);
    String check = FacetResponse.get(0);
    if (check.equals(facet + " created")) {
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
      response[i] = api.readFacetDraft(appUrl, serviceName, entityName, facet[i], entityID, ID[i]);
      if ("OK".equals(response[i])) Counter++;
    }
    if (Counter >= 2) {
      String saveResponse = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if ("Saved".equals(saveResponse)) {
        for (int i = 0; i < facet.length; i++) {
          response[i] = api.readFacet(appUrl, serviceName, entityName, facet[i], entityID, ID[i]);
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

  private boolean renameAndCheck(String facet, String id, String eId, String newName) {
    String result;
    String type = facet;
    switch (type.toLowerCase()) {
      case "attachments":
        result = api.renameFacet(appUrl, serviceName, entityName, facet, eId, id, newName);
        break;
      case "references":
        result = api.renameFacet(appUrl, serviceName, entityName, facet, eId, id, newName);
        break;
      case "footnotes":
        result = api.renameFacet(appUrl, serviceName, entityName, facet, eId, id, newName);
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
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
      entityID = response;
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response.equals("Saved")) {
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
    if (response.equals("Entity in draft mode")) {
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response.equals("Saved")) {
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
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

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if ("Entity in draft mode".equals(response)) {
      Boolean allFacetsFailedCorrectly = true;
      for (int i = 0; i < facet.length; i++) {
        List<String> facetResponse =
            api.createFacet(
                appUrl, serviceName, entityName, facet[i], entityID, srvpath, postData, file);
        allFacetsFailedCorrectly &= checkDuplicateCreation(facet[i], facetResponse);
      }
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
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
      String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);

      if ("Entity in draft mode".equals(response)) {
        String[] name = {"sample123", "reference123", "footnote123"};
        for (int i = 0; i < facet.length; i++) {
          // Read the facet to ensure it exists
          response =
              api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID, ID[i], name[i]);
          if (!"Renamed".equals(response)) {
            testStatus = false;
            System.out.println(facet[i] + " was not renamed: " + response);
          }
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
    String[] name = {"sample1234", "reference1234", "footnote1234"};
    String[] name2 = {"sample12345", "reference12345", "footnote12345"};
    for (int i = 0; i < facet.length; i++) {
      // Read the facet to ensure it exists
      testStatus &= renameAndCheck(facet[i], ID2[i], entityID, name[i]);
      testStatus &= renameAndCheck(facet[i], ID3[i], entityID, name2[i]);
    }
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
  void testRenameSingleDuplicate() {
    System.out.println("Test (10) : Rename duplicates for attachment, reference, and footnote");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String[] name = {"sample1234", "reference1234", "footnote1234"};
    String[] name2 = {"sample123456", "reference123456", "footnote123456"};
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID, ID3[i], name[i]);
        if (response.equals("Renamed")) counter++;
      }
      if (counter >= 2) {
        counter = -1; // Reset counter for the next check
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
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
                api.renameFacet(
                    appUrl, serviceName, entityName, facet[i], entityID, ID3[i], name2[i]);
            if (response.equals("Renamed")) counter++;
          }
        }
        if (counter >= 2) {
          // If all renames were successful, save the draft
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            testStatus = true;
          }
        } else {
          testStatus = false;
          fail("Attachment was renamed");
        }
      } else {
        api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
  }

  @Test
  @Order(11)
  void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (11) : Delete single attachment, reference, and footnote");
    Boolean testStatus = false;

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        response = api.deleteFacet(appUrl, serviceName, entityName, facet[i], entityID, ID[i]);
        if (response.equals("Deleted")) counter++;
      }
      if (counter >= 2)
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      counter = -1; // Reset counter for the next check
      if (response.equals("Saved")) {
        for (int i = 0; i < facet.length; i++) {
          response = api.readFacet(appUrl, serviceName, entityName, facet[i], entityID, ID[i]);
          if (response.equals("Could not read " + facet[i])) counter++;
        }
        if (counter >= 2) testStatus = true;
        else fail("Could not read deleted facets");
      } else {
        fail("Could not save entity after deletion");
      }
    }
  }

  @Test
  @Order(12)
  void testDeleteMultipleAttachmentsReferencesFootnotes() throws IOException {
    System.out.println("Test (12) : Delete multiple attachments, references, and footnotes");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response.equals("Entity in draft mode")) {
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.deleteFacet(appUrl, serviceName, entityName, facet[i], entityID, ID2[i]);
        String response2 =
            api.deleteFacet(appUrl, serviceName, entityName, facet[i], entityID, ID3[i]);
        if (response1.equals("Deleted") && response2.equals("Deleted")) counter++;
      }
    }
    if (counter >= 2)
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response.equals("Saved")) {
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.readFacet(appUrl, serviceName, entityName, facet[i], entityID, ID2[i]);
        String response2 =
            api.readFacet(appUrl, serviceName, entityName, facet[i], entityID, ID3[i]);
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
  @Order(13)
  void testDeleteEntity() {
    System.out.println("Test (13) : Delete entity");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, serviceName, entityName, entityID);
    String response2 = api.deleteEntity(appUrl, serviceName, entityName, entityID2);
    if (response.equals("Entity Deleted") && response2.equals("Entity Deleted")) testStatus = true;
    if (!testStatus) fail("Could not delete entity");
  }

  @Test
  @Order(14)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println("Test (14) : Rename & Update secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
    if (response != "Could not create entity") {
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

      // Rename and update secondary properties
      String secondaryPropertyString = "sample12345";
      Integer secondaryPropertyInt = 1234;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      String name[] = {"sample1234.pdf", "reference1234.pdf", "footnote1234.pdf"};
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name[i]);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2)
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
      if (response.equals("Saved")) testStatus = true;
    }
    if (!testStatus) fail("Could not update secondary property before entity is saved");
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
      String name[] = {"sample.pdf", "reference_sample.pdf", "footnote_sample.pdf"};
      String secondaryPropertyString = "sampleString";
      Integer secondaryPropertyInt = 42;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();

      System.out.println("Renaming and updating secondary properties for attachment");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name[i]);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2)
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
      if (response.equals("Saved")) {
        testStatus = true;
        System.out.println("Renamed & updated Secondary properties for attachment");
      }
      // Clean up
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) fail("Could not delete entity");
    }
    if (!testStatus) fail("Could not update secondary properties after entity is saved");
  }

  @Test
  @Order(16)
  void testUpdateInvalidSecondaryProperty_beforeEntityIsSaved_single() throws IOException {
    System.out.println(
        "Test (16): Rename & Update invalid secondary property before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
      String secondaryPropertyString = "sample12345";
      Integer secondaryPropertyInt = 1234;
      LocalDateTime secondaryPropertyDateTime = LocalDateTime.now();
      String invalidProperty = "testid";

      for (int i = 0; i < facet.length; i++) {
        // Rename and update secondary properties
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateInvalidSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], invalidProperty);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2)
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
      for (int i = 0; i < facet.length; i++) {
        Map<String, Object> FacetMetadata =
            api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID[i]);
        assertEquals("sample.pdf", FacetMetadata.get("fileName"));
        assertNull(FacetMetadata.get("abc___myId1"));
        assertNull(FacetMetadata.get("abc___myId2"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
      }
      if (response.equals("Saved")) {
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

      for (int i = 0; i < facet.length; i++) {
        // Rename and update secondary properties
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime);
        String updateSecondaryPropertyResponse4 =
            api.updateInvalidSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], invalidProperty);

        if (response1.equals("Renamed")
            && updateSecondaryPropertyResponse1.equals("Updated")
            && updateSecondaryPropertyResponse2.equals("Updated")
            && updateSecondaryPropertyResponse3.equals("Updated")
            && updateSecondaryPropertyResponse4.equals("Updated")) counter++;
      }
      if (counter >= 2)
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
      for (int i = 0; i < facet.length; i++) {
        Map<String, Object> FacetMetadata =
            api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID[i]);
        assertEquals("sample.pdf", FacetMetadata.get("fileName"));
        assertNull(FacetMetadata.get("abc___myId1"));
        assertNull(FacetMetadata.get("abc___myId2"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
        assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
      }
      if (response.equals("Saved")) {
        System.out.println("Entity saved");
        testStatus = true;
        System.out.println(
            "Rename & update secondary properties for attachment, reference, footnote is unsuccessfull");
      }
      String deleteEntityResponse = api.deleteEntity(appUrl, serviceName, entityName, entityID3);
      if (!deleteEntityResponse.equals("Entity Deleted")) {
        fail("Could not delete entity");
      }
    }
    if (!testStatus)
      fail(
          "Could not update secondary property after entity is saved for attachment, reference, or footnote");
  }

  @Test
  @Order(18)
  void testUpdateValidSecondaryProperty_beforeEntityIsSaved_multipleAttachments()
      throws IOException {
    System.out.println(
        "Test (18): Rename & Update valid secondary properties for multiple facets before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
      String secondaryPropertyString1 = "sample12345";
      Integer secondaryPropertyInt1 = 1234;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      // PDF
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt1);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime1);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);

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
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID2[i], true);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }

      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyInt1);
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyDateTime1);

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
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
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
  @Order(19)
  void testUpdateValidSecondaryProperty_afterEntityIsSaved_multipleAttachments() {
    System.out.println(
        "Test (19): Rename & Update  valid secondary properties for multiple facets after entity is saved");
    System.out.println("Editing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
    if (response.equals("Entity in draft mode")) {
      Boolean Updated1[] = new Boolean[3];
      Boolean Updated2[] = new Boolean[3];
      Boolean Updated3[] = new Boolean[3];

      String name1 = "sample1.pdf";
      String secondaryPropertyString1 = "sample1";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt1);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime1);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);

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
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID2[i], true);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }

      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyInt1);
        String updateSecondaryPropertyResponseEXE3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyDateTime1);

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
        "Test (20): Rename & Update invalid and valid secondary properties for multiple facets before entity is saved");
    System.out.println("Creating entity");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, entityName2, srvpath);
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
      String secondaryPropertyString1 = "sample12345";
      Integer secondaryPropertyInt1 = 1234;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";

      // PDF
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt1);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime1);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], invalidPropertyPDF);

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
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID2[i], true);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }
      String secondaryPropertyString3 = "sample12345";
      Integer secondaryPropertyInt3 = 1234;

      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyString3);
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyInt3);

        if (updateSecondaryPropertyResponseEXE1.equals("Updated")
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
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        String name[] = {"sample.pdf", "sample.txt", "sample.exe"};
        // for PDF
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID[i]);
          assertEquals(name[0], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
        }
        // for TXT
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID2[i]);
          assertEquals(name[1], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
          assertTrue((Boolean) FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
        }
        // for EXE
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID3[i]);
          assertEquals(name[2], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertEquals("sample12345", FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertEquals(1234, FacetMetadata.get("Working___DocumentInfoRecordInt"));
        }
        if ("Saved".equals(response)) {
          System.out.println("Entity saved");
          testStatus = true;
          System.out.println(
              "Rename & update unsuccessfull for invalid Secondary properties and successfull for valid property attachments");
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
    if (response.equals("Entity in draft mode")) {
      Boolean Updated1[] = new Boolean[3];
      Boolean Updated2[] = new Boolean[3];
      Boolean Updated3[] = new Boolean[3];
      String name1 = "sample.pdf";
      String secondaryPropertyString1 = "sample";
      Integer secondaryPropertyInt1 = 12;
      LocalDateTime secondaryPropertyDateTime1 = LocalDateTime.now();
      String invalidPropertyPDF = "testidinvalidPDF";

      // PDF
      System.out.println("Renaming and updating secondary properties for PDF");
      for (int i = 0; i < facet.length; i++) {
        String response1 =
            api.renameFacet(appUrl, serviceName, entityName, facet[i], entityID3, ID[i], name1);
        String updateSecondaryPropertyResponse1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyString1);
        String updateSecondaryPropertyResponse2 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], secondaryPropertyInt1);
        String updateSecondaryPropertyResponse3 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID[i],
                secondaryPropertyDateTime1);
        String updateSecondaryPropertyResponse4 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], true);
        String updateSecondaryPropertyResponse5 =
            api.updateInvalidSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID[i], invalidPropertyPDF);

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
        String updateSecondaryPropertyResponseTXT1 =
            api.updateSecondaryProperty(
                appUrl, serviceName, entityName, facet[i], entityID3, ID2[i], false);
        if (updateSecondaryPropertyResponseTXT1.equals("Updated")) {
          Updated2[i] = true;
          System.out.println("Renamed & updated Secondary properties for " + facet[i] + " TXT");
        }
      }

      String secondaryPropertyString3 = "sample";
      Integer secondaryPropertyInt3 = 12;
      // EXE
      System.out.println("Renaming and updating secondary properties for EXE");
      for (int i = 0; i < facet.length; i++) {
        String updateSecondaryPropertyResponseEXE1 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyString3);
        String updateSecondaryPropertyResponseEXE2 =
            api.updateSecondaryProperty(
                appUrl,
                serviceName,
                entityName,
                facet[i],
                entityID3,
                ID3[i],
                secondaryPropertyInt3);

        if (updateSecondaryPropertyResponseEXE1.equals("Updated")
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
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID3);
        String name[] = {"sample.pdf", "sample.txt", "sample.exe"};
        // for PDF
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID[i]);
          assertEquals(name[0], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
        }
        // for TXT
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID2[i]);
          assertEquals(name[1], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordInt"));
          assertFalse((Boolean) FacetMetadata.get("Working___DocumentInfoRecordBoolean"));
          assertNull(FacetMetadata.get("Working___DocumentInfoRecordDate"));
        }
        // for EXE
        for (int i = 0; i < facet.length; i++) {
          Map<String, Object> FacetMetadata =
              api.fetchFacetMetadata(appUrl, serviceName, entityName, facet[i], entityID3, ID3[i]);
          assertEquals(name[2], FacetMetadata.get("fileName"));
          assertNull(FacetMetadata.get("abc___myId1"));
          assertNull(FacetMetadata.get("abc___myId2"));
          assertEquals("sample", FacetMetadata.get("Working___DocumentInfoRecordString"));
          assertEquals(12, FacetMetadata.get("Working___DocumentInfoRecordInt"));
        }

        if (response.equals("Saved")) {
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
