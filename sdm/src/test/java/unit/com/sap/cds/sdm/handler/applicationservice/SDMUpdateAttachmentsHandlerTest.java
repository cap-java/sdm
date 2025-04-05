package unit.com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.persistence.DBQuery.getAttachmentForID;
import static com.sap.cds.sdm.utilities.SDMUtils.isFileNameDuplicateInDrafts;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMUpdateAttachmentsHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.ehcache.Cache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMUpdateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  @Mock private CdsUpdateEventContext context;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsEntity cdsEntity;
  @Mock private CdsModel model;
  private SDMService sdmService;
  private SDMUpdateAttachmentsHandler handler;

  private MockedStatic<TokenHandler> tokenHandlerMockedStatic;
  private MockedStatic<DBQuery> dbQueryMockedStatic;
  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;
  private MockedStatic<CacheConfig> cacheConfigMockedStatic;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmService = mock(SDMServiceImpl.class);
    tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
    tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockCredentials);
    handler = spy(new SDMUpdateAttachmentsHandler(persistenceService, sdmService));
    cacheConfigMockedStatic = mockStatic(CacheConfig.class);
    dbQueryMockedStatic = mockStatic(DBQuery.class);
    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  }

  @AfterEach
  public void tearDown() {
    if (tokenHandlerMockedStatic != null) {
      tokenHandlerMockedStatic.close();
    }
    if (dbQueryMockedStatic != null) {
      dbQueryMockedStatic.close();
    }
    if (sdmUtilsMockedStatic != null) {
      sdmUtilsMockedStatic.close();
    }
    if (cacheConfigMockedStatic != null) {
      cacheConfigMockedStatic.close();
    }
  }

  @Test
  public void testProcessBeforeCallsRename() throws IOException {
    List<CdsData> data = new ArrayList<>();
    doNothing().when(handler).updateName(any(CdsUpdateEventContext.class), anyList());
    handler.processBefore(context, data);
    verify(handler, times(1)).updateName(context, data);
  }

  @Test
  public void testRenameWithDuplicateFilenames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    Set<String> duplicateFilenames = new HashSet<>(Arrays.asList("file1.txt", "file2.txt"));
    when(context.getMessages()).thenReturn(messages);
    sdmUtilsMockedStatic
        .when(() -> isFileNameDuplicateInDrafts(data))
        .thenReturn(duplicateFilenames);

    handler.updateName(context, data);

    verify(messages, times(1))
        .error(
            "The file(s) file1.txt, file2.txt have been added multiple times. Please rename and try again.");
  }

  @Test
  public void testRenameWithUniqueFilenames() throws IOException {
    List<CdsData> data = prepareMockAttachmentData("file1.txt");
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    Map<String, String> secondaryProperties = new HashMap<>();
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal
    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file1.txt");

    handler.updateName(context, data);
    verify(sdmService, never())
        .updateAttachments("token", mockCredentials, document, secondaryProperties);
  }

  @Test
  public void testRenameWithConflictResponseCode() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

    when(sdmService.updateAttachments("jwtToken", mockCredentials, document, secondaryProperties))
        .thenReturn(409); // Mock conflict response code

    // Mock the returned messages
    when(context.getMessages()).thenReturn(messages);

    // Execute the method under test
    handler.updateName(context, data);
    // Verify the attachment's file name was attempted to be replaced with "file-sdm.txt"
    verify(attachment).put("fileName", "file1.txt");

    // Verify that a warning message was added to the context
    verify(messages, times(1))
        .warn("The following files could not be renamed as they already exist:\nfile1.txt\n");
  }

  @Test
  public void testRenameWithNoSDMRoles() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

    when(sdmService.updateAttachments("jwtToken", mockCredentials, document, secondaryProperties))
        .thenReturn(403); // Mock conflict response code

    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              handler.updateName(context, data);
            });

    assertEquals(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG, exception.getMessage());
  }

  @Test
  public void testRenameWith500Error() throws IOException {
    List<CdsData> data = prepareMockAttachmentData("file1");
    Map<String, Object> entity = new HashMap<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("id", "id");
    attachment.put("fileName", "file1");
    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock duplicate filename check
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data))
        .thenReturn(new HashSet<>());

    // Mock secondary properties
    List<String> secondaryTypeProperties = Arrays.asList("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(Optional.of(cdsEntity), attachment))
        .thenReturn(secondaryTypeProperties);
    // Mock properties in DB
    Map<String, String> propertiesInDB = new HashMap<>();
    propertiesInDB.put("property1", "value1");
    dbQueryMockedStatic
        .when(
            () ->
                DBQuery.getPropertiesForID(
                    cdsEntity, persistenceService, "id", secondaryTypeProperties))
        .thenReturn(propertiesInDB);
    // Mock updateSecondaryProperties
    Map<String, String> updatedSecondaryProperties = new HashMap<>();
    updatedSecondaryProperties.put("filename", "file1");
    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.getUpdatedSecondaryProperties(
                    entity, secondaryTypeProperties, propertiesInDB))
        .thenReturn(updatedSecondaryProperties);
    String fileNameInDb = "fileNameInDb";
    dbQueryMockedStatic
        .when(() -> DBQuery.getAttachmentForID(cdsEntity, persistenceService, "id"))
        .thenReturn(fileNameInDb);
    // Mock restricted character in name
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isRestrictedCharactersInName("file1"))
        .thenReturn(false);

    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName("file1");
    // Mock response code
    when(sdmService.updateAttachments(
            "jwtToken", mockCredentials, cmisDocument, updatedSecondaryProperties))
        .thenReturn(500);

    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              handler.updateName(context, data);
            });
    assertEquals(SDMConstants.SDM_ROLES_ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  public void testRenameWith200ResponseCode() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

    when(sdmService.updateAttachments("jwtToken", mockCredentials, document, secondaryProperties))
        .thenReturn(200);

    // Execute the method under test
    handler.updateName(context, data);

    verify(attachment, never()).replace("fileName", "file-sdm.txt");

    // Verify that a warning message was added to the context
    verify(messages, times(0))
        .warn("The following files could not be renamed as they already exist:\nfile1.txt\n");
  }

  @Test
  public void testRenameWithoutFileInSDM() throws IOException {
    // Mocking the necessary objects
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");

    // Simulating the scenario where the attachment is not found in the database
    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file1.txt"); // Return the same file name to simulate unchanged state

    // Verify that updateAttachments is never called
    verify(sdmService, never())
        .updateAttachments("jwtToken", mockCredentials, document, secondaryProperties);
  }

  @Test
  public void testRenameWithNoAttachments() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    Map<String, String> secondaryProperties = new HashMap<>();
    CmisDocument document = new CmisDocument();
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(null);
    data.add(mockCdsData);

    handler.updateName(context, data);

    verify(sdmService, never())
        .updateAttachments("jwtToken", mockCredentials, document, secondaryProperties);
  }

  @Test
  public void testRenameWithRestrictedFilenames() throws IOException {
    List<CdsData> data = prepareMockAttachmentData("file1.txt", "file2/abc.txt", "file3\\abc.txt");
    Map<String, String> secondaryProperties = new HashMap<>();
    secondaryProperties.put("filename", "file1.txt");
    CmisDocument document = new CmisDocument();
    document.setFileName("file1.txt");
    List<String> fileNameWithRestrictedChars = new ArrayList<>();
    fileNameWithRestrictedChars.add("file2/abc.txt");
    fileNameWithRestrictedChars.add("file3\\abc.txt");

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    when(context.getMessages()).thenReturn(messages);

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
        .thenAnswer(
            invocation -> {
              String filename = invocation.getArgument(0);
              return filename.contains("/") || filename.contains("\\");
            });

    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal

    when(sdmService.updateAttachments("jwtToken", mockCredentials, document, secondaryProperties))
        .thenReturn(409); // Mock conflict response code

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file-in-sdm.txt");

    handler.updateName(context, data);

    verify(messages, times(1))
        .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename"));

    verify(messages, never()).error(anyString());
  }

  @Test
  public void testRenameWithValidRestrictedNames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    List<String> fileNameWithRestrictedChars = new ArrayList<>();
    fileNameWithRestrictedChars.add("file2/abc.txt");
    attachment.put("fileName", "file2/abc.txt");
    attachment.put("objectId", "objectId-123");
    attachment.put("ID", "id-123");
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));

    when(context.getMessages()).thenReturn(messages);

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
        .thenAnswer(
            invocation -> {
              String filename = invocation.getArgument(0);
              return filename.contains("/") || filename.contains("\\");
            });

    // Mock cache
    Cache mockCache = mock(Cache.class);
    SecondaryPropertiesKey mockSecondaryPropertiesKey = mock(SecondaryPropertiesKey.class);
    cacheConfigMockedStatic
        .when(() -> CacheConfig.getSecondaryPropertiesCache())
        .thenReturn(mockCache);
    mockSecondaryPropertiesKey.setRepositoryId(SDMConstants.REPOSITORY_ID);
    mockCache.remove(mockSecondaryPropertiesKey); // Simulating the cache removal

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file3/abc.txt");

    // Call the method under test
    handler.updateName(context, data);

    // Verify the attachment's file name was replaced with the name in SDM
    // Now use `put` to verify the change was made instead of `replace`
    verify(attachment).put("fileName", "file2/abc.txt");

    // Verify that a warning message is correct
    verify(messages, times(1))
        .warn(
            String.format(
                SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename")));
  }

  @Test
  public void testProcessAttachment_EmptyFilename_ThrowsServiceException() {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, String> propertiesInDB = spy(new HashMap<>());
    List<String> secondaryTypeProperties = new ArrayList<>();

    // Create a spy for the attachment map
    Map<String, Object> attachment = spy(new HashMap<>());

    // Prepare attachment with test data - set filename to null
    attachment.put("ID", "test-id");
    attachment.put("fileName", null);
    attachment.put("objectId", "test-object-id");

    attachments.add(attachment);
    entity.put("attachments", attachments);

    // Mock necessary dependencies
    CdsData mockCdsData = mock(CdsData.class);
    data.add(mockCdsData);

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);

    // Prepare lists for restricted characters and duplicate files
    List<String> fileNameWithRestrictedCharacters = new ArrayList<>();
    List<String> duplicateFileNameList = new ArrayList<>();
    List<String> filesNotFound = new ArrayList<>();
    List<String> filesWithUnsupportedProperties = new ArrayList<>();
    Map<String, String> badRequest = new HashMap<>();

    // Setup mocking for secondary type properties
    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.getSecondaryTypeProperties(
                    eq(Optional.of(attachmentDraftEntity)), eq(attachment)))
        .thenReturn(Collections.emptyList());

    // Setup mocking for updated secondary properties
    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.getUpdatedSecondaryProperties(
                    attachment, secondaryTypeProperties, propertiesInDB))
        .thenReturn(new HashMap<>());
    // Mock restricted characters check
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
        .thenReturn(false);
    // Mock DB query for attachment
    dbQueryMockedStatic
        .when(
            () -> DBQuery.getAttachmentForID(attachmentDraftEntity, persistenceService, "test-id"))
        .thenReturn("existing-filename.txt");
    // Act & Assert
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handler.processAttachment(
                  Optional.of(attachmentDraftEntity),
                  context,
                  attachment,
                  duplicateFileNameList,
                  fileNameWithRestrictedCharacters,
                  filesNotFound,
                  filesWithUnsupportedProperties,
                  badRequest);
            });

    // Verify the exception message
    assertEquals("Filename cannot be empty", thrown.getMessage());

    // Verify interactions
    verify(attachment).get("fileName");
    assertTrue(fileNameWithRestrictedCharacters.isEmpty());
    assertTrue(duplicateFileNameList.isEmpty());
  }

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
