package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateAttachmentsHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
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
  @Mock private SDMService sdmService;
  @Mock private CdsCreateEventContext context;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsModel model;
  private MockedStatic<CacheConfig> cacheConfigMockedStatic;
  private Cache<Object, Object> mockCache;
  private SDMCreateAttachmentsHandler handler;
  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;
  @Mock private CdsElement cdsElement;
  @Mock private CdsAssociationType cdsAssociationType;
  @Mock private CdsStructuredType targetAspect;
  @Mock private TokenHandler tokenHandler;
  @Mock private DBQuery dbQuery;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
    cacheConfigMockedStatic = mockStatic(CacheConfig.class);
    mockCache = mock(Cache.class);
    cacheConfigMockedStatic.when(CacheConfig::getSecondaryPropertiesCache).thenReturn(mockCache);

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
    if (sdmUtilsMockedStatic != null) {
      sdmUtilsMockedStatic.close();
    }
    if (cacheConfigMockedStatic != null) {
      cacheConfigMockedStatic.close();
    }
  }

  @Test
  public void testProcessBefore() throws IOException {
    // Arrange the mock compositions scenario
    List<String> expectedCompositionNames = Arrays.asList("Name1", "Name2");

    // Create a Stream of mocked CdsElement instances
    Stream<CdsElement> compositionsStream = Stream.of(cdsElement, cdsElement);

    when(context.getTarget().compositions()).thenReturn(compositionsStream);
    when(cdsElement.getType()).thenReturn(cdsAssociationType);
    when(cdsAssociationType.getTargetAspect()).thenReturn(Optional.of(targetAspect));
    when(cdsAssociationType.getTargetAspect().get().getQualifiedName())
        .thenReturn("sap.attachments.Attachments");
    when(cdsElement.getName()).thenReturn("Name1").thenReturn("Name2");

    List<CdsData> dataList = new ArrayList<>();

    // Act
    handler.processBefore(context, dataList);

    // Assert that updateName was called with the compositions detected
    for (String compositionName : expectedCompositionNames) {
      verify(handler).updateName(context, dataList, compositionName);
    }
  }

  @Test
  public void testUpdateNameWithDuplicateFilenames() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    Set<String> duplicateFilenames = new HashSet<>(Arrays.asList("file1.txt", "file2.txt"));
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data, "composition"))
        .thenReturn(duplicateFilenames);

    // Act
    handler.updateName(context, data, "composition");

    // Assert
    verify(messages, times(1))
        .error(
            "The file(s) file1.txt, file2.txt have been added multiple times. Please rename and try again.");
  }

  @Test
  public void testUpdateNameWithEmptyData() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data, "composition"))
        .thenReturn(Collections.emptySet());

    // Act
    handler.updateName(context, data, "");

    // Assert
    verify(messages, never()).error(anyString());
    verify(messages, never()).warn(anyString());
  }

  @Test
  public void testUpdateNameWithNoAttachments() throws IOException {
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
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data, "composition"))
        .thenReturn(Collections.emptySet());

    // Act
    handler.updateName(context, data, "");

    // Assert that no updateAttachments calls were made, as there are no attachments
    verify(sdmService, never()).updateAttachments(any(), any(), any(), any(), anyBoolean());

    // Assert that no error or warning messages were logged
    verify(messages, never()).error(anyString());
    verify(messages, never()).warn(anyString());
  }

  @Test
  public void testRenameWithWhitespaceFilename() throws IOException {
    // Arrange
    List<CdsData> data = new ArrayList<>();

    // Create entity with attachment that has whitespace-only filename
    Map<String, Object> entity = new HashMap<>();

    // Create attachments list with whitespace-only filename
    List<Map<String, Object>> attachments = new ArrayList<>();

    // Attachment with whitespace-only filename
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("ID", "id1");
    attachment.put("fileName", "   "); // Only whitespace
    attachment.put("objectId", "objId1");
    attachments.add(attachment);

    // Add attachments to entity
    entity.put("attachments", attachments);

    // Wrap entity in CdsData and add to data list
    CdsData cdsDataEntity = CdsData.create(entity);
    data.add(cdsDataEntity);

    // Mock the attachment entity
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentEntity));

    // Mock utility methods
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data, "attachments"))
        .thenReturn(Collections.emptySet());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getPropertyTitles(any(Optional.class), any(Map.class)))
        .thenReturn(new HashMap<>());

    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.getSecondaryPropertiesWithInvalidDefinition(
                    any(Optional.class), any(Map.class)))
        .thenReturn(new HashMap<>());

    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(Optional.class), any(Map.class)))
        .thenReturn(new HashMap<>());

    sdmUtilsMockedStatic
        .when(
            () ->
                SDMUtils.getUpdatedSecondaryProperties(
                    any(Optional.class), any(Map.class), any(), any(Map.class), any(Map.class)))
        .thenReturn(new HashMap<>());

    // Mock that the whitespace filename does NOT have restricted characters
    sdmUtilsMockedStatic.when(() -> SDMUtils.isRestrictedCharactersInName(" ")).thenReturn(false);

    // Mock database queries
    when(dbQuery.getAttachmentForID(any(CdsEntity.class), any(PersistenceService.class), eq("id1")))
        .thenReturn("existingFile.txt"); // Existing filename in DB

    // Mock properties query to return empty map
    Map<String, String> emptyProperties = new HashMap<>();
    when(dbQuery.getPropertiesForID(
            any(CdsEntity.class), any(PersistenceService.class), eq("id1"), any(Map.class)))
        .thenReturn(emptyProperties);

    // Mock SDM service calls - CRITICAL: Prevent any exceptions
    when(tokenHandler.getSDMCredentials()).thenReturn(mockCredentials);
    when(sdmService.getObject("objId1", mockCredentials, false)).thenReturn("originalFile.txt");

    // IMPORTANT: Mock the updateAttachments to return success to avoid permission
    // errors
    when(sdmService.updateAttachments(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(200); // Return HTTP 200 success

    // Mock UserInfo
    UserInfo userInfo = mock(UserInfo.class);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Act
    handler.updateName(context, data, "attachments");

    // Assert
    // Verify that the warning message for whitespace filename was called
    // Check if the exact warning message matches what's in SDMConstants
    verify(messages, times(1)).warn(anyString());

    // Verify that the attachment filename was reverted to the DB value
    assertEquals("existingFile.txt", attachment.get("fileName"));

    // Verify no error messages were called (only warnings)
    verify(messages, never()).error(anyString());

    // Verify that SDM service updateAttachments was called (since whitespace
    // doesn't prevent the call)
    // The key is that it should succeed (return 200) instead of throwing exceptions
    verify(sdmService, times(1)).updateAttachments(any(), any(), any(), any(), anyBoolean());
  }

  //   @Test
  //   public void testUpdateNameWithRestrictedCharacters() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isFileNameDuplicateInDrafts(anyList()))
  //         .thenReturn(Collections.emptySet());

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName("file/1.txt"))
  //         .thenReturn(true);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName("file2.txt"))
  //         .thenReturn(false);

  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //         .thenReturn(Collections.emptyList());

  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentForID(any(), any(), anyString()))
  //         .thenReturn("fileInDB.txt");

  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("fileInSDM.txt");

  //     // Act
  //     handler.updateName(context, data);

  //     // Assert
  //     verify(messages, times(1)).warn(anyString());
  //   }

  //   @Test
  //   public void testUpdateNameWithSDMConflict() throws IOException {
  //     // Arrange
  //     List<CdsData> data = createTestData();
  //     Map<String, Object> attachment =
  //         ((List<Map<String, Object>>) ((Map<String, Object>)
  // data.get(0)).get("attachments")).get(0);

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
  //     when(sdmService.updateAttachments(anyString(), any(), any(), any())).thenReturn(409);

  //     // Act
  //     handler.updateName(context, data);

  //     // Assert
  //     verify(attachment).replace(eq("fileName"), eq("fileInSDM.txt"));
  //     verify(messages, times(1)).warn(anyString());
  //   }

  //   @Test
  //   public void testUpdateNameWithSDMMissingRoles() throws IOException {
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
  public void testUpdateNameWithEmptyFilename() throws IOException {
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
        .when(() -> SDMUtils.isFileNameDuplicateInDrafts(data, "composition"))
        .thenReturn(new HashSet<>());

    // Mock attachment entity
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);

    // Mock findEntity to return an optional containing attachmentDraftEntity
    when(model.findEntity("some.qualified.Name" + "." + "composition"))
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
                SDMUtils.getSecondaryTypeProperties(Optional.of(attachmentDraftEntity), attachment))
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
        .when(() -> SDMUtils.isRestrictedCharactersInName("fileNameInRequest"))
        .thenReturn(false);

    when(dbQuery.getAttachmentForID(attachmentDraftEntity, persistenceService, "test-id"))
        .thenReturn(null);

    // When getPropertiesForID is called
    when(dbQuery.getPropertiesForID(
            attachmentDraftEntity, persistenceService, "test-id", secondaryTypeProperties))
        .thenReturn(updatedSecondaryProperties);

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class, () -> handler.updateName(context, data, "composition"));

    // Assert that the correct exception message is returned
    assertEquals("Filename cannot be empty", exception.getMessage());
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
}
