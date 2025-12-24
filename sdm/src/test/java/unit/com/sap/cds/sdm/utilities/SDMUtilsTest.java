package unit.com.sap.cds.sdm.utilities;

import static com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.cds.CdsData;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.ehcache.Cache;
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
  @Mock private HttpEntity responseEntity;
  @Mock private CdsEntity attachmentEntity;
  @Mock private CdsAnnotation<Object> maxcountAnnotation;

  @Mock private CdsAnnotation<Object> errormsgAnnotation;
  private List<CdsEntity> entities;

  private void setUp() {
    mockedDbQuery = mockStatic(DBQuery.class);
    mockEntity = mock(CdsEntity.class);
    mockElement = mock(CdsElement.class);
    mockAnnotation = mock(CdsAnnotation.class);
    responseEntity = mock(HttpEntity.class);
    entities = new ArrayList<>();
  }

  @Test
  public void testIsFileNameDuplicateInDrafts() {
    List<CdsData> data = new ArrayList<>();
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

    // Create the nested structure that fetchAttachments expects
    Map<String, Object> entityData = new HashMap<>();
    entityData.put("attachmentCompositionName", attachments);
    entity.put("entity", entityData);
    data.add(CdsData.create(entity));

    Set<String> duplicateFilenames =
        SDMUtils.FileNameDuplicateInDrafts(data, "attachmentCompositionName", "entity", "upId");

    assertTrue(duplicateFilenames.contains("file1.txt"));
  }

  @Test
  public void testIsFileNameContainsRestrictedCharaters() {
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", "file/1.txt"); // restricted char
    attachments.add(attachment);
    entity.put("composition", attachments);
    data.add(CdsData.create(entity));

    try (MockedStatic<AttachmentsHandlerUtils> mocked = mockStatic(AttachmentsHandlerUtils.class)) {
      mocked
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments("TestEntity", entity, "compositionName"))
          .thenReturn(attachments);

      List<String> result =
          SDMUtils.FileNameContainsRestrictedCharaters(data, "compositionName", "TestEntity");
      assertTrue(result.contains("file/1.txt"));
    }
  }

  @Test
  public void testIsFileNameContainsRestrictedCharatersNoData() {
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    entity.put("composition", new ArrayList<>());
    data.add(CdsData.create(entity));

    try (MockedStatic<AttachmentsHandlerUtils> mocked = mockStatic(AttachmentsHandlerUtils.class)) {
      mocked
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments("TestEntity", entity, "compositionName"))
          .thenReturn(Collections.emptyList());

      List<String> result =
          SDMUtils.FileNameContainsRestrictedCharaters(data, "compositionName", "TestEntity");
      assertTrue(result.isEmpty());
    }
  }

  @Test
  public void testIsRestrictedCharactersInName() {
    assertTrue(SDMUtils.hasRestrictedCharactersInName("file/abc.txt"));
    assertTrue(SDMUtils.hasRestrictedCharactersInName("file\\abc.txt"));
    assertFalse(SDMUtils.hasRestrictedCharactersInName("file-abc.txt"));
    assertFalse(SDMUtils.hasRestrictedCharactersInName("file_abc.txt"));
    assertFalse(SDMUtils.hasRestrictedCharactersInName(""));
    assertFalse(SDMUtils.hasRestrictedCharactersInName(null));
  }

  @Test
  public void prepareSecondaryPropertiesTest_withFilenameKey() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "myfile.txt");

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, true);

    assertEquals("cmis:name", requestBody.get("propertyId[1]"));
    assertEquals("myfile.txt", requestBody.get("propertyValue[1]"));
  }

  @Test
  public void testPrepareSecondaryProperties_withOtherKeys() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("author", "test user");
    secondaryProperties.put("subject", "JUnit Testing");

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, true);

    assertEquals("author", requestBody.get("propertyId[1]"));
    assertEquals("test user", requestBody.get("propertyValue[1]"));
    assertEquals("subject", requestBody.get("propertyId[2]"));
    assertEquals("JUnit Testing", requestBody.get("propertyValue[2]"));
  }

  @Test
  public void testPrepareSecondaryProperties_emptySecondaryProperties() {
    Map<String, String> requestBody = new HashMap<>();
    Map<String, String> secondaryProperties = new HashMap<>();

    SDMUtils.prepareSecondaryProperties(requestBody, secondaryProperties, true);

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

  // @Test
  // public void testCheckMCM_withPropertyDefinitionNull() throws IOException {
  // // Create a mock response entity with valid propertyDefinitions but not part
  // of the table
  // String jsonResponse = "{\"propertyDefinitions\": null}";
  // HttpEntity responseEntity = new StringEntity(jsonResponse,
  // StandardCharsets.UTF_8);

  // List<String> secondaryPropertyIds = new ArrayList<>();

  // // Call the method to test
  // Boolean result = SDMUtils.checkMCM(responseEntity, secondaryPropertyIds);

  // // Assertions
  // assertFalse(result);
  // assertTrue(secondaryPropertyIds.isEmpty());
  // }

  @Test
  public void testCheckMCM_withPropertyDefinitionsNotPartOfTable() throws IOException {
    // Create a mock response entity with valid propertyDefinitions but not part of
    // the table
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
    // Create a mock response entity with valid propertyDefinitions but not part of
    // the table
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

  // @Test
  // public void testGetUpdatedSecondaryProperties_withModifiedValues() {
  // // Mock the necessary components
  // CdsEntity mockEntity = mock(CdsEntity.class);
  // PersistenceService mockPersistenceService = mock(PersistenceService.class);

  // // Prepare attachment and secondaryTypeProperties
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "123");
  // attachment.put("property1", "newValue1");
  // attachment.put("property2", "newValue2");

  // List<String> secondaryTypeProperties = Arrays.asList("property1",
  // "property2");

  // // Mock DBQuery class behavior
  // List<String> propertiesInDB = Arrays.asList("oldValue1", "newValue2");
  // mockedDbQuery
  // .when(
  // () ->
  // DBQuery.getpropertiesForID(
  // mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
  // .thenReturn(propertiesInDB);

  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // Optional.of(mockEntity), attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // assertEquals(1, result.size());
  // assertEquals("newValue1", result.get("property1"));
  // assertNull(result.get("property2"));
  // }

  // @Test
  // public void
  // testGetUpdatedSecondaryProperties_withSecondaryTypePropertiesNull() {
  // // Mock the necessary components
  // CdsEntity mockEntity = mock(CdsEntity.class);
  // PersistenceService mockPersistenceService = mock(PersistenceService.class);

  // // Prepare attachment and secondaryTypeProperties
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "123");
  // attachment.put("property1", "newValue1");
  // attachment.put("property2", "newValue2");

  // List<String> secondaryTypeProperties = new ArrayList<>();

  // // Mock DBQuery class behavior
  // List<String> propertiesInDB = new ArrayList<>();
  // mockedDbQuery
  // .when(
  // () ->
  // DBQuery.getpropertiesForID(
  // mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
  // .thenReturn(propertiesInDB);

  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // Optional.of(mockEntity), attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // assertEquals(0, result.size());
  // assertEquals(null, result.get("property1"));
  // assertEquals(null, result.get("property2"));
  // }

  // @Test
  // public void testGetUpdatedSecondaryProperties_withPropertiesMapNull() {
  // // Mock the necessary components
  // CdsEntity mockEntity = mock(CdsEntity.class);
  // PersistenceService mockPersistenceService = mock(PersistenceService.class);

  // // Prepare attachment and secondaryTypeProperties
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "123");

  // List<String> secondaryTypeProperties = new ArrayList<>();

  // // Mock DBQuery class behavior
  // List<String> propertiesInDB = new ArrayList<>();
  // mockedDbQuery
  // .when(
  // () ->
  // DBQuery.getpropertiesForID(
  // mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
  // .thenReturn(propertiesInDB);

  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // Optional.of(mockEntity), attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // assertEquals(0, result.size());
  // assertEquals(null, result.get("property1"));
  // assertEquals(null, result.get("property2"));
  // }

  // @Test
  // public void testGetUpdatedSecondaryProperties_DBPropertiesNull() {
  // // Mock the necessary components
  // CdsEntity mockEntity = mock(CdsEntity.class);
  // PersistenceService mockPersistenceService = mock(PersistenceService.class);

  // // Prepare attachment and secondaryTypeProperties
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "123");
  // attachment.put("property1", "newValue1");
  // attachment.put("property2", "newValue2");

  // List<String> secondaryTypeProperties = Arrays.asList("property1",
  // "property2");

  // // Mock DBQuery class behavior
  // List<String> propertiesInDB = null;
  // mockedDbQuery
  // .when(
  // () ->
  // DBQuery.getpropertiesForID(
  // mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
  // .thenReturn(propertiesInDB);

  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // Optional.of(mockEntity), attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // assertEquals(2, result.size());
  // assertEquals("newValue1", result.get("property1"));
  // assertEquals("newValue2", result.get("property2"));
  // }

  // @Test
  // public void testGetUpdatedSecondaryProperties_withNoChanges() {
  // // Mock the necessary components
  // PersistenceService mockPersistenceService = mock(PersistenceService.class);

  // // Prepare attachment and secondaryTypeProperties
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "123");
  // attachment.put("property1", "sameValue1");
  // attachment.put("property2", "sameValue2");

  // List<String> secondaryTypeProperties = Arrays.asList("property1",
  // "property2");

  // // Mock DBQuery static method behavior using try-with-resources
  // List<String> propertiesInDB = Arrays.asList("sameValue1", "sameValue2");
  // mockedDbQuery
  // .when(
  // () ->
  // DBQuery.getpropertiesForID(
  // mockEntity, mockPersistenceService, "123", secondaryTypeProperties))
  // .thenReturn(propertiesInDB);

  // // Call the method under test
  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // Optional.of(mockEntity), attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // // Validate results
  // assertTrue(result.isEmpty());
  // }

  @Test
  public void getSecondaryTypeProperties_whenAnnotationIsPresent() {
    Optional<CdsEntity> attachmentEntity = Optional.of(mockEntity);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("VALID_PROPERTY", new Object());
    when(mockAnnotation.getValue()).thenReturn("name");
    when(mockEntity.getElement("VALID_PROPERTY")).thenReturn(mockElement);
    when(mockElement.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.of(mockAnnotation));
    when(mockElement.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(mockAnnotation));
    when(mockElement.getName()).thenReturn("VALID_PROPERTY");

    // Act: calling the method under test
    Map<String, String> result = SDMUtils.getSecondaryTypeProperties(attachmentEntity, attachment);

    // Assert: we expect "VALID_PROPERTY" to be in the result
    assertEquals(Map.of("VALID_PROPERTY", "name"), result);
  }

  @Test
  public void testPropertyNullOrMissingMiscellaneous() throws IOException {
    // Arrange
    HttpEntity mockResponseEntity = mock(HttpEntity.class);
    List<String> secondaryPropertyIds = new ArrayList<>();

    // Simulate response string with "propertyDefinitions" but no
    // "mcm:miscellaneous"
    String responseString = "{\"propertyDefinitions\": {\"key1\": {}}}";
    when(mockResponseEntity.getContent())
        .thenReturn(new java.io.ByteArrayInputStream(responseString.getBytes()));

    // Act
    Boolean result = SDMUtils.checkMCM(mockResponseEntity, secondaryPropertyIds);

    // Assert
    assertFalse(result);
    assertTrue(secondaryPropertyIds.isEmpty()); // No property ID should be added
  }

  // @Test
  // public void testPropertyValueIsNullInMapAndNotNullInDB() {
  // // Arrange
  // Map<String, Object> attachment = new HashMap<>();
  // attachment.put("ID", "12345"); // Sample ID

  // // Simulating that "property1" has a null value in attachment map
  // attachment.put("property1", null);

  // // Secondary type properties to check
  // List<String> secondaryTypeProperties = Arrays.asList("property1",
  // "property2");

  // // Simulate the database response where "property1" has a value in the DB
  // List<String> propertiesInDB = Arrays.asList("DBValueForProperty1",
  // "DBValueForProperty2");

  // // Mocking the DBQuery call to return propertiesInDB for "property1"
  // when(DBQuery.getpropertiesForID(
  // any(), eq(mockPersistenceService), eq("12345"), eq(secondaryTypeProperties)))
  // .thenReturn(propertiesInDB);

  // Optional<CdsEntity> attachmentEntity = Optional.of(mock(CdsEntity.class));

  // // Act
  // Map<String, String> result =
  // SDMUtils.getUpdatedSecondaryProperties(
  // attachmentEntity, attachment, mockPersistenceService,
  // secondaryTypeProperties);

  // // Assert
  // assertTrue(result.containsKey("property1"));
  // assertNull(
  // result.get(
  // "property1")); // Since property1 is null in attachment and non-null in DB,
  // it should
  // be
  // // set to null
  // }

  @Test
  void testAttachmentEntityNotPresent() {
    Map<String, String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.empty(), Map.of("key1", "value1"));
    assertEquals(Collections.emptyMap(), result);
  }

  @Test
  void testAttachmentEntityPresentNoMatchingKeys() {
    CdsEntity entity = mock(CdsEntity.class);
    when(entity.getElement(anyString())).thenReturn(null);

    Map<String, String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(Collections.emptyMap(), result);
  }

  @Test
  void testDraftReadonlyContextSkipped() {
    CdsEntity entity = mock(CdsEntity.class);
    Map<String, String> result =
        SDMUtils.getSecondaryTypeProperties(
            Optional.of(entity), Map.of(SDMConstants.DRAFT_READONLY_CONTEXT, "value"));
    assertEquals(Collections.emptyMap(), result);
    verify(entity, never()).getElement(anyString());
  }

  @Test
  void testElementWithoutAnnotation() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    when(entity.getElement("key1")).thenReturn(element);
    when(element.findAnnotation(anyString())).thenReturn(Optional.empty());

    Map<String, String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(Collections.emptyMap(), result);
  }

  @Test
  void testElementWithAnnotation() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    CdsAnnotation<Object> annotation = mock(CdsAnnotation.class);
    when(annotation.getValue()).thenReturn("name");

    when(entity.getElement("key1")).thenReturn(element);
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.of(annotation));
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(annotation));
    when(element.getName()).thenReturn("key1");

    Map<String, String> result =
        SDMUtils.getSecondaryTypeProperties(Optional.of(entity), Map.of("key1", "value1"));
    assertEquals(Map.of("key1", "name"), result);
  }

  @Test
  public void testGetAttachmentCountAndMessage_CachePresent() {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache mockCache = mock(Cache.class);
      String errorMessageCount = "1__Only one attachment allowed";
      cacheConfigMockedStatic
          .when(CacheConfig::getMaxAllowedAttachmentsCache)
          .thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(errorMessageCount);
      // Invoke the method
      String result = getAttachmentCountAndMessage(entities, attachmentEntity);

      // Assert the result - no processing occurs so default is used
      assertEquals("1__Only one attachment allowed", result);
    }
  }

  @Test
  public void testGetAttachmentCountAndMessage_NoAnnotations() {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic
          .when(CacheConfig::getMaxAllowedAttachmentsCache)
          .thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(null);
      CdsElement cdsElement = mock(CdsElement.class);

      // Set up the composition elements
      List<CdsElement> compElements = Collections.singletonList(cdsElement);
      // when(cdsEntity.compositions().toList()).thenReturn(() -> compElements);

      // Set up the annotations
      CdsEntity entityOne = mock(CdsEntity.class);
      CdsEntity entityTwo = mock(CdsEntity.class);
      when(entityOne.getQualifiedName()).thenReturn("com.sap.demo.EntityOne");
      when(entityTwo.getQualifiedName()).thenReturn("com.sap.demo.EntityOne");
      when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
      entities = List.of(entityOne, entityTwo);
      // Invoke the method
      String result = getAttachmentCountAndMessage(entities, attachmentEntity);

      // Assert the result
      assertEquals("0__null", result);
    }
  }

  @Test
  public void testGetAttachmentCountAndMessage_AnnotationsPresent() {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic
          .when(CacheConfig::getMaxAllowedAttachmentsCache)
          .thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(null);
      CdsEntity mainEntity =
          new CdsEntity() {
            @Override
            public Stream<CdsAnnotation<?>> annotations() {
              return null;
            }

            @Override
            public <T> Optional<CdsAnnotation<T>> findAnnotation(String s) {
              return Optional.empty();
            }

            @Override
            public boolean isAbstract() {
              return false;
            }

            @Override
            public boolean isView() {
              return false;
            }

            @Override
            public boolean isProjection() {
              return false;
            }

            @Override
            public Optional<CqnSelect> query() {
              return Optional.empty();
            }

            @Override
            public Stream<CdsParameter> params() {
              return null;
            }

            @Override
            public Stream<CdsAction> actions() {
              return null;
            }

            @Override
            public CdsAction getAction(String s) {
              return null;
            }

            @Override
            public Optional<CdsAction> findAction(String s) {
              return Optional.empty();
            }

            @Override
            public Stream<CdsFunction> functions() {
              return null;
            }

            @Override
            public CdsFunction getFunction(String s) {
              return null;
            }

            @Override
            public Optional<CdsFunction> findFunction(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getElement(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findElement(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getAssociation(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findAssociation(String s) {
              return Optional.empty();
            }

            @Override
            public <S extends CdsStructuredType> S getTargetOf(String s) {
              return null;
            }

            @Override
            public Stream<CdsElement> elements() {
              return null;
            }

            @Override
            public String getQualifiedName() {
              return "com.sap.demo.EntityOne";
            }

            @Override
            public String getName() {
              return null;
            }

            @Override
            public String getQualifier() {
              return null;
            }

            public Stream<CdsElement> compositions() {
              CdsElement element1 = mock(CdsElement.class);
              CdsElement element2 = mock(CdsElement.class);
              when(element1.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
              when(element2.getQualifiedName()).thenReturn("demo.abcd:nnn");
              when(element1.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT))
                  .thenReturn(Optional.of(maxcountAnnotation));
              when(element1.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT_ERROR_MSG))
                  .thenReturn(Optional.of(errormsgAnnotation));
              when(maxcountAnnotation.getValue()).thenReturn("1");
              when(errormsgAnnotation.getValue()).thenReturn("Only 1 attachment allowed");

              List<CdsElement> compositions = List.of(element1, element2);

              // Create a Stream from the List of CdsElements
              return compositions.stream();
            }
          };
      when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
      entities = List.of(mainEntity);
      // when(cds)
      // Invoke the method
      String result = getAttachmentCountAndMessage(entities, attachmentEntity);
      // Assert the result
      assertEquals("1__Only 1 attachment allowed", result);
    }
  }

  @Test
  public void testGetAttachmentCountAndMessage_CountAnnotationsPresent() {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic
          .when(CacheConfig::getMaxAllowedAttachmentsCache)
          .thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(null);
      CdsEntity mainEntity =
          new CdsEntity() {
            @Override
            public Stream<CdsAnnotation<?>> annotations() {
              return null;
            }

            @Override
            public <T> Optional<CdsAnnotation<T>> findAnnotation(String s) {
              return Optional.empty();
            }

            @Override
            public boolean isAbstract() {
              return false;
            }

            @Override
            public boolean isView() {
              return false;
            }

            @Override
            public boolean isProjection() {
              return false;
            }

            @Override
            public Optional<CqnSelect> query() {
              return Optional.empty();
            }

            @Override
            public Stream<CdsParameter> params() {
              return null;
            }

            @Override
            public Stream<CdsAction> actions() {
              return null;
            }

            @Override
            public CdsAction getAction(String s) {
              return null;
            }

            @Override
            public Optional<CdsAction> findAction(String s) {
              return Optional.empty();
            }

            @Override
            public Stream<CdsFunction> functions() {
              return null;
            }

            @Override
            public CdsFunction getFunction(String s) {
              return null;
            }

            @Override
            public Optional<CdsFunction> findFunction(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getElement(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findElement(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getAssociation(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findAssociation(String s) {
              return Optional.empty();
            }

            @Override
            public <S extends CdsStructuredType> S getTargetOf(String s) {
              return null;
            }

            @Override
            public Stream<CdsElement> elements() {
              return null;
            }

            @Override
            public String getQualifiedName() {
              return "com.sap.demo.EntityOne";
            }

            @Override
            public String getName() {
              return null;
            }

            @Override
            public String getQualifier() {
              return null;
            }

            public Stream<CdsElement> compositions() {
              CdsElement element1 = mock(CdsElement.class);
              CdsElement element2 = mock(CdsElement.class);
              when(element1.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
              when(element2.getQualifiedName()).thenReturn("demo.abcd:nnn");
              when(element1.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT))
                  .thenReturn(Optional.of(maxcountAnnotation));

              when(maxcountAnnotation.getValue()).thenReturn("1");

              List<CdsElement> compositions = List.of(element1, element2);
              return compositions.stream();
            }
          };
      when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
      entities = List.of(mainEntity);
      // when(cds)
      // Invoke the method
      String result = getAttachmentCountAndMessage(entities, attachmentEntity);
      // Assert the result
      assertEquals("1__null", result);
    }
  }

  @Test
  public void testGetAttachmentCountAndMessage_NoAnnotationsPresent() {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache mockCache = mock(Cache.class);
      cacheConfigMockedStatic
          .when(CacheConfig::getMaxAllowedAttachmentsCache)
          .thenReturn(mockCache);
      when(mockCache.get(any())).thenReturn(null);
      CdsEntity mainEntity =
          new CdsEntity() {
            @Override
            public Stream<CdsAnnotation<?>> annotations() {
              return null;
            }

            @Override
            public <T> Optional<CdsAnnotation<T>> findAnnotation(String s) {
              return Optional.empty();
            }

            @Override
            public boolean isAbstract() {
              return false;
            }

            @Override
            public boolean isView() {
              return false;
            }

            @Override
            public boolean isProjection() {
              return false;
            }

            @Override
            public Optional<CqnSelect> query() {
              return Optional.empty();
            }

            @Override
            public Stream<CdsParameter> params() {
              return null;
            }

            @Override
            public Stream<CdsAction> actions() {
              return null;
            }

            @Override
            public CdsAction getAction(String s) {
              return null;
            }

            @Override
            public Optional<CdsAction> findAction(String s) {
              return Optional.empty();
            }

            @Override
            public Stream<CdsFunction> functions() {
              return null;
            }

            @Override
            public CdsFunction getFunction(String s) {
              return null;
            }

            @Override
            public Optional<CdsFunction> findFunction(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getElement(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findElement(String s) {
              return Optional.empty();
            }

            @Override
            public CdsElement getAssociation(String s) {
              return null;
            }

            @Override
            public Optional<CdsElement> findAssociation(String s) {
              return Optional.empty();
            }

            @Override
            public <S extends CdsStructuredType> S getTargetOf(String s) {
              return null;
            }

            @Override
            public Stream<CdsElement> elements() {
              return null;
            }

            @Override
            public String getQualifiedName() {
              return "com.sap.demo.EntityOne";
            }

            @Override
            public String getName() {
              return null;
            }

            @Override
            public String getQualifier() {
              return null;
            }

            public Stream<CdsElement> compositions() {
              CdsElement element1 = mock(CdsElement.class);
              CdsElement element2 = mock(CdsElement.class);
              when(element1.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
              when(element2.getQualifiedName()).thenReturn("demo.abcd:nnn");
              List<CdsElement> compositions = List.of(element1, element2);
              return compositions.stream();
            }
          };
      when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.demo.EntityOne.Attachments");
      entities = List.of(mainEntity);
      String result = getAttachmentCountAndMessage(entities, attachmentEntity);
      // Assert the result
      assertEquals("0__null", result);
    }
  }

  @Test
  void testGetPropertyTitles_WithValidEntity_ReturnsCorrectTitles() throws Exception {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element1 = mock(CdsElement.class);
    CdsElement element2 = mock(CdsElement.class);
    CdsAnnotation<Object> titleAnnotation = mock(CdsAnnotation.class);
    CdsAnnotation<Object> propertyNameAnnotation = mock(CdsAnnotation.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("customProp1", "value1");
    attachment.put("customProp2", "value2");

    when(entity.getElement("customProp1")).thenReturn(element1);
    when(entity.getElement("customProp2")).thenReturn(element2);

    when(element1.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.of(propertyNameAnnotation));
    when(propertyNameAnnotation.getValue()).thenReturn("prop1");
    when(element1.findAnnotation("title")).thenReturn(Optional.of(titleAnnotation));
    when(titleAnnotation.getValue()).thenReturn("Property 1");

    when(element2.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.empty());
    when(element2.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(mock(CdsAnnotation.class)));
    when(element2.getName()).thenReturn("customProp2");
    when(element2.findAnnotation("title")).thenReturn(Optional.empty());

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.of(entity), attachment);

    assertEquals("Property 1", result.get("prop1"));
    assertEquals("customProp2", result.get("customProp2"));
  }

  @Test
  void testGetPropertyTitles_WithEmptyEntity_ReturnsEmptyMap() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("customProp1", "value1");

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.empty(), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetPropertyTitles_SkipsDraftReadonlyContext() {
    CdsEntity entity = mock(CdsEntity.class);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put(SDMConstants.DRAFT_READONLY_CONTEXT, "value");

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.of(entity), attachment);

    verify(entity, never()).getElement(SDMConstants.DRAFT_READONLY_CONTEXT);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetPropertyTitles_SkipsNullElements() {
    CdsEntity entity = mock(CdsEntity.class);
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("customProp1", "value1");

    when(entity.getElement("customProp1")).thenReturn(null);

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.of(entity), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetPropertyTitles_WithNoAnnotations_ReturnsEmpty() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("customProp1", "value1");

    when(entity.getElement("customProp1")).thenReturn(element);
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.empty());
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.empty());

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.of(entity), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetPropertyTitles_WithNewAnnotationAndNoTitle_UsesElementName() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    CdsAnnotation<Object> propertyNameAnnotation = mock(CdsAnnotation.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("customProp1", "value1");

    when(entity.getElement("customProp1")).thenReturn(element);
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.of(propertyNameAnnotation));
    when(propertyNameAnnotation.getValue()).thenReturn("prop1");
    when(element.findAnnotation("title")).thenReturn(Optional.empty());
    when(element.getName()).thenReturn("customProp1");

    Map<String, String> result = SDMUtils.getPropertyTitles(Optional.of(entity), attachment);

    assertEquals("customProp1", result.get("prop1"));
  }

  @Test
  void
      testGetSecondaryPropertiesWithInvalidDefinition_WithOldAnnotation_ReturnsInvalidProperties() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element1 = mock(CdsElement.class);
    CdsElement element2 = mock(CdsElement.class);
    CdsAnnotation<Object> sdmAnnotation1 = mock(CdsAnnotation.class);
    CdsAnnotation<Object> sdmAnnotation2 = mock(CdsAnnotation.class);
    CdsAnnotation<Object> titleAnnotation = mock(CdsAnnotation.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");
    attachment.put("prop2", "value2");

    when(entity.getElement("prop1")).thenReturn(element1);
    when(entity.getElement("prop2")).thenReturn(element2);

    when(element1.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(sdmAnnotation1));
    when(element1.findAnnotation("title")).thenReturn(Optional.of(titleAnnotation));
    when(titleAnnotation.getValue()).thenReturn("Property 1 Title");

    when(element2.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(sdmAnnotation2));
    when(element2.findAnnotation("title")).thenReturn(Optional.empty());
    when(element2.getName()).thenReturn("prop2");

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.of(entity), attachment);

    assertEquals(2, result.size());
    assertEquals("Property 1 Title", result.get("prop1"));
    assertEquals("prop2", result.get("prop2"));
  }

  @Test
  void testGetSecondaryPropertiesWithInvalidDefinition_WithEmptyEntity_ReturnsEmpty() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.empty(), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetSecondaryPropertiesWithInvalidDefinition_WithNewAnnotation_NotIncluded() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");

    when(entity.getElement("prop1")).thenReturn(element);
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.empty());

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.of(entity), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetSecondaryPropertiesWithInvalidDefinition_SkipsDraftReadonlyContext() {
    CdsEntity entity = mock(CdsEntity.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put(SDMConstants.DRAFT_READONLY_CONTEXT, "value");

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.of(entity), attachment);

    verify(entity, never()).getElement(SDMConstants.DRAFT_READONLY_CONTEXT);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetSecondaryPropertiesWithInvalidDefinition_WithNullElement_SkipsIt() {
    CdsEntity entity = mock(CdsEntity.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");

    when(entity.getElement("prop1")).thenReturn(null);

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.of(entity), attachment);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetSecondaryPropertiesWithInvalidDefinition_MultiplePropertiesMixed() {
    CdsEntity entity = mock(CdsEntity.class);
    CdsElement validElement = mock(CdsElement.class);
    CdsElement invalidElement = mock(CdsElement.class);
    CdsAnnotation<Object> sdmAnnotation = mock(CdsAnnotation.class);
    CdsAnnotation<Object> titleAnnotation = mock(CdsAnnotation.class);

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("validProp", "value1");
    attachment.put("invalidProp", "value2");

    when(entity.getElement("validProp")).thenReturn(validElement);
    when(entity.getElement("invalidProp")).thenReturn(invalidElement);

    // validProp uses new annotation (not invalid)
    when(validElement.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.empty());

    // invalidProp uses old annotation (is invalid)
    when(invalidElement.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(sdmAnnotation));
    when(invalidElement.findAnnotation("title")).thenReturn(Optional.of(titleAnnotation));
    when(titleAnnotation.getValue()).thenReturn("Invalid Property Title");

    Map<String, String> result =
        SDMUtils.getSecondaryPropertiesWithInvalidDefinition(Optional.of(entity), attachment);

    assertEquals(1, result.size());
    assertEquals("Invalid Property Title", result.get("invalidProp"));
  }

  @Test
  void testExtractPropertyName_WithNewAnnotation_ReturnsAnnotationValue() throws Exception {
    CdsElement element = mock(CdsElement.class);
    CdsAnnotation<Object> propertyNameAnnotation = mock(CdsAnnotation.class);

    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.of(propertyNameAnnotation));
    when(propertyNameAnnotation.getValue()).thenReturn("customPropertyName");

    java.lang.reflect.Method method =
        SDMUtils.class.getDeclaredMethod("extractPropertyName", CdsElement.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, element);

    assertEquals("customPropertyName", result);
  }

  @Test
  void testExtractPropertyName_WithOldAnnotation_ReturnsElementName() throws Exception {
    CdsElement element = mock(CdsElement.class);
    CdsAnnotation<Object> oldAnnotation = mock(CdsAnnotation.class);

    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.empty());
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.of(oldAnnotation));
    when(element.getName()).thenReturn("elementName");

    java.lang.reflect.Method method =
        SDMUtils.class.getDeclaredMethod("extractPropertyName", CdsElement.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, element);

    assertEquals("elementName", result);
  }

  @Test
  void testExtractPropertyName_WithNoAnnotations_ReturnsNull() throws Exception {
    CdsElement element = mock(CdsElement.class);

    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY_NAME))
        .thenReturn(Optional.empty());
    when(element.findAnnotation(SDMConstants.SDM_ANNOTATION_ADDITIONALPROPERTY))
        .thenReturn(Optional.empty());

    java.lang.reflect.Method method =
        SDMUtils.class.getDeclaredMethod("extractPropertyName", CdsElement.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, element);

    assertEquals(null, result);
  }

  @Test
  void testExtractTitle_WithTitleAnnotation_ReturnsAnnotationValue() throws Exception {
    CdsElement element = mock(CdsElement.class);
    CdsAnnotation<Object> titleAnnotation = mock(CdsAnnotation.class);

    when(element.findAnnotation("title")).thenReturn(Optional.of(titleAnnotation));
    when(titleAnnotation.getValue()).thenReturn("Custom Title");

    java.lang.reflect.Method method =
        SDMUtils.class.getDeclaredMethod("extractTitle", CdsElement.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, element);

    assertEquals("Custom Title", result);
  }

  @Test
  void testExtractTitle_WithoutTitleAnnotation_ReturnsElementName() throws Exception {
    CdsElement element = mock(CdsElement.class);

    when(element.findAnnotation("title")).thenReturn(Optional.empty());
    when(element.getName()).thenReturn("elementName");

    java.lang.reflect.Method method =
        SDMUtils.class.getDeclaredMethod("extractTitle", CdsElement.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, element);

    assertEquals("elementName", result);
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithModifiedValues_ReturnsUpdated() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "newValue1");
    attachment.put("prop2", "newValue2");
    attachment.put("prop3", "unchangedValue");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");
    secondaryTypeProperties.put("prop3", "Property 3");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("Property 1", "oldValue1");
    propertiesInDB.put("Property 2", "oldValue2");
    propertiesInDB.put("Property 3", "unchangedValue");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(2, result.size());
    assertEquals("newValue1", result.get("Property 1"));
    assertEquals("newValue2", result.get("Property 2"));
    assertFalse(result.containsKey("Property 3"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithNullValueInAttachment_ReturnsNullValue() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", null);

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("Property 1", "oldValue");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(1, result.size());
    assertNull(result.get("Property 1"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithValueInDBNull_ReturnsUpdated() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "newValue");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("Property 1", null);

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(1, result.size());
    assertEquals("newValue", result.get("Property 1"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithNoChanges_ReturnsEmpty() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "sameValue1");
    attachment.put("prop2", "sameValue2");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("Property 1", "sameValue1");
    propertiesInDB.put("Property 2", "sameValue2");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithEmptySecondaryTypeProperties_ReturnsEmpty() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    Map<String, String> propertiesInDB = new HashMap<>();

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithPropertyNotInDB_AddsToUpdated() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "newValue");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");

    Map<String, String> propertiesInDB = new HashMap<>();
    // prop1 not in DB

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(1, result.size());
    assertEquals("newValue", result.get("Property 1"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithMultipleChanges_ReturnsAllUpdated() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "updated1");
    attachment.put("prop2", null);
    attachment.put("prop3", "updated3");
    attachment.put("prop4", "same4");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");
    secondaryTypeProperties.put("prop3", "Property 3");
    secondaryTypeProperties.put("prop4", "Property 4");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("Property 1", "old1");
    propertiesInDB.put("Property 2", "old2");
    propertiesInDB.put("Property 3", null);
    propertiesInDB.put("Property 4", "same4");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(3, result.size());
    assertEquals("updated1", result.get("Property 1"));
    assertNull(result.get("Property 2"));
    assertEquals("updated3", result.get("Property 3"));
    assertFalse(result.containsKey("Property 4"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithNumericValues_ConvertsToString() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", 123);
    attachment.put("prop2", 456L);
    attachment.put("prop3", 78.9);

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");
    secondaryTypeProperties.put("prop3", "Property 3");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("prop1", "100");
    propertiesInDB.put("prop2", "400");
    propertiesInDB.put("prop3", "70.0");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(3, result.size());
    assertEquals("123", result.get("Property 1"));
    assertEquals("456", result.get("Property 2"));
    assertEquals("78.9", result.get("Property 3"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithBooleanValues_ConvertsToString() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", true);
    attachment.put("prop2", false);

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");

    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("prop1", "false");
    propertiesInDB.put("prop2", "true");

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(2, result.size());
    assertEquals("true", result.get("Property 1"));
    assertEquals("false", result.get("Property 2"));
  }

  @Test
  void testGetUpdatedSecondaryProperties_WithEmptyPropertiesInDB_AddsAll() {
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("prop1", "value1");
    attachment.put("prop2", "value2");

    Map<String, String> secondaryTypeProperties = new HashMap<>();
    secondaryTypeProperties.put("prop1", "Property 1");
    secondaryTypeProperties.put("prop2", "Property 2");

    Map<String, String> propertiesInDB = new HashMap<>();

    Map<String, String> result =
        SDMUtils.getUpdatedSecondaryProperties(
            Optional.empty(),
            attachment,
            mockPersistenceService,
            secondaryTypeProperties,
            propertiesInDB);

    assertEquals(2, result.size());
    assertEquals("value1", result.get("Property 1"));
    assertEquals("value2", result.get("Property 2"));
  }
}
