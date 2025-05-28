package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.DeletionUserInfo;
import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.messages.Message;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.ParameterInfo;
import com.sap.cds.services.request.UserInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class SDMAttachmentsServiceHandlerTest {
  @Mock private AttachmentCreateEventContext mockContext;
  @Mock private AttachmentReadEventContext mockReadContext;
  @Mock private List<CdsData> mockData;
  @Mock private AuthenticationInfo mockAuthInfo;
  @Mock private JwtTokenAuthenticationInfo mockJwtTokenInfo;
  @Mock private ParameterInfo mockParameterInfo;
  private SDMAttachmentsServiceHandler handlerSpy;
  private PersistenceService persistenceService;
  @Mock private AttachmentMarkAsDeletedEventContext attachmentMarkAsDeletedEventContext;
  @Mock private MediaData mockMediaData;
  @Mock private CdsEntity mockDraftEntity;

  @Mock private AttachmentRestoreEventContext restoreEventContext;
  private SDMService sdmService;
  private DocumentUploadService documentUploadService;
  @Mock private CdsModel cdsModel;
  @Mock private CdsEntity cdsEntity;
  @Mock private UserInfo userInfo;
  @Mock private Messages mockMessages;
  @Mock private AttachmentCreateEventContext eventContext;

  String objectId = "objectId";
  String folderId = "folderId";
  String userEmail = "email";
  String subdomain = "subdomain";
  JsonObject mockPayload = new JsonObject();
  String token =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwLCJleHRfYXR0ciI6eyJ6ZG4iOiJ0ZW5hbnQifX0.efgtgCjF7bxG2kEgYbkTObovuZN5YQP5t7yr9aPKntk";

  @Mock private SDMCredentials sdmCredentials;
  @Mock private DeletionUserInfo deletionUserInfo;
  Map<String, String> headers = new HashMap<>();
  @Mock ParameterInfo parameterInfo;

  @BeforeEach
  public void setUp() {
    mockPayload.addProperty("email", "john.doe@example.com");
    mockPayload.addProperty("exp", "1234567890");
    mockPayload.addProperty("zid", "tenant-id-value");
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("zdn", "tenant");
    mockPayload.add("ext_attr", jsonObject);
    MockitoAnnotations.openMocks(this);
    persistenceService = mock(PersistenceService.class);
    sdmService = mock(SDMServiceImpl.class);
    documentUploadService = mock(DocumentUploadService.class);
    when(attachmentMarkAsDeletedEventContext.getContentId())
        .thenReturn("objectId:folderId:entity:subdomain");
    when(attachmentMarkAsDeletedEventContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn(userEmail);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getName()).thenReturn(userEmail);

    headers.put("content-length", "100000");

    handlerSpy =
        spy(
            new SDMAttachmentsServiceHandler(
                persistenceService, sdmService, documentUploadService));
  }

  @Test
  public void testCreateVersioned() throws IOException {
    // Initialization of mocks and setup
    Message mockMessage = mock(Message.class);
    Messages mockMessages = mock(Messages.class);
    MediaData mockMediaData = mock(MediaData.class);
    CdsModel mockModel = mock(CdsModel.class);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("0__null");
      when(sdmService.checkRepositoryType(anyString(), any())).thenReturn("Versioned");
      when(mockContext.getMessages()).thenReturn(mockMessages);
      when(mockMessages.error("Upload not supported for versioned repositories."))
          .thenReturn(mockMessage);
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockContext.getModel()).thenReturn(mockModel);
      when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
      when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
      when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
      when(parameterInfo.getHeaders()).thenReturn(headers);
      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals("Upload not supported for versioned repositories.", thrown.getMessage());
    }
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

    when(sdmService.checkRepositoryType(anyString(), any())).thenReturn("Versioned");
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockMessages.error("Upload not supported for versioned repositories."))
        .thenReturn(mockMessage);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    // Use assertThrows to expect a ServiceException and validate the message
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });

    // Verify the exception message
    assertEquals("Upload not supported for versioned repositories.", thrown.getMessage());
  }

  @Test
  public void testCreateNonVersionedDuplicate() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    MediaData mockMediaData = mock(MediaData.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(true).when(handlerSpy).duplicateCheck(any(), any(), any());

    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("0__null");
      dbQueryMockedStatic
          .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals(SDMConstants.getDuplicateFilesError("sample.pdf"), thrown.getMessage());
    }
  }

  // @Test
  // public void testCreateNonVersionedDIDuplicate() throws IOException {
  //   // Initialize mocks and setup
  //   Map<String, Object> mockAttachmentIds = new HashMap<>();
  //   mockAttachmentIds.put("up__ID", "upid");
  //   mockAttachmentIds.put("ID", "id");
  //   mockAttachmentIds.put("repositoryId", "repo1");

  //   Result mockResult = mock(Result.class);
  //   Row mockRow = mock(Row.class);
  //   List<Row> nonEmptyRowList = List.of(mockRow);

  //   MediaData mockMediaData = mock(MediaData.class);
  //   CdsEntity mockDraftEntity = mock(CdsEntity.class);
  //   CdsModel mockModel = mock(CdsModel.class);
  //   CdsElement mockAssociationElement = mock(CdsElement.class);
  //   CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
  //   CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

  //   JSONObject mockCreateResult = new JSONObject();
  //   mockCreateResult.put("status", "duplicate");
  //   mockCreateResult.put("name", "sample.pdf");

  //   ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
  //   Map<String, String> mockHeaders = new HashMap<>();
  //   mockHeaders.put("content-length", "12345");

  //   when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
  //   when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

  //   // Mock return values and method calls
  //   when(mockContext.getModel()).thenReturn(mockModel);
  //   when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
  //   when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
  //   when(mockAssociationElement.getType()).thenReturn(mockAssocType);
  //   when(mockAssocType.refs()).thenReturn(Stream.of(mockCqnElementRef));
  //   when(mockCqnElementRef.path()).thenReturn("ID");
  //   when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
  //   when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
  //   when(mockContext.getData()).thenReturn(mockMediaData);
  //   when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //   when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //   when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //   when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
  //   JSONObject mockResponse = new JSONObject();
  //   mockResponse.put("status", "duplicate");

  //   // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
  //   when(documentUploadService.createDocumentRx(any(), any(), any()))
  //       .thenReturn(Single.just(mockResponse));
  //   when(mockResult.list()).thenReturn(nonEmptyRowList);
  //   doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
  //   when(mockMediaData.getFileName()).thenReturn("sample.pdf");

  //   // Mock DBQuery and TokenHandler
  //   try (MockedStatic<DBQuery> DBQueryMockedStatic = mockStatic(DBQuery.class);
  //       MockedStatic<TokenHandler> tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
  //       MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
  //         .thenReturn("0__null");
  //     DBQueryMockedStatic.when(
  //             () ->
  //                 DBQuery.getAttachmentsForUPID(
  //                     mockDraftEntity, persistenceService, "upid", "up__ID"))
  //         .thenReturn(mockResult);

  //     SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);

  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

  //     // Mock SDMUtils.isRestrictedCharactersInName
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
  //         .thenReturn(false); // Return false to indicate no restricted characters

  //     when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
  //     when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
  //     // Validate ServiceException for duplicate detection
  //     ServiceException thrown =
  //         assertThrows(
  //             ServiceException.class,
  //             () -> {
  //               handlerSpy.createAttachment(mockContext);
  //             });

  //     assertEquals(SDMConstants.getDuplicateFilesError("sample.pdf"), thrown.getMessage());
  //   }
  // }

  // @Test
  // public void testCreateNonVersionedDIVirus() throws IOException {
  //   // Initialize mocks and setup
  //   Map<String, Object> mockAttachmentIds = new HashMap<>();
  //   mockAttachmentIds.put("up__ID", "upid");
  //   mockAttachmentIds.put("ID", "id");
  //   mockAttachmentIds.put("repositoryId", "repo1");
  //   Result mockResult = mock(Result.class);
  //   Row mockRow = mock(Row.class);
  //   List<Row> nonEmptyRowList = List.of(mockRow);
  //   MediaData mockMediaData = mock(MediaData.class);
  //   CdsEntity mockEntity = mock(CdsEntity.class);
  //   CdsEntity mockDraftEntity = mock(CdsEntity.class);
  //   CdsElement mockAssociationElement = mock(CdsElement.class);
  //   CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
  //   CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
  //   byte[] byteArray = "Example content".getBytes();
  //   InputStream contentStream = new ByteArrayInputStream(byteArray);
  //   JSONObject mockCreateResult = new JSONObject();
  //   mockCreateResult.put("status", "virus");
  //   mockCreateResult.put("name", "sample.pdf");

  //   when(mockMediaData.getFileName()).thenReturn("sample.pdf");
  //   when(mockMediaData.getContent()).thenReturn(contentStream);
  //   when(mockContext.getModel()).thenReturn(cdsModel);
  //   when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
  //   when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
  //   when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
  //   when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
  //   when(mockCqnElementRef.path()).thenReturn("ID");
  //   when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
  //   when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
  //   when(mockResult.list()).thenReturn(nonEmptyRowList);
  //   when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //   when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //   when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //   when(mockContext.getData()).thenReturn(mockMediaData);
  //   doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
  //   when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
  //   JSONObject mockResponse = new JSONObject();
  //   mockResponse.put("status", "virus");

  //   // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
  //   when(documentUploadService.createDocumentRx(any(), any(), any()))
  //       .thenReturn(Single.just(mockResponse));
  //   ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
  //   Map<String, String> mockHeaders = new HashMap<>();
  //   mockHeaders.put("content-length", "12345");

  //   when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
  //   when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

  //   try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
  //       MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //           Mockito.mockStatic(TokenHandler.class);
  //       MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
  //         .thenReturn("0__null");
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
  //         .thenReturn(mockResult);
  //     SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
  //     when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
  //     when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");

  //     // Use assertThrows to expect a ServiceException and validate the message
  //     ServiceException thrown =
  //         assertThrows(
  //             ServiceException.class,
  //             () -> {
  //               handlerSpy.createAttachment(mockContext);
  //             });

  //     // Verify the exception message
  //     assertEquals(SDMConstants.getVirusFilesError("sample.pdf"), thrown.getMessage());
  //   }
  // }

  // @Test
  // public void testCreateNonVersionedDIOther() throws IOException {
  //   // Initialization of mocks and setup
  //   Map<String, Object> mockAttachmentIds = new HashMap<>();
  //   mockAttachmentIds.put("up__ID", "upid");
  //   mockAttachmentIds.put("ID", "id");
  //   mockAttachmentIds.put("repositoryId", "repo1");
  //   MediaData mockMediaData = mock(MediaData.class);
  //   Result mockResult = mock(Result.class);
  //   Row mockRow = mock(Row.class);
  //   List<Row> nonEmptyRowList = List.of(mockRow);
  //   CdsEntity mockEntity = mock(CdsEntity.class);
  //   // CdsEntity mockDraftEntity = mock(CdsEntity.class);
  //   CdsElement mockAssociationElement = mock(CdsElement.class);
  //   CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
  //   CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
  //   byte[] byteArray = "Example content".getBytes();
  //   InputStream contentStream = new ByteArrayInputStream(byteArray);
  //   JSONObject mockCreateResult = new JSONObject();
  //   mockCreateResult.put("status", "fail");
  //   mockCreateResult.put("message", "Failed due to a DI error");
  //   ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
  //   Map<String, String> mockHeaders = new HashMap<>();
  //   mockHeaders.put("content-length", "12345");

  //   when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
  //   when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

  //   when(mockMediaData.getFileName()).thenReturn("sample.pdf");
  //   when(mockMediaData.getContent()).thenReturn(contentStream);
  //   when(mockContext.getModel()).thenReturn(cdsModel);
  //   when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
  //   when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
  //   when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
  //   when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
  //   when(mockCqnElementRef.path()).thenReturn("ID");
  //   when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
  //   when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
  //   when(mockResult.list()).thenReturn(nonEmptyRowList);
  //   when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //   when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //   when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //   when(mockContext.getData()).thenReturn(mockMediaData);
  //   doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
  //   when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
  //   JSONObject mockResponse = new JSONObject();
  //   mockResponse.put("status", "fail");
  //   mockResponse.put("message", "Failed due to a DI error");
  //   // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
  //   when(documentUploadService.createDocumentRx(any(), any(), any()))
  //       .thenReturn(Single.just(mockResponse));
  //   try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
  //       MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //           Mockito.mockStatic(TokenHandler.class);
  //       MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
  //         .thenReturn("0__null");
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
  //         .thenReturn(mockResult);
  //     SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
  //     when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
  //     when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");

  //     // Use assertThrows to expect a ServiceException and validate the message
  //     ServiceException thrown =
  //         assertThrows(
  //             ServiceException.class,
  //             () -> {
  //               handlerSpy.createAttachment(mockContext);
  //             });

  //     // Verify the exception message
  //     assertEquals("Failed due to a DI error", thrown.getMessage());
  //   }
  // }

  // @Test
  // public void testCreateNonVersionedDISuccess() throws IOException {
  //   // Initialization of mocks and setup
  //   Map<String, Object> mockAttachmentIds = new HashMap<>();
  //   mockAttachmentIds.put("up__ID", "upid");
  //   mockAttachmentIds.put("ID", "id");
  //   mockAttachmentIds.put("repositoryId", "repo1");
  //   MediaData mockMediaData = mock(MediaData.class);
  //   Result mockResult = mock(Result.class);
  //   Row mockRow = mock(Row.class);
  //   List<Row> nonEmptyRowList = List.of(mockRow);
  //   CdsEntity mockEntity = mock(CdsEntity.class);
  //   CdsEntity mockDraftEntity = mock(CdsEntity.class);
  //   CdsElement mockAssociationElement = mock(CdsElement.class);
  //   CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
  //   CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
  //   byte[] byteArray = "Example content".getBytes();
  //   InputStream contentStream = new ByteArrayInputStream(byteArray);
  //   JSONObject mockCreateResult = new JSONObject();
  //   mockCreateResult.put("status", "success");
  //   mockCreateResult.put("url", "url");
  //   mockCreateResult.put("name", "sample.pdf");
  //   mockCreateResult.put("objectId", "objectId");

  //   when(mockMediaData.getFileName()).thenReturn("sample.pdf");
  //   when(mockMediaData.getContent()).thenReturn(contentStream);
  //   when(mockContext.getModel()).thenReturn(cdsModel);
  //   when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
  //   when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
  //   when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
  //   when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
  //   when(mockCqnElementRef.path()).thenReturn("ID");
  //   when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
  //   when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
  //   when(mockResult.list()).thenReturn(nonEmptyRowList);
  //   when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
  //   when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
  //   when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
  //   when(mockContext.getData()).thenReturn(mockMediaData);
  //   doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
  //   when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
  //   JSONObject mockResponse = new JSONObject();
  //   mockResponse.put("status", "success");
  //   mockResponse.put("objectId", "123");
  //   // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
  //   when(documentUploadService.createDocumentRx(any(), any(), any()))
  //       .thenReturn(Single.just(mockResponse));
  //   ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
  //   Map<String, String> mockHeaders = new HashMap<>();
  //   mockHeaders.put("content-length", "12345");

  //   when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
  //   when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
  //   try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
  //       MockedStatic<TokenHandler> tokenHandlerMockedStatic =
  //           Mockito.mockStatic(TokenHandler.class);
  //       MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
  //     sdmUtilsMockedStatic
  //         .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
  //         .thenReturn("0__null");
  //     dbQueryMockedStatic
  //         .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
  //         .thenReturn(mockResult);
  //     SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
  //     when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
  //     when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
  //
  // tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
  //     handlerSpy.createAttachment(mockContext);
  //     verifyNoInteractions(mockMessages);
  //   }
  // }

  // @Test
  public void testCreateNonVersionedNoUpAssociation() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);

    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("url", "url");
    mockCreateResult.put("name", "sample.pdf");
    mockCreateResult.put("objectId", "objectId");
    Result mockResult = mock(Result.class);
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.empty());
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      when(mockResult.rowCount()).thenReturn(2L);
      String upId = "8219w112dd";
      String upIdKey = "ew32332";

      dbQueryMockedStatic
          .when(() -> DBQuery.getAttachmentsForUPID(cdsEntity, persistenceService, null, ""))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
      when(parameterInfo.getHeaders()).thenReturn(headers);
      handlerSpy.createAttachment(mockContext);
      // verifyNoInteractions(mockMessages);
    }
  }

  // @Test
  public void testCreateNonVersionedEmptyResultList() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    List<Row> emptyRowList = Collections.emptyList();
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(emptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class)) {
      dbQueryMockedStatic
          .when(
              () ->
                  DBQuery.getAttachmentsForUPID(
                      cdsEntity, persistenceService, anyString(), anyString()))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

      handlerSpy.createAttachment(mockContext);
      verifyNoInteractions(mockMessages);
    }
  }

  @Test
  public void testCreateNonVersionedNameConstraint() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);

    when(mockMediaData.getFileName()).thenReturn("sample@.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = Mockito.mockStatic(SDMUtils.class)) {
      dbQueryMockedStatic
          .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("0__null");
      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.isRestrictedCharactersInName(anyString()))
          .thenReturn(true);

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals(
          SDMConstants.nameConstraintMessage(Collections.singletonList("sample@.pdf"), "Upload"),
          thrown.getMessage());
    }
  }

  @Test
  public void testDocumentDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
      List<CmisDocument> cmisDocuments = new ArrayList<>();
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setObjectId("objectId1");
      cmisDocuments.add(cmisDocument);
      cmisDocument = new CmisDocument();
      cmisDocument.setObjectId("objectId2");
      cmisDocuments.add(cmisDocument);

      mockedDBQuery
          .when(() -> DBQuery.getAttachmentsForFolder(any(), any(), any(), any()))
          .thenReturn(cmisDocuments);

      handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("delete", objectId, userEmail, subdomain);
    }
  }

  @Test
  public void testDocumentDeletionForObjectPresent() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
      List<CmisDocument> cmisDocuments = new ArrayList<>();
      String entity = "Books.attachments";
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setObjectId("objectId");
      cmisDocuments.add(cmisDocument);
      cmisDocument = new CmisDocument();
      cmisDocument.setObjectId("objectId2");
      cmisDocuments.add(cmisDocument);

      mockedDBQuery
          .when(() -> DBQuery.getAttachmentsForFolder(any(), any(), any(), any()))
          .thenReturn(cmisDocuments);

      handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
    }
  }

  @Test
  public void testFolderDeletion() throws IOException {
    try (MockedStatic<DBQuery> mockedDBQuery = mockStatic(DBQuery.class)) {

      when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
      when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
      List<CmisDocument> cmisDocuments = new ArrayList<>();
      String entity = "Books.attachments";
      mockedDBQuery
          .when(
              () ->
                  DBQuery.getAttachmentsForFolder(
                      entity, persistenceService, folderId, attachmentMarkAsDeletedEventContext))
          .thenReturn(cmisDocuments);
      handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
      verify(sdmService).deleteDocument("deleteTree", folderId, userEmail, subdomain);
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
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("0__null");
      // Mocking a raw list of maps
      List<Map> mockedResultList = new ArrayList<>();

      // Creating a map with duplicate filename but different file ID
      Map<String, Object> attachment1 = new HashMap<>();
      attachment1.put("fileName", "sample.pdf");
      attachment1.put("ID", "1234"); // Different ID, not a duplicate
      attachment1.put("repositoryId", "repoid");
      Map<String, Object> attachment2 = new HashMap<>();
      attachment2.put("fileName", "sample.pdf");
      attachment2.put("ID", "456"); // Same filename but different ID (this is the duplicate)
      attachment2.put("repositoryId", "repoid");
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

  @Test
  void testDuplicateCheck_WithDuplicateFilesFor2DifferentRepositories() {
    Result result = mock(Result.class);

    // Mocking a raw list of maps
    List<Map> mockedResultList = new ArrayList<>();

    // Creating a map with duplicate filename but different file ID
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "sample.pdf");
    attachment1.put("ID", "123"); // Different ID, not a duplicate
    attachment1.put("repositoryId", "repoid");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "sample.pdf");
    attachment2.put("ID", "456"); // Same filename but different ID (this is the duplicate)
    attachment1.put("repositoryId", "repoid");
    mockedResultList.add((Map) attachment1);
    mockedResultList.add((Map) attachment2);

    // Mocking the result to return the list containing the attachments
    when(result.listOf(Map.class)).thenReturn((List) mockedResultList);

    String filename = "sample.pdf";
    String fileid = "123"; // The fileid to check, same as attachment1, different from attachment2

    // Checking for duplicate
    boolean isDuplicate = handlerSpy.duplicateCheck(filename, fileid, result);

    // Assert that a duplicate is found
    assertTrue(!isDuplicate, "Expected to find a duplicate");
  }

  @Test
  public void testReadAttachment_NotVersionedRepository() throws IOException {
    when(mockReadContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");
    when(mockReadContext.getContentId()).thenReturn("objectId:part2");
    try (MockedStatic<TokenHandler> mockedStatic = mockStatic(TokenHandler.class)) {
      when(sdmService.checkRepositoryType(SDMConstants.REPOSITORY_ID, token))
          .thenReturn("NotVersioned");
      when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

      handlerSpy.readAttachment(mockReadContext);

      // Verify that readDocument method was called
      verify(sdmService)
          .readDocument(anyString(), anyString(), any(SDMCredentials.class), eq(mockReadContext));
    }
  }

  @Test
  public void testReadAttachment_FailureInReadDocument() throws IOException {
    when(mockReadContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");
    when(mockReadContext.getContentId()).thenReturn("objectId:part2");
    try (MockedStatic<TokenHandler> mockedStatic = mockStatic(TokenHandler.class)) {
      when(sdmService.checkRepositoryType(SDMConstants.REPOSITORY_ID, token))
          .thenReturn("NotVersioned");
      when(TokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
      doThrow(new ServiceException(SDMConstants.FILE_NOT_FOUND_ERROR))
          .when(sdmService)
          .readDocument(anyString(), anyString(), any(SDMCredentials.class), eq(mockReadContext));

      ServiceException exception =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.readAttachment(mockReadContext);
              });

      assertEquals("Object not found in repository", exception.getMessage());
    }
  }

  @Test
  public void testRestoreAttachment() {
    handlerSpy.restoreAttachment(restoreEventContext);
  }

  @Test
  public void testMaxCountErrorMessage() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockResult.rowCount()).thenReturn(3L);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("1__Only 1 Attachment is allowed");
      dbQueryMockedStatic
          .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
      when(parameterInfo.getHeaders()).thenReturn(headers);
      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals("Only 1 Attachment is allowed", thrown.getMessage());
    }
  }

  @Test
  public void testMaxCountError() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockResult.rowCount()).thenReturn(3L);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), any())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    try (MockedStatic<DBQuery> dbQueryMockedStatic = Mockito.mockStatic(DBQuery.class);
        MockedStatic<TokenHandler> tokenHandlerMockedStatic =
            Mockito.mockStatic(TokenHandler.class);
        MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn("1__null");
      dbQueryMockedStatic
          .when(() -> DBQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockSdmCredentials);

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals(
          "Cannot upload more than 1 attachments as set up by the application",
          thrown.getMessage());
    }
  }

  @Test
  public void throwAttachmetDraftEntityException() throws IOException {

    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn("Non Versioned");
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    when(cdsModel.findEntity(anyString()))
        .thenThrow(new ServiceException(SDMConstants.DRAFT_NOT_FOUND));
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
  }
}
