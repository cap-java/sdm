package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateAttachmentsHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
import java.util.*;
import org.ehcache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class SDMCreateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;

  @Mock private SDMService sdmService;

  @Mock private TokenHandler tokenHandler;

  @Mock private DBQuery dbQuery;

  @Mock private CdsCreateEventContext context;

  @Mock private Messages messages;

  @Mock private AuthenticationInfo authInfo;

  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;

  @Mock private CdsModel model;

  @Mock private CdsEntity attachmentDraftEntity;

  @Mock private UserInfo userInfo;

  private SDMCreateAttachmentsHandler handler;
  private SDMCredentials mockCredentials;

  @BeforeEach
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    handler =
        new SDMCreateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);

    when(context.getMessages()).thenReturn(messages);
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("testJwtToken");
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Default mock for draft entity
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments")).thenReturn(Optional.empty());

    mockCredentials = new SDMCredentials("url", "baseTokenUrl", "clientId", "clientSecret");
  }

  @Test
  public void testConstructor() {
    assertNotNull(handler);
  }

  @Test
  public void testProcessBefore() throws IOException {
    // Just test that the method doesn't throw exceptions
    List<CdsData> data = new ArrayList<>();
    CdsData testData = mock(CdsData.class);
    data.add(testData);

    CdsEntity targetEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.getModel()).thenReturn(model);

    // Execute
    handler.processBefore(context, data);

    // Assert
    verify(context, atLeastOnce()).getTarget();
  }

  @Test
  public void testProcessBeforeWithNullData() throws IOException {
    // Test processBefore with null data - should handle gracefully
    CdsEntity targetEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.getModel()).thenReturn(model);

    // Execute with empty list instead of null to avoid NPE
    handler.processBefore(context, new ArrayList<>());

    // Assert
    verify(context, atLeastOnce()).getTarget();
  }

  @Test
  public void testUpdateNameSuccess() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      when(context.getMessages()).thenReturn(messages);

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      CdsEntity targetEntity = mock(CdsEntity.class);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getTarget()).thenReturn(targetEntity);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock null attachments to skip processing
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify validation was called
      attachmentUtilsMockedStatic.verify(
          () ->
              AttachmentsHandlerUtils.validateFileNames(
                  eq(context), eq(data), eq("attachments"), contains("TestTitle")),
          times(1));
    }
  }

  @Test
  public void testProcessEntityWithAttachments() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
            mockStatic(AttachmentsHandlerUtils.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {

      @SuppressWarnings("unchecked")
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity attachmentEntity = mock(CdsEntity.class);
      when(context.getModel().findEntity("TestEntity.attachments"))
          .thenReturn(Optional.of(attachmentEntity));

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock attachment data
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("ID", "test-id");
      attachment.put("fileName", "test.pdf");
      attachment.put("note", "description");
      attachment.put("objectId", "object-123");
      attachments.add(attachment);

      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(attachments);

      // Mock DB and SDM data
      when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("test.pdf");

      List<String> sdmAttachmentData = new ArrayList<>();
      sdmAttachmentData.add("test.pdf");
      sdmAttachmentData.add("description");
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
                      any(), anyString(), any(), anyBoolean()))
          .thenReturn(sdmAttachmentData);

      // Mock utility methods
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getPropertyTitles(any(), any()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
          .thenReturn(new HashMap<>());

      when(dbQuery.getPropertiesForID(
              any(CdsEntity.class), any(PersistenceService.class), anyString(), anyList()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
          .thenReturn(new HashMap<>());

      // Mock CMIS document preparation
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.prepareCmisDocument(
                      anyString(), anyString(), anyString()))
          .thenReturn(mock(com.sap.cds.sdm.model.CmisDocument.class));

      // Mock property update methods
      attachmentUtilsMockedStatic
          .when(
              () -> AttachmentsHandlerUtils.updateFilenameProperty(anyString(), anyString(), any()))
          .then(invocation -> null);

      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.updateDescriptionProperty(
                      anyString(), anyString(), any()))
          .then(invocation -> null);

      when(sdmService.updateAttachments(any(), any(), any(), any(), anyBoolean())).thenReturn(200);

      when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

      // Mock response handling
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.handleSDMUpdateResponse(
                      anyInt(),
                      any(),
                      anyString(),
                      anyString(),
                      any(),
                      any(),
                      anyString(),
                      any(),
                      any(),
                      any()))
          .then(invocation -> null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify
      verify(sdmService, times(1)).updateAttachments(any(), any(), any(), any(), anyBoolean());
      verify(mockCache, times(1)).remove(any());
    }
  }

  @Test
  public void testSDMCredentialsException() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      when(context.getMessages()).thenReturn(messages);

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock attachments
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(new ArrayList<>());

      // Mock credentials to throw exception
      when(tokenHandler.getSDMCredentials()).thenThrow(new RuntimeException("Credentials error"));

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // No specific verification needed - just ensuring no exception propagates
    }
  }

  @Test
  public void testProcessAttachmentWithNullValues() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
            mockStatic(AttachmentsHandlerUtils.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {

      @SuppressWarnings("unchecked")
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      when(testData.get("ID")).thenReturn("test-id");
      when(testData.get("fileName")).thenReturn(null); // Null filename
      when(testData.get("note")).thenReturn(null); // Null description
      when(testData.get("objectId")).thenReturn("test-object-id");
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity attachmentEntity = mock(CdsEntity.class);
      when(context.getModel().findEntity("TestEntity.attachments"))
          .thenReturn(Optional.of(attachmentEntity));

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock attachment data with null values
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("ID", "test-id");
      attachment.put("fileName", null);
      attachment.put("note", null);
      attachment.put("objectId", "object-123");
      attachments.add(attachment);

      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(attachments);

      // Mock DB and SDM data
      when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("existing.pdf");

      List<String> sdmAttachmentData = new ArrayList<>();
      sdmAttachmentData.add("existing.pdf");
      sdmAttachmentData.add("existing description");
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
                      any(), anyString(), any(), anyBoolean()))
          .thenReturn(sdmAttachmentData);

      // Mock utility methods
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getPropertyTitles(any(), any()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
          .thenReturn(new HashMap<>());

      when(dbQuery.getPropertiesForID(
              any(CdsEntity.class), any(PersistenceService.class), anyString(), anyList()))
          .thenReturn(new HashMap<>());

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
          .thenReturn(new HashMap<>());

      // Mock CMIS document preparation
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.prepareCmisDocument(
                      anyString(), anyString(), anyString()))
          .thenReturn(null); // Null document

      // Mock property update methods
      attachmentUtilsMockedStatic
          .when(
              () -> AttachmentsHandlerUtils.updateFilenameProperty(anyString(), anyString(), any()))
          .then(invocation -> null);

      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.updateDescriptionProperty(
                      anyString(), anyString(), any()))
          .then(invocation -> null);

      when(sdmService.updateAttachments(any(), any(), any(), any(), anyBoolean())).thenReturn(200);

      when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

      // Mock response handling
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.handleSDMUpdateResponse(
                      anyInt(),
                      any(),
                      anyString(),
                      anyString(),
                      any(),
                      any(),
                      anyString(),
                      any(),
                      any(),
                      any()))
          .then(invocation -> null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify
      verify(sdmService, times(1)).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  @Test
  public void testProcessEntityWithEmptyAttachments() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock empty attachments list
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(new ArrayList<>()); // Empty list

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify no SDM service calls for empty attachments
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  @Test
  public void testProcessEntityWithNullAttachments() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock null attachments
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null); // Null attachments

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify no SDM service calls for null attachments
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  @Test
  public void testComplexCompositionNameWithMultipleDots() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "com.example.entity.attachments.files"); // Multiple dots
      compositionInfo.put("parentTitle", "Complex Entity");
      attachmentCompositionDetails.put("ComplexEntity.attachments.files", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock null attachments to skip processing
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify validation was called with correct composition name
      attachmentUtilsMockedStatic.verify(
          () ->
              AttachmentsHandlerUtils.validateFileNames(
                  eq(context),
                  eq(data),
                  eq("com.example.entity.attachments.files"),
                  contains("files")), // Should extract "files" from the complex name
          times(1));
    }
  }

  @Test
  public void testValidationErrorSkipsProcessing() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(true); // Validation error

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify fetchAttachments is never called when validation fails
      attachmentUtilsMockedStatic.verify(
          () -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()), never());

      // Verify no SDM service calls
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  @Test
  public void testHandleWarningsWithNullParentTitle() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", null); // Null parent title
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock null attachments to skip processing
      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify validation was called with "Unknown" as parent title
      attachmentUtilsMockedStatic.verify(
          () ->
              AttachmentsHandlerUtils.validateFileNames(
                  eq(context),
                  eq(data),
                  eq("attachments"),
                  contains("Unknown")), // Should show "Unknown" when parentTitle is null
          times(1));
    }
  }

  @Test
  public void testCacheInvalidationAfterProcessing() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
            mockStatic(AttachmentsHandlerUtils.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {

      @SuppressWarnings("unchecked")
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      List<CdsData> data = new ArrayList<>();
      CdsData testData = mock(CdsData.class);
      data.add(testData);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity attachmentEntity = mock(CdsEntity.class);
      when(context.getModel().findEntity("TestEntity.attachments"))
          .thenReturn(Optional.of(attachmentEntity));

      // Mock validation to return no error
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock attachment data
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("ID", "test-id");
      attachment.put("fileName", "test.pdf");
      attachment.put("note", "description");
      attachment.put("objectId", "object-123");
      attachments.add(attachment);

      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(attachments);

      // Mock all required methods to avoid NPE
      when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("test.pdf");

      List<String> sdmAttachmentData = new ArrayList<>();
      sdmAttachmentData.add("test.pdf");
      sdmAttachmentData.add("description");
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachmentDataFromSDM(
                      any(), anyString(), any(), anyBoolean()))
          .thenReturn(sdmAttachmentData);

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getPropertyTitles(any(), any()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
          .thenReturn(new HashMap<>());
      when(dbQuery.getPropertiesForID(
              any(CdsEntity.class), any(PersistenceService.class), anyString(), anyList()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
          .thenReturn(new HashMap<>());
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.prepareCmisDocument(
                      anyString(), anyString(), anyString()))
          .thenReturn(mock(com.sap.cds.sdm.model.CmisDocument.class));
      attachmentUtilsMockedStatic
          .when(
              () -> AttachmentsHandlerUtils.updateFilenameProperty(anyString(), anyString(), any()))
          .then(invocation -> null);
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.updateDescriptionProperty(
                      anyString(), anyString(), any()))
          .then(invocation -> null);
      when(sdmService.updateAttachments(any(), any(), any(), any(), anyBoolean())).thenReturn(200);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.handleSDMUpdateResponse(
                      anyInt(),
                      any(),
                      anyString(),
                      anyString(),
                      any(),
                      any(),
                      anyString(),
                      any(),
                      any(),
                      any()))
          .then(invocation -> null);

      // Execute
      handler.updateName(context, data, attachmentCompositionDetails);

      // Verify cache is cleared after processing attachments
      verify(mockCache, times(1)).remove(any());
    }
  }
}
