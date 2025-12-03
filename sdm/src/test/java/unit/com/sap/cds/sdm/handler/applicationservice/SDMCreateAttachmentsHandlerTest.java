package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
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
import java.util.stream.Stream;
import org.ehcache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SDMCreateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  private SDMService sdmService;
  @Mock private CdsCreateEventContext context;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsModel model;
  private SDMCreateAttachmentsHandler handler;
  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;
  private MockedStatic<CacheConfig> cacheConfigMockedStatic;
  private MockedStatic<DBQuery> dbQueryMockedStatic;
  @Mock private CdsElement cdsElement;
  @Mock private CdsAssociationType cdsAssociationType;
  @Mock private CdsStructuredType targetAspect;
  @Mock private TokenHandler tokenHandler;
  @Mock private DBQuery dbQuery;
  @Mock private UserInfo userInfo;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
    dbQueryMockedStatic = mockStatic(DBQuery.class);
    cacheConfigMockedStatic = mockStatic(CacheConfig.class);
    sdmService = mock(SDMService.class);

    handler =
        spy(new SDMCreateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery));

    when(context.getMessages()).thenReturn(messages);
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("testJwtToken");

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
  }

  @AfterEach
  public void tearDown() {
    try {
      if (sdmUtilsMockedStatic != null) {
        sdmUtilsMockedStatic.close();
      }
    } catch (Exception e) {
      // Already closed
    }
    try {
      if (cacheConfigMockedStatic != null) {
        cacheConfigMockedStatic.close();
      }
    } catch (Exception e) {
      // Already closed
    }
    try {
      if (dbQueryMockedStatic != null) {
        dbQueryMockedStatic.close();
      }
    } catch (Exception e) {
      // Already closed
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testProcessBefore() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);
      // Arrange the mock compositions scenario
      Map<String, Map<String, String>> expectedCompositionMapping = new HashMap<>();
      Map<String, String> compositionInfo1 = new HashMap<>();
      compositionInfo1.put("name", "Name1");
      compositionInfo1.put("parentTitle", "TestTitle");
      expectedCompositionMapping.put("Name1", compositionInfo1);

      Map<String, String> compositionInfo2 = new HashMap<>();
      compositionInfo2.put("name", "Name2");
      compositionInfo2.put("parentTitle", "TestTitle");
      expectedCompositionMapping.put("Name2", compositionInfo2);

      // Mock AttachmentsHandlerUtils.getAttachmentCompositionDetails to return the expected mapping
      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentCompositionDetails(
                      any(), any(), any(), any(), any()))
          .thenReturn(expectedCompositionMapping);

      List<CdsData> dataList = new ArrayList<>();
      CdsData entityData = mock(CdsData.class);
      dataList.add(entityData);

      // Act
      handler.processBefore(context, dataList);

      // Assert that updateName was called with the compositions detected
      verify(handler).updateName(context, dataList, expectedCompositionMapping);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUpdateNameWithDuplicateFilenames() throws IOException {
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

    // Arrange
    List<CdsData> data = new ArrayList<>();
    Set<String> duplicateFilenames = new HashSet<>(Arrays.asList("file1.txt", "file2.txt"));
    when(context.getMessages()).thenReturn(messages);

    // Mock the target entity
    CdsEntity targetEntity = mock(CdsEntity.class);
    when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.getTarget()).thenReturn(targetEntity);
    // Make validateFileName execute its real implementation, and stub helper methods
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameContainsWhitespace(anyList(), anyString(), anyString()))
        .thenCallRealMethod();
    sdmUtilsMockedStatic
        .when(
            () -> SDMUtils.FileNameContainsRestrictedCharaters(anyList(), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(data, "compositionName", "TestEntity"))
        .thenReturn(duplicateFilenames);
    try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {
      attachmentUtilsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(
                      any(), anyList(), anyString(), anyString()))
          .thenCallRealMethod();

      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "compositionName");
      compositionInfo.put("definition", "compositionDefinition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Assert: validateFileName should have logged an error for duplicate filenames
      verify(messages, times(1))
          .error(
              org.mockito.ArgumentMatchers.contains(
                  "Objects with the following names already exist"));
    }
  }

  @Test
  public void testUpdateNameWithEmptyData() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(data, "compositionName", "entity"))
        .thenReturn(Collections.emptySet());

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "compositionName");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Assert
    verify(messages, never()).error(anyString());
    verify(messages, never()).warn(anyString());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUpdateNameWithNoAttachmentsOriginal() throws IOException {
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

    // Arrange
    List<CdsData> data = new ArrayList<>();

    // Create an entity map without any attachments
    Map<String, Object> entity = new HashMap<>();

    // Wrap the entity map in CdsData
    CdsData cdsDataEntity = CdsData.create(entity);

    // Add the CdsData entity to the data list
    data.add(cdsDataEntity);

    // Mock utility methods
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(data, "compositionName", "entity"))
        .thenReturn(Collections.emptySet());

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "compositionName");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Assert that no updateAttachments calls were made, as there are no attachments
    verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());

    // Assert that no error or warning messages were logged
    verify(messages, never()).error(anyString());
    verify(messages, never()).warn(anyString());
  }

  @Test
  public void testUpdateNameWithRestrictedCharacters() throws IOException {
    // Arrange
    List<CdsData> data = createTestData();

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
        .thenReturn(Collections.emptySet());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName("file/1.txt"))
        .thenReturn(true);

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName("file2.txt"))
        .thenReturn(false);

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(Collections.emptyMap());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getPropertyTitles(any(), any()))
        .thenReturn(Collections.emptyMap());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
        .thenReturn(Collections.emptyMap());

    when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("fileInDB.txt");

    when(sdmService.getObject(anyString(), any(SDMCredentials.class), anyBoolean()))
        .thenReturn("fileInSDM.txt");

    // Mock the model and entity
    when(context.getModel()).thenReturn(model);
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("attachments")).thenReturn(Optional.of(attachmentEntity));
    when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.cds.Attachments");

    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    @SuppressWarnings("unchecked")
    Cache<String, String> mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "attachments");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("attachments", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Assert
    verify(messages, times(2)).warn(anyString());
  }

  @Test
  public void testUpdateNameWithSDMConflict() throws IOException {
    // Arrange
    List<CdsData> data = createTestData();

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
        .thenReturn(Collections.emptySet());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
        .thenReturn(false);

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(Collections.emptyMap());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getPropertyTitles(any(), any()))
        .thenReturn(Collections.emptyMap());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
        .thenReturn(Collections.emptyMap());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
        .thenReturn(new HashMap<>());

    when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("differentFile.txt");

    when(sdmService.getObject(anyString(), any(SDMCredentials.class), anyBoolean()))
        .thenReturn("fileInSDM.txt");
    when(sdmService.updateAttachments(any(SDMCredentials.class), any(), any(), any(), anyBoolean()))
        .thenReturn(409);

    // Mock the model and entity
    when(context.getModel()).thenReturn(model);
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("attachments")).thenReturn(Optional.of(attachmentEntity));
    when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.cds.Attachments");

    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    @SuppressWarnings("unchecked")
    Cache<String, String> mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "attachments");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("attachments", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Assert
    verify(messages, times(1)).warn(anyString());
  }

  // @Test
  // public void testUpdateNameWithSDMMissingRoles() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Collections.emptyList());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
  //         .thenReturn(new HashMap<>());

  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), anyString()))
  //         .thenReturn("differentFile.txt");

  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("fileInSDM.txt");
  //     when(sdmService.updateAttachments(anyString(), any(), any(), any())).thenReturn(403);

  //     // Act & Assert
  //     ServiceException exception =
  //         assertThrows(ServiceException.class, () -> handler.updateName(context, data));
  //     assertEquals(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG, exception.getMessage());
  //   }

  //   @Test
  //   public void testUpdateNameWithSDMError() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Collections.emptyList());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
  //         .thenReturn(new HashMap<>());

  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), anyString()))
  //         .thenReturn("differentFile.txt");

  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("fileInSDM.txt");
  //     when(sdmService.updateAttachments(anyString(), any(), any(), any())).thenReturn(500);

  //     // Act & Assert
  //     ServiceException exception =
  //         assertThrows(ServiceException.class, () -> handler.updateName(context, data));
  //     assertEquals(SDMConstants.SDM_ROLES_ERROR_MESSAGE, exception.getMessage());
  //   }

  //   @Test
  //   public void testUpdateNameWithSuccessResponse() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Collections.emptyList());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
  //         .thenReturn(new HashMap<>());

  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), anyString()))
  //         .thenReturn("differentFile.txt");

  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("fileInSDM.txt");
  //     when(sdmService.updateAttachments(anyString(), any(), any(), any())).thenReturn(200);

  //     // Act
  //     handler.updateName(context, data);

  //     // Assert
  //     verify(messages, never()).error(anyString());
  //     verify(messages, never()).warn(anyString());
  //   }

  //   @Test
  //   public void testUpdateNameWithSecondaryProperties() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Arrays.asList("property1", "property2", "property3"));

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
  //         .thenReturn(new HashMap<>());

  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), anyString()))
  //         .thenReturn("differentFile.txt");

  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("fileInSDM.txt");
  //     when(sdmService.updateAttachments(anyString(), any(), any(), any())).thenReturn(200);

  //     // Act
  //     handler.updateName(context, data);

  //     // Assert
  //     verify(messages, never()).error(anyString());
  //     verify(messages, never()).warn(anyString());
  //   }
  @Test
  @SuppressWarnings("unchecked")
  public void testUpdateNameWithEmptyFilename() throws IOException {
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "test-id");
    attachment.put("fileName", null); // Empty filename
    attachment.put("objectId", "test-object-id");
    attachments.add(attachment);

    // entity.put("attachments", attachments);
    entity.put("composition", attachments);

    CdsData cdsDataEntity = CdsData.create(entity); // Wrap entity in CdsData
    data.add(cdsDataEntity); // Add to data

    // Mock duplicate file name
    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.FileNameDuplicateInDrafts(data, "compositionName", "some.qualified.Name"))
        .thenReturn(new HashSet<>());

    // Mock AttachmentsHandlerUtils.fetchAttachments to return the attachment with null filename
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {
      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments(
                      "some.qualified.Name", entity, "compositionName"))
          .thenReturn(attachments);
      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(
                      any(), anyList(), anyString(), anyString()))
          .thenCallRealMethod();

      // Mock attachment entity
      CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
      when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
      when(context.getTarget()).thenReturn(attachmentDraftEntity);
      when(context.getModel()).thenReturn(model);

      // Mock findEntity to return an optional containing attachmentDraftEntity
      when(model.findEntity("compositionDefinition"))
          .thenReturn(Optional.of(attachmentDraftEntity));
      UserInfo userInfo = Mockito.mock(UserInfo.class);
      when(context.getUserInfo()).thenReturn(userInfo);
      when(userInfo.isSystemUser()).thenReturn(false);
      // Mock authentication
      when(context.getMessages()).thenReturn(messages);
      when(context.getAuthenticationInfo()).thenReturn(authInfo);
      when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
      when(jwtTokenInfo.getToken()).thenReturn("testJwtToken");

      // Mock getObject
      when(sdmService.getObject("test-object-id", mockCredentials, false))
          .thenReturn("fileInSDM.txt");

      // Mock getSecondaryTypeProperties
      Map<String, String> secondaryTypeProperties = new HashMap<>();
      Map<String, String> updatedSecondaryProperties = new HashMap<>();
      sdmUtilsMockedStatic
          .when(
              () ->
                  SDMUtils.getSecondaryTypeProperties(
                      Optional.of(attachmentDraftEntity), attachment))
          .thenReturn(secondaryTypeProperties);
      sdmUtilsMockedStatic
          .when(
              () ->
                  SDMUtils.getUpdatedSecondaryProperties(
                      Optional.of(attachmentDraftEntity),
                      attachment,
                      persistenceService,
                      secondaryTypeProperties,
                      updatedSecondaryProperties))
          .thenReturn(new HashMap<>());

      // Mock restricted character
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName("fileNameInRequest"))
          .thenReturn(false);

      when(dbQuery.getAttachmentForID(attachmentDraftEntity, persistenceService, "test-id"))
          .thenReturn(null);

      // When getPropertiesForID is called
      when(dbQuery.getPropertiesForID(
              attachmentDraftEntity, persistenceService, "test-id", secondaryTypeProperties))
          .thenReturn(updatedSecondaryProperties);

      // Make validateFileName execute its real implementation so it logs the error
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.FileNameContainsWhitespace(anyList(), anyString(), anyString()))
          .thenCallRealMethod();
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(anyList(), anyString(), anyString()))
          .thenReturn(new HashSet<>());

      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "compositionName");
      compositionInfo.put("definition", "compositionDefinition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Assert: since validation logs an error instead of throwing, ensure the message was
      // logged
      verify(messages, times(1))
          .error(
              SDMConstants.FILENAME_WHITESPACE_ERROR_MESSAGE
                  + "\n\nTable: compositionName\nPage: TestTitle");
    } // Close AttachmentsHandlerUtils mock
  }

  //   @Test
  //   public void testUpdateNameWithMultipleAttachments() throws IOException {
  //     // Arrange
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();

  //     // Mock the attachments instead of using HashMap directly
  //     Map<String, Object> attachment1 = new HashMap<>();
  //     attachment1.put("ID", "test-id-1");
  //     attachment1.put("fileName", "file1.txt");
  //     attachment1.put("objectId", "test-object-id-1");
  //     attachments.add(attachment1);

  //     // Mock the second attachment
  //     Map<String, Object> attachment2 = Mockito.mock(Map.class);
  //     Mockito.when(attachment2.get("ID")).thenReturn("test-id-2");
  //     Mockito.when(attachment2.get("fileName")).thenReturn("file/2.txt");
  //     Mockito.when(attachment2.get("objectId")).thenReturn("test-object-id-2");
  //     attachments.add(attachment2);

  //     // Mock the third attachment
  //     Map<String, Object> attachment3 = Mockito.mock(Map.class);
  //     Mockito.when(attachment3.get("ID")).thenReturn("test-id-3");
  //     Mockito.when(attachment3.get("fileName")).thenReturn("file3.txt");
  //     Mockito.when(attachment3.get("objectId")).thenReturn("test-object-id-3");
  //     attachments.add(attachment3);

  //     // Convert entity map to CdsData
  //     entity.put("attachments", attachments);
  //     CdsData cdsDataEntity = CdsData.create(entity); // Wrap entity in CdsData
  //     data.add(cdsDataEntity); // Add to data

  //     // Mock utility methods
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     // Mock restricted character checks
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName("file1.txt"))
  //         .thenReturn(false);
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName("file/2.txt"))
  //         .thenReturn(true); // Restricted
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName("file3.txt"))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Collections.emptyList());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(),any()))
  //         .thenReturn(new HashMap<>());

  //     // Mock DB query responses
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), eq("test-id-1")))
  //         .thenReturn("file1.txt");
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), eq("test-id-2")))
  //         .thenReturn("file2.txt");
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), eq("test-id-3")))
  //         .thenReturn("file3.txt");

  //     // Mock SDM service responses
  //     when(sdmService.getObject(anyString(), eq("test-object-id-1"),
  // any())).thenReturn("file1.txt");
  //     when(sdmService.getObject(anyString(), eq("test-object-id-2"), any()))
  //         .thenReturn("file2_sdm.txt");
  //     when(sdmService.getObject(anyString(), eq("test-object-id-3"), any()))
  //         .thenReturn("file3_sdm.txt");

  //     // Setup conflict for the third attachment
  //     when(sdmService.updateAttachments(anyString(), any(), any(CmisDocument.class), any()))
  //         .thenAnswer(
  //             invocation -> {
  //               CmisDocument doc = invocation.getArgument(2);
  //               if ("file3.txt".equals(doc.getFileName())) {
  //                 return 409; // Conflict
  //               }
  //               return 200; // Success for others
  //             });

  //     // Act
  //     handler.updateName(context, data);

  //     // Assert
  //     // Check restricted character warning
  //     List<String> expectedRestrictedFiles = Collections.singletonList("file/2.txt");
  //     verify(messages, times(1))
  //         .warn(SDMConstants.nameConstraintMessage(expectedRestrictedFiles, "Rename"));

  //     // Check conflict warning
  //     List<String> expectedConflictFiles = Collections.singletonList("file3.txt");
  //     verify(messages, times(1))
  //         .warn(
  //             String.format(
  //                 SDMConstants.FILES_RENAME_WARNING_MESSAGE,
  //                 String.join(", ", expectedConflictFiles)));

  //     // Verify file replacements were attempted
  //     verify(attachment2).replace("fileName", "file2_sdm.txt"); // This one has restricted chars
  //     verify(attachment3).replace("fileName", "file3_sdm.txt"); // This one had a conflict
  //   }

  /** Helper method to create a standard test data structure */
  private List<CdsData> createTestData() {
    List<CdsData> data = new ArrayList<>();

    // Create a map for the entity
    Map<String, Object> entity = new HashMap<>();

    // Create attachments
    List<Map<String, Object>> attachments = new ArrayList<>();

    // Create attachment map
    @SuppressWarnings("unchecked")
    Map<String, Object> attachment = mock(Map.class);
    when(attachment.get("ID")).thenReturn("test-id");
    when(attachment.get("fileName")).thenReturn("file/1.txt");
    when(attachment.get("objectId")).thenReturn("test-object-id");

    // Add attachment to the list
    attachments.add(attachment);

    // Add attachments to the entity
    entity.put("attachments", attachments);

    // Convert the entity map to a CdsData instance and add it to the data list
    CdsData cdsDataEntity = CdsData.create(entity);
    data.add(cdsDataEntity);

    return data;
  }

  @Test
  public void testProcessBeforeWithNoCompositions() throws IOException {
    // Arrange
    when(context.getTarget()).thenReturn(mock(CdsEntity.class));
    when(context.getTarget().compositions()).thenReturn(Stream.empty());

    List<CdsData> dataList = new ArrayList<>();

    // Act
    handler.processBefore(context, dataList);

    // Then
    verify(handler, never()).updateName(eq(context), eq(dataList), any());
  }

  @Test
  public void testProcessBeforeWithNonAttachmentComposition() throws IOException {
    // Arrange
    Stream<CdsElement> compositionsStream = Stream.of(cdsElement);
    when(context.getTarget().compositions()).thenReturn(compositionsStream);
    when(cdsElement.getType()).thenReturn(cdsAssociationType);
    when(cdsAssociationType.getTargetAspect()).thenReturn(Optional.of(targetAspect));
    when(targetAspect.getQualifiedName()).thenReturn("some.other.Entity"); // Not attachment
    when(cdsElement.getName()).thenReturn("nonAttachmentComposition");

    List<CdsData> dataList = new ArrayList<>();

    // Act
    handler.processBefore(context, dataList);

    // Then
    verify(handler, never()).updateName(eq(context), eq(dataList), any());
  }

  @Test
  public void testUpdateNameWithAttachmentEntityNotFound() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    CdsEntity mockEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("test.Entity.attachments")).thenReturn(Optional.empty());

    sdmUtilsMockedStatic
        .when(
            () -> SDMUtils.FileNameDuplicateInDrafts(eq(data), eq("testComposition"), anyString()))
        .thenReturn(Collections.emptySet());

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "testComposition");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("testComposition", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Then - Should handle gracefully when attachment entity not found
    verify(messages, never()).error(anyString());
  }

  @Test
  public void testUpdateNameWithAttachmentsWithIds() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();

    // Create entity with attachments directly under the composition name
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "test-attachment-id");
    attachment.put("fileName", "test.txt");
    attachment.put("objectId", "test-object-id"); // Use objectId instead of url
    attachments.add(attachment);

    entity.put("testComposition", attachments);
    data.add(CdsData.create(entity));

    CdsEntity mockEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);
    when(model.findEntity("test.Entity.testComposition")).thenReturn(Optional.of(mockEntity));

    // Mock the model and entity for attachments processing
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("testComposition")).thenReturn(Optional.of(attachmentEntity));
    when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.cds.Attachments");

    // Use the already set up global CacheConfig mock
    @SuppressWarnings("unchecked")
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);

    sdmUtilsMockedStatic
        .when(
            () -> SDMUtils.FileNameDuplicateInDrafts(eq(data), eq("testComposition"), anyString()))
        .thenReturn(Collections.emptySet());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
        .thenReturn(false);
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(Collections.emptyMap());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getPropertyTitles(any(), any()))
        .thenReturn(Collections.emptyMap());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
        .thenReturn(Collections.emptyMap());

    when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("test.txt");

    try {
      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testComposition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("testComposition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then
      verify(messages, never()).error(anyString());
      // Verify cache remove was called
      verify(mockCache).remove(any());
    } finally {
      cacheConfigMockedStatic.close();
    }
  }

  @Test
  public void testUpdateNameWithSecondaryPropertiesError() throws IOException {
    // Arrange
    List<CdsData> data = createTestDataWithAttachments();
    CdsEntity mockEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Mock the model and entity for attachments processing
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("testComposition")).thenReturn(Optional.of(attachmentEntity));
    when(attachmentEntity.getQualifiedName()).thenReturn("com.sap.cds.Attachments");
    when(model.findEntity("test.Entity.testComposition")).thenReturn(Optional.of(mockEntity));

    // Use the already set up global CacheConfig mock
    @SuppressWarnings("unchecked")
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);

    Map<String, String> secondaryPropsError = new HashMap<>();
    secondaryPropsError.put("invalidProp", "Invalid property definition");

    sdmUtilsMockedStatic
        .when(
            () -> SDMUtils.FileNameDuplicateInDrafts(eq(data), eq("testComposition"), anyString()))
        .thenReturn(Collections.emptySet());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
        .thenReturn(false);
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(Map.of("validProp", "Valid Property"));
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getPropertyTitles(any(), any()))
        .thenReturn(Collections.emptyMap());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
        .thenReturn(Collections.emptyMap());

    when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("test.txt");

    try {
      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testComposition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("testComposition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should handle secondary properties validation
      verify(messages, never()).error(anyString());
      // Verify cache remove was called
      verify(mockCache).remove(any());
    } finally {
      cacheConfigMockedStatic.close();
    }
  }

  @Test
  public void testConstructorInitialization() {
    // Act
    SDMCreateAttachmentsHandler newHandler =
        new SDMCreateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);

    // Then
    assertEquals(handler.getClass(), newHandler.getClass());
  }

  @Test
  public void testProcessBeforeWithIOException() throws IOException {
    // Arrange
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {
      // Mock getAttachmentCompositionDetails to return a valid mapping
      Map<String, Map<String, String>> compositionMapping = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testComposition");
      compositionInfo.put("parentTitle", "TestTitle");
      compositionMapping.put("testComposition", compositionInfo);

      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentCompositionDetails(
                      any(), any(), any(), any(), any()))
          .thenReturn(compositionMapping);

      // Mock updateName to throw IOException on the existing spy
      doThrow(new IOException("Test IO Exception"))
          .when(handler)
          .updateName(eq(context), anyList(), any());

      List<CdsData> dataList = new ArrayList<>();
      dataList.add(CdsData.create(Map.of("test", "data")));

      // Act & Assert
      assertThrows(
          IOException.class,
          () -> {
            handler.processBefore(context, dataList);
          });
    }
  }

  private List<CdsData> createTestDataWithAttachments() {
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();

    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "test-attachment-id");
    attachment.put("fileName", "test.txt");
    attachment.put("url", "test-object-id");
    attachments.add(attachment);

    entity.put("testComposition", attachments);
    data.add(CdsData.create(entity));

    return data;
  }

  @Test
  public void testConstructor() {
    // Test constructor creates instance successfully
    SDMCreateAttachmentsHandler newHandler =
        new SDMCreateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);
    assertNotNull(newHandler);
  }

  @Test
  public void testProcessBeforeWithEmptyData() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {

      // Mock empty attachment composition details
      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentCompositionDetails(
                      any(), any(), any(), any(), any()))
          .thenReturn(new HashMap<>());

      List<CdsData> dataList = new ArrayList<>();
      CdsData entityData = mock(CdsData.class);
      dataList.add(entityData);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getModel()).thenReturn(model);

      // When
      handler.processBefore(context, dataList);

      // Then - Should complete without error and call updateName with empty map
      verify(handler).updateName(eq(context), eq(dataList), eq(new HashMap<>()));
    }
  }

  @Test
  public void testProcessBeforeWithMultipleEntities() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {

      // Mock composition details for each entity
      Map<String, Map<String, String>> compositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testAttachments");
      compositionInfo.put("parentTitle", "TestEntity");
      compositionDetails.put("TestEntity.attachments", compositionInfo);

      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentCompositionDetails(
                      any(), any(), any(), any(), any()))
          .thenReturn(compositionDetails);

      List<CdsData> dataList = new ArrayList<>();
      dataList.add(mock(CdsData.class));
      dataList.add(mock(CdsData.class)); // Multiple entities

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getModel()).thenReturn(model);

      // When
      handler.processBefore(context, dataList);

      // Then - Should process both entities
      verify(handler, times(2)).updateName(eq(context), eq(dataList), eq(compositionDetails));
    }
  }

  @Test
  public void testUpdateNameWithCompositionNameContainingDots() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      data.add(mock(CdsData.class));

      // Test composition name with dots - should extract last part
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "com.example.Entity.attachments"); // Contains dots
      compositionInfo.put("parentTitle", "Example Entity");
      attachmentCompositionDetails.put("compositionDef", compositionInfo);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(model.findEntity("compositionDef")).thenReturn(Optional.of(targetEntity));
      when(context.getMessages()).thenReturn(messages);

      // Mock validateFileNames to return no error
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock fetchAttachments to return empty (no processing needed)
      attachmentsMockStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(new ArrayList<>());

      // When
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should extract "attachments" from the dotted name and use it in context
      attachmentsMockStatic.verify(
          () ->
              AttachmentsHandlerUtils.validateFileNames(
                  eq(context),
                  eq(data),
                  eq("com.example.Entity.attachments"),
                  contains("Table: attachments")), // Should contain extracted name
          times(1));
    }
  }

  @Test
  public void testUpdateNameWithNoAttachments() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      Map<String, Object> entity = new HashMap<>();
      data.add(CdsData.create(entity));

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestEntity");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(model.findEntity("TestEntity.attachments")).thenReturn(Optional.of(targetEntity));
      when(context.getMessages()).thenReturn(messages);

      // Mock validateFileNames to return no error
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock fetchAttachments to return null (no attachments)
      attachmentsMockStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null);

      // When
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should handle gracefully and not process any attachments
      // Verify no SDM service calls were made
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  @Test
  public void testUpdateNameWithValidationError() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      data.add(mock(CdsData.class));

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestEntity");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getMessages()).thenReturn(messages);

      // Mock validateFileNames to return error
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(true); // Return true to indicate validation error

      // When
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should skip processing due to validation error
      // Verify fetchAttachments was never called due to validation error
      attachmentsMockStatic.verify(
          () -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()), never());
    }
  }

  @Test
  public void testUpdateNameWithNullContext() {
    // Test handling of null context
    List<CdsData> data = new ArrayList<>();
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();

    // When/Then - Should handle gracefully without throwing exceptions
    try {
      handler.updateName(null, data, attachmentCompositionDetails);
    } catch (IOException e) {
      // Expected behavior may vary, but shouldn't crash
    }
  }

  @Test
  public void testUpdateNameWithEntityNotFound() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      data.add(mock(CdsData.class));

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestEntity");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getMessages()).thenReturn(messages);

      // Mock entity not found in model
      when(model.findEntity("TestEntity.attachments")).thenReturn(Optional.empty());

      // Mock validateFileNames to return no error
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock fetchAttachments to return null (will still be called even if entity not found)
      attachmentsMockStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(null);

      // When
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should handle entity not found gracefully
      // Verify fetchAttachments was called
      attachmentsMockStatic.verify(
          () -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()),
          times(1));
    }
  }

  @Test
  public void testUpdateNameWithEmptyAttachmentsList() throws IOException {
    // Test handling of empty attachments list
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
        mockStatic(AttachmentsHandlerUtils.class)) {

      List<CdsData> data = new ArrayList<>();
      data.add(mock(CdsData.class));

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "attachments");
      compositionInfo.put("parentTitle", "TestEntity");
      attachmentCompositionDetails.put("TestEntity.attachments", compositionInfo);

      CdsEntity targetEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getMessages()).thenReturn(messages);
      when(model.findEntity("TestEntity.attachments")).thenReturn(Optional.of(targetEntity));

      // Mock validateFileNames to return no error
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(any(), any(), anyString(), anyString()))
          .thenReturn(false);

      // Mock fetchAttachments to return empty list
      attachmentsMockStatic
          .when(() -> AttachmentsHandlerUtils.fetchAttachments(anyString(), any(), anyString()))
          .thenReturn(new ArrayList<>());

      // When - Should handle gracefully with empty attachments
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Should complete without errors
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }
}
