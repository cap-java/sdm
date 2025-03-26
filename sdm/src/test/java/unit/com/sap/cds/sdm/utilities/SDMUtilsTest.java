package unit.com.sap.cds.sdm.utilities;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMUtilsTest {

  @Mock private PersistenceService mockPersistenceService;
  @Mock private MockedStatic<DBQuery> mockedDbQuery;
  @Mock private CdsEntity mockEntity;
  @Mock private CdsElement mockElement;
  @Mock private CdsAnnotation<Object> mockAnnotation;
  @Mock private JsonObject jsonObjectMock;
  @Mock private HttpEntity responseEntity;

  private void setUp() {
    mockedDbQuery = mockStatic(DBQuery.class);
    mockEntity = mock(CdsEntity.class);
    mockElement = mock(CdsElement.class);
    mockAnnotation = mock(CdsAnnotation.class);
    jsonObjectMock = mock(JsonObject.class);
    responseEntity = mock(HttpEntity.class);
  }

  @Test
  public void testIsFileNameDuplicateInDrafts() {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "file1.txt");
    attachment1.put("repositoryId", "repo1");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "file1.txt");
    attachment2.put("repositoryId", "repo1");
    attachments.add(attachment1);
    attachments.add(attachment2);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments); // Correctly mock get method
    data.add(mockCdsData);

    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data);

    assertTrue(duplicateFilenames.contains("file1.txt"));
  }

  @Test
  public void testIsFileNameContainsRestrictedCharaters() {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);

    when(mockCdsData.get("attachments")).thenReturn(null); // Correctly mock get method
    data.add(mockCdsData);

    List<String> restrictedFilenames = SDMUtils.isFileNameContainsRestrictedCharaters(data);

    assertEquals(0, restrictedFilenames.size());
  }

  @Test
  public void testIsFileNameContainsRestrictedCharatersNoData() {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();

    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "file1.txt");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "file2/abc.txt");
    Map<String, Object> attachment3 = new HashMap<>();
    attachment3.put("fileName", "file3\\abc.txt");
    attachments.add(attachment1);
    attachments.add(attachment2);
    attachments.add(attachment3);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments); // Correctly mock get method
    data.add(mockCdsData);

    List<String> restrictedFilenames = SDMUtils.isFileNameContainsRestrictedCharaters(data);

    assertEquals(2, restrictedFilenames.size());
    assertTrue(restrictedFilenames.contains("file2/abc.txt"));
    assertTrue(restrictedFilenames.contains("file3\\abc.txt"));
  }

  @Test
  public void testIsRestrictedCharactersInName() {
    assertTrue(SDMUtils.isRestrictedCharactersInName("file/abc.txt"));
    assertTrue(SDMUtils.isRestrictedCharactersInName("file\\abc.txt"));
    assertFalse(SDMUtils.isRestrictedCharactersInName("file-abc.txt"));
    assertFalse(SDMUtils.isRestrictedCharactersInName("file_abc.txt"));
  }

  @Test
  public void prepareSecondaryPropertiesTest_withFilenameKey() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "myfile.txt");

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, "myfile.txt");

    assertEquals("cmis:name", requestBody.get("propertyId[1]"));
    assertEquals("myfile.txt", requestBody.get("propertyValue[1]"));
  }

  @Test
  public void testPrepareSecondaryProperties_withOtherKeys() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("author", "test user");
    secondaryProperties.put("subject", "JUnit Testing");

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, "testfile.txt");

    assertEquals("author", requestBody.get("propertyId[1]"));
    assertEquals("test user", requestBody.get("propertyValue[1]"));
    assertEquals("subject", requestBody.get("propertyId[2]"));
    assertEquals("JUnit Testing", requestBody.get("propertyValue[2]"));
  }

  @Test
  public void testPrepareSecondaryProperties_emptySecondaryProperties() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, "emptyfile.txt");

    assertTrue(requestBody.isEmpty());
  }

  @Test
  public void testCheckMCM_withValidResponse() throws IOException {
    // Create a mock response entity with a valid JSON string
    String jsonResponse =
        "{\"propertyDefinitions\": {"
            + "\"property1\": {\"mcm:miscellaneous\": {\"isPartOfTable\": \"true\"}},"
            + "\"property2\": {\"mcm:miscellaneous\": {\"isPartOfTable\": \"false\"}}"
            + "}}";

    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    assertTrue(result);
    assertEquals(1, secondaryPropertyIds.size());
    assertEquals("property1", secondaryPropertyIds.get(0));
  }

  @Test
  public void testCheckMCM_withEmptyResponse() throws IOException {
    // Create a mock response entity with an empty JSON string
    String jsonResponse = "";

    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty());
  }

  @Test
  public void testCheckMCM_withMissingPropertyDefinitions() throws IOException {
    // Create a mock response entity with a JSON string missing propertyDefinitions
    String jsonResponse = "{\"otherKey\": {}}";

    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty());
  }

  @Test
  public void testCheckMCM_withPropertyDefinitionNull() throws IOException {
    // Create a mock response entity with valid propertyDefinitions but not part of the table
    String jsonResponse = "{\"propertyDefinitions\": null}";
    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    // Call the method to test
    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    // Assertions
    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty());
  }

  @Test
  public void testCheckMCM_withPropertyDefinitionsNotPartOfTable() throws IOException {
    // Create a mock response entity with valid propertyDefinitions but not part of the table
    String jsonResponse =
        "{\"propertyDefinitions\": {"
            + "\"propertyA\": {\"mcm:miscellaneous\": {\"isPartOfTable\": \"false\"}}"
            + "}}";

    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    // Call the method to test
    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    // Assertions
    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty());
  }

  @Test
  public void testCheckMCM_withMCMMiscellanousNotPartOfTable() throws IOException {
    // Create a mock response entity with valid propertyDefinitions but not part of the table
    String jsonResponse =
        "{\"propertyDefinitions\": {"
            + "\"propertyA\": {\"mcm:miscellaneous\": {\"isQueryableInUi\": \"false\"}}"
            + "}}";
    HttpEntity responseEntity = new StringEntity(jsonResponse, StandardCharsets.UTF_8);

    List<String> secondaryPropertyIds = new ArrayList<>();

    // Call the method to test
    Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

    // Assertions
    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty());
  }

  @Test
  public void testAssembleRequestBodySecondaryTypes() {
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("propertyId1", "value1");
    requestBody.put("propertyId2", "value2");

    String objectId = "testObjectId";

    SDMUtils.assembleRequestBodySecondaryTypes(builder, requestBody, objectId);

    assertDoesNotThrow(
        () -> {
          assertTrue(builder.build().isRepeatable());
        });
  }

  @Test
  public void testExtractSecondaryTypeIds_withValidJSONArray() {
    JSONArray jsonArray = new JSONArray();

    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("type", new JSONObject().put("id", "typeId1"));
    jsonArray.put(jsonObject1);

    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("type", new JSONObject().put("id", "typeId2"));
    jsonObject2.put(
        "children",
        new JSONArray(
            Collections.singletonList(
                new JSONObject().put("type", new JSONObject().put("id", "childTypeId1")))));
    jsonArray.put(jsonObject2);

    List<String> result = new ArrayList<>();
    SDMUtils.extractSecondaryTypeIds(jsonArray, result);

    assertEquals(3, result.size());
    assertTrue(result.contains("typeId1"));
    assertTrue(result.contains("typeId2"));
    assertTrue(result.contains("childTypeId1"));
  }

  @Test
  public void testExtractSecondaryTypeIds_withOnlyTypeJSONArray() {
    JSONArray jsonArray = new JSONArray();

    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("type", new JSONObject().put("notid", "typeId1"));
    jsonArray.put(jsonObject1);

    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("type", new JSONObject().put("notid", "typeId2"));
    jsonObject2.put(
        "children",
        new JSONArray(
            Collections.singletonList(
                new JSONObject().put("type", new JSONObject().put("notid", "childTypeId1")))));
    jsonArray.put(jsonObject2);

    List<String> result = new ArrayList<>();
    SDMUtils.extractSecondaryTypeIds(jsonArray, result);

    assertEquals(0, result.size());
    assertFalse(result.contains("typeId1"));
    assertFalse(result.contains("typeId2"));
    assertFalse(result.contains("childTypeId1"));
  }

  @Test
  public void testExtractSecondaryTypeIds_withEmptyJSONArray() {
    JSONArray jsonArray = new JSONArray();

    List<String> result = new ArrayList<>();
    SDMUtils.extractSecondaryTypeIds(jsonArray, result);

    assertTrue(result.isEmpty());
  }

  @Test
  public void testGetUpdatedSecondaryProperties_withModifiedValues() {
    // Mock the necessary components
    CdsEntity mockEntity = mock(CdsEntity.class);
    PersistenceService mockPersistenceService = mock(PersistenceService.class);

    // Prepare attachment and secondaryTypeProperties
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "123");
    attachment.put("property1", "newValue1");
    attachment.put("property2", "newValue2");

    List<String> secondaryTypeProperties = Arrays.asList("property1", "property2");

    // Mock DBQuery class behavior
    List<String> propertiesInDB = Arrays.asList("oldValue1", "newValue2");
    mockedDbQuery
        .when(
            () ->
                DBQuery.getpropertiesForID(
                    mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
        .thenReturn(propertiesInDB);

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.of(mockEntity), attachment, mockPersistenceService, secondaryTypeProperties);

    assertEquals(1, result.size());
    assertEquals("newValue1", result.get("property1"));
    assertNull(result.get("property2"));
  }

  @Test
  public void testGetUpdatedSecondaryProperties_withSecondaryTypePropertiesNull() {
    // Mock the necessary components
    CdsEntity mockEntity = mock(CdsEntity.class);
    PersistenceService mockPersistenceService = mock(PersistenceService.class);

    // Prepare attachment and secondaryTypeProperties
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "123");
    attachment.put("property1", "newValue1");
    attachment.put("property2", "newValue2");

    List<String> secondaryTypeProperties = new ArrayList<>();

    // Mock DBQuery class behavior
    List<String> propertiesInDB = new ArrayList<>();
    mockedDbQuery
        .when(
            () ->
                DBQuery.getpropertiesForID(
                    mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
        .thenReturn(propertiesInDB);

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.of(mockEntity), attachment, mockPersistenceService, secondaryTypeProperties);

    assertEquals(0, result.size());
    assertEquals(null, result.get("property1"));
    assertEquals(null, result.get("property2"));
  }

  @Test
  public void testGetUpdatedSecondaryProperties_withPropertiesMapNull() {
    // Mock the necessary components
    CdsEntity mockEntity = mock(CdsEntity.class);
    PersistenceService mockPersistenceService = mock(PersistenceService.class);

    // Prepare attachment and secondaryTypeProperties
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "123");

    List<String> secondaryTypeProperties = new ArrayList<>();

    // Mock DBQuery class behavior
    List<String> propertiesInDB = new ArrayList<>();
    mockedDbQuery
        .when(
            () ->
                DBQuery.getpropertiesForID(
                    mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
        .thenReturn(propertiesInDB);

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.of(mockEntity), attachment, mockPersistenceService, secondaryTypeProperties);

    assertEquals(0, result.size());
    assertEquals(null, result.get("property1"));
    assertEquals(null, result.get("property2"));
  }

  @Test
  public void testGetUpdatedSecondaryProperties_DBPropertiesNull() {
    // Mock the necessary components
    CdsEntity mockEntity = mock(CdsEntity.class);
    PersistenceService mockPersistenceService = mock(PersistenceService.class);

    // Prepare attachment and secondaryTypeProperties
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "123");
    attachment.put("property1", "newValue1");
    attachment.put("property2", "newValue2");

    List<String> secondaryTypeProperties = Arrays.asList("property1", "property2");

    // Mock DBQuery class behavior
    List<String> propertiesInDB = null;
    mockedDbQuery
        .when(
            () ->
                DBQuery.getpropertiesForID(
                    mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
        .thenReturn(propertiesInDB);

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.of(mockEntity), attachment, mockPersistenceService, secondaryTypeProperties);

    assertEquals(2, result.size());
    assertEquals("newValue1", result.get("property1"));
    assertEquals("newValue2", result.get("property2"));
  }

  @Test
  public void testGetUpdatedSecondaryProperties_withNoChanges() {
    // Mock the necessary components
    PersistenceService mockPersistenceService = mock(PersistenceService.class);

    // Prepare attachment and secondaryTypeProperties
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "123");
    attachment.put("property1", "sameValue1");
    attachment.put("property2", "sameValue2");

    List<String> secondaryTypeProperties = Arrays.asList("property1", "property2");

    // Mock DBQuery static method behavior using try-with-resources
    List<String> propertiesInDB = Arrays.asList("sameValue1", "sameValue2");
    mockedDbQuery
        .when(
            () ->
                DBQuery.getpropertiesForID(
                    mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
        .thenReturn(propertiesInDB);

    // Call the method under test
    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.of(mockEntity), attachment, mockPersistenceService, secondaryTypeProperties);

    // Validate results
    assertTrue(result.isEmpty());
  }

  @Test
  public void getSecondaryTypeProperties_whenAnnotationIsPresent() {
    Optional<CdsEntity> attachmentEntity = Optional.of(mockEntity);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("VALID_PROPERTY", new Object());
    when(mockEntity.getElement("VALID_PROPERTY")).thenReturn(mockElement);
    when(mockElement.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(mockAnnotation));
    when(mockElement.getName()).thenReturn("VALID_PROPERTY");

    // Act: calling the method under test
    List<String> result = SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);

    // Assert: we expect "VALID_PROPERTY" to be in the result
    assertEquals(Collections.singletonList("VALID_PROPERTY"), result);
  }

  @Test
  public void testPropertyNullOrMissingMiscellaneous() throws IOException {
    // Arrange
    HttpEntity mockResponseEntity = mock(HttpEntity.class);
    List<String> secondaryPropertyIds = new ArrayList<>();

    // Simulate response string with "propertyDefinitions" but no "mcm:miscellaneous"
    String responseString = "{\"propertyDefinitions\": {\"key1\": {}}}";
    when(mockResponseEntity.getContent())
        .thenReturn(new java.io.ByteArrayInputStream(responseString.getBytes()));

    // Act
    Boolean result = SDMUtils.checkMCM(mockResponseEntity, secondaryPropertyIds);

    // Assert
    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty()); // No property ID should be added
  }

  @Test
  public void testPropertyValueIsNullInMapAndNotNullInDB() {
    // Arrange
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "12345"); // Sample ID

    // Simulating that "property1" has a null value in attachment map
    attachment.put("property1", null);

    // Secondary type properties to check
    List<String> secondaryTypeProperties = Arrays.asList("property1", "property2");

    // Simulate the database response where "property1" has a value in the DB
    List<String> propertiesInDB = Arrays.asList("DBValueForProperty1", "DBValueForProperty2");

    // Mocking the DBQuery call to return propertiesInDB for "property1"
    when(DBQuery.getpropertiesForID(
            any(), eq(mockPersistenceService), eq("12345"), eq(secondaryTypeProperties)))
        .thenReturn(propertiesInDB);

    Optional<CdsEntity> attachmentEntity = Optional.of(mock(CdsEntity.class));

    // Act
    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            attachmentEntity, attachment, mockPersistenceService, secondaryTypeProperties);

    // Assert
    assertTrue(result.containsKey("property1"));
    assertNull(
        result.get(
            "property1")); // Since property1 is null in attachment and non-null in DB, it should be
    // set to null
  }

  @Test
  void testAttachmentEntityNotPresent() {
    List<String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.empty(), Map.of("key1", "value1"));
    assertEquals(Collections.emptyList(), result);
  }

  @Test
  void testAttachmentEntityPresentNoMatchingKeys() {
    CdsEntity entity = mock(CdsEntity.class);
    when(entity.getElement(anyString())).thenReturn(null);

    List<String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(Collections.emptyList(), result);
  }

  @Test
  void testDraftReadonlyContextSkipped() {
    CdsEntity entity = mock(CdsEntity.class);
    List<String> result =
        SDMUtils.getSecondaryTypeProperties(
            Optional.of(entity), Map.of("DRAFT_READONLY_CONTEXT", "value"));
    assertEquals(Collections.emptyList(), result);
    verify(entity, never()).getElement(anyString());
  }

  @Test
  void testElementWithoutAnnotation() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    when(entity.getElement("key1")).thenReturn(element);
    when(element.findAnnotation(anyString())).thenReturn(Optional.empty());

    List<String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(Collections.emptyList(), result);
  }

  @Test
  void testElementWithAnnotation() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    @SuppressWarnings("unchecked")
    CdsAnnotation<Object> annotation = mock(CdsAnnotation.class);

    when(entity.getElement("key1")).thenReturn(element);
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(annotation));
    when(element.getName()).thenReturn("key1");

    List<String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(List.of("key1"), result);
  }
}
