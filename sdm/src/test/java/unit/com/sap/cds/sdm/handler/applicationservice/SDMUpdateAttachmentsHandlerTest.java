package unit.com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.persistence.DBQuery.getAttachmentForID;
import static com.sap.cds.sdm.utilities.SDMUtils.isFileNameDuplicateInDrafts;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMUpdateAttachmentsHandler;
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
import java.io.IOException;
import java.util.*;
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
  @Mock private Result result;
  @Mock private CdsEntity cdsEntity;
  @Mock private CdsModel model;
  private SDMService sdmService;

  private SDMUpdateAttachmentsHandler handler;

  private MockedStatic<TokenHandler> tokenHandlerMockedStatic;
  private MockedStatic<DBQuery> dbQueryMockedStatic;
  private MockedStatic<SDMUtils> sdmUtilsMockedStatic;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sdmService = mock(SDMServiceImpl.class);
    tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
    tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockCredentials);
    handler = spy(new SDMUpdateAttachmentsHandler(persistenceService, sdmService));
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
    sdmUtilsMockedStatic = mockStatic(SDMUtils.class);
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
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    dbQueryMockedStatic = mockStatic(DBQuery.class);
    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file1.txt");

    handler.updateName(context, data);
    verify(sdmService, never())
        .renameAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class));
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

    // Mock the SDM service responses
    dbQueryMockedStatic = mockStatic(DBQuery.class);
    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

    when(sdmService.renameAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class)))
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

    // Mock the SDM service responses
    dbQueryMockedStatic = mockStatic(DBQuery.class);
    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn("file123.txt"); // Mock a different file name in SDM to trigger renaming

    when(sdmService.renameAttachments(
            anyString(), any(SDMCredentials.class), any(CmisDocument.class)))
        .thenReturn(200); // Mock conflict response code

    // Execute the method under test
    handler.updateName(context, data);

    verify(attachment, never()).replace("fileName", "file-sdm.txt");

    // Verify that a warning message was added to the context
    verify(messages, times(0))
        .warn("The following files could not be renamed as they already exist:\nfile1.txt\n");
  }

  @Test
  public void testRenameWithoutFileInSDM() throws IOException {
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    when(context.getTarget()).thenReturn(attachmentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(attachmentDraftEntity.getQualifiedName()).thenReturn("some.qualified.Name");
    when(model.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");

    List<CdsData> data = prepareMockAttachmentData("file1.txt");

    dbQueryMockedStatic = mockStatic(DBQuery.class);

    dbQueryMockedStatic
        .when(
            () ->
                getAttachmentForID(
                    any(CdsEntity.class), any(PersistenceService.class), anyString()))
        .thenReturn(null);

    handler.updateName(context, data);
    verify(sdmService, never())
        .renameAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class));
  }

  @Test
  public void testRenameWithNoAttachments() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
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
        .renameAttachments(anyString(), any(SDMCredentials.class), any(CmisDocument.class));
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
