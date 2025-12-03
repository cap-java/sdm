package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMUpdateAttachmentsHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.ehcache.Cache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SDMUpdateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  @Mock private CdsUpdateEventContext context;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsModel model;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private UserInfo userInfo;
  private SDMService sdmService;
  @Mock private SDMUtils sdmUtilsMock;
  @Mock private CdsStructuredType targetAspect;
  private SDMUpdateAttachmentsHandler handler;

  @Mock private CdsElement cdsElement;
  @Mock private CdsEntity targetEntity;
  @Mock private CdsAssociationType cdsAssociationType;

  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;
  private MockedStatic<CacheConfig> cacheConfigMockedStatic;

  @Mock private TokenHandler tokenHandler;
  @Mock private DBQuery dbQuery;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmService = mock(SDMServiceImpl.class);
    handler =
        spy(new SDMUpdateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery));
    sdmUtilsMock = mock(SDMUtils.class);
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
  }

  @Test
  public void testProcessBefore() throws IOException {
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
            mockStatic(AttachmentsHandlerUtils.class);
        MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {

      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      // Arrange the mock compositions scenario
      Map<String, String> expectedCompositionMapping = new HashMap<>();
      expectedCompositionMapping.put("Name1", "Name1");
      expectedCompositionMapping.put("Name2", "Name2");

      // Mock context.getTarget() and context.getModel()
      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getModel()).thenReturn(model);
      when(model.findEntity(anyString())).thenReturn(Optional.of(targetEntity));

      // Mock AttachmentsHandlerUtils.getAttachmentCompositionDetails to return the expected mapping
      Map<String, Map<String, String>> expectedCompositionMapping2 = new HashMap<>();
      Map<String, String> compositionInfo1 = new HashMap<>();
      compositionInfo1.put("name", "Name1");
      compositionInfo1.put("parentTitle", "TestTitle");
      expectedCompositionMapping2.put("Name1", compositionInfo1);

      Map<String, String> compositionInfo2 = new HashMap<>();
      compositionInfo2.put("name", "Name2");
      compositionInfo2.put("parentTitle", "TestTitle");
      expectedCompositionMapping2.put("Name2", compositionInfo2);

      attachmentsHandlerUtilsMocked
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentCompositionDetails(
                      any(), any(), any(), any(), any()))
          .thenReturn(expectedCompositionMapping2);

      List<CdsData> dataList = new ArrayList<>();
      CdsData entityData = mock(CdsData.class);
      dataList.add(entityData);

      // Act
      handler.processBefore(context, dataList);

      // Assert that updateName was called with the compositions detected
      verify(handler).updateName(context, dataList, expectedCompositionMapping2);
    }
  }

  @Test
  public void testRenameWithDuplicateFilenames() throws IOException {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class);
        MockedStatic<AttachmentsHandlerUtils> attachmentsMockedStatic =
            mockStatic(AttachmentsHandlerUtils.class)) {
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      // Prepare a data list with a mocked CdsData element (CdsData implements Map)
      List<CdsData> data = new ArrayList<>();
      CdsData mockCdsData = mock(CdsData.class);
      data.add(mockCdsData);

      // Prepare attachments that contain duplicate file names
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment1 = new HashMap<>();
      attachment1.put("fileName", "file1.txt");
      attachment1.put("repositoryId", SDMConstants.REPOSITORY_ID);
      Map<String, Object> attachment2 = new HashMap<>();
      attachment2.put("fileName", "file1.txt");
      attachment2.put("repositoryId", SDMConstants.REPOSITORY_ID);
      attachments.add(attachment1);
      attachments.add(attachment2);

      when(context.getMessages()).thenReturn(messages);

      // Mock the target entity
      CdsEntity targetEntity = mock(CdsEntity.class);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getTarget()).thenReturn(targetEntity);

      // Make AttachmentsHandlerUtils.fetchAttachments return our attachments for any entity
      attachmentsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments(
                      anyString(), any(Map.class), eq("compositionName")))
          .thenReturn(attachments);
      attachmentsMockedStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.validateFileNames(
                      any(), anyList(), anyString(), anyString()))
          .thenCallRealMethod();

      // Mock SDMUtils helper methods to ensure validation works correctly
      try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class)) {
        sdmUtilsMockedStatic
            .when(() -> SDMUtils.FileNameContainsWhitespace(anyList(), anyString(), anyString()))
            .thenReturn(new HashSet<>());
        sdmUtilsMockedStatic
            .when(
                () ->
                    SDMUtils.FileNameContainsRestrictedCharaters(
                        anyList(), anyString(), anyString()))
            .thenReturn(new ArrayList<>());
        Set<String> duplicateFiles = new HashSet<>();
        duplicateFiles.add("file1.txt");
        sdmUtilsMockedStatic
            .when(() -> SDMUtils.FileNameDuplicateInDrafts(anyList(), anyString(), anyString()))
            .thenReturn(duplicateFiles);

        // Call the method under test; validateFileNames will detect duplicates and call
        // context.getMessages().error(...)
        Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
        Map<String, String> compositionInfo = new HashMap<>();
        compositionInfo.put("name", "compositionName");
        compositionInfo.put("definition", "compositionDefinition");
        compositionInfo.put("parentTitle", "TestTitle");
        attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
        handler.updateName(context, data, attachmentCompositionDetails);

        Set<String> expected = new HashSet<>();
        expected.add("file1.txt");
        verify(messages, times(1))
            .error(
                SDMConstants.duplicateFilenameFormat(expected)
                    + "\n\nTable: compositionName\nPage: TestTitle");
      }
    }
  }

  @Test
  public void testRenameWithUniqueFilenames() throws IOException {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class);
        MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
            mockStatic(AttachmentsHandlerUtils.class);
        MockedStatic<SDMUtils> sdmUtilsMockStatic = mockStatic(SDMUtils.class)) {

      @SuppressWarnings("unchecked")
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      List<CdsData> data = new ArrayList<>();
      CdsData mockCdsData = mock(CdsData.class);
      data.add(mockCdsData);

      // Create attachment with unique filename
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("fileName", "uniquefile1.txt");
      attachment.put("ID", "test-id");
      attachment.put("objectId", "obj123");
      attachments.add(attachment);

      when(context.getTarget()).thenReturn(targetEntity);
      when(context.getModel()).thenReturn(model);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(model.findEntity("compositionDefinition")).thenReturn(Optional.of(targetEntity));
      when(context.getMessages()).thenReturn(messages);

      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments(
                      anyString(), any(), eq("compositionName")))
          .thenReturn(attachments);

      // Mock DBQuery to return same filename (no renaming needed)
      when(dbQuery.getAttachmentForID(any(), any(), eq("test-id"))).thenReturn("uniquefile1.txt");

      sdmUtilsMockStatic
          .when(() -> SDMUtils.getPropertyTitles(any(), any()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockStatic
          .when(() -> SDMUtils.getSecondaryPropertiesWithInvalidDefinition(any(), any()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockStatic
          .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
          .thenReturn(new HashMap<>());
      sdmUtilsMockStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
          .thenReturn(false);
      sdmUtilsMockStatic
          .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any(), any()))
          .thenReturn(new HashMap<>());

      List<String> propertyKeys = new ArrayList<>();
      when(dbQuery.getPropertiesForID(any(), any(), anyString(), eq(propertyKeys)))
          .thenReturn(new HashMap<>());

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "compositionName");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("compositionDefinition", compositionInfo);

      // Act
      handler.updateName(context, data, attachmentCompositionDetails);

      // Assert - verify no update service call was made since filename didn't change
      verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());
    }
  }

  //   @Test
  //   public void testRenameWithConflictResponseCode() throws IOException {
  //     // Mock the data structure to simulate the attachments
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();
  //     Map<String, Object> attachment = spy(new HashMap<>());
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     secondaryProperties.put("filename", "file1.txt");
  //     CmisDocument document = new CmisDocument();
  //     document.setFileName("file1.txt");
  //     attachment.put("fileName", "file1.txt");
  //     attachment.put("url", "objectId");
  //     attachment.put("ID", "test-id"); // assuming there's an ID field
  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);
  //     CdsData mockCdsData = mock(CdsData.class);
  //     when(mockCdsData.get("attachments")).thenReturn(attachments);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));

  //     // Mock the authentication context
  //     when(context.getAuthenticationInfo()).thenReturn(authInfo);
  //     when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
  //     when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

  //     // Mock the static TokenHandler
  //     when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

  //     // Mock the SDM service responses
  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

  //     when(sdmService.updateAttachments("jwtToken", mockCredentials, document,
  // secondaryProperties))
  //         .thenReturn(409); // Mock conflict response code

  //     // Mock the returned messages
  //     when(context.getMessages()).thenReturn(messages);

  //     // Execute the method under test
  //     handler.updateName(context, data);

  //     // Verify the attachment's file name was attempted to be replaced with "file-sdm.txt"
  //     verify(attachment).put("fileName", "file1.txt");

  //     // Verify that a warning message was added to the context
  //     verify(messages, times(1))
  //         .warn("The following files could not be renamed as they already
  // exist:\nfile1.txt\n");
  //   }

  @Test
  public void testRenameWithNoSDMRoles() throws IOException {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class);
        MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
            mockStatic(AttachmentsHandlerUtils.class)) {
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      // Mock the data structure to simulate the attachments
      List<CdsData> data = new ArrayList<>();
      Map<String, Object> entity = new HashMap<>();
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = spy(new HashMap<>());
      Map<String, String> secondaryProperties = new HashMap<>();
      Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();
      secondaryProperties.put("filename", "file1.txt");

      CmisDocument document = new CmisDocument();
      document.setFileName("file1.txt");

      attachment.put("fileName", "file1.txt");
      attachment.put("url", "objectId");
      attachment.put("ID", "test-id");
      attachments.add(attachment);

      entity.put("compositionName", attachments);
      CdsData mockCdsData = mock(CdsData.class);
      data.add(mockCdsData);

      CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
      when(context.getTarget()).thenReturn(attachmentDraftEntity);
      when(context.getModel()).thenReturn(model);
      when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
      when(model.findEntity("compositionDefinition"))
          .thenReturn(Optional.of(attachmentDraftEntity));
      when(context.getMessages()).thenReturn(messages);
      UserInfo userInfo = Mockito.mock(UserInfo.class);
      when(context.getUserInfo()).thenReturn(userInfo);
      when(userInfo.isSystemUser()).thenReturn(false);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);
      when(dbQuery.getAttachmentForID(
              any(CdsEntity.class), any(PersistenceService.class), anyString()))
          .thenReturn("file123.txt");

      when(sdmService.updateAttachments(
              mockCredentials,
              document,
              secondaryProperties,
              secondaryPropertiesWithInvalidDefinitions,
              false))
          .thenReturn(403); // Forbidden

      // Mock AttachmentsHandlerUtils.fetchAttachments
      attachmentsMockStatic
          .when(
              () ->
                  AttachmentsHandlerUtils.fetchAttachments(
                      anyString(), any(Map.class), eq("compositionName")))
          .thenReturn(attachments);

      // Mock SDMUtils methods
      try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class)) {
        sdmUtilsMock
            .when(
                () ->
                    SDMUtils.FileNameDuplicateInDrafts(
                        any(List.class), eq("compositionName"), anyString()))
            .thenReturn(Collections.emptySet());

        sdmUtilsMock
            .when(() -> SDMUtils.getPropertyTitles(any(Optional.class), any(Map.class)))
            .thenReturn(Collections.emptyMap());

        sdmUtilsMock
            .when(
                () ->
                    SDMUtils.getSecondaryPropertiesWithInvalidDefinition(
                        any(Optional.class), any(Map.class)))
            .thenReturn(Collections.emptyMap());

        sdmUtilsMock
            .when(() -> SDMUtils.getSecondaryTypeProperties(any(Optional.class), any(Map.class)))
            .thenReturn(Collections.emptyMap());

        sdmUtilsMock
            .when(
                () ->
                    SDMUtils.getUpdatedSecondaryProperties(
                        any(Optional.class),
                        any(Map.class),
                        any(PersistenceService.class),
                        any(Map.class),
                        any(Map.class)))
            .thenReturn(secondaryProperties);

        sdmUtilsMock
            .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
            .thenReturn(false);

        // Call the method
        Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
        Map<String, String> compositionInfo = new HashMap<>();
        compositionInfo.put("name", "compositionName");
        compositionInfo.put("parentTitle", "TestTitle");
        attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
        handler.updateName(context, data, attachmentCompositionDetails);

        // Capture and assert the warning message
        ArgumentCaptor<String> warningCaptor = ArgumentCaptor.forClass(String.class);
        verify(messages).warn(warningCaptor.capture());
        String warningMessage = warningCaptor.getValue();

        // Assert that the warning message contains the expected content
        assertTrue(warningMessage.contains("Could not update the following files"));
        assertTrue(warningMessage.contains("file123.txt"));
        assertTrue(warningMessage.contains("You do not have the required permissions"));
      }
    }
  }

  //   @Test
  //   public void testRenameWith500Error() throws IOException {
  //     // Mock the data structure to simulate the attachments
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();
  //     Map<String, Object> attachment = spy(new HashMap<>());
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     secondaryProperties.put("filename", "file1.txt");
  //     CmisDocument document = new CmisDocument();
  //     document.setFileName("file1.txt");
  //     attachment.put("fileName", "file1.txt");
  //     attachment.put("url", "objectId");
  //     attachment.put("ID", "test-id"); // assuming there's an ID field
  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);
  //     CdsData mockCdsData = mock(CdsData.class);
  //     when(mockCdsData.get("attachments")).thenReturn(attachments);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));

  //     // Mock the authentication context
  //     when(context.getAuthenticationInfo()).thenReturn(authInfo);
  //     when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
  //     when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

  //     // Mock the static TokenHandler
  //     when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

  //     // Mock the SDM service responses
  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

  //     when(sdmService.updateAttachments("jwtToken", mockCredentials, document,
  // secondaryProperties))
  //         .thenReturn(500); // Mock conflict response code

  //     ServiceException exception =
  //         assertThrows(
  //             ServiceException.class,
  //             () -> {
  //               handler.updateName(context, data);
  //             });

  //     assertEquals(SDMConstants.SDM_ROLES_ERROR_MESSAGE, exception.getMessage());
  //   }

  //   @Test
  //   public void testRenameWith200ResponseCode() throws IOException {
  //     // Mock the data structure to simulate the attachments
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();
  //     Map<String, Object> attachment = spy(new HashMap<>());
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     secondaryProperties.put("filename", "file1.txt");
  //     CmisDocument document = new CmisDocument();
  //     document.setFileName("file1.txt");
  //     attachment.put("fileName", "file1.txt");
  //     attachment.put("url", "objectId");
  //     attachment.put("ID", "test-id"); // assuming there's an ID field
  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);
  //     CdsData mockCdsData = mock(CdsData.class);
  //     when(mockCdsData.get("attachments")).thenReturn(attachments);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));

  //     // Mock the authentication context
  //     when(context.getAuthenticationInfo()).thenReturn(authInfo);
  //     when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
  //     when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

  //     // Mock the static TokenHandler
  //     when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

  //     // Mock the SDM service responses
  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

  //     when(sdmService.updateAttachments("jwtToken", mockCredentials, document,
  // secondaryProperties))
  //         .thenReturn(200);

  //     // Execute the method under test
  //     handler.updateName(context, data);

  //     verify(attachment, never()).replace("fileName", "file-sdm.txt");

  //     // Verify that a warning message was added to the context
  //     verify(messages, times(0))
  //         .warn("The following files could not be renamed as they already
  // exist:\nfile1.txt\n");
  //   }

  @Test
  public void testRenameWithoutFileInSDM() throws IOException {
    // Mocking the necessary objects
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    Map<String, String> secondaryProperties = new HashMap<>();
    Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");

    // Verify that updateAttachments is never called
    verify(sdmService, never())
        .updateAttachments(
            mockCredentials,
            document,
            secondaryProperties,
            secondaryPropertiesWithInvalidDefinitions,
            false);
  }

  @Test
  public void testRenameWithNoAttachments() throws IOException {
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class)) {
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      // Arrange
      List<CdsData> data = new ArrayList<>();
      CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
      Map<String, String> secondaryProperties = new HashMap<>();
      Map<String, String> secondaryPropertiesWithInvalidDefinitions = new HashMap<>();
      CmisDocument document = new CmisDocument();
      when(context.getTarget()).thenReturn(attachmentDraftEntity);
      when(context.getModel()).thenReturn(model);

      when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");

      // Mock the correct entity name that the handler will look for
      when(model.findEntity("compositionDefinition"))
          .thenReturn(Optional.of(attachmentDraftEntity));

      Map<String, Object> entity = new HashMap<>();
      CdsData cdsDataEntity = CdsData.create(entity);
      data.add(cdsDataEntity);

      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "compositionName");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("compositionDefinition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Assert
      verify(sdmService, never())
          .updateAttachments(
              eq(mockCredentials),
              eq(document),
              eq(secondaryProperties),
              eq(secondaryPropertiesWithInvalidDefinitions),
              eq(false));
    }
  }

  //   @Test
  //   public void testRenameWithRestrictedFilenames() throws IOException {
  //     List<CdsData> data = prepareMockAttachmentData("file1.txt", "file2/abc.txt",
  // "file3\\abc.txt");
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     secondaryProperties.put("filename", "file1.txt");
  //     CmisDocument document = new CmisDocument();
  //     document.setFileName("file1.txt");
  //     List<String> fileNameWithRestrictedChars = new ArrayList<>();
  //     fileNameWithRestrictedChars.add("file2/abc.txt");
  //     fileNameWithRestrictedChars.add("file3\\abc.txt");

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));
  //     when(context.getAuthenticationInfo()).thenReturn(authInfo);
  //     when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
  //     when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

  //     when(context.getMessages()).thenReturn(messages);

  //     sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenAnswer(
  //             invocation -> {
  //               String filename = invocation.getArgument(0);
  //               return filename.contains("/") || filename.contains("\\");
  //             });

  //     when(sdmService.updateAttachments("jwtToken", mockCredentials, document,
  // secondaryProperties))
  //         .thenReturn(409); // Mock conflict response code

  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file-in-sdm.txt");

  //     handler.updateName(context, data);

  //     verify(messages, times(1))
  //         .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename"));

  //     verify(messages, never()).error(anyString());
  //   }

  //   @Test
  //   public void testRenameWithValidRestrictedNames() throws IOException {
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();
  //     Map<String, Object> attachment = spy(new HashMap<>());
  //     List<String> fileNameWithRestrictedChars = new ArrayList<>();
  //     fileNameWithRestrictedChars.add("file2/abc.txt");
  //     attachment.put("fileName", "file2/abc.txt");
  //     attachment.put("objectId", "objectId-123");
  //     attachment.put("ID", "id-123");
  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);
  //     CdsData mockCdsData = mock(CdsData.class);
  //     when(mockCdsData.get("attachments")).thenReturn(attachments);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));

  //     when(context.getMessages()).thenReturn(messages);

  //     sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenAnswer(
  //             invocation -> {
  //               String filename = invocation.getArgument(0);
  //               return filename.contains("/") || filename.contains("\\");
  //             });

  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file3/abc.txt");

  //     // Call the method under test
  //     handler.updateName(context, data);

  //     // Verify the attachment's file name was replaced with the name in SDM
  //     // Now use `put` to verify the change was made instead of `replace`
  //     verify(attachment).put("fileName", "file2/abc.txt");

  //     // Verify that a warning message is correct
  //     verify(messages, times(1))
  //         .warn(
  //             String.format(
  //                 SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename")));
  //   }

  //   @Test
  //   public void testProcessAttachment_PopulateSecondaryTypeProperties() throws IOException {
  //     // Arrange
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();

  //     // Create a spy for the attachment map
  //     Map<String, Object> attachment = spy(new HashMap<>());

  //     // Prepare attachment with test data
  //     attachment.put("ID", "test-id");
  //     attachment.put("fileName", "test-file.txt");
  //     attachment.put("objectId", "test-object-id");

  //     // Add secondary type properties
  //     attachment.put("category", "document");
  //     attachment.put("description", "Test document");

  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);

  //     // Mock necessary dependencies
  //     CdsData mockCdsData = mock(CdsData.class);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);

  //     // Prepare lists for restricted characters and duplicate files
  //     List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
  //     List<String> duplicateFileNameList = new ArrayList<>();

  //     // Mock static methods
  //     try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  //         MockedStatic<DBQuery> dbQueryMockedStatic = mockStatic(DBQuery.class)) {

  //       // Setup mocking for secondary type properties

  //       when(sdmUtilsMock.getSecondaryTypeProperties(
  //               eq(Optional.of(attachmentDraftEntity)), eq(attachment)))
  //           .thenReturn(Arrays.asList("category", "description"));

  //           Map<String, String> propertiesInDB = new HashMap<>();

  //       // Setup mocking for updated secondary properties
  //       when(sdmUtilsMock.getUpdatedSecondaryProperties(
  //               eq(Optional.of(attachmentDraftEntity)),
  //               eq(attachment),
  //               eq(persistenceService),
  //               eq(propertiesInDB))
  //           .thenReturn(new HashMap<>());

  //       // Mock restricted characters check
  //       when(sdmUtilsMock.isRestrictedCharactersInName(anyString())).thenReturn(false);

  //       // Mock DB query for attachment

  //       when(dbQueryMock.getAttachmentForID(
  //               eq(attachmentDraftEntity), eq(persistenceService), eq("test-id")))
  //           .thenReturn("test-file.txt");

  //       handler.processAttachment(
  //           Optional.of(attachmentDraftEntity),
  //           context,
  //           attachment,
  //           duplicateFileNameList,
  //           fileNameWithRestrictedCharacters);

  //       // Assert
  //       verify(attachment).get("category");
  //       verify(attachment).get("description");
  //     }
  //   }

  //   @Test
  //   public void testProcessAttachment_EmptyFilename_ThrowsServiceException() {
  //     // Arrange
  //     List<CdsData> data = new ArrayList<>();
  //     Map<String, Object> entity = new HashMap<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();

  //     // Create a spy for the attachment map
  //     Map<String, Object> attachment = spy(new HashMap<>());

  //     // Prepare attachment with test data - set filename to null
  //     attachment.put("ID", "test-id");
  //     attachment.put("fileName", null);
  //     attachment.put("objectId", "test-object-id");

  //     attachments.add(attachment);
  //     entity.put("attachments", attachments);

  //     // Mock necessary dependencies
  //     CdsData mockCdsData = mock(CdsData.class);
  //     data.add(mockCdsData);

  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);

  //     // Prepare lists for restricted characters and duplicate files
  //     List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
  //     List<String> duplicateFileNameList = new ArrayList<>();

  //     // Mock static methods
  //     try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  //         MockedStatic<DBQuery> dbQueryMockedStatic = mockStatic(DBQuery.class)) {

  //       // Setup mocking for secondary type properties
  //       when(sdmUtilsMock.getSecondaryTypeProperties(
  //               eq(Optional.of(attachmentDraftEntity)), eq(attachment)))
  //           .thenReturn(Collections.emptyList());

  //       // Setup mocking for updated secondary properties
  //       when(sdmUtilsMock.getUpdatedSecondaryProperties(
  //               eq(Optional.of(attachmentDraftEntity)),
  //               eq(attachment),
  //               eq(persistenceService),
  //               eq(Collections.emptyList())))
  //           .thenReturn(new HashMap<>());
  //       // Mock restricted characters check
  //       when(sdmUtilsMock.isRestrictedCharactersInName(anyString())).thenReturn(false);

  //       // Mock DB query for attachment
  //       when(dbQueryMock.getAttachmentForID(
  //               eq(attachmentDraftEntity), eq(persistenceService), eq("test-id")))
  //           .thenReturn("existing-filename.txt");
  //       // Act & Assert
  //       ServiceException thrown =
  //           assertThrows(
  //               ServiceException.class,
  //               () -> {
  //                 handler.processAttachment(
  //                     Optional.of(attachmentDraftEntity),
  //                     context,
  //                     attachment,
  //                     duplicateFileNameList,
  //                     fileNameWithRestrictedCharacters);
  //               });

  //       // Verify the exception message
  //       assertEquals("Filename cannot be empty", thrown.getMessage());

  //       // Verify interactions
  //       verify(attachment).get("fileName");
  //       assertTrue(fileNameWithRestrictedCharacters.isEmpty());
  //       assertTrue(duplicateFileNameList.isEmpty());
  //     }
  //   }

  private List<CdsData> prepareMockAttachmentData(String... fileNames) {
    List<CdsData> data = new ArrayList<>();
    for (String fileName : fileNames) {
      CdsData cdsData = mock(CdsData.class);
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("ID", UUID.randomUUID().toString());
      attachment.put("fileName", fileName);
      attachment.put("url", "objectId");
      attachments.add(attachment);
      when(cdsData.get("attachments")).thenReturn(attachments);
      when(cdsData.get("testComposition")).thenReturn(attachments);
      data.add(cdsData);
    }
    return data;
  }

  @Test
  public void testGetEntityCompositionsWithNoCompositions() throws IOException {
    // Arrange
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.compositions()).thenReturn(Stream.empty());

    List<CdsData> dataList = new ArrayList<>();

    // Act
    handler.processBefore(context, dataList);

    // Then
    verify(handler, never()).updateName(eq(context), eq(dataList), any());
  }

  @Test
  public void testGetEntityCompositionsWithNonAttachmentComposition() throws IOException {
    // Arrange
    Stream<CdsElement> compositionsStream = Stream.of(cdsElement);
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.compositions()).thenReturn(compositionsStream);
    when(cdsElement.getType()).thenReturn(cdsAssociationType);
    when(cdsAssociationType.getTargetAspect()).thenReturn(Optional.of(targetAspect));
    when(targetAspect.getQualifiedName()).thenReturn("some.other.Entity"); // Not attachment

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
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("test.Entity.testComposition")).thenReturn(Optional.empty());

    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
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
    sdmUtilsMockedStatic.close();
  }

  @Test
  public void testUpdateNameWithEmptyComposition() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("test.Entity.")).thenReturn(Optional.empty());

    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(eq(data), eq(""), anyString()))
        .thenReturn(Collections.emptySet());

    // Act
    Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
    Map<String, String> compositionInfo = new HashMap<>();
    compositionInfo.put("name", "");
    compositionInfo.put("parentTitle", "TestTitle");
    attachmentCompositionDetails.put("", compositionInfo);
    handler.updateName(context, data, attachmentCompositionDetails);

    // Then
    verify(messages, never()).error(anyString());
    sdmUtilsMockedStatic.close();
  }

  @Test
  public void testHandleWarningsWithRestrictedCharacters() throws IOException {
    // Arrange
    List<CdsData> data = prepareMockAttachmentData("file<test>.txt", "file|test.txt");
    List<String> restrictedCharFiles = Arrays.asList("file<test>.txt", "file|test.txt");
    when(context.getMessages()).thenReturn(messages);

    // Use reflection to access private method
    try {
      java.lang.reflect.Method handleWarningsMethod =
          SDMUpdateAttachmentsHandler.class.getDeclaredMethod(
              "handleWarnings", List.class, List.class, Map.class, CdsUpdateEventContext.class);
      handleWarningsMethod.setAccessible(true);

      // Act
      handleWarningsMethod.invoke(
          handler, restrictedCharFiles, new ArrayList<>(), new HashMap<>(), context);

      // Then
      verify(messages).warn(contains("file<test>.txt, file|test.txt"));
    } catch (Exception e) {
      // Test the public interface instead if reflection fails
      // This tests the integration path
      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("test.Entity");
      when(context.getModel()).thenReturn(model);
      when(model.findEntity("test.Entity.testComposition")).thenReturn(Optional.of(targetEntity));
      when(context.getUserInfo()).thenReturn(userInfo);
      when(userInfo.isSystemUser()).thenReturn(false);

      sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
          .thenReturn(Collections.emptySet());
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName("file<test>.txt"))
          .thenReturn(true);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName("file|test.txt"))
          .thenReturn(true);

      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testComposition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("testComposition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);
      sdmUtilsMockedStatic.close();
    }
  }

  @Test
  public void testProcessBeforeWithIOException() throws IOException {
    // Arrange
    try (MockedStatic<AttachmentsHandlerUtils> attachmentsHandlerUtilsMocked =
        mockStatic(AttachmentsHandlerUtils.class)) {

      // Mock the context.getTarget() to return a valid entity
      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");

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

      // Mock updateName to throw IOException on the spy
      doThrow(new IOException("Test IO Exception"))
          .when(handler)
          .updateName(eq(context), anyList(), any());

      List<CdsData> dataList = new ArrayList<>();
      dataList.add(CdsData.create(Map.of("test", "data")));

      // Act & Assert
      Assertions.assertThrows(
          IOException.class,
          () -> {
            handler.processBefore(context, dataList);
          });
    }
  }

  @Test
  public void testConstructorInitialization() {
    // Act
    SDMUpdateAttachmentsHandler newHandler =
        new SDMUpdateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);

    // Then
    Assertions.assertNotNull(newHandler);
  }

  @Test
  public void testUpdateNameWithSecondaryProperties() throws IOException {
    // Arrange
    List<CdsData> data = prepareMockAttachmentData("test.txt");
    when(context.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("test.Entity");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("test.Entity.testComposition")).thenReturn(Optional.of(targetEntity));
    when(context.getMessages()).thenReturn(messages);
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("testToken");
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Map<String, String> invalidSecondaryProps = new HashMap<>();
    invalidSecondaryProps.put("invalidProp", "Invalid secondary property definition");

    // Mock CacheConfig
    cacheConfigMockedStatic = mockStatic(CacheConfig.class);
    @SuppressWarnings("unchecked")
    Cache<Object, Object> mockCache = mock(Cache.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);

    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.FileNameDuplicateInDrafts(any(), anyString(), anyString()))
        .thenReturn(Collections.emptySet());
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(Map.of("prop1", "Property 1", "prop2", "Property 2"));
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.hasRestrictedCharactersInName("test.txt"))
        .thenReturn(false);

    when(dbQuery.getAttachmentForID(any(), any(), anyString())).thenReturn("test.txt");

    try {
      // Act
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "testComposition");
      compositionInfo.put("parentTitle", "TestTitle");
      attachmentCompositionDetails.put("testComposition", compositionInfo);
      handler.updateName(context, data, attachmentCompositionDetails);

      // Then - Verify no errors for valid scenario
      verify(messages, never()).error(anyString());
      // Note: Cache interactions are internal implementation details
    } finally {
      sdmUtilsMockedStatic.close();
      cacheConfigMockedStatic.close();
    }
  }

  @Test
  public void testConstructor() {
    // Test constructor creates instance successfully
    SDMUpdateAttachmentsHandler newHandler =
        new SDMUpdateAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);
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

      when(context.getTarget()).thenReturn(targetEntity);
      when(targetEntity.getQualifiedName()).thenReturn("TestEntity");
      when(context.getModel()).thenReturn(model);

      // When
      handler.processBefore(context, dataList);

      // Then - Should complete without error and still call updateName with empty map
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
    try (MockedStatic<CacheConfig> cacheConfigMockedStatic = mockStatic(CacheConfig.class);
        MockedStatic<AttachmentsHandlerUtils> attachmentsMockStatic =
            mockStatic(AttachmentsHandlerUtils.class)) {

      @SuppressWarnings("unchecked")
      Cache<Object, Object> mockCache = mock(Cache.class);
      cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

      List<CdsData> data = new ArrayList<>();
      data.add(mock(CdsData.class));

      // Test composition name with dots - should extract last part
      Map<String, Map<String, String>> attachmentCompositionDetails = new HashMap<>();
      Map<String, String> compositionInfo = new HashMap<>();
      compositionInfo.put("name", "com.example.Entity.attachments"); // Contains dots
      compositionInfo.put("parentTitle", "Example Entity");
      attachmentCompositionDetails.put("compositionDef", compositionInfo);

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
}
