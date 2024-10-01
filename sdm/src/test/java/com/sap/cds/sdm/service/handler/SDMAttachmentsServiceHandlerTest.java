package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.*;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.messages.Message;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class SDMAttachmentsServiceHandlerTest {
  @Mock private AttachmentCreateEventContext mockContext;
  @Mock private List<CdsData> mockData;
  @Mock private AuthenticationInfo mockAuthInfo;
  @Mock private JwtTokenAuthenticationInfo mockJwtTokenInfo;
  private SDMAttachmentsServiceHandler handlerSpy;
  private PersistenceService persistenceService;
  private SDMService sdmService;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    persistenceService = mock(PersistenceService.class);
    sdmService = mock(SDMServiceImpl.class);
    handlerSpy = spy(new SDMAttachmentsServiceHandler(persistenceService, sdmService));
  }

  @Test
  public void testCreateVersioned() throws IOException {
    Message mockMessage = mock(Message.class);
    Messages mockMessages = mock(Messages.class);

    when(sdmService.checkRepositoryType(anyString())).thenReturn("Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockMessages.error("Upload not supported for versioned repositories"))
        .thenReturn(mockMessage);

    handlerSpy.createAttachment(mockContext);
    verify(mockMessages).error("Upload not supported for versioned repositories");
  }

  @Test
  public void testCreateNonVersionedDuplicate() throws IOException {
    Map<String, Object> mockattachmentIds = new HashMap<>();
    mockattachmentIds.put("up__ID", "upid");
    mockattachmentIds.put("ID", "id");
    Result mockResult = mock(Result.class);
    MediaData mockMediaData = mock(MediaData.class);
    Messages mockMessages = mock(Messages.class);
    CdsEntity targetMock = mock(CdsEntity.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));
    when(mockModel.findEntity("some.qualified.Name.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntity)); // mockDraftEntity is your mock CdsEntity
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockContext.getAttachmentIds()).thenReturn(mockattachmentIds);
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(true).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));

    try (MockedStatic<DBQuery> DBQueryMockedStatic = Mockito.mockStatic(DBQuery.class)) {
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUP__ID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      handlerSpy.createAttachment(mockContext);
      verify(mockMessages).warn("This attachment already exists. Please remove it and try again");
    }
  }

  //  @Test
  //  public void testCreateNonVersionedSuccess() throws IOException {
  //    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
  //    JSONObject mockResult = new JSONObject(); // Mock result object
  //    mockResult.put("url", "url");
  //    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockResult);
  //    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderId");
  //
  //    CdsEntity targetMock = mock(CdsEntity.class);
  //    when(mockContext.getTarget()).thenReturn(targetMock);
  //    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
  //    CdsEntity mockEntity = mock(CdsEntity.class);
  //    CdsModel mockModel = mock(CdsModel.class);
  //    when(mockContext.getModel()).thenReturn(mockModel);
  //    when(mockModel.findEntity("some.qualified.Name.attachments"))
  //        .thenReturn(Optional.of(mockEntity));
  //
  //    mockData = new ArrayList<>();
  //    CdsData cdsData = mock(CdsData.class);
  //    List<Map<String, Object>> attachments = new ArrayList<>();
  //    Map<String, Object> attachment1 = new HashMap<>();
  //    Map<String, Object> attachment2 = new HashMap<>();
  //    String simulatedContent = "Sample Content"; // Example content as a string
  //    InputStream contentStream =
  //        new ByteArrayInputStream(simulatedContent.getBytes(StandardCharsets.UTF_8));
  //    attachment1.put("up__ID", "up__id");
  //    attachment1.put("fileName", "sample1.pdf");
  //    attachment1.put("ID", "id1");
  //    attachment1.put("content", contentStream);
  //    attachment2.put("up__ID", "up__id");
  //    attachment2.put("fileName", "sample2.pdf");
  //    attachment2.put("ID", "id2");
  //    attachment2.put("content", contentStream);
  //    attachments.add(attachment1);
  //    attachments.add(attachment2);
  //    when(cdsData.get("attachments")).thenReturn(attachments);
  //    mockData.add(cdsData);
  //
  //    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //        Mockito.mockStatic(TokenHandler.class)) {
  //      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
  //
  //      handlerSpy.processBefore(mockContext, mockData);
  //    }
  //  }
  //
  //  @Test
  //  public void testCreateNonVersionedDuplicate() throws IOException {
  //    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
  //    Messages mockMessages = Mockito.mock(Messages.class);
  //    Mockito.when(mockContext.getMessages()).thenReturn(mockMessages);
  //    JSONObject mockResult1 = new JSONObject(); // Mock result object
  //    JSONObject mockResult2 = new JSONObject();
  //    JSONObject mockResult3 = new JSONObject();
  //    mockResult1.put("duplicate", true);
  //    mockResult1.put("virus", false);
  //    mockResult1.put("id", "id1");
  //    mockResult1.put("failedDocument", "sample1.pdf");
  //    mockResult2.put("duplicate", false);
  //    mockResult2.put("virus", true);
  //    mockResult2.put("id", "id2");
  //    mockResult2.put("failedDocument", "sample2.pdf");
  //    mockResult3.put("fail", true);
  //    mockResult3.put("id", "id3");
  //    mockResult3.put("failedDocument", "sample3.pdf");
  //    when(sdmService.createDocument(any(), any(), any()))
  //        .thenReturn(mockResult1)
  //        .thenReturn(mockResult2)
  //        .thenReturn(mockResult3);
  //    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderId");
  //
  //    CdsEntity targetMock = mock(CdsEntity.class);
  //    when(mockContext.getTarget()).thenReturn(targetMock);
  //    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
  //    CdsEntity mockEntity = mock(CdsEntity.class);
  //    CdsModel mockModel = mock(CdsModel.class);
  //    when(mockContext.getModel()).thenReturn(mockModel);
  //    when(mockModel.findEntity("some.qualified.Name.attachments"))
  //        .thenReturn(Optional.of(mockEntity));
  //
  //    mockData = new ArrayList<>();
  //    CdsData cdsData = mock(CdsData.class);
  //    List<Map<String, Object>> attachments = new ArrayList<>();
  //    Map<String, Object> attachment1 = new HashMap<>();
  //    Map<String, Object> attachment2 = new HashMap<>();
  //    Map<String, Object> attachment3 = new HashMap<>();
  //    String simulatedContent = "Sample Content"; // Example content as a string
  //    InputStream contentStream =
  //        new ByteArrayInputStream(simulatedContent.getBytes(StandardCharsets.UTF_8));
  //    attachment1.put("up__ID", "up__id");
  //    attachment1.put("fileName", "sample1.pdf");
  //    attachment1.put("ID", "id1");
  //    attachment1.put("content", contentStream);
  //    attachment2.put("up__ID", "up__id");
  //    attachment2.put("fileName", "sample2.pdf");
  //    attachment2.put("ID", "id2");
  //    attachment2.put("content", contentStream);
  //    attachment3.put("up__ID", "up__id");
  //    attachment3.put("fileName", "sample3.pdf");
  //    attachment3.put("ID", "id3");
  //    attachment3.put("content", contentStream);
  //    attachments.add(attachment1);
  //    attachments.add(attachment2);
  //    attachments.add(attachment3);
  //    when(cdsData.get("attachments")).thenReturn(attachments);
  //    mockData.add(cdsData);
  //
  //    String expectedWarnMessage =
  //        "The following files already exist and cannot be uploaded:\n"
  //            + "• sample1.pdf\n"
  //            + "The following files contain potential malware and cannot be uploaded:\n"
  //            + "• sample2.pdf\n"
  //            + "The following files cannot be uploaded:\n"
  //            + "• sample3.pdf";
  //
  //    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //        Mockito.mockStatic(TokenHandler.class)) {
  //      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
  //      handlerSpy.processBefore(mockContext, mockData);
  //      // verify(mockMessages).warn(trim(expectedWarnMessage));
  //      ArgumentCaptor<String> warnMessageCaptor = forClass(String.class);
  //      verify(mockMessages).warn(warnMessageCaptor.capture());
  //      String actualWarnMessage = warnMessageCaptor.getValue().trim(); // Trim the actual message
  //      assertEquals(expectedWarnMessage, actualWarnMessage);
  //    }
  //  }
  //
  //  @Test
  //  public void testProcessBeforeDuplicateFiles() throws IOException {
  //    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //    Messages mockMessages = mock(Messages.class);
  //    when(mockContext.getMessages()).thenReturn(mockMessages);
  //    CdsEntity mockTarget = mock(CdsEntity.class);
  //    when(mockContext.getTarget()).thenReturn(mockTarget);
  //
  //    mockData = new ArrayList<>();
  //    CdsData cdsData = mock(CdsData.class);
  //    List<Map<String, Object>> attachments = new ArrayList<>();
  //    Map<String, Object> attachment1 = new HashMap<>();
  //    Map<String, Object> attachment2 = new HashMap<>();
  //    attachment1.put("up__ID", "up__id");
  //    attachment1.put("fileName", "sample1.pdf");
  //    attachment2.put("up__ID", "up__id");
  //    attachment2.put("fileName", "sample1.pdf");
  //    attachments.add(attachment1);
  //    attachments.add(attachment2);
  //    when(cdsData.get("attachments")).thenReturn(attachments);
  //    mockData.add(cdsData);
  //
  //    IOException exception =
  //        assertThrows(
  //            IOException.class,
  //            () -> {
  //              handlerSpy.processBefore(mockContext, mockData);
  //            });
  //    assertEquals("Duplicate files", exception.getMessage());
  //  }
}
