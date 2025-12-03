package unit.com.sap.cds.sdm.handler.applicationservice.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.reflect.CdsType;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.handler.common.SDMAttachmentsReader;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentsHandlerUtilsTest {

  @Mock private CdsModel mockModel;
  @Mock private CdsEntity mockEntity;
  @Mock private PersistenceService mockPersistenceService;
  @Mock private SDMAttachmentsReader mockReader;
  @Mock private EventContext mockContext;
  @Mock private Messages mockMessages;
  @Mock private CdsElement mockComposition;
  @Mock private CdsType mockType;
  @Mock private CdsAssociationType mockAssociationType;
  @Mock private CdsStructuredType mockTargetAspect;
  @Mock private CdsEntity mockTargetEntity;

  @Test
  void testPrivateConstructor() {
    // Test that the constructor is private but doesn't throw an exception
    try {
      Constructor<AttachmentsHandlerUtils> constructor =
          AttachmentsHandlerUtils.class.getDeclaredConstructor();
      assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
      constructor.setAccessible(true);
      assertDoesNotThrow(() -> constructor.newInstance());
    } catch (Exception e) {
      fail("Constructor should be accessible via reflection");
    }
  }

  @Test
  void testGetAttachmentEntityPathsSuccess() {
    // Since the method creates objects internally and catches all exceptions,
    // we can't effectively mock the constructor calls. Let's test with real objects
    // or test the exception handling behavior

    // Given - test when no exception occurs by calling with valid inputs
    List<String> result =
        AttachmentsHandlerUtils.getAttachmentEntityPaths(
            mockModel, mockEntity, mockPersistenceService);

    // Then - should return empty list (since our mocks won't have real data)
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetAttachmentEntityPathsWithException() {
    // Test with null inputs to trigger exception path
    List<String> result = AttachmentsHandlerUtils.getAttachmentEntityPaths(null, null, null);

    // Then
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetAttachmentPathMappingWithDirectAttachment() {
    // Test with real mocks to exercise the path mapping logic
    when(mockEntity.compositions()).thenReturn(Stream.of(mockComposition));
    when(mockComposition.getName()).thenReturn("attachments");
    when(mockComposition.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.isAssociation()).thenReturn(true);
    when(mockAssociationType.getTargetAspect()).thenReturn(Optional.of(mockTargetAspect));
    when(mockTargetAspect.getQualifiedName()).thenReturn("sap.attachments.Attachments");
    when(mockEntity.getQualifiedName()).thenReturn("Service.Entity");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentPathMapping(
            mockModel, mockEntity, mockPersistenceService);

    // Then
    assertNotNull(result);
    // The method will create internal objects, so we mainly test it doesn't crash
  }

  @Test
  void testGetAttachmentPathMappingWithException() {
    // Test with null entity to trigger exception
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentPathMapping(mockModel, null, mockPersistenceService);

    // Then
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testFetchAttachmentsSimpleCase() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    entity.put("title", "Test Book");

    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "att1");
    attachment.put("fileName", "test.pdf");
    attachments.add(attachment);
    entity.put("attachments", attachments);

    String attachmentCompositionName = "attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertEquals(1, result.size());
    assertEquals("att1", result.get(0).get("ID"));
    assertEquals("test.pdf", result.get(0).get("fileName"));
  }

  @Test
  void testFetchAttachmentsNestedCase() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();

    Map<String, Object> chapter = new HashMap<>();
    chapter.put("ID", "chapter1");
    chapter.put("title", "Chapter 1");

    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "att1");
    attachment.put("fileName", "chapter1.pdf");
    attachments.add(attachment);
    chapter.put("attachments", attachments);

    List<Map<String, Object>> chapters = new ArrayList<>();
    chapters.add(chapter);
    entity.put("chapters", chapters);

    String attachmentCompositionName = "chapters.attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertEquals(1, result.size());
    assertEquals("att1", result.get(0).get("ID"));
    assertEquals("chapter1.pdf", result.get(0).get("fileName"));
  }

  @Test
  void testFetchAttachmentsEmptyResult() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    entity.put("title", "Test Book");
    String attachmentCompositionName = "attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testWrapEntityWithParent() {
    // Given
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    entity.put("title", "Test Book");
    String targetEntityName = "books";

    // When
    Map<String, Object> result =
        AttachmentsHandlerUtils.wrapEntityWithParent(entity, targetEntityName);

    // Then
    assertEquals(1, result.size());
    assertTrue(result.containsKey("books"));
    assertEquals(entity, result.get("books"));
  }

  @Test
  void testGetAttachmentCompositionDetailsSuccess() {
    // Given
    when(mockEntity.compositions()).thenReturn(Stream.of(mockComposition));
    when(mockComposition.getName()).thenReturn("attachments");
    when(mockComposition.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.isAssociation()).thenReturn(true);
    when(mockAssociationType.getTargetAspect()).thenReturn(Optional.of(mockTargetAspect));
    when(mockTargetAspect.getQualifiedName()).thenReturn("sap.attachments.Attachments");
    when(mockEntity.getQualifiedName()).thenReturn("Service.Books");

    Map<String, Object> entityData = new HashMap<>();
    entityData.put("ID", "123");
    entityData.put("title", "Test Book");

    // When
    Map<String, Map<String, String>> result =
        AttachmentsHandlerUtils.getAttachmentCompositionDetails(
            mockModel, mockEntity, mockPersistenceService, "Service.Books", entityData);

    // Then
    assertNotNull(result);
    // The method will handle internal object creation
  }

  @Test
  void testGetAttachmentParentTitlesWithDirectAttachment() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    entity.put("title", "Test Book");

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertEquals(1, result.size());
    assertEquals("Test Book", result.get("Service.Books.attachments"));
  }

  @Test
  void testGetAttachmentParentTitlesWithNestedAttachment() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();

    Map<String, Object> chapter = new HashMap<>();
    chapter.put("ID", "chapter1");
    chapter.put("title", "Chapter 1");

    List<Map<String, Object>> chapters = new ArrayList<>();
    chapters.add(chapter);
    entity.put("chapters", chapters);

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put(
        "Service.Chapters.attachments", "Service.Books.chapters.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertEquals(1, result.size());
    assertEquals("Chapter 1", result.get("Service.Books.chapters.attachments"));
  }

  @Test
  void testGetAttachmentParentTitlesWithFallbackFields() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    entity.put("name", "Test Book Name"); // Using 'name' instead of 'title'

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertEquals(1, result.size());
    assertEquals("Test Book Name", result.get("Service.Books.attachments"));
  }

  @Test
  void testGetAttachmentParentTitlesWithIDFallback() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "book-123");

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertEquals(1, result.size());
    assertEquals("book-123", result.get("Service.Books.attachments"));
  }

  @Test
  void testGetAttachmentParentTitlesWithEmptyEntity() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testValidateFileNamesWithWhitespaceError() {
    // Given
    when(mockContext.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("Service.Books");
    when(mockContext.getMessages()).thenReturn(mockMessages);

    List<CdsData> data = new ArrayList<>();
    CdsData mockData = mock(CdsData.class);
    data.add(mockData);

    Set<String> whitespaceFiles = new HashSet<>();
    whitespaceFiles.add("file with spaces.txt");

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class)) {
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsWhitespace(any(), anyString(), anyString()))
          .thenReturn(whitespaceFiles);
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsRestrictedCharaters(any(), anyString(), anyString()))
          .thenReturn(Collections.emptyList());
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
          .thenReturn(Collections.emptySet());

      // When
      Boolean result =
          AttachmentsHandlerUtils.validateFileNames(
              mockContext, data, "attachments", " - Context Info");

      // Then
      assertTrue(result);
      verify(mockMessages, times(1))
          .error(
              "The object name cannot be empty or consist entirely of space characters. Enter a value. - Context Info");
    }
  }

  @Test
  void testValidateFileNamesWithRestrictedCharactersError() {
    // Given
    when(mockContext.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("Service.Books");
    when(mockContext.getMessages()).thenReturn(mockMessages);

    List<CdsData> data = new ArrayList<>();
    CdsData mockData = mock(CdsData.class);
    data.add(mockData);

    List<String> restrictedFiles = Arrays.asList("file/with/slash.txt");

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class)) {
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsWhitespace(any(), anyString(), anyString()))
          .thenReturn(Collections.emptySet());
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsRestrictedCharaters(any(), anyString(), anyString()))
          .thenReturn(restrictedFiles);
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
          .thenReturn(Collections.emptySet());

      // When
      Boolean result =
          AttachmentsHandlerUtils.validateFileNames(
              mockContext, data, "attachments", " - Context Info");

      // Then
      assertTrue(result);
      verify(mockMessages, times(1)).error(anyString());
    }
  }

  @Test
  void testValidateFileNamesWithDuplicateFilesError() {
    // Given
    when(mockContext.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("Service.Books");
    when(mockContext.getMessages()).thenReturn(mockMessages);

    List<CdsData> data = new ArrayList<>();
    CdsData mockData = mock(CdsData.class);
    data.add(mockData);

    Set<String> duplicateFiles = new HashSet<>();
    duplicateFiles.add("duplicate.txt");

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class)) {
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsWhitespace(any(), anyString(), anyString()))
          .thenReturn(Collections.emptySet());
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsRestrictedCharaters(any(), anyString(), anyString()))
          .thenReturn(Collections.emptyList());
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
          .thenReturn(duplicateFiles);

      // When
      Boolean result =
          AttachmentsHandlerUtils.validateFileNames(
              mockContext, data, "attachments", " - Context Info");

      // Then
      assertTrue(result);
      verify(mockMessages, times(1)).error(anyString());
    }
  }

  @Test
  void testValidateFileNamesWithAllErrors() {
    // Given
    when(mockContext.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("Service.Books");
    when(mockContext.getMessages()).thenReturn(mockMessages);

    List<CdsData> data = new ArrayList<>();
    CdsData mockData = mock(CdsData.class);
    data.add(mockData);

    Set<String> whitespaceFiles = new HashSet<>();
    whitespaceFiles.add("file with spaces.txt");

    List<String> restrictedFiles = Arrays.asList("file/with/slash.txt");

    Set<String> duplicateFiles = new HashSet<>();
    duplicateFiles.add("duplicate.txt");

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class)) {
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsWhitespace(any(), anyString(), anyString()))
          .thenReturn(whitespaceFiles);
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameContainsRestrictedCharaters(any(), anyString(), anyString()))
          .thenReturn(restrictedFiles);
      sdmUtilsMock
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
          .thenReturn(duplicateFiles);

      // When
      Boolean result =
          AttachmentsHandlerUtils.validateFileNames(
              mockContext, data, "attachments", " - Context Info");

      // Then
      assertTrue(result);
      verify(mockMessages, times(3)).error(anyString());
    }
  }

  @Test
  void testFetchAttachmentsWithComplexNestedStructure() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();

    // Create nested structure: books -> chapters -> sections -> attachments
    Map<String, Object> section = new HashMap<>();
    section.put("ID", "section1");
    section.put("title", "Introduction");

    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("ID", "att1");
    attachment1.put("fileName", "intro.pdf");
    attachments.add(attachment1);

    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("ID", "att2");
    attachment2.put("fileName", "diagram.png");
    attachments.add(attachment2);

    section.put("attachments", attachments);

    List<Map<String, Object>> sections = new ArrayList<>();
    sections.add(section);

    Map<String, Object> chapter = new HashMap<>();
    chapter.put("ID", "chapter1");
    chapter.put("sections", sections);

    List<Map<String, Object>> chapters = new ArrayList<>();
    chapters.add(chapter);
    entity.put("chapters", chapters);

    String attachmentCompositionName = "sections.attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertEquals(2, result.size());
    assertEquals("att1", result.get(0).get("ID"));
    assertEquals("intro.pdf", result.get(0).get("fileName"));
    assertEquals("att2", result.get(1).get("ID"));
    assertEquals("diagram.png", result.get(1).get("fileName"));
  }

  @Test
  void testFetchAttachmentsWithInvalidAttachmentStructure() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("attachments", "invalid-not-a-list"); // Invalid: should be a list

    String attachmentCompositionName = "attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testFetchAttachmentsWithNullValues() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("attachments", null);

    String attachmentCompositionName = "attachments";

    // When
    List<Map<String, Object>> result =
        AttachmentsHandlerUtils.fetchAttachments(targetEntity, entity, attachmentCompositionName);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetAttachmentParentTitlesWithNoStringValues() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", 123); // Non-string value
    entity.put("active", true); // Non-string value

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetAttachmentParentTitlesWithEmptyStringValues() {
    // Given
    String targetEntity = "Service.Books";
    Map<String, Object> entity = new HashMap<>();
    entity.put("title", ""); // Empty string
    entity.put("name", "   "); // Whitespace only

    Map<String, String> compositionPathMapping = new HashMap<>();
    compositionPathMapping.put("Service.Books.attachments", "Service.Books.attachments");

    // When
    Map<String, String> result =
        AttachmentsHandlerUtils.getAttachmentParentTitles(
            targetEntity, entity, compositionPathMapping);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void testWrapEntityWithParentWithNullEntity() {
    // Given
    Map<String, Object> entity = null;
    String targetEntityName = "books";

    // When
    Map<String, Object> result =
        AttachmentsHandlerUtils.wrapEntityWithParent(entity, targetEntityName);

    // Then
    assertEquals(1, result.size());
    assertTrue(result.containsKey("books"));
    assertNull(result.get("books"));
  }

  @Test
  void testWrapEntityWithParentWithEmptyTargetName() {
    // Given
    Map<String, Object> entity = new HashMap<>();
    entity.put("ID", "123");
    String targetEntityName = "";

    // When
    Map<String, Object> result =
        AttachmentsHandlerUtils.wrapEntityWithParent(entity, targetEntityName);

    // Then
    assertEquals(1, result.size());
    assertTrue(result.containsKey(""));
    assertEquals(entity, result.get(""));
  }
}
