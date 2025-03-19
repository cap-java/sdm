package unit.com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.utilities.SDMUtils.isFileNameDuplicateInDrafts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateAttachmentsHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class SDMCreateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  @Mock private CdsCreateEventContext context;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;
  @Mock private CdsModel model;
  private SDMService sdmService;

  private SDMCreateAttachmentsHandler handler; // Use Spy to allow partial mocking

  private MockedStatic<TokenHandler> tokenHandlerMockedStatic;
  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    // Set up static mocking for `TokenHandler.getSDMCredentials`
    sdmService = mock(SDMServiceImpl.class);
    tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
    tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockCredentials);
    handler = spy(new SDMCreateAttachmentsHandler(persistenceService, sdmService));
    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  }

  @AfterEach
  public void tearDown() {
    if (tokenHandlerMockedStatic != null) {
      tokenHandlerMockedStatic.close();
    }
    if (sdmUtilsMockedStatic != null) {
      sdmUtilsMockedStatic.close();
    }
  }

  @Test
  public void testProcessBefore() throws IOException {
    List<CdsData> data = new ArrayList<>();
    doNothing().when(handler).updateName(any(CdsCreateEventContext.class), anyList());

    handler.processBefore(context, data);

    verify(handler, times(1)).updateName(context, data);
  }

  @Test
  public void testRenameWithDuplicateFilenames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    Set<String> duplicateFilenames = new HashSet<>(Arrays.asList("file1.txt", "file2.txt"));
    when(context.getMessages()).thenReturn(messages);
    // sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
    sdmUtilsMockedStatic
        .when(() -> isFileNameDuplicateInDrafts(data))
        .thenReturn(duplicateFilenames);

    handler.updateName(context, data);

    verify(messages, times(1))
        .error(
            "The file(s) file1.txt, file2.txt have been added multiple times. Please rename and try again.");
  }

  @Test
  public void testRenameWithNoDuplicateFilenames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    handler.updateName(context, data);

    verify(messages, never()).error(anyString());
  }

  @Test
  public void testRenameWithNoAttachments() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(null);
    data.add(mockCdsData);

    handler.updateName(context, data);

    verify(sdmService, never())
        .updateAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class), any());
  }

  @Test
  public void testRenameWithoutFileInSDM() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachments.add(attachment);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");
    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn(null); // Mock with same file name in SDM to not trigger renaming

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    handler.updateName(context, data);

    verify(sdmService, never())
        .updateAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class), any());
  }

  @Test
  public void testRenameWithSameFileNameInSDM() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachments.add(attachment);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");
    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn("file1.txt"); // Mock with same file name in SDM to not trigger renaming

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    handler.updateName(context, data);

    verify(sdmService, never())
        .updateAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class), any());
  }

  @Test
  public void testRenameWithConflictResponseCode() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn("file-sdm.txt"); // Mock a different file name in SDM to trigger renaming
    when(sdmService.updateAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class), any()))
        .thenReturn(409); // Mock conflict response code

    // Mock the returned messages
    when(context.getMessages()).thenReturn(messages);

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Execute the method under test
    handler.updateName(context, data);

    // Verify the attachment's file name was attempted to be replaced with "file-sdm.txt"
    verify(attachment).replace("fileName", "file-sdm.txt");

    // Verify that a warning message was added to the context
    verify(messages, times(1))
        .warn("The following files could not be renamed as they already exist:\nfile1.txt\n");
  }

  @Test
  public void testCreateAttachmentWithNoSDMRoles() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn("file-sdm.txt"); // Mock a different file name in SDM to trigger renaming
    when(sdmService.updateAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class), any()))
        .thenReturn(403); // Mock conflict response code

    when(sdmService.updateAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class), any()))
        .thenReturn(403); // Mock conflict response code

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              handler.updateName(context, data);
            });

    assertEquals(SDMConstants.SDM_MISSING_ROLES_EXCEPTION_MSG, exception.getMessage());
  }

  @Test
  public void testCreateAttachmentWith500Error() throws IOException {
    // Mock the data structure to simulate the attachments
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn("file-sdm.txt"); // Mock a different file name in SDM to trigger renaming
    when(sdmService.updateAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class), any()))
        .thenReturn(500); // Mock conflict response code

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

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
    System.out.println("testRenameWithConflictResponseCode");
    List<CdsData> data = new ArrayList<>();
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    entity.put("attachments", attachments);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Mock the authentication context
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    // Mock the static TokenHandler
    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);

    // Mock the SDM service responses
    when(sdmService.getObject(any(), any(), any()))
        .thenReturn("file-sdm.txt"); // Mock a different file name in SDM to trigger renaming
    when(sdmService.updateAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class), any()))
        .thenReturn(200); // Mock conflict response code

    // Mock the returned messages
    when(context.getMessages()).thenReturn(messages);

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Execute the method under test
    handler.updateName(context, data);

    verify(attachment, never()).replace("fileName", "file-sdm.txt");

    // Verify that a warning message was added to the context
    verify(messages, times(0))
        .warn("The following files could not be renamed as they already exist:\nfile1.txt\n");
  }

  //   @Test
  //   public void testRenameWithRestrictedCharacters() throws IOException {
  //     // Prepare the test data with restricted characters in filenames
  //     List<CdsData> data = prepareMockAttachmentData("file1.txt", "file/2.txt", "file\\3.txt");
  //     List<String> fileNameWithRestrictedChars = new ArrayList<>();
  //     List<Map<String, Object>> attachments = new ArrayList<>();
  //     fileNameWithRestrictedChars.add("file/2.txt");
  //     fileNameWithRestrictedChars.add("file\\3.txt");

  //     // Mock the CdsEntity and setup context
  //     CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
  //     when(context.getTarget()).thenReturn(attachmentDraftEntity);
  //     when(context.getAuthenticationInfo()).thenReturn(authInfo);
  //     when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
  //     when(jwtTokenInfo.getToken()).thenReturn("jwtToken");
  //     when(context.getMessages()).thenReturn(messages);

  //     // Mock SDMUtils to simulate restricted characters
  // //    MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenAnswer(
  //             invocation -> {
  //               String filename = invocation.getArgument(0);
  //               return filename.contains("/") || filename.contains("\\");
  //             });

  //     // Mock the SDM service object retrieval
  //     when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("file-in-sdm");

  //     // Ensure renameAttachments behaves as expected
  //     when(sdmService.renameAttachments(anyString(), any(), any(CmisDocument.class), any()))
  //         .thenReturn(200); // or a desired response code

  //     // Mock the context.getModel().getEntity() method
  //     CdsEntity mockedEntity = mock(CdsEntity.class);
  //     when(context.getModel()).thenReturn(model);
  //     when(model.getEntity("file1.txt")).thenReturn(mockedEntity);  // Mocking the entity

  //     // Mock SDMUtils static methods
  //       List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
  //       sdmUtilsMockedStatic.when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
  //               .thenReturn(mockedSecondaryTypeProperties);

  //       Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
  //       sdmUtilsMockedStatic.when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(),
  // any(), any()))
  //               .thenReturn(mockedUpdatedSecondaryProperties);

  //     // Act
  //     handler.updateName(context, data);

  //     // Verify warning message about restricted characters
  //     verify(messages, times(1))
  //         .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename"));

  //     // Verify the filenames with restricted characters are replaced in attachments
  //     for (CdsData cdsData : data) {
  //       List<Map<String, Object>> attachments =
  //           (List<Map<String, Object>>) cdsData.get("attachments");
  //       for (Map<String, Object> attachment : attachments) {
  //         String filename = (String) attachment.get("fileName");
  //         if (filename.equals("file/2.txt") || filename.equals("file\\3.txt")) {
  //           // Ensure the filename is replaced
  //           verify(attachment).replace("fileName", "file-in-sdm");
  //         }
  //       }
  //     }

  //     // Close the mocked static method
  //     sdmUtilsMockedStatic.close();
  //   }

  @Test
  public void testWarnOnRestrictedCharacters() throws IOException {
    // Prepare the sample data with restricted characters
    List<CdsData> data = prepareMockAttachmentData("file1.txt", "file/2.txt", "file3\\abc.txt");
    List<String> fileNameWithRestrictedChars = new ArrayList<>();
    fileNameWithRestrictedChars.add("file/2.txt");
    fileNameWithRestrictedChars.add("file3\\abc.txt");
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = spy(new HashMap<>());
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachment.put("ID", "test-id"); // assuming there's an ID field
    attachments.add(attachment);
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Mock context and related authentication methods
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    when(TokenHandler.getSDMCredentials()).thenReturn(mockCredentials);
    when(sdmService.getObject(anyString(), anyString(), any())).thenReturn("file-in-sdm");

    // Mock message handling
    when(context.getMessages()).thenReturn(messages);

    // Mock SDMUtils restricted character check
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
        .thenAnswer(
            invocation -> {
              String filename = invocation.getArgument(0);
              return filename.contains("/") || filename.contains("\\");
            });

    // Mock renameAttachments implementation to avoid ServiceExceptions for testing
    when(sdmService.updateAttachments(any(String.class), any(), any(CmisDocument.class), any()))
        .thenReturn(200); // assuming successful rename

    // Mock the context.getModel().getEntity() method
    CdsEntity mockedEntity = mock(CdsEntity.class);
    when(context.getModel()).thenReturn(model);
    when(model.getEntity("file1.txt")).thenReturn(mockedEntity); // Mocking the entity

    // Mock SDMUtils static methods
    List<String> mockedSecondaryTypeProperties = List.of("property1", "property2");
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getSecondaryTypeProperties(any(), any()))
        .thenReturn(mockedSecondaryTypeProperties);

    Map<String, String> mockedUpdatedSecondaryProperties = new HashMap<>();
    sdmUtilsMockedStatic
        .when(() -> SDMUtils.getUpdatedSecondaryProperties(any(), any(), any(), any()))
        .thenReturn(mockedUpdatedSecondaryProperties);

    // Mock the attachment data (fileName, objectId)
    attachment.put("fileName", "file1.txt");
    attachment.put("objectId", "objectId123");

    // Preparing the mock CdsData
    attachments.add(attachment);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    // Act by invoking the handler updateName method with the context and data
    handler.updateName(context, data);

    // Verify the warning for restricted filenames is correctly handled
    verify(messages, times(1))
        .warn(SDMConstants.nameConstraintMessage(fileNameWithRestrictedChars, "Rename"));

    // Ensure no error messages are appearing unexpectedly
    verify(messages, never()).error(anyString());
  }

  private List<CdsData> prepareMockAttachmentData(String... fileNames) {
    List<CdsData> data = new ArrayList<>();
    for (String fileName : fileNames) {
      CdsData cdsData = mock(CdsData.class);
      List<Map<String, Object>> attachments = new ArrayList<>();
      Map<String, Object> attachment = new HashMap<>();
      attachment.put("ID", UUID.randomUUID().toString());
      attachment.put("fileName", fileName);
      attachment.put("objectId", "objectId-" + UUID.randomUUID());
      attachments.add(attachment);
      when(cdsData.get("attachments")).thenReturn(attachments);
      data.add(cdsData);
    }
    return data;
  }
}
