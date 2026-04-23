package unit.com.sap.cds.sdm.service.handler;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.model.service.AttachmentModificationResult;
import com.sap.cds.feature.attachments.service.model.service.CreateAttachmentInput;
import com.sap.cds.feature.attachments.service.model.service.MarkAsDeletedInput;
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
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.SDMAttachmentsService;
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
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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

  @Mock private CdsRuntime cdsRuntime;

  @Mock private AttachmentRestoreEventContext restoreEventContext;
  private SDMService sdmService;
  private DocumentUploadService documentUploadService;
  @Mock private CdsModel cdsModel;
  @Mock private CdsEntity cdsEntity;
  @Mock private UserInfo userInfo;
  @Mock private Messages mockMessages;
  @Mock private AttachmentCreateEventContext eventContext;
  @Mock DBQuery dbQuery;
  @Mock TokenHandler tokenHandler;

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
    when(userInfo.getTenant()).thenReturn("test-tenant");

    headers.put("content-length", "100000");

    handlerSpy =
        spy(
            new SDMAttachmentsServiceHandler(
                persistenceService, sdmService, documentUploadService, tokenHandler, dbQuery));
  }

  @Test
  public void testCreateVersioned() throws IOException {
    // Initialization of mocks and setup
    Message mockMessage = mock(Message.class);
    Messages mockMessages = mock(Messages.class);
    MediaData mockMediaData = mock(MediaData.class);
    CdsModel mockModel = mock(CdsModel.class);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      RepoValue repoValue = new RepoValue();
      repoValue.setVirusScanEnabled(false);
      repoValue.setVersionEnabled(true);
      when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
      when(mockContext.getMessages()).thenReturn(mockMessages);
      when(mockMessages.error("Upload not supported for versioned repositories."))
          .thenReturn(mockMessage);
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockContext.getModel()).thenReturn(mockModel);
      when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
      when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
      when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
      when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
      when(cdsRuntime.getLocalizedMessage(any(), any(), any())).thenReturn("VERSIONED_REPO_ERROR");
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
      when(parameterInfo.getHeaders()).thenReturn(headers);
      when(cdsRuntime.getLocalizedMessage(any(), any(), any())).thenReturn("VERSIONED_REPO_ERROR");
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
    RepoValue repoValue = new RepoValue();
    repoValue.setVersionEnabled(true);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
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
  public void testCreateVersionedI18nMessage() throws IOException {
    // Initialization of mocks and setup
    Message mockMessage = mock(Message.class);
    Messages mockMessages = mock(Messages.class);
    MediaData mockMediaData = mock(MediaData.class);
    CdsModel mockModel = mock(CdsModel.class);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      RepoValue repoValue = new RepoValue();
      repoValue.setVirusScanEnabled(false);
      repoValue.setVersionEnabled(true);
      when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
      when(mockContext.getMessages()).thenReturn(mockMessages);
      when(mockMessages.error("Upload not supported for versioned repositories."))
          .thenReturn(mockMessage);
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockContext.getModel()).thenReturn(mockModel);
      when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
      when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
      when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
      when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
      when(cdsRuntime.getLocalizedMessage(any(), any(), any())).thenReturn("VERSIONED_REPO_ERROR");
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
    RepoValue repoValue = new RepoValue();
    repoValue.setVersionEnabled(true);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getMessages()).thenReturn(mockMessages);
    when(mockMessages.error("Versioned repo error in German")).thenReturn(mockMessage);
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
  public void testCreateVirusEnabled() throws IOException {
    // Initialization of mocks and setup
    Message mockMessage = mock(Message.class);
    Messages mockMessages = mock(Messages.class);
    MediaData mockMediaData = mock(MediaData.class);
    CdsModel mockModel = mock(CdsModel.class);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      RepoValue repoValue = new RepoValue();
      repoValue.setVirusScanEnabled(true);
      repoValue.setDisableVirusScannerForLargeFile(false);
      repoValue.setVersionEnabled(false);
      repoValue.setIsAsyncVirusScanEnabled(false);
      when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
      when(mockContext.getMessages()).thenReturn(mockMessages);
      when(mockMessages.error(SDMUtils.getErrorMessage("VIRUS_REPO_ERROR_MORE_THAN_400MB")))
          .thenReturn(mockMessage);
      when(mockContext.getData()).thenReturn(mockMediaData);
      when(mockContext.getModel()).thenReturn(mockModel);
      when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
      when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
      when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
      headers.put("content-length", "900000089999");
      when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
      when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
          .thenReturn("VIRUS_REPO_ERROR_MORE_THAN_400MB");
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
      assertEquals("You cannot upload files that are larger than 400 MB", thrown.getMessage());
    }
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
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentEntity()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(true).when(handlerSpy).duplicateCheck(any(), any(), any());

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals(SDMErrorMessages.getDuplicateFilesError("sample.pdf"), thrown.getMessage());
    }
  }

  @Test
  public void testCreateNonVersionedDIDuplicate() throws IOException {
    // Initialize mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");

    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);

    MediaData mockMediaData = mock(MediaData.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "duplicate");
    mockCreateResult.put("name", "sample.pdf");

    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

    // Mock return values and method calls
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("status", "duplicate");

    // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");

    // Mock DBQuery and TokenHandler
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(mockDraftEntity, persistenceService, "upid", "up__ID"))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      // Mock SDMUtils.isRestrictedCharactersInName
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
          .thenReturn(false); // Return false to indicate no restricted characters

      when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
      when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
      when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);

      // Now safe to call getDuplicateFilesError since SDMUtils is mocked
      String expectedErrorMessage = SDMErrorMessages.getDuplicateFilesError("sample.pdf");
      when(cdsRuntime.getLocalizedMessage(any(), any(), any())).thenReturn(expectedErrorMessage);
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);

      // Validate ServiceException for duplicate detection
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      assertEquals(
          "An object named \"sample.pdf\" already exists. Rename the object and try again.",
          thrown.getMessage());
    }
  }

  @Test
  public void testCreateNonVersionedDIVirus() throws IOException {
    // Initialize mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    MediaData mockMediaData = mock(MediaData.class);
    CdsEntity mockEntity = mock(CdsEntity.class);
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "virus");
    mockCreateResult.put("name", "sample.pdf");

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("status", "virus");

    // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
      when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
      when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);

      // Now safe to call getVirusFilesError since SDMUtils is mocked
      String expectedErrorMessage = SDMErrorMessages.getVirusFilesError("sample.pdf");
      when(cdsRuntime.getLocalizedMessage(any(), any(), any())).thenReturn(expectedErrorMessage);
      when(mockContext.getParameterInfo()).thenReturn(parameterInfo);

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals(expectedErrorMessage, thrown.getMessage());
    }
  }

  @Test
  void testCopyAttachments_invalidFacetFormat() {
    SDMAttachmentsService service = new SDMAttachmentsService();
    CopyAttachmentInput input = mock(CopyAttachmentInput.class);
    when(input.facet()).thenReturn("invalidfacet");
    when(input.upId()).thenReturn("upId");
    when(input.objectIds()).thenReturn(List.of("obj1"));
    Exception ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              service.copyAttachments(input, false);
            });
    assertTrue(ex.getMessage().contains("Invalid facet format"));
  }

  @Test
  void testReadAttachment_emitsContext() {
    SDMAttachmentsService service = spy(new SDMAttachmentsService());
    doNothing().when(service).emit(any());
    InputStream result = service.readAttachment("docId");
    assertNull(result);
  }

  @Test
  void testCreateAttachment_emitsContextAndReturnsResult() {
    SDMAttachmentsService service = spy(new SDMAttachmentsService());
    doNothing().when(service).emit(any());
    CreateAttachmentInput input = mock(CreateAttachmentInput.class);
    when(input.attachmentIds()).thenReturn(new HashMap<>());
    when(input.attachmentEntity()).thenReturn(mock(com.sap.cds.reflect.CdsEntity.class));
    when(input.fileName()).thenReturn("file.txt");
    when(input.mimeType()).thenReturn("text/plain");
    when(input.content()).thenReturn(new ByteArrayInputStream(new byte[0]));
    AttachmentModificationResult result = service.createAttachment(input);
    assertNotNull(result);
  }

  @Test
  void testMarkAttachmentAsDeleted_emitsContext() {
    SDMAttachmentsService service = spy(new SDMAttachmentsService());
    doNothing().when(service).emit(any());
    MarkAsDeletedInput input = mock(MarkAsDeletedInput.class);
    when(input.contentId()).thenReturn("docId");
    UserInfo userInfo = mock(UserInfo.class);
    when(userInfo.getName()).thenReturn("user");
    when(input.userInfo()).thenReturn(userInfo);
    service.markAttachmentAsDeleted(input);
  }

  @Test
  void testRestoreAttachment_emitsContext() {
    SDMAttachmentsService service = spy(new SDMAttachmentsService());
    doNothing().when(service).emit(any());
    service.restoreAttachment(Instant.now());
  }

  @Test
  public void testCreateNonVersionedDIOther() throws IOException {
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
    // CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "fail");
    mockCreateResult.put("message", "Failed due to a DI error");
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders

    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("status", "fail");
    mockResponse.put("message", "Failed due to a DI error");
    // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
      when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertEquals("Failed due to a DI error", thrown.getMessage());
    }
  }

  @Test
  public void testCreateNonVersionedDIUnauthorizedI18n() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");

    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    List<Row> nonEmptyRowList = List.of(mock(Row.class));
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    // Set up the JSON response for the "unauthorized" case
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "unauthorized");

    // Mock method calls
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
    when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("USER_NOT_AUTHORISED_ERROR");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    // Ensure filename is present so handler's own validateFileName doesn't throw whitespace error
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockMediaData.get("fileName")).thenReturn("test.txt");
    when(mockMediaData.getFileName()).thenReturn("test.txt");

    // Mock the behavior of createDocument and other dependencies
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
            mockStatic(SDMUtils.class, CALLS_REAL_METHODS);
        MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
            mockStatic(AttachmentsHandlerUtils.class)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(10L);

      attachmentUtilsMockedStatic
          .when(() -> AttachmentsHandlerUtils.validateFileNames(any(), any(), any(), any(), any()))
          .thenCallRealMethod();

      // Assert that a ServiceException is thrown and verify its message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });
      assertEquals(
          "You do not have the required permissions to upload attachments. Please contact your administrator for access.",
          thrown.getMessage());
    }
  }

  @Test
  public void testCreateNonVersionedDIUnauthorized() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    mockAttachmentIds.put("fileName", "test.txt");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.get("fileName")).thenReturn("test.txt");
    when(mockMediaData.getFileName()).thenReturn("test.txt");
    Result mockResult = mock(Result.class);
    List<Row> nonEmptyRowList = List.of(mock(Row.class));
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Test content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);

    // Set up the JSON response for the "unauthorized" case
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "unauthorized");

    // Mock method calls
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
    when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("Unauthorised error german");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");

    // Mock the behavior of createDocument and other dependencies
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
          .thenReturn(false);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR"))
          .thenReturn("Unauthorised error german");
      try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
          mockStatic(AttachmentsHandlerUtils.class)) {
        attachmentUtilsMockedStatic
            .when(
                () -> AttachmentsHandlerUtils.validateFileNames(any(), any(), any(), any(), any()))
            .thenCallRealMethod();

        // Assert that a ServiceException is thrown and verify its message
        ServiceException thrown =
            assertThrows(
                ServiceException.class,
                () -> {
                  handlerSpy.createAttachment(mockContext);
                });
        assertEquals("Unauthorised error german", thrown.getMessage());
      }
    }
  }

  @Test
  public void testCreateNonVersionedDIBlocked() throws IOException {
    // Initialization of mocks and setup
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    mockAttachmentIds.put("fileName", "test.txt");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.get("fileName")).thenReturn("test.txt");
    when(mockMediaData.getFileName()).thenReturn("test.txt");
    Result mockResult = mock(Result.class);
    List<Row> nonEmptyRowList = List.of(mock(Row.class));
    CdsEntity mockDraftEntity = mock(CdsEntity.class);
    CdsModel mockModel = mock(CdsModel.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Test content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);

    // Set up the JSON response for the "blocked" case
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "blocked");

    // Mock method calls
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getModel()).thenReturn(mockModel);
    when(mockModel.findEntity(anyString())).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    repoValue.setDisableVirusScannerForLargeFile(false);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
    when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("The file type is not allowed");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");

    // Mock the behavior of createDocument and other dependencies
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = mockStatic(SDMUtils.class)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
          .thenReturn(false);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getErrorMessage("MIMETYPE_INVALID_ERROR"))
          .thenReturn("The file type is not allowed");
      try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
          mockStatic(AttachmentsHandlerUtils.class)) {
        attachmentUtilsMockedStatic
            .when(
                () -> AttachmentsHandlerUtils.validateFileNames(any(), any(), any(), any(), any()))
            .thenCallRealMethod();

        // Assert that a ServiceException is thrown and verify its message
        ServiceException thrown =
            assertThrows(
                ServiceException.class,
                () -> {
                  handlerSpy.createAttachment(mockContext);
                });
        assertEquals("The file type is not allowed", thrown.getMessage());
      }
    }
  }

  @Test
  public void testCreateNonVersionedDISuccess() throws IOException {
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
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("url", "url");
    mockCreateResult.put("name", "sample.pdf");
    mockCreateResult.put("objectId", "objectId");
    mockCreateResult.put("mimeType", "application/pdf");
    mockCreateResult.put("uploadStatus", "Success");
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("status", "success");
    mockResponse.put("objectId", "123");

    // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
      when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
      when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name_drafts");

      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verifyNoInteractions(mockMessages);
    }
  }

  @Test
  public void testCreateVirusEnabledDisableLargeFileDISuccess() throws IOException {
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
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("url", "url");
    mockCreateResult.put("name", "sample.pdf");
    mockCreateResult.put("objectId", "objectId");
    mockCreateResult.put("mimeType", "application/pdf");
    mockCreateResult.put("uploadStatus", "Success");
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(true);
    repoValue.setVersionEnabled(false);
    repoValue.setDisableVirusScannerForLargeFile(true);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    JSONObject mockResponse = new JSONObject();
    mockResponse.put("status", "success");
    mockResponse.put("objectId", "123");
    mockResponse.put("mimeType", "application/pdf");
    // Mock the behavior of createDocumentRx to return the mock response wrapped in a Single
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
      when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
      when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name_drafts");

      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      handlerSpy.createAttachment(mockContext);
      verifyNoInteractions(mockMessages);
    }
  }

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
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
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
    when(mockResult.rowCount()).thenReturn(2L);

    when(dbQuery.getAttachmentsForUPID(cdsEntity, persistenceService, null, ""))
        .thenReturn(mockResult);
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    handlerSpy.createAttachment(mockContext);
    verifyNoInteractions(mockMessages);
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
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
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
    when(dbQuery.getAttachmentsForUPID(cdsEntity, persistenceService, anyString(), anyString()))
        .thenReturn(mockResult);
    SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    handlerSpy.createAttachment(mockContext);
    verifyNoInteractions(mockMessages);
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
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentEntity()).thenReturn(mockDraftEntity);
    when(mockDraftEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");

    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo); // Mock getParameterInfo
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders); // Mock getHeaders
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic = Mockito.mockStatic(SDMUtils.class)) {
      when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.hasRestrictedCharactersInName(anyString()))
          .thenReturn(true);
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getErrorMessage("SINGLE_RESTRICTED_CHARACTER_IN_FILE"))
          .thenReturn(
              "The file name '%s' contains restricted characters. File names cannot contain the following characters: / \\");

      try (MockedStatic<AttachmentsHandlerUtils> attachmentUtilsMockedStatic =
          mockStatic(AttachmentsHandlerUtils.class)) {
        attachmentUtilsMockedStatic
            .when(
                () -> AttachmentsHandlerUtils.validateFileNames(any(), any(), any(), any(), any()))
            .thenCallRealMethod();

        // Use assertThrows to expect a ServiceException and validate the message
        ServiceException thrown =
            assertThrows(
                ServiceException.class,
                () -> {
                  handlerSpy.createAttachment(mockContext);
                });

        // Verify the exception message
        assertEquals(
            SDMErrorMessages.nameConstraintMessage(Collections.singletonList("sample@.pdf")),
            thrown.getMessage());
      }
    }
  }

  @Test
  public void testDocumentDeletion() throws IOException {
    when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("objectId1");
    cmisDocuments.add(cmisDocument);
    cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("objectId2");
    cmisDocuments.add(cmisDocument);
    when(attachmentMarkAsDeletedEventContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("system-internal");
    when(dbQuery.getAttachmentsForFolder(any(), any(), any(), any())).thenReturn(cmisDocuments);
    handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
    verify(sdmService)
        .deleteDocument(
            "delete",
            objectId,
            attachmentMarkAsDeletedEventContext.getDeletionUserInfo().getName(),
            attachmentMarkAsDeletedEventContext.getDeletionUserInfo().getIsSystemUser());
  }

  @Test
  public void testDocumentDeletionForObjectPresent() throws IOException {
    when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("objectId");
    cmisDocuments.add(cmisDocument);
    cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("objectId2");
    cmisDocuments.add(cmisDocument);

    when(dbQuery.getAttachmentsForFolder(any(), any(), any(), any())).thenReturn(cmisDocuments);

    handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
  }

  @Test
  public void testFolderDeletion() throws IOException {
    when(attachmentMarkAsDeletedEventContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(cdsEntity));
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    String entity = "Books.attachments";
    when(dbQuery.getAttachmentsForFolder(
            entity, persistenceService, folderId, attachmentMarkAsDeletedEventContext))
        .thenReturn(cmisDocuments);
    when(attachmentMarkAsDeletedEventContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("system-internal");
    handlerSpy.markAttachmentAsDeleted(attachmentMarkAsDeletedEventContext);
    verify(sdmService)
        .deleteDocument(
            "deleteTree",
            folderId,
            attachmentMarkAsDeletedEventContext.getDeletionUserInfo().getName(),
            attachmentMarkAsDeletedEventContext.getDeletionUserInfo().getIsSystemUser());
  }

  @Test
  void testDuplicateCheck_NoDuplicates() {
    Result result = mock(Result.class);

    // Mocking a raw list of maps to match Result.listOf(Map.class) expectation
    @SuppressWarnings("rawtypes")
    List<Map> mockedResultList = new ArrayList<>();
    Map<String, Object> map1 = new HashMap<>();
    map1.put("key1", "value1");
    mockedResultList.add(map1);

    when(result.listOf(Map.class)).thenReturn(mockedResultList);

    String filename = "sample.pdf";
    String fileid = "123";
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", filename);
    attachment.put("ID", fileid);

    @SuppressWarnings("rawtypes")
    List<Map> resultList = Arrays.asList((Map) attachment);
    when(result.listOf(Map.class)).thenReturn(resultList);

    boolean isDuplicate = handlerSpy.duplicateCheck(filename, fileid, result);
    assertFalse(isDuplicate, "Expected no duplicates");
  }

  @Test
  void testDuplicateCheck_WithDuplicate() {
    Result result = mock(Result.class);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);

      // Initialize list with proper generic type
      List<Map<String, Object>> mockedResultList = new ArrayList<>();

      // Creating a map with duplicate filename but different file ID
      Map<String, Object> attachment1 = new HashMap<>();
      attachment1.put("fileName", "sample.pdf");
      attachment1.put("ID", "1234"); // Different ID, not a duplicate
      attachment1.put("repositoryId", SDMConstants.REPOSITORY_ID);
      Map<String, Object> attachment2 = new HashMap<>();
      attachment2.put("fileName", "sample.pdf");
      attachment2.put("ID", "456"); // Same filename but different ID (this is the duplicate)
      attachment2.put("repositoryId", SDMConstants.REPOSITORY_ID);
      mockedResultList.add(attachment1);
      mockedResultList.add(attachment2);

      // Mock with proper type casting
      when(result.listOf(Map.class)).thenReturn((List<Map>) (List<?>) mockedResultList);

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
    List<Map<String, Object>> mockedResultList = new ArrayList<>();

    // Creating a map with duplicate filename but different file ID
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "sample.pdf");
    attachment1.put("ID", "123"); // Different ID, not a duplicate
    attachment1.put("repositoryId", "repoid");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "sample.pdf");
    attachment2.put("ID", "456"); // Same filename but different ID (this is the duplicate)
    attachment1.put("repositoryId", "repoid");
    mockedResultList.add(attachment1);
    mockedResultList.add(attachment2);

    // Mocking the result to return the list containing the attachments
    when(result.listOf(Map.class)).thenReturn((List<Map>) (List<?>) mockedResultList);

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
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);
    handlerSpy.readAttachment(mockReadContext);

    // Verify that readDocument method was called
    verify(sdmService).readDocument(anyString(), any(SDMCredentials.class), eq(mockReadContext));
  }

  @Test
  public void testReadAttachment_FailureInReadDocument() throws IOException {
    when(mockReadContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");
    when(mockReadContext.getContentId()).thenReturn("objectId:part2");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    doThrow(new ServiceException("FILE_NOT_FOUND_ERROR"))
        .when(sdmService)
        .readDocument(anyString(), any(SDMCredentials.class), eq(mockReadContext));
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.readAttachment(mockReadContext);
            });

    assertEquals("FILE_NOT_FOUND_ERROR", exception.getMessage());
  }

  @Test
  public void testRestoreAttachment() {
    handlerSpy.restoreAttachment(restoreEventContext);
  }

  @Test
  public void testMaxCountErrorMessagei18n() throws IOException {
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
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentEntity()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockResult.rowCount()).thenReturn(3L);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("Only 1 Attachment is allowed");
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(1L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(1L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
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
      assertTrue(thrown.getMessage().contains("Cannot upload more than"));
    }
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
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentEntity()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockResult.rowCount()).thenReturn(3L);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(String.format(SDMUtils.getErrorMessage("MAX_COUNT_ERROR_MESSAGE"), "1"));
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(1L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(1L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
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
      assertTrue(thrown.getMessage().contains("Cannot upload more than"));
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
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentEntity()).thenReturn(mockEntity);
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockResult.rowCount()).thenReturn(3L);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(sdmService.createDocument(any(), any(), any())).thenReturn(mockCreateResult);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS); ) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(1L);

      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);

      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      // Use assertThrows to expect a ServiceException and validate the message
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      // Verify the exception message
      assertTrue(thrown.getMessage().contains("Cannot upload more than"));
    }
  }

  @Test
  public void throwAttachmetDraftEntityException() throws IOException {
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    when(cdsModel.findEntity(anyString()))
        .thenThrow(new ServiceException("Attachment draft entity not found"));

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  @Test
  public void testCreateAttachment_WithNullContentLength() throws IOException {
    // Test scenario where content-length header is null
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    Map<String, String> emptyHeaders = new HashMap<>();
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(emptyHeaders);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.empty());

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  @Test
  public void testCreateAttachment_WithEmptyContentLength() throws IOException {
    // Test scenario where content-length header is empty string
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    Map<String, String> headersWithEmpty = new HashMap<>();
    headersWithEmpty.put("content-length", "");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headersWithEmpty);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity2 = mock(CdsEntity.class);
    when(mockAttachmentEntity2.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity2);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.empty());

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  @Test
  public void testCreateAttachment_VirusScanEnabledExceedsLimit() throws IOException {
    // Test scenario where virus scan is enabled and file size exceeds 400MB limit
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(true);
    repoValue.setVersionEnabled(false);
    repoValue.setDisableVirusScannerForLargeFile(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    Map<String, String> largeFileHeaders = new HashMap<>();
    largeFileHeaders.put("content-length", String.valueOf(500 * 1024 * 1024L)); // 500MB
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(largeFileHeaders);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("VIRUS_REPO_ERROR_MORE_THAN_400MB");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("You cannot upload files that are larger than 400 MB", thrown.getMessage());
  }

  @Test
  public void testCreateAttachment_VirusScanEnabledWithinLimit() throws IOException {
    // Test scenario where virus scan is enabled but file size is within 400MB limit
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(true);
    repoValue.setVersionEnabled(false);
    repoValue.setDisableVirusScannerForLargeFile(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    Map<String, String> normalFileHeaders = new HashMap<>();
    normalFileHeaders.put("content-length", String.valueOf(100 * 1024 * 1024L)); // 100MB
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(normalFileHeaders);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity3 = mock(CdsEntity.class);
    when(mockAttachmentEntity3.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity3);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.empty());

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  @Test
  public void testCreateAttachment_VirusScanDisabledForLargeFile() throws IOException {
    // Test scenario where virus scan is enabled but disabled for large files
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(true);
    repoValue.setVersionEnabled(false);
    repoValue.setDisableVirusScannerForLargeFile(true);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    Map<String, String> largeFileHeaders = new HashMap<>();
    largeFileHeaders.put("content-length", String.valueOf(500 * 1024 * 1024L)); // 500MB
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(largeFileHeaders);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity4 = mock(CdsEntity.class);
    when(mockAttachmentEntity4.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity4);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.empty());

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  @Test
  public void testMarkAttachmentAsDeleted_WithNullObjectId() throws IOException {
    // Test scenario where objectId is null
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    when(deleteContext.getContentId()).thenReturn("null:folderId:entity:subdomain");

    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(deleteContext).setCompleted();
    verify(sdmService, never()).deleteDocument(anyString(), anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testMarkAttachmentAsDeleted_WithInsufficientContextValues() throws IOException {
    // Test scenario where contentId has insufficient parts (less than 3)
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    when(deleteContext.getContentId()).thenReturn("only-one-part");

    // With less than 3 parts, the handler skips processing and calls setCompleted()
    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(deleteContext).setCompleted();
    verify(sdmService, never()).deleteDocument(anyString(), anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testMarkAttachmentAsDeleted_WithEmptyString() throws IOException {
    // Test scenario where contentId is empty (contextValues.length = 0)
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    when(deleteContext.getContentId()).thenReturn("");

    // Empty string split results in array of length 1, handler skips processing and calls
    // setCompleted()
    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(deleteContext).setCompleted();
    verify(sdmService, never()).deleteDocument(anyString(), anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testMarkAttachmentAsDeleted_DeleteFolderWhenNoAttachments() throws IOException {
    // Test scenario where no attachments exist for folder, so folder should be deleted
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteContext.getContentId()).thenReturn("objectId:folderId:entity:subdomain");
    when(deleteContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");

    // Mock empty list for no attachments in folder
    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(sdmService).deleteDocument("deleteTree", "folderId", "testUser", false);
    verify(deleteContext).setCompleted();
  }

  @Test
  public void testMarkAttachmentAsDeleted_DeleteObjectWhenNotPresent() throws IOException {
    // Test scenario where objectId is not present in attachments list
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteContext.getContentId()).thenReturn("objectId:folderId:entity:subdomain");
    when(deleteContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");

    // Mock attachments list without the target objectId
    CmisDocument otherDoc = new CmisDocument();
    otherDoc.setObjectId("otherObjectId");
    List<CmisDocument> attachments = Arrays.asList(otherDoc);
    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(attachments);

    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(sdmService).deleteDocument("delete", "objectId", "testUser", false);
    verify(deleteContext).setCompleted();
  }

  @Test
  public void testMarkAttachmentAsDeleted_ObjectIdPresent() throws IOException {
    // Test scenario where objectId is present in attachments list (should not delete)
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteContext.getContentId()).thenReturn("objectId:folderId:entity:subdomain");
    when(deleteContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");

    // Mock attachments list with the target objectId
    CmisDocument targetDoc = new CmisDocument();
    targetDoc.setObjectId("objectId");
    List<CmisDocument> attachments = Arrays.asList(targetDoc);
    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(attachments);

    handlerSpy.markAttachmentAsDeleted(deleteContext);

    verify(sdmService, never()).deleteDocument(anyString(), anyString(), anyString(), anyBoolean());
    verify(deleteContext).setCompleted();
  }

  @Test
  public void testReadAttachment_ValidContentId() throws IOException {
    // Test scenario for successful attachment reading
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("objectId:folderId:entity");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);
    handlerSpy.readAttachment(readContext);

    verify(sdmService).readDocument(eq("objectId"), eq(mockSdmCredentials), eq(readContext));
  }

  @Test
  public void testReadAttachment_InvalidContentId() throws IOException {
    // Test scenario with insufficient contentId parts
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("invalid");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);
    // This should work as readAttachment handles the parsing internally
    handlerSpy.readAttachment(readContext);

    // Verify the method was called with the full contentId as objectId
    verify(sdmService).readDocument(eq("invalid"), eq(mockSdmCredentials), eq(readContext));
  }

  @Test
  public void testRestoreAttachment_CompletesSuccessfully() {
    // Test scenario for restore attachment (should just complete)
    AttachmentRestoreEventContext restoreContext = mock(AttachmentRestoreEventContext.class);

    handlerSpy.restoreAttachment(restoreContext);

    verify(restoreContext).setCompleted();
  }

  @Test
  public void testDuplicateCheck_WithEmptyResult() {
    // Test scenario with no existing attachments
    Result mockResult = mock(Result.class);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    boolean isDuplicate = handlerSpy.duplicateCheck("test.pdf", "new-id", mockResult);

    assertFalse(isDuplicate);
  }

  @Test
  public void testCreateAttachment_WithInvalidParameterInfo() throws IOException {
    // Test scenario where ParameterInfo is null
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    when(mockContext.getParameterInfo()).thenReturn(null);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");

    // This should throw a NullPointerException or be handled gracefully
    assertThrows(
        Exception.class,
        () -> {
          handlerSpy.createAttachment(mockContext);
        });
  }

  @Test
  public void testCreateAttachment_WithNullHeaders() throws IOException {
    // Test scenario where headers are null
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(null);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");

    // This should throw a NullPointerException or be handled gracefully
    assertThrows(
        Exception.class,
        () -> {
          handlerSpy.createAttachment(mockContext);
        });
  }

  @Test
  public void testReadAttachment_ExceptionInService() throws IOException {
    // Test scenario where sdmService.readDocument throws an exception
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("objectId:folderId");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

    // Mock service to throw exception
    doThrow(new RuntimeException("Service error"))
        .when(sdmService)
        .readDocument(
            anyString(), any(SDMCredentials.class), any(AttachmentReadEventContext.class));
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.readAttachment(readContext);
            });

    assertEquals("Service error", thrown.getMessage());
  }

  @Test
  public void testReadAttachment_WithSinglePartContentId() throws IOException {
    // Test scenario with single part content ID (no colon separator)
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("singleObjectId");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_SUCCESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);

    handlerSpy.readAttachment(readContext);

    // Should call readDocument with the full contentId as objectId
    verify(sdmService).readDocument(eq("singleObjectId"), eq(mockSdmCredentials), eq(readContext));
    verify(readContext).setCompleted();
  }

  @Test
  public void testReadAttachment_WithSinglePartContentId_NotSuccess() throws IOException {
    // Test scenario with single part content ID (no colon separator)
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("singleObjectId");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.readAttachment(readContext);
            });
    // Should fail on draft entity not found, not on virus scan
    assertEquals(
        "Virus detected. Remove the file and upload a clean version.", thrown.getMessage());
  }

  @Test
  public void testReadAttachment_WithSinglePartContentId_Uploading() throws IOException {
    // Test scenario with single part content ID (no colon separator)
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("singleObjectId");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_IN_PROGRESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.readAttachment(readContext);
            });
    // Should fail on draft entity not found, not on virus scan
    assertEquals("UPLOAD_IN_PROGRESS_FILE_ERROR", thrown.getMessage());
  }

  @Test
  public void testReadAttachment_WithSinglePartContentId_ScanInProgress() throws IOException {
    // Test scenario with single part content ID (no colon separator)
    AttachmentReadEventContext readContext = mock(AttachmentReadEventContext.class);
    when(readContext.getContentId()).thenReturn("singleObjectId");
    when(readContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("dummyToken");

    SDMCredentials mockSdmCredentials = new SDMCredentials();
    when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setUploadStatus(SDMConstants.VIRUS_SCAN_INPROGRESS);
    when(dbQuery.getuploadStatusForAttachment(any(), any(), any(), any())).thenReturn(cmisDocument);

    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.readAttachment(readContext);
            });
    // Should fail on draft entity not found, not on virus scan
    assertEquals(
        "Scan in progress. Wait until the scan is complete before opening the file.",
        thrown.getMessage());
  }

  @Test
  public void testMarkAttachmentAsDeleted_MultipleObjectsInFolder() throws IOException {
    // Test scenario where multiple attachments exist and target object is among them
    AttachmentMarkAsDeletedEventContext deleteContext =
        mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteContext.getContentId()).thenReturn("targetObjectId:folderId:entity:subdomain");
    when(deleteContext.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");

    // Mock attachments list with multiple objects including target
    CmisDocument targetDoc = new CmisDocument();
    targetDoc.setObjectId("targetObjectId");
    CmisDocument otherDoc = new CmisDocument();
    otherDoc.setObjectId("otherObjectId");
    List<CmisDocument> attachments = Arrays.asList(targetDoc, otherDoc);
    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(attachments);

    handlerSpy.markAttachmentAsDeleted(deleteContext);

    // Should not call delete on either document since target is present
    verify(sdmService, never()).deleteDocument(anyString(), anyString(), anyString(), anyBoolean());
    verify(deleteContext).setCompleted();
  }

  @Test
  public void testCreateAttachment_LargeFileVirusScanDisabled() throws IOException {
    // Test large file with virus scan disabled should proceed
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false); // Virus scan disabled
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Set large file size (600MB)
    Map<String, String> largeFileHeaders = new HashMap<>();
    largeFileHeaders.put("content-length", String.valueOf(600 * 1024 * 1024L));
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(largeFileHeaders);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity5 = mock(CdsEntity.class);
    when(mockAttachmentEntity5.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity5);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.empty());

    // Should not throw exception for large file when virus scan is disabled
    ServiceException thrown =
        assertThrows(
            ServiceException.class,
            () -> {
              handlerSpy.createAttachment(mockContext);
            });
    // Should fail on draft entity not found, not on virus scan
    assertEquals("Attachment draft entity not found", thrown.getMessage());
  }

  // ========== Tests for Active Entity Attachment Creation ==========

  @Test
  public void testThreadLocalCleanedUpAtStartOfCreate() throws IOException {
    // Verify that SDM_METADATA_THREADLOCAL is defensively cleaned up at the start of
    // createAttachment
    Map<String, Object> staleMetadata = new HashMap<>();
    staleMetadata.put("attachmentId", "stale-id");
    SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.set(staleMetadata);

    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(true); // Will fail early on versioned repo
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");

    assertThrows(
        ServiceException.class,
        () -> {
          handlerSpy.createAttachment(mockContext);
        });

    // ThreadLocal should have been removed at the start
    assertNull(SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get());
  }

  @Test
  public void testCreateActiveEntity_SuccessStoresMetadataInThreadLocal() throws IOException {
    // When creating an attachment in active entity context (not draft),
    // the handler should store metadata in ThreadLocal and finalize context
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    // Active entity (no _drafts suffix)
    CdsEntity mockActiveEntity = mock(CdsEntity.class);
    when(mockActiveEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("objectId", "obj123");
    mockCreateResult.put("mimeType", "application/pdf");
    mockCreateResult.put("uploadStatus", "Success");
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");
    when(mockContext.getModel()).thenReturn(cdsModel);
    // findEntity returns active entity (no _drafts variant)
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockActiveEntity));
    when(mockActiveEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");
    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo);
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders);
    // Mock getAttachmentEntity for both processEntities and handleCreateDocumentResult
    when(mockContext.getAttachmentEntity()).thenReturn(mockActiveEntity);
    // isDraftContext: parent draft entity NOT present => not draft
    when(cdsModel.findEntity("AdminService.Books_drafts")).thenReturn(Optional.empty());

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      handlerSpy.createAttachment(mockContext);

      // Verify ThreadLocal was set with correct metadata
      Map<String, Object> metadata = SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get();
      assertNotNull(metadata);
      assertEquals("id", metadata.get("attachmentId"));
      assertEquals("obj123", metadata.get("objectId"));
      assertEquals("folderid", metadata.get("folderId"));
      assertEquals("application/pdf", metadata.get("mimeType"));
      assertEquals("Success", metadata.get("uploadStatus"));
      assertEquals(mockActiveEntity, metadata.get("attachmentEntity"));

      // Verify context was finalized
      verify(mockContext).setCompleted();

      // Cleanup ThreadLocal
      SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.remove();
    }
  }

  @Test
  public void testCreateDraftEntity_SuccessCallsAddAttachmentToDraft() throws IOException {
    // When creating an attachment in draft context,
    // the handler should call addAttachmentToDraft and NOT set ThreadLocal
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockDraftEntityLocal = mock(CdsEntity.class);
    when(mockDraftEntityLocal.getQualifiedName())
        .thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "Example content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "success");
    mockCreateResult.put("objectId", "obj456");
    mockCreateResult.put("mimeType", "text/plain");
    mockCreateResult.put("uploadStatus", "Success");
    when(mockMediaData.getFileName()).thenReturn("readme.txt");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    // findEntity returns different entities based on name
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntityLocal));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(mock(CdsEntity.class)));
    when(mockDraftEntityLocal.findAssociation("up_"))
        .thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");
    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo);
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders);
    // Draft context: parent draft entity IS present and record exists in draft table
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    Result draftResult = mock(Result.class);
    when(draftResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class))).thenReturn(draftResult);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      handlerSpy.createAttachment(mockContext);

      // Verify addAttachmentToDraft was called (draft path)
      verify(dbQuery).addAttachmentToDraft(any(), any(), any());
      // Verify ThreadLocal was NOT set (draft path should not set it)
      assertNull(SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get());
      // Verify context was finalized
      verify(mockContext).setCompleted();
    }
  }

  @Test
  public void testIsDraftContext_ParentExistsInDraftTable() throws IOException {
    // Tests isDraftContext returning true when parent record exists in draft table
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockDraftEntityLocal = mock(CdsEntity.class);
    when(mockDraftEntityLocal.getQualifiedName())
        .thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    when(mockMediaData.getFileName()).thenReturn("test.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockContext.getModel()).thenReturn(cdsModel);
    // findEntity for draft entity
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntityLocal));
    // findEntity for parent draft entity
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));
    when(mockDraftEntityLocal.findAssociation("up_"))
        .thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    // Parent record exists in draft table
    Result draftResult = mock(Result.class);
    when(draftResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class))).thenReturn(draftResult);

    // Should use draft entity since parent exists in draft table
    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
      when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
      JSONObject mockCreateResult = new JSONObject();
      mockCreateResult.put("status", "success");
      mockCreateResult.put("objectId", "obj789");
      mockCreateResult.put("mimeType", "application/pdf");
      mockCreateResult.put("uploadStatus", "Success");
      when(documentUploadService.createDocument(
              any(CmisDocument.class),
              any(SDMCredentials.class),
              anyBoolean(),
              any(AttachmentCreateEventContext.class)))
          .thenReturn(mockCreateResult);

      handlerSpy.createAttachment(mockContext);

      // Verify addAttachmentToDraft was called (draft path was chosen)
      verify(dbQuery).addAttachmentToDraft(eq(mockDraftEntityLocal), any(), any());
    }
  }

  @Test
  public void testIsDraftContext_ParentNotInDraftTable() throws IOException {
    // Tests isDraftContext returning false when parent record does NOT exist in draft table
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockActiveEntity = mock(CdsEntity.class);
    when(mockActiveEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    when(mockMediaData.getFileName()).thenReturn("test.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockContext.getModel()).thenReturn(cdsModel);
    // findEntity for active entity
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(mockActiveEntity));
    // findEntity for parent draft entity
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));
    when(mockActiveEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    // Mock findAssociation on mockAttachmentEntity so getUpIdKey returns "up__ID"
    CdsElement attAssocElm = mock(CdsElement.class);
    CdsAssociationType attAssocType = mock(CdsAssociationType.class);
    CqnElementRef attRef = mock(CqnElementRef.class);
    when(mockAttachmentEntity.findAssociation("up_")).thenReturn(Optional.of(attAssocElm));
    when(attAssocElm.getType()).thenReturn(attAssocType);
    when(attAssocType.refs()).thenAnswer(inv -> Stream.of(attRef));
    when(attRef.path()).thenReturn("ID");
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");
    // Parent record does NOT exist in draft table
    Result draftResult = mock(Result.class);
    when(draftResult.first()).thenReturn(Optional.empty());
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class))).thenReturn(draftResult);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
      when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
      JSONObject mockCreateResult = new JSONObject();
      mockCreateResult.put("status", "success");
      mockCreateResult.put("objectId", "obj789");
      mockCreateResult.put("mimeType", "application/pdf");
      mockCreateResult.put("uploadStatus", "Success");
      when(documentUploadService.createDocument(
              any(CmisDocument.class),
              any(SDMCredentials.class),
              anyBoolean(),
              any(AttachmentCreateEventContext.class)))
          .thenReturn(mockCreateResult);

      handlerSpy.createAttachment(mockContext);

      // Verify addAttachmentToDraft was NOT called (active path chosen)
      verify(dbQuery, never()).addAttachmentToDraft(any(), any(), any());
      // Verify ThreadLocal was set (active entity path)
      Map<String, Object> metadata = SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get();
      assertNotNull(metadata);
      assertEquals("obj789", metadata.get("objectId"));
      SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.remove();
    }
  }

  @Test
  public void testIsDraftContext_ExceptionDefaultsToDraft() throws IOException {
    // When isDraftContext throws an exception, it should default to true (draft)
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    MediaData mockMediaData = mock(MediaData.class);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    List<Row> nonEmptyRowList = List.of(mockRow);
    CdsEntity mockDraftEntityLocal = mock(CdsEntity.class);
    when(mockDraftEntityLocal.getQualifiedName())
        .thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    when(mockMediaData.getFileName()).thenReturn("test.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftEntityLocal));
    // Parent draft entity query throws exception
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenThrow(new RuntimeException("DB error"));
    when(mockDraftEntityLocal.findAssociation("up_"))
        .thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
      when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
      JSONObject mockCreateResult = new JSONObject();
      mockCreateResult.put("status", "success");
      mockCreateResult.put("objectId", "obj789");
      mockCreateResult.put("mimeType", "application/pdf");
      mockCreateResult.put("uploadStatus", "Success");
      when(documentUploadService.createDocument(
              any(CmisDocument.class),
              any(SDMCredentials.class),
              anyBoolean(),
              any(AttachmentCreateEventContext.class)))
          .thenReturn(mockCreateResult);

      handlerSpy.createAttachment(mockContext);

      // Should default to draft path - addAttachmentToDraft called
      verify(dbQuery).addAttachmentToDraft(eq(mockDraftEntityLocal), any(), any());
      // ThreadLocal should NOT be set (draft path)
      assertNull(SDMAttachmentsServiceHandler.SDM_METADATA_THREADLOCAL.get());
    }
  }

  @Test
  public void testIsDraftContext_NoDraftEntityInModel_DefaultsToDraft() throws IOException {
    // When no parent draft entity exists in model (e.g., entity without draft support),
    // isDraftContext defaults to true (draft)
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "upid");
    mockAttachmentIds.put("ID", "id");
    mockAttachmentIds.put("repositoryId", "repo1");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(parameterInfo.getHeaders()).thenReturn(headers);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    // 3-part entity name, but parent draft entity NOT in model
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    // Parent draft entity NOT found → isDraftContext will default to true
    when(cdsModel.findEntity("AdminService.Books_drafts")).thenReturn(Optional.empty());
    // Draft entity found
    CdsEntity mockDraftFound = mock(CdsEntity.class);
    when(mockDraftFound.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(mockDraftFound));

    CdsElement mockAssocElm = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(mockDraftFound.findAssociation("up_")).thenReturn(Optional.of(mockAssocElm));
    when(mockAssocElm.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");
    MediaData mockMd = mock(MediaData.class);
    when(mockMd.getFileName()).thenReturn("test.pdf");
    when(mockMd.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockMd.get("mimeType")).thenReturn("text/plain");
    when(mockContext.getData()).thenReturn(mockMd);
    Result mockResult = mock(Result.class);
    Row mockRow = mock(Row.class);
    when(mockResult.list()).thenReturn(List.of(mockRow));

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);
      doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
      when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
      JSONObject mockCreateResult = new JSONObject();
      mockCreateResult.put("status", "success");
      mockCreateResult.put("objectId", "obj111");
      mockCreateResult.put("mimeType", "text/plain");
      mockCreateResult.put("uploadStatus", "Success");
      when(documentUploadService.createDocument(
              any(CmisDocument.class),
              any(SDMCredentials.class),
              anyBoolean(),
              any(AttachmentCreateEventContext.class)))
          .thenReturn(mockCreateResult);

      handlerSpy.createAttachment(mockContext);

      // Should default to draft path (addAttachmentToDraft called)
      verify(dbQuery).addAttachmentToDraft(eq(mockDraftFound), any(), any());
    }
  }

  @Test
  public void testDuplicateStatus_ActiveEntityExistingAttachment_CompletesGracefully()
      throws IOException {
    // When a duplicate is detected but the attachment already exists in active entity with
    // objectId, the handler should complete gracefully rather than throwing
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
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    byte[] byteArray = "content".getBytes();
    InputStream contentStream = new ByteArrayInputStream(byteArray);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "duplicate");
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(contentStream);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name_drafts");
    when(mockAssociationElement.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");
    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo);
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders);
    // Active entity with existing objectId
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    // Return active entity in model
    CdsEntity activeEntity = mock(CdsEntity.class);
    when(cdsModel.findEntity("some.qualified.name")).thenReturn(Optional.of(activeEntity));
    // Mock existing attachment in active entity
    CmisDocument existingDoc = new CmisDocument();
    existingDoc.setObjectId("existing-obj-id");
    existingDoc.setFolderId("existing-folder-id");
    existingDoc.setMimeType("application/pdf");
    when(dbQuery.getObjectIdForAttachmentID(eq(activeEntity), any(), eq("id")))
        .thenReturn(existingDoc);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      // Should NOT throw - should complete gracefully
      handlerSpy.createAttachment(mockContext);

      // Verify context was completed
      verify(mockContext).setCompleted();
    }
  }

  @Test
  public void testDuplicateStatus_ActiveEntityNoExistingAttachment_ThrowsException()
      throws IOException {
    // When duplicate is detected and attachment does NOT exist in active entity,
    // should throw ServiceException
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
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);
    JSONObject mockCreateResult = new JSONObject();
    mockCreateResult.put("status", "duplicate");
    when(mockMediaData.getFileName()).thenReturn("sample.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(cdsModel.findEntity(anyString())).thenReturn(Optional.of(mockEntity));
    when(mockEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockEntity.getQualifiedName()).thenReturn("some.qualified.name_drafts");
    when(mockAssociationElement.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");
    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t123");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(mockResult.list()).thenReturn(nonEmptyRowList);
    when(mockContext.getAuthenticationInfo()).thenReturn(mockAuthInfo);
    when(mockAuthInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(mockJwtTokenInfo);
    when(mockJwtTokenInfo.getToken()).thenReturn("mockedJwtToken");
    when(mockContext.getData()).thenReturn(mockMediaData);
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderid");
    when(documentUploadService.createDocument(
            any(CmisDocument.class),
            any(SDMCredentials.class),
            anyBoolean(),
            any(AttachmentCreateEventContext.class)))
        .thenReturn(mockCreateResult);
    ParameterInfo mockParameterInfo = mock(ParameterInfo.class);
    Map<String, String> mockHeaders = new HashMap<>();
    mockHeaders.put("content-length", "12345");
    when(mockContext.getParameterInfo()).thenReturn(mockParameterInfo);
    when(mockParameterInfo.getHeaders()).thenReturn(mockHeaders);
    // Active entity with NO existing objectId (null)
    CdsEntity mockAttachmentEntity = mock(CdsEntity.class);
    when(mockAttachmentEntity.getQualifiedName()).thenReturn("some.qualified.name");
    when(mockContext.getAttachmentEntity()).thenReturn(mockAttachmentEntity);
    CdsEntity activeEntity = mock(CdsEntity.class);
    when(cdsModel.findEntity("some.qualified.name")).thenReturn(Optional.of(activeEntity));
    CmisDocument emptyDoc = new CmisDocument();
    emptyDoc.setObjectId(null);
    when(dbQuery.getObjectIdForAttachmentID(eq(activeEntity), any(), eq("id")))
        .thenReturn(emptyDoc);

    try (MockedStatic<SDMUtils> sdmUtilsMockedStatic =
        mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMockedStatic
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(0L);
      when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(0L);
      SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
      when(tokenHandler.getSDMCredentials()).thenReturn(mockSdmCredentials);

      // Should throw ServiceException for genuine duplicate
      ServiceException thrown =
          assertThrows(
              ServiceException.class,
              () -> {
                handlerSpy.createAttachment(mockContext);
              });

      assertTrue(thrown.getMessage().contains("sample.pdf"));
    }
  }

  // =====================================================================
  // Tests for isUploadable / Upload button disable-enable feature
  // =====================================================================

  // ----- deriveFacetFieldName (via checkAndUpdateIsUploadableOnCreate) -----

  @Test
  public void testCreateDraft_MaxCountReached_SetsIsAttachmentsUploadableFalse()
      throws IOException {
    // Arrange: successful upload on a draft entity; count == maxCount after upload
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "parent-uuid");
    mockAttachmentIds.put("ID", "attach-id");
    mockAttachmentIds.put("repositoryId", "repo1");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.getFileName()).thenReturn("file.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    // Qualified name ends with _drafts so handler treats it as draft path
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");

    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");

    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(baseAttachEntity);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");

    // findEntity("AdminService.Books.attachments_drafts") → draftAttachEntity
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    // findEntity("AdminService.Books_drafts") → parentDraftEntity (for updateParentIsUploadable)
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));

    // isDraftContext: parent draft entity exists and parent record is in draft table
    Result draftCheckResult = mock(Result.class);
    when(draftCheckResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class)))
        .thenReturn(draftCheckResult);

    // 1st call (pre-upload constraint check): count=1 < maxCount=2, upload is allowed
    // 2nd call (post-upload check in checkAndUpdateIsUploadableOnCreate): count=2 == maxCount=2,
    // triggers disable
    Result preCheckResult = mock(Result.class);
    when(preCheckResult.rowCount()).thenReturn(1L);
    Result attachResult = mock(Result.class);
    when(attachResult.list()).thenReturn(List.of(mock(Row.class)));
    when(attachResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
        .thenReturn(preCheckResult, attachResult);
    when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);
    // updateIsUploadableOnParentEntity returns 1 (draft row updated)
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(false)))
        .thenReturn(1L);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folder1");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());

    JSONObject successResult = new JSONObject();
    successResult.put("status", "success");
    successResult.put("objectId", "obj1");
    successResult.put("mimeType", "application/pdf");
    successResult.put("uploadStatus", "Success");
    when(documentUploadService.createDocument(any(), any(), anyBoolean(), any()))
        .thenReturn(successResult);

    ParameterInfo mockParamInfo = mock(ParameterInfo.class);
    when(mockParamInfo.getHeaders()).thenReturn(Map.of("content-length", "1024"));
    when(mockContext.getParameterInfo()).thenReturn(mockParamInfo);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      // maxCount = 2
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.createAttachment(mockContext);
    }

    // Verify the parent draft row was updated with isAttachmentsUploadable=false
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(false));
  }

  @Test
  public void testCreateDraft_BelowMaxCount_DoesNotSetIsUploadable() throws IOException {
    // Arrange: successful upload but count < maxCount → no isUploadable update
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "parent-uuid");
    mockAttachmentIds.put("ID", "attach-id");
    mockAttachmentIds.put("repositoryId", "repo1");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.getFileName()).thenReturn("file.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");

    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(baseAttachEntity);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(mock(CdsEntity.class)));

    Result draftCheckResult = mock(Result.class);
    when(draftCheckResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class)))
        .thenReturn(draftCheckResult);

    Result attachResult = mock(Result.class);
    when(attachResult.list()).thenReturn(List.of(mock(Row.class)));
    // count = 1, maxCount = 2 → below threshold, no update expected
    when(attachResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folder1");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());

    JSONObject successResult = new JSONObject();
    successResult.put("status", "success");
    successResult.put("objectId", "obj1");
    successResult.put("mimeType", "application/pdf");
    successResult.put("uploadStatus", "Success");
    when(documentUploadService.createDocument(any(), any(), anyBoolean(), any()))
        .thenReturn(successResult);

    ParameterInfo mockParamInfo = mock(ParameterInfo.class);
    when(mockParamInfo.getHeaders()).thenReturn(Map.of("content-length", "1024"));
    when(mockContext.getParameterInfo()).thenReturn(mockParamInfo);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.createAttachment(mockContext);
    }

    // updateIsUploadableOnParentEntity must NOT be called when count < maxCount
    verify(dbQuery, never())
        .updateIsUploadableOnParentEntity(any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  public void testCreateDraft_ReferencesMaxCountReached_SetsIsReferencesUploadableFalse()
      throws IOException {
    // Verifies deriveFacetFieldName works for "references" → "isReferencesUploadable"
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "parent-uuid");
    mockAttachmentIds.put("ID", "attach-id");
    mockAttachmentIds.put("repositoryId", "repo1");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.getFileName()).thenReturn("file.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    // "references" facet
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.references_drafts");

    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");

    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.references");
    when(mockContext.getAttachmentEntity()).thenReturn(baseAttachEntity);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");

    when(cdsModel.findEntity("AdminService.Books.references_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));

    Result draftCheckResult = mock(Result.class);
    when(draftCheckResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class)))
        .thenReturn(draftCheckResult);

    // 1st call (pre-upload constraint check): count=2 < maxCount=3, upload is allowed
    // 2nd call (post-upload check in checkAndUpdateIsUploadableOnCreate): count=3 == maxCount=3,
    // triggers disable
    Result preCheckResult = mock(Result.class);
    when(preCheckResult.rowCount()).thenReturn(2L);
    Result attachResult = mock(Result.class);
    when(attachResult.list()).thenReturn(List.of(mock(Row.class)));
    when(attachResult.rowCount()).thenReturn(3L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
        .thenReturn(preCheckResult, attachResult);
    when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isReferencesUploadable"),
            eq(false)))
        .thenReturn(1L);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folder1");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());

    JSONObject successResult = new JSONObject();
    successResult.put("status", "success");
    successResult.put("objectId", "obj1");
    successResult.put("mimeType", "application/pdf");
    successResult.put("uploadStatus", "Success");
    when(documentUploadService.createDocument(any(), any(), anyBoolean(), any()))
        .thenReturn(successResult);

    ParameterInfo mockParamInfo = mock(ParameterInfo.class);
    when(mockParamInfo.getHeaders()).thenReturn(Map.of("content-length", "1024"));
    when(mockContext.getParameterInfo()).thenReturn(mockParamInfo);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(3L);

      handlerSpy.createAttachment(mockContext);
    }

    // Must update isReferencesUploadable, not isAttachmentsUploadable
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isReferencesUploadable"),
            eq(false));
    verify(dbQuery, never())
        .updateIsUploadableOnParentEntity(
            any(), any(), any(), any(), eq("isAttachmentsUploadable"), anyBoolean());
  }

  // ----- contentId format: upID embedded as 4th segment -----

  @Test
  public void testFinalizeContext_EmbeddsUpIdAsSegment4() throws IOException {
    // Verifies that after a successful draft upload the contentId is
    // objectId:folderId:entityName:upID
    Map<String, Object> mockAttachmentIds = new HashMap<>();
    mockAttachmentIds.put("up__ID", "parent-uuid");
    mockAttachmentIds.put("ID", "attach-id");
    mockAttachmentIds.put("repositoryId", "repo1");

    MediaData mockMediaData = mock(MediaData.class);
    when(mockMediaData.getFileName()).thenReturn("file.pdf");
    when(mockMediaData.getContent()).thenReturn(new ByteArrayInputStream("content".getBytes()));
    when(mockMediaData.get("mimeType")).thenReturn("application/pdf");

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");

    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");

    when(mockContext.getAttachmentIds()).thenReturn(mockAttachmentIds);
    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    when(mockContext.getAttachmentEntity()).thenReturn(baseAttachEntity);
    when(mockContext.getData()).thenReturn(mockMediaData);
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("t1");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));

    Result draftCheckResult = mock(Result.class);
    when(draftCheckResult.first()).thenReturn(Optional.of(mock(Row.class)));
    when(persistenceService.run(any(com.sap.cds.ql.cqn.CqnSelect.class)))
        .thenReturn(draftCheckResult);

    Result attachResult = mock(Result.class);
    when(attachResult.list()).thenReturn(List.of(mock(Row.class)));
    // count(1) < maxCount(2) so no disable call, but finalize still happens
    when(attachResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), anyString(), anyString()))
        .thenReturn(attachResult);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderX");
    when(tokenHandler.getSDMCredentials()).thenReturn(mock(SDMCredentials.class));
    doReturn(false).when(handlerSpy).duplicateCheck(any(), any(), any());

    JSONObject successResult = new JSONObject();
    successResult.put("status", "success");
    successResult.put("objectId", "sdmObjId");
    successResult.put("mimeType", "application/pdf");
    successResult.put("uploadStatus", "Success");
    when(documentUploadService.createDocument(any(), any(), anyBoolean(), any()))
        .thenReturn(successResult);

    ParameterInfo mockParamInfo = mock(ParameterInfo.class);
    when(mockParamInfo.getHeaders()).thenReturn(Map.of("content-length", "1024"));
    when(mockContext.getParameterInfo()).thenReturn(mockParamInfo);

    // Capture the value passed to setContentId
    ArgumentCaptor<String> contentIdCaptor = ArgumentCaptor.forClass(String.class);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.createAttachment(mockContext);
    }

    verify(mockContext).setContentId(contentIdCaptor.capture());
    String contentId = contentIdCaptor.getValue();
    String[] parts = contentId.split(":");
    assertEquals(4, parts.length, "contentId must have 4 colon-separated segments");
    assertEquals("sdmObjId", parts[0]);
    assertEquals("folderX", parts[1]);
    assertEquals("AdminService.Books.attachments", parts[2]);
    assertEquals("parent-uuid", parts[3]);
  }

  // ----- markAttachmentAsDeleted: upID from 4th segment + re-enable -----

  @Test
  public void testMarkAttachmentAsDeleted_ReEnablesButtonWhenCountDropsBelowMax()
      throws IOException {
    // Setup: contentId has upID as 4th segment (new format)
    // count in DB = 2, maxCount = 2, so after delete count-1=1 < 2 → re-enable
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteCtx.getContentId())
        .thenReturn("sdmObjId:folderId:AdminService.Books.attachments:parent-uuid");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    // No other docs in the folder → folder will be deleted
    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");

    // Draft attachment entity found, base entity found
    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));

    // Count query on draft entity = 2 (the record being deleted is still in DB)
    Result countResult = mock(Result.class);
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(
            eq(draftAttachEntity), any(), eq("parent-uuid"), eq("up__ID")))
        .thenReturn(countResult);

    // updateIsUploadableOnParentEntity on draft parent returns 1 (row found and updated)
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true)))
        .thenReturn(1L);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.markAttachmentAsDeleted(deleteCtx);
    }

    // Parent draft row must be set to isAttachmentsUploadable=true
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true));
  }

  @Test
  public void testMarkAttachmentAsDeleted_NoReEnableWhenCountStillAtMax() throws IOException {
    // count after delete would still be >= maxCount → no re-enable
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteCtx.getContentId())
        .thenReturn("sdmObjId:folderId:AdminService.Books.attachments:parent-uuid");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));

    // count = 3, maxCount = 2, count-1 = 2 which is NOT < maxCount → no update
    Result countResult = mock(Result.class);
    when(countResult.rowCount()).thenReturn(3L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(
            eq(draftAttachEntity), any(), eq("parent-uuid"), eq("up__ID")))
        .thenReturn(countResult);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.markAttachmentAsDeleted(deleteCtx);
    }

    verify(dbQuery, never())
        .updateIsUploadableOnParentEntity(any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  public void testMarkAttachmentAsDeleted_OldFormat_FallbackDbLookup() throws IOException {
    // Old format contentId (3 segments, no upID) → falls back to DB lookup for upID
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    // Only 3 segments — old format before the upID embedding change
    when(deleteCtx.getContentId()).thenReturn("sdmObjId:folderId:AdminService.Books.attachments");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));

    // DB lookup returns the upID (backward-compat path)
    when(dbQuery.getUpIdByObjectId(eq(draftAttachEntity), any(), eq("sdmObjId"), eq("up__ID")))
        .thenReturn("lookup-upid");

    Result countResult = mock(Result.class);
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(
            eq(draftAttachEntity), any(), eq("lookup-upid"), eq("up__ID")))
        .thenReturn(countResult);

    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("lookup-upid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true)))
        .thenReturn(1L);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.markAttachmentAsDeleted(deleteCtx);
    }

    // DB lookup must have been called for old-format contentId
    verify(dbQuery).getUpIdByObjectId(eq(draftAttachEntity), any(), eq("sdmObjId"), eq("up__ID"));
    // Re-enable must still fire
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("lookup-upid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true));
  }

  // ----- updateParentIsUploadable: fallthrough from draft to active when 0 rows updated -----

  @Test
  public void testMarkAttachmentAsDeleted_DraftParentGone_FallsThroughToActiveEntity()
      throws IOException {
    // If draft parent returned 0 rows updated (draft was already activated),
    // the handler must update the active entity instead.
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteCtx.getContentId())
        .thenReturn("sdmObjId:folderId:AdminService.Books.attachments:parent-uuid");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    CdsEntity parentActiveEntity = mock(CdsEntity.class);
    when(parentActiveEntity.getQualifiedName()).thenReturn("AdminService.Books");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));
    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));

    Result countResult = mock(Result.class);
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(
            eq(draftAttachEntity), any(), eq("parent-uuid"), eq("up__ID")))
        .thenReturn(countResult);

    // Draft parent update returns 0 → draft row is gone (post draftActivate)
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true)))
        .thenReturn(0L);
    // Active parent update returns 1
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentActiveEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true)))
        .thenReturn(1L);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.markAttachmentAsDeleted(deleteCtx);
    }

    // Draft update was tried first
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true));
    // Active update must follow as fallthrough
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentActiveEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true));
  }

  @Test
  public void testMarkAttachmentAsDeleted_DraftParentUpdated_DoesNotTouchActiveEntity()
      throws IOException {
    // If draft parent update returns > 0, active entity must NOT be touched
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    when(deleteCtx.getContentId())
        .thenReturn("sdmObjId:folderId:AdminService.Books.attachments:parent-uuid");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    CdsEntity parentActiveEntity = mock(CdsEntity.class);
    when(parentActiveEntity.getQualifiedName()).thenReturn("AdminService.Books");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));
    when(cdsModel.findEntity("AdminService.Books_drafts"))
        .thenReturn(Optional.of(parentDraftEntity));
    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));

    Result countResult = mock(Result.class);
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(
            eq(draftAttachEntity), any(), eq("parent-uuid"), eq("up__ID")))
        .thenReturn(countResult);

    // Draft parent update returns 1 row updated → stop here, do NOT touch active
    when(dbQuery.updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true)))
        .thenReturn(1L);

    try (MockedStatic<SDMUtils> sdmUtilsMock = mockStatic(SDMUtils.class, CALLS_REAL_METHODS)) {
      sdmUtilsMock
          .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
          .thenReturn(2L);

      handlerSpy.markAttachmentAsDeleted(deleteCtx);
    }

    // Draft entity updated
    verify(dbQuery)
        .updateIsUploadableOnParentEntity(
            eq(parentDraftEntity),
            any(),
            eq("parent-uuid"),
            eq("ID"),
            eq("isAttachmentsUploadable"),
            eq(true));
    // Active entity must NOT be touched
    verify(dbQuery, never())
        .updateIsUploadableOnParentEntity(
            eq(parentActiveEntity), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  public void testMarkAttachmentAsDeleted_NoUpID_SkipsIsUploadableUpdate() throws IOException {
    // When upID cannot be resolved (null from contentId and DB lookup), update is skipped
    AttachmentMarkAsDeletedEventContext deleteCtx = mock(AttachmentMarkAsDeletedEventContext.class);
    DeletionUserInfo deletionUserInfo = mock(DeletionUserInfo.class);

    // 4th segment is empty string → treated as null/empty
    when(deleteCtx.getContentId()).thenReturn("sdmObjId:folderId:AdminService.Books.attachments:");
    when(deleteCtx.getDeletionUserInfo()).thenReturn(deletionUserInfo);
    when(deletionUserInfo.getName()).thenReturn("testUser");
    when(deleteCtx.getModel()).thenReturn(cdsModel);

    when(dbQuery.getAttachmentsForFolder(anyString(), any(), anyString(), any()))
        .thenReturn(Collections.emptyList());

    CdsEntity draftAttachEntity = mock(CdsEntity.class);
    when(draftAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments_drafts");
    CdsElement mockAssocElem = mock(CdsElement.class);
    CdsAssociationType mockAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(draftAttachEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssocElem));
    when(mockAssocElem.getType()).thenReturn(mockAssocType);
    when(mockAssocType.refs()).thenAnswer(inv -> Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");

    CdsEntity baseAttachEntity = mock(CdsEntity.class);
    when(baseAttachEntity.getQualifiedName()).thenReturn("AdminService.Books.attachments");
    CdsElement baseAssocElem = mock(CdsElement.class);
    CdsAssociationType baseAssocType = mock(CdsAssociationType.class);
    CqnElementRef baseRef = mock(CqnElementRef.class);
    when(baseAttachEntity.findAssociation("up_")).thenReturn(Optional.of(baseAssocElem));
    when(baseAssocElem.getType()).thenReturn(baseAssocType);
    when(baseAssocType.refs()).thenAnswer(inv -> Stream.of(baseRef));
    when(baseRef.path()).thenReturn("ID");

    when(cdsModel.findEntity("AdminService.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachEntity));
    when(cdsModel.findEntity("AdminService.Books.attachments"))
        .thenReturn(Optional.of(baseAttachEntity));

    // DB lookup also returns null (record already gone)
    when(dbQuery.getUpIdByObjectId(any(), any(), eq("sdmObjId"), eq("up__ID"))).thenReturn(null);

    handlerSpy.markAttachmentAsDeleted(deleteCtx);

    // No isUploadable update should happen
    verify(dbQuery, never())
        .updateIsUploadableOnParentEntity(any(), any(), any(), any(), any(), anyBoolean());
    verify(deleteCtx).setCompleted();
  }
}
