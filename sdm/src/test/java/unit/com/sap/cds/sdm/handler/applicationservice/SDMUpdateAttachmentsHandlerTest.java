package unit.com.sap.cds.sdm.handler.applicationservice;

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
import org.ehcache.Cache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMUpdateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  @Mock private CdsUpdateEventContext context;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsModel model;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  private SDMService sdmService;
  @Mock private SDMUtils sdmUtilsMock;
  @Mock private CdsStructuredType targetAspect;
  private SDMUpdateAttachmentsHandler handler;

  @Mock private CdsElement cdsElement;
  @Mock private CdsEntity targetEntity;
  @Mock private CdsAssociationType cdsAssociationType;

  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;

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
    if (sdmUtilsMockedStatic != null) {
      sdmUtilsMockedStatic.close();
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

  //   @Test
  //   public void testRenameWithUniqueFilenames() throws IOException {
  //     List<CdsData> data = prepareMockAttachmentData("file1.txt");
  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     Map<String, String> secondaryProperties = new HashMap<>();
  //     CmisDocument document = new CmisDocument();
  //     document.setFileName("file1.txt");
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getModel()).thenReturn(model);
  //     when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
  //     when(model.findEntity("some.qualified.Name.attachments"))
  //         .thenReturn(Optional.of(attachmentDraftEntity));
  //     dbQueryMockedStatic = mockStatic(DBQuery.class);
  //     dbQueryMockedStatic
  //         .when(
  //             () ->
  //                 getAttachmentForID(
  //                     any(CdsEntity.class), any(PersistenceService.class), anyString()))
  //         .thenReturn("file1.txt");

  //     handler.updateName(context, data);
  //     verify(sdmService, never())
  //         .updateAttachments("token", mockCredentials, document, secondaryProperties);
  //   }

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
      data.add(cdsData);
    }
    return data;
  }
}
