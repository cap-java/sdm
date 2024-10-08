package com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ThreadDataStorageReader;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEventFactory;
import com.sap.cds.feature.attachments.handler.common.AttachmentsReader;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class SDMUpdateEventHandlerTest {
  @Mock private CdsUpdateEventContext mockContext;
  @Mock private List<CdsData> mockData;
  @Mock private AuthenticationInfo mockAuthInfo;
  @Mock private JwtTokenAuthenticationInfo mockJwtTokenInfo;
  private SDMUpdateEventHandler handlerSpy;
  private ModifyAttachmentEventFactory eventFactory;
  private AttachmentsReader attachmentsReader;
  private CdsUpdateEventContext updateContext;
  private ThreadDataStorageReader storageReader;
  private PersistenceService persistenceService;
  private SDMService sdmService;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this); // Initialize mocks
    eventFactory = mock(ModifyAttachmentEventFactory.class);
    storageReader = mock(ThreadDataStorageReader.class);
    persistenceService = mock(PersistenceService.class);
    attachmentsReader = mock(AttachmentsReader.class);
    sdmService = mock(SDMServiceImpl.class);
    handlerSpy =
        spy(
            new SDMUpdateEventHandler(
                eventFactory, attachmentsReader, storageReader, persistenceService, sdmService));
  }

  //    @Test
  //    public void testCreateVersioned() throws IOException {
  //        when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //        when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //        when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //        when(sdmService.checkRepositoryType(anyString())).thenReturn("Versioned");
  //        Messages mockMessages = mock(Messages.class);
  //        when(mockContext.getMessages()).thenReturn(mockMessages);
  //        Message mockMessage = mock(Message.class);
  //        when(mockMessages.error("Upload not supported for versioned
  // repositories")).thenReturn(mockMessage);
  //
  //        mockData = new ArrayList<>();
  //        CdsData cdsData = mock(CdsData.class);
  //        List<Map<String, Object>> attachments = new ArrayList<>();
  //        Map<String, Object> attachment1 = new HashMap<>();
  //        Map<String, Object> attachment2 = new HashMap<>();
  //        attachment1.put("up__ID", "up__id");
  //        attachment1.put("fileName", "sample1.pdf");
  //        attachment2.put("up__ID", "up__id");
  //        attachment2.put("fileName", "sample2.pdf");
  //        attachments.add(attachment1);
  //        attachments.add(attachment2);
  //        when(cdsData.get("attachments")).thenReturn(attachments);
  //        mockData.add(cdsData);
  //
  //        handlerSpy.processBefore(mockContext, mockData);
  //        verify(mockMessages).error("Upload not supported for versioned repositories");
  //    }

  @Test
  public void testUpdateNonVersionedSuccess() throws IOException {
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    JSONObject mockResult = new JSONObject(); // Mock result object
    mockResult.put("url", "url");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockResult);
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderId");

    CdsEntity targetMock = mock(CdsEntity.class);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));

    mockData = new ArrayList<>();
    CdsData cdsData = mock(CdsData.class);
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    Map<String, Object> attachment2 = new HashMap<>();
    String simulatedContent = "Sample Content"; // Example content as a string
    InputStream contentStream =
        new ByteArrayInputStream(simulatedContent.getBytes(StandardCharsets.UTF_8));
    attachment1.put("up__ID", "up__id");
    attachment1.put("fileName", "sample1.pdf");
    attachment1.put("ID", "id1");
    attachment1.put("content", contentStream);
    attachment2.put("up__ID", "up__id");
    attachment2.put("fileName", "sample2.pdf");
    attachment2.put("ID", "id2");
    attachment2.put("content", contentStream);
    attachments.add(attachment1);
    attachments.add(attachment2);
    when(cdsData.get("attachments")).thenReturn(attachments);
    mockData.add(cdsData);

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

      handlerSpy.processBefore(mockContext, mockData);
    }
  }

  @Test
  public void testUpdateNonVersionedDuplicate() throws IOException {
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    Messages mockMessages = Mockito.mock(Messages.class);
    Mockito.when(mockContext.getMessages()).thenReturn(mockMessages);
    JSONObject mockResult1 = new JSONObject(); // Mock result object
    JSONObject mockResult2 = new JSONObject();
    JSONObject mockResult3 = new JSONObject();
    mockResult1.put("duplicate", true);
    mockResult1.put("virus", false);
    mockResult1.put("id", "id1");
    mockResult1.put("failedDocument", "sample1.pdf");
    mockResult2.put("duplicate", false);
    mockResult2.put("virus", true);
    mockResult2.put("id", "id2");
    mockResult2.put("failedDocument", "sample2.pdf");
    mockResult3.put("fail", true);
    mockResult3.put("id", "id3");
    mockResult3.put("failedDocument", "sample3.pdf");
    when(sdmService.createDocument(any(), any(), any()))
        .thenReturn(mockResult1)
        .thenReturn(mockResult2)
        .thenReturn(mockResult3);
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderId");

    CdsEntity targetMock = mock(CdsEntity.class);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));

    mockData = new ArrayList<>();
    CdsData cdsData = mock(CdsData.class);
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    Map<String, Object> attachment2 = new HashMap<>();
    Map<String, Object> attachment3 = new HashMap<>();
    Map<String, Object> attachment4 = new HashMap<>();
    String simulatedContent = "Sample Content"; // Example content as a string
    InputStream contentStream =
        new ByteArrayInputStream(simulatedContent.getBytes(StandardCharsets.UTF_8));
    attachment1.put("up__ID", "up__id");
    attachment1.put("fileName", "sample1.pdf");
    attachment1.put("ID", "id1");
    attachment1.put("content", contentStream);
    attachment2.put("up__ID", "up__id");
    attachment2.put("fileName", "sample2.pdf");
    attachment2.put("ID", "id2");
    attachment2.put("content", contentStream);
    attachment3.put("up__ID", "up__id");
    attachment3.put("fileName", "sample3.pdf");
    attachment3.put("ID", "id3");
    attachment3.put("content", contentStream);
    attachment4.put("up__ID", "up__id");
    attachment4.put("fileName", "sample4.pdf");
    attachment4.put("ID", "id4");
    attachments.add(attachment1);
    attachments.add(attachment2);
    attachments.add(attachment3);
    attachments.add(attachment4);
    when(cdsData.get("attachments")).thenReturn(attachments);
    mockData.add(cdsData);

    String expectedWarnMessage =
        "The following files already exist and cannot be uploaded:\n"
            + "• sample1.pdf\n"
            + "The following files contain potential malware and cannot be uploaded:\n"
            + "• sample2.pdf\n"
            + "The following files are empty and could not be uploaded:\n"
            + "• sample4.pdf\n"
            + "The following files cannot be uploaded:\n"
            + "• sample3.pdf";

    try (MockedStatic<TokenHandler> tokenHandlerMockedStatic =
        Mockito.mockStatic(TokenHandler.class)) {
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      handlerSpy.processBefore(mockContext, mockData);
      // verify(mockMessages).warn(trim(expectedWarnMessage));
      ArgumentCaptor<String> warnMessageCaptor = forClass(String.class);
      verify(mockMessages).warn(warnMessageCaptor.capture());
      String actualWarnMessage = warnMessageCaptor.getValue().trim(); // Trim the actual message
      assertEquals(expectedWarnMessage, actualWarnMessage);
    }
  }

  @Test
  public void testProcessBeforeDuplicateFiles() throws IOException {
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    Messages mockMessages = mock(Messages.class);
    when(mockContext.getMessages()).thenReturn(mockMessages);
    CdsEntity mockTarget = mock(CdsEntity.class);
    when(mockContext.getTarget()).thenReturn(mockTarget);

    mockData = new ArrayList<>();
    CdsData cdsData = mock(CdsData.class);
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    Map<String, Object> attachment2 = new HashMap<>();
    attachment1.put("up__ID", "up__id");
    attachment1.put("fileName", "sample1.pdf");
    attachment2.put("up__ID", "up__id");
    attachment2.put("fileName", "sample1.pdf");
    attachments.add(attachment1);
    attachments.add(attachment2);
    when(cdsData.get("attachments")).thenReturn(attachments);
    mockData.add(cdsData);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              handlerSpy.processBefore(mockContext, mockData);
            });
    assertEquals("Duplicate files", exception.getMessage());
  }

  //    @Test
  //    public void testProcessBeforeDraft() throws IOException {
  //        when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //
  //        mockData = new ArrayList<>();
  //        CdsData cdsData = mock(CdsData.class);
  //        List<Map<String, Object>> attachments = new ArrayList<>();
  //        Map<String, Object> attachment1 = new HashMap<>();
  //        Map<String, Object> attachment2 = new HashMap<>();
  //        attachment1.put("up__ID", "up__id");
  //        attachment1.put("fileName", "sample1.pdf");
  //        attachment2.put("up__ID", "up__id");
  //        attachment2.put("fileName", "sample1.pdf");
  //        attachments.add(attachment1);
  //        attachments.add(attachment2);
  //        when(cdsData.get("attachments")).thenReturn(attachments);
  //        mockData.add(cdsData);
  //
  //        handlerSpy.processBeforeForDraft(mockContext, mockData);
  //    }
}
