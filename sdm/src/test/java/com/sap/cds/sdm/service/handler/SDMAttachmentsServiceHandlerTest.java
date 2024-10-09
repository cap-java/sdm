package com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.messages.Message;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.json.JSONObject;
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
  @Mock private AttachmentMarkAsDeletedEventContext attachmentMarkAsDeletedEventContext;
  private SDMService sdmService;
  @Mock private CdsModel cdsModel;

  @Mock private CdsEntity cdsEntity;

  @Mock private UserInfo userInfo;

  String objectId = "objectId";
  String folderId = "folderId";
  String userEmail = "email";
  String subdomain = "subdomain";

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    persistenceService = mock(PersistenceService.class);
    sdmService = mock(SDMServiceImpl.class);
    when(attachmentMarkAsDeletedEventContext.getContentId())
        .thenReturn("objectId:folderId:email:entity:subdomain");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getName()).thenReturn(userEmail);
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
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
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
      when(mockRow.get("columnName")).thenReturn("mockDataValue");
      when(mockResult.list()).thenReturn(nonEmptyRowList);
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUPID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      handlerSpy.createAttachment(mockContext);
      verify(mockMessages).warn("This attachment already exists. Please remove it and try again");
    }
  }

  @Test
  public void testDocumentDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));

      mockedDBQuery
          .when(() -> DBQuery.isFolderEmpty(cdsEntity, persistenceService, folderId))
          .thenReturn(false);

      handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("delete", objectId, userEmail, subdomain);
    }
  }

  @Test
  public void testCreateNonVersionedDIDuplicate() throws IOException {
    Map<String, Object> mockattachmentIds = new HashMap<>();
    mockattachmentIds.put("up__ID", "upid");
    mockattachmentIds.put("ID", "id");
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    MediaData mockMediaData = mock(MediaData.class);
    Messages mockMessages = mock(Messages.class);
    CdsEntity targetMock = mock(CdsEntity.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "duplicate");
    mockCreateResult.put("name", "sample.pdf");

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));
    when(mockModel.findEntity("some.qualified.Name.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntity));
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockContext.getAttachmentIds()).thenReturn(mockattachmentIds);
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<DBQuery> DBQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      when(mockRow.get("columnName")).thenReturn("mockDataValue");
      when(mockResult.list()).thenReturn(nonEmptyRowList);
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUPID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verify(mockMessages)
          .error("The following files already exist and cannot be uploaded:\n• sample.pdf\n");
    }
  }

  @Test
  public void testFolderDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
      mockedDBQuery
          .when(() -> DBQuery.isFolderEmpty(cdsEntity, persistenceService, folderId))
          .thenReturn(true);
      handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("deleteTree", folderId, userEmail, subdomain);
    }
  }

  @Test
  public void testCreateNonVersionedDIVirus() throws IOException {
    Map<String, Object> mockattachmentIds = new HashMap<>();
    mockattachmentIds.put("up__ID", "upid");
    mockattachmentIds.put("ID", "id");
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    MediaData mockMediaData = mock(MediaData.class);
    Messages mockMessages = mock(Messages.class);
    CdsEntity targetMock = mock(CdsEntity.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "virus");
    mockCreateResult.put("name", "sample.pdf");

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));
    when(mockModel.findEntity("some.qualified.Name.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntity));
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockContext.getAttachmentIds()).thenReturn(mockattachmentIds);
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<DBQuery> DBQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      when(mockRow.get("columnName")).thenReturn("mockDataValue");
      when(mockResult.list()).thenReturn(nonEmptyRowList);
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUPID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verify(mockMessages)
          .error(
              "The following files contain potential malware and cannot be uploaded:\n• sample.pdf\n");
    }
  }

  @Test
  public void testCreateNonVersionedDIOther() throws IOException {
    Map<String, Object> mockattachmentIds = new HashMap<>();
    mockattachmentIds.put("up__ID", "upid");
    mockattachmentIds.put("ID", "id");
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    MediaData mockMediaData = mock(MediaData.class);
    Messages mockMessages = mock(Messages.class);
    CdsEntity targetMock = mock(CdsEntity.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "fail");
    mockCreateResult.put("name", "sample.pdf");

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));
    when(mockModel.findEntity("some.qualified.Name.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntity));
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockContext.getAttachmentIds()).thenReturn(mockattachmentIds);
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<DBQuery> DBQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      when(mockRow.get("columnName")).thenReturn("mockDataValue");
      when(mockResult.list()).thenReturn(nonEmptyRowList);
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUPID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verify(mockMessages).error("The following files cannot be uploaded:\n• sample.pdf\n");
    }
  }

  @Test
  public void testCreateNonVersionedDISuccess() throws IOException {
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
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("url", "url");
    mockCreateResult.put("name", "sample.pdf");

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getTarget()).thenReturn(targetMock);
    when(targetMock.getQualifiedName()).thenReturn("some.qualified.Name");
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity("some.qualified.Name.attachments"))
        .thenReturn(Optional.of(mockEntity));
    when(mockModel.findEntity("some.qualified.Name.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntity));
    when(sdmService.checkRepositoryType(anyString())).thenReturn("Non Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockContext.getAttachmentIds()).thenReturn(mockattachmentIds);
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<DBQuery> DBQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      DBQueryMockedStatic.when(
              () -> DBQuery.getAttachmentsForUPID(mockEntity, persistenceService, "upid"))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verifyNoInteractions(mockMessages);
    }
  }

  @Test
  void testDuplicateCheck_NoDuplicates() {
    Result result = mock(Result.class);

    // Mocking a raw list of maps
    List<Map> mockedResultList = new ArrayList<>();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("key1", "value1");
    mockedResultList.add(map1);

    // Casting to raw types to avoid type mismatch
    when(result.listOf(Map.class)).thenReturn((List) mockedResultList);

    String filename = "sample.pdf";
    String fileid = "123";
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", filename);
    attachment.put("ID", fileid);

    List<Map> resultList = Arrays.asList((Map) attachment);
    when(result.listOf(Map.class)).thenReturn((List) resultList);

    boolean isDuplicate = handlerSpy.duplicateCheck(filename, fileid, result);
    assertFalse(isDuplicate, "Expected no duplicates");
  }

  @Test
  void testDuplicateCheck_WithDuplicate() {
    Result result = mock(Result.class);

    // Mocking a raw list of maps
    List<Map> mockedResultList = new ArrayList<>();

    // Creating a map with duplicate filename but different file ID
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "sample.pdf");
    attachment1.put("ID", "123"); // Different ID, not a duplicate

    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "sample.pdf");
    attachment2.put("ID", "456"); // Same filename but different ID (this is the duplicate)

    mockedResultList.add((Map) attachment1);
    mockedResultList.add((Map) attachment2);

    // Mocking the result to return the list containing the attachments
    when(result.listOf(Map.class)).thenReturn((List) mockedResultList);

    String filename = "sample.pdf";
    String fileid = "123"; // The fileid to check, same as attachment1, different from attachment2

    // Checking for duplicate
    boolean isDuplicate = handlerSpy.duplicateCheck(filename, fileid, result);

    // Assert that a duplicate is found
    assertTrue(isDuplicate, "Expected to find a duplicate");
  }
}
