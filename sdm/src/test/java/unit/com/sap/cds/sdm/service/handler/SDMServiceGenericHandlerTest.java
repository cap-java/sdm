package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils;
import com.sap.cds.sdm.model.*;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.SDMServiceGenericHandler;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftCancelEventContext;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.ParameterInfo;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.*;

public class SDMServiceGenericHandlerTest {

  @Mock private RegisterService attachmentService;
  @Mock private PersistenceService persistenceService;
  @Mock private SDMService sdmService;
  @Mock private DocumentUploadService documentService;
  @Mock private DraftService draftService;
  @Mock private DBQuery dbQuery;
  @Mock private TokenHandler tokenHandler;
  @Mock private EventContext mockContext;
  @Mock private CdsModel cdsModel;
  @Mock private CqnSelect cqnSelect;
  @Mock private CdsEntity cdsEntity;
  @Mock private CdsEntity draftEntity;
  @Mock private CdsRuntime cdsRuntime;

  private CmisDocument cmisDocument;
  private SDMCredentials sdmCredentials;
  private SDMServiceGenericHandler sdmServiceGenericHandler;

  private MockedStatic<CqnAnalyzer> cqnAnalyzerMock;
  private MockedStatic<SDMUtils> sdmUtilsMock;
  @Mock ParameterInfo parameterInfo;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    // Prepare a real list with the mock DraftService
    when(draftService.getName()).thenReturn("MyService.MyEntity.attachments");
    when(draftService.newDraft(any(Insert.class))).thenReturn(mock(Result.class));
    List<DraftService> draftServiceList = List.of(draftService);

    sdmServiceGenericHandler =
        new SDMServiceGenericHandler(
            attachmentService,
            persistenceService,
            sdmService,
            documentService,
            draftServiceList,
            dbQuery,
            tokenHandler);

    // Static mock for CqnAnalyzer
    cqnAnalyzerMock = mockStatic(CqnAnalyzer.class);
    sdmUtilsMock = mockStatic(SDMUtils.class);

    cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("12345");

    sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://example.com/");
  }

  @AfterEach
  void tearDown() {
    cqnAnalyzerMock.close();
    sdmUtilsMock.close();
  }

  @Test
  void testCopyAttachments_shouldCopyAttachment() throws IOException {
    when(mockContext.get("up__ID")).thenReturn("123");
    when(mockContext.get("objectIds")).thenReturn("abc, xyz");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    UserInfo userInfo = Mockito.mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    sdmServiceGenericHandler.copyAttachments(mockContext);

    ArgumentCaptor<CopyAttachmentInput> captor = ArgumentCaptor.forClass(CopyAttachmentInput.class);
    verify(attachmentService, times(1)).copyAttachments(captor.capture(), eq(false));
    CopyAttachmentInput input = captor.getValue();
    assert input.upId().equals("123");
    assert input.facet().equals("MyService.MyEntity.attachments");
    assert input.objectIds().equals(List.of("abc", "xyz"));
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testCopyAttachments_ThrowsRuntimeException() throws IOException {
    when(mockContext.get("up__ID")).thenReturn("123");
    when(mockContext.get("objectIds")).thenReturn("abc,xyz");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    UserInfo userInfo = Mockito.mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    doThrow(new RuntimeException("IO error"))
        .when(attachmentService)
        .copyAttachments(any(CopyAttachmentInput.class), eq(false));

    try {
      sdmServiceGenericHandler.copyAttachments(mockContext);
      assert false : "Expected RuntimeException";
    } catch (RuntimeException e) {
      assert e.getMessage().equals("IO error");
    }
    verify(mockContext, never()).setCompleted();
  }

  @Test
  void testCreate_shouldCreateLink() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject createResult = new JSONObject();
    createResult.put("status", "success");
    createResult.put("objectId", "obj123");
    createResult.put("folderId", "folderId123");
    createResult.put("message", "ok");
    when(documentService.createDocument(any(), any(), anyBoolean())).thenReturn(createResult);

    // Act
    sdmServiceGenericHandler.create(mockContext);

    // Assert
    verify(sdmService).checkRepositoryType(anyString(), anyString());
    verify(documentService).createDocument(any(), any(), anyBoolean());
    verify(draftService).newDraft(any(Insert.class));
    verify(mockContext).setCompleted();
  }

  @Test
  void testCreate_ThrowsServiceException_WhenVersionedRepo() throws IOException {
    UserInfo userInfo = mock(UserInfo.class);

    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity");
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(true);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.VERSIONED_REPO_ERROR);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals(SDMConstants.VERSIONED_REPO_ERROR, ex.getMessage());
  }

  @Test
  void testCreate_ShouldThrowSpecifiedExceptionWhenMaxCountReached() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("Maximum two links allowed");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    //
    when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(2L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("2__Maximum two links allowed");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), any())).thenReturn(repoValue);
    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals("Maximum two links allowed", ex.getMessage());
  }

  @Test
  void testCreate_ShouldThrowDefaultExceptionWhenMaxCountReached() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    //
    when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(2L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("2__");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), any())).thenReturn(repoValue);
    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals(String.format(SDMConstants.MAX_COUNT_ERROR_MESSAGE, 2), ex.getMessage());
  }

  @Test
  void testCreate_ShouldThrowExceptionWhenRestrictedCharacterInLinkName() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("test/URL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    //
    when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(true);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), any())).thenReturn(repoValue);
    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals(
        SDMConstants.nameConstraintMessage(Collections.singletonList("test/URL")), ex.getMessage());
  }

  @Test
  void testCreate_ThrowsServiceExceptionOnDuplicateFile() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("duplicateFile.txt");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    // Simulate a duplicate file in the result list
    Map<String, Object> duplicateAttachment = new HashMap<>();
    duplicateAttachment.put("fileName", "duplicateFile.txt");
    duplicateAttachment.put("repositoryId", SDMConstants.REPOSITORY_ID);
    when(mockResult.listOf(Map.class)).thenReturn(List.of(duplicateAttachment));
    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertTrue(ex.getMessage().contains("duplicateFile.txt"));
  }

  @Test
  void testCreate_ThrowsServiceException_WhenCreateDocumentThrowsException() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    when(documentService.createDocument(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Document creation failed"));

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertTrue(
        ex.getMessage().contains("Error occurred while creating attachment")
            || ex.getMessage().contains(AttachmentService.EVENT_CREATE_ATTACHMENT));
    assertTrue(ex.getCause() instanceof RuntimeException);
    assertEquals("Document creation failed", ex.getCause().getMessage());
  }

  @Test
  void testCreate_ThrowsServiceExceptionOnDuplicateStatus() throws IOException {
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("duplicateFile.txt");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject createResult = new JSONObject();
    createResult.put("status", "duplicate");
    createResult.put("objectId", "obj123");
    createResult.put("folderId", "folderId123");
    createResult.put("message", "Duplicate file");
    when(documentService.createDocument(any(), any(), anyBoolean())).thenReturn(createResult);

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertTrue(ex.getMessage().contains("duplicateFile.txt"));
  }

  @Test
  void testCreate_ThrowsServiceExceptionOnFailStatus() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("duplicateFile.txt");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject createResult = new JSONObject();
    createResult.put("status", "fail");
    createResult.put("objectId", "obj123");
    createResult.put("folderId", "folderId123");
    createResult.put("message", "Some error message");
    when(documentService.createDocument(any(), any(), anyBoolean())).thenReturn(createResult);

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals("Some error message", ex.getMessage());
  }

  @Test
  void testCreate_ThrowsServiceExceptionOnUnauthorizedStatus() throws IOException {
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Mock parent entity for key extraction
    CdsEntity mockParentEntity = mock(CdsEntity.class);
    CdsElement mockKeyElement = mock(CdsElement.class);
    when(mockKeyElement.isKey()).thenReturn(true);
    when(mockKeyElement.getName()).thenReturn("ID");
    when(mockParentEntity.elements()).thenReturn(Stream.of(mockKeyElement));
    when(cdsModel.findEntity("MyService.MyEntity")).thenReturn(Optional.of(mockParentEntity));

    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("duplicateFile.txt");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK);

    JSONObject createResult = new JSONObject();
    createResult.put("status", "unauthorized");
    createResult.put("objectId", "obj123");
    createResult.put("folderId", "folderId123");
    createResult.put("message", "Unauthorized");
    when(documentService.createDocument(any(), any(), anyBoolean())).thenReturn(createResult);

    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK, ex.getMessage());
  }

  @Test
  void testOpenAttachment_InternetShortcut() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    CdsModel cdsModel = mock(CdsModel.class);
    CdsEntity cdsEntity = mock(CdsEntity.class);
    CqnSelect cqnSelect = mock(CqnSelect.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);

    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(context.get("cqn")).thenReturn(cqnSelect);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    // Mock for _drafts entity
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(cdsEntity));

    // Mock CmisDocument with internet shortcut
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName("file.url");
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setUrl("http://shortcut-url");

    when(dbQuery.getObjectIdForAttachmentID(cdsEntity, persistenceService, "123"))
        .thenReturn(cmisDocument);

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("http://shortcut-url");
  }

  @Test
  void testOpenAttachment_NonDraftEntity() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    CdsModel cdsModel = mock(CdsModel.class);
    CdsEntity cdsEntity = mock(CdsEntity.class);
    CqnSelect cqnSelect = mock(CqnSelect.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);

    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(context.get("cqn")).thenReturn(cqnSelect);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    // First call returns a CmisDocument with no fileName (simulate non-draft)
    CmisDocument emptyDoc = new CmisDocument();
    emptyDoc.setFileName("");
    emptyDoc.setMimeType("application/pdf");
    emptyDoc.setUrl(null);

    // Second call returns a valid document
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName("file.pdf");
    cmisDocument.setMimeType("application/pdf");
    cmisDocument.setUrl("http://file-url");

    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(cdsEntity));
    when(dbQuery.getObjectIdForAttachmentID(cdsEntity, persistenceService, "123"))
        .thenReturn(emptyDoc) // first call (draft)
        .thenReturn(cmisDocument); // second call (non-draft)

    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("None");
  }

  @Test
  void testOpenAttachment_NonInternetShortcut() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    CdsModel cdsModel = mock(CdsModel.class);
    CdsEntity cdsEntity = mock(CdsEntity.class);
    CqnSelect cqnSelect = mock(CqnSelect.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);

    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(context.get("cqn")).thenReturn(cqnSelect);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(cdsEntity));

    // Mock CmisDocument with non-internet shortcut mime type
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName("file.pdf");
    cmisDocument.setMimeType("application/pdf");
    cmisDocument.setUrl("http://file-url");

    when(dbQuery.getObjectIdForAttachmentID(cdsEntity, persistenceService, "123"))
        .thenReturn(cmisDocument);

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("None");
  }

  @Test
  void testEditLinkSuccess() throws IOException {
    // Arrange
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.getEvent()).thenReturn("editLink");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    UserInfo userInfo = Mockito.mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(cmisDocument);
    when(mockContext.get("url")).thenReturn("http://newlink.com");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject successResponse = new JSONObject();
    successResponse.put("status", "success");
    when(sdmService.editLink(any(CmisDocument.class), any(SDMCredentials.class), eq(false)))
        .thenReturn(successResponse);

    // Act
    sdmServiceGenericHandler.edit(mockContext);

    // Assert
    assertEquals("http://newlink.com", cmisDocument.getUrl());
    verify(persistenceService).run(any(Update.class));
    verify(mockContext).setCompleted();
  }

  @Test
  void testEditLinkFailure() throws IOException {
    // Arrange
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    UserInfo userInfo = Mockito.mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.FAILED_TO_EDIT_LINK_MSG);
    when(userInfo.isSystemUser()).thenReturn(false);

    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(cmisDocument);
    when(mockContext.get("url")).thenReturn("http://badlink.com");

    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject failureResponse = new JSONObject();
    failureResponse.put("status", "error");
    when(sdmService.editLink(any(CmisDocument.class), any(SDMCredentials.class), eq(false)))
        .thenReturn(failureResponse);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.edit(mockContext));
    verify(persistenceService, never()).run(any(Update.class));
    verify(mockContext, never()).setCompleted();
  }

  @Test
  void testOpenAttachment_WithLinkFile() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(context.get("cqn")).thenReturn(cqnSelect);
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    CmisDocument linkDocument = new CmisDocument();
    linkDocument.setFileName("test.url");
    linkDocument.setMimeType("application/internet-shortcut");
    linkDocument.setUrl("http://test.com");
    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(linkDocument);

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("http://test.com");
  }

  @Test
  void testOpenAttachment_WithRegularFile() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(context.get("cqn")).thenReturn(cqnSelect);
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    CmisDocument regularDocument = new CmisDocument();
    regularDocument.setFileName("test.pdf");
    regularDocument.setMimeType("application/pdf");
    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(regularDocument);

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("None");
  }

  @Test
  void testOpenAttachment_FallbackToNonDraftEntity() throws Exception {
    // Arrange
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    when(context.getModel()).thenReturn(cdsModel);
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(context.get("cqn")).thenReturn(cqnSelect);
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    when(cdsModel.findEntity("MyEntity")).thenReturn(Optional.of(cdsEntity));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    // First call returns document with empty filename (triggers fallback)
    CmisDocument emptyDocument = new CmisDocument();
    emptyDocument.setFileName("");
    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(emptyDocument);

    // Second call returns proper document
    CmisDocument properDocument = new CmisDocument();
    properDocument.setFileName("test.url");
    properDocument.setMimeType("application/internet-shortcut");
    properDocument.setUrl("http://fallback.com");
    when(dbQuery.getObjectIdForAttachmentID(eq(cdsEntity), eq(persistenceService), eq("123")))
        .thenReturn(properDocument);

    // Act
    sdmServiceGenericHandler.openAttachment(context);

    // Assert
    verify(context).setResult("http://fallback.com");
    verify(dbQuery).getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123"));
    verify(dbQuery).getObjectIdForAttachmentID(eq(cdsEntity), eq(persistenceService), eq("123"));
  }

  @Test
  void testCreateLink_RepositoryValidationFails() throws IOException {
    // Arrange
    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.VERSIONED_REPO_ERROR_MSG);

    RepoValue repoValue = new RepoValue();
    repoValue.setVersionEnabled(true); // This will trigger validation failure
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
  }

  @Test
  void testCreateLink_LocalizedRepositoryValidationMessage() throws IOException {
    // Arrange
    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn("Custom localized message for versioned repository");

    RepoValue repoValue = new RepoValue();
    repoValue.setVersionEnabled(true);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    ServiceException exception =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals("Custom localized message for versioned repository", exception.getMessage());
  }

  @Test
  void testCreateLink_AttachmentCountConstraintExceeded() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));
    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.ATTACHMENT_MAXCOUNT_ERROR_MSG);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(5L); // Exceeds limit
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("3__Maximum attachments exceeded"); // Max 3, current 5
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
  }

  @Test
  void testCreateLink_RestrictedCharactersInName() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));
    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("test/invalid\\name");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(true);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
  }

  @Test
  void testCreateLink_UnauthorizedError() throws IOException {
    // Arrange
    Result mockResult = mock(Result.class);
    UserInfo userInfo = mock(UserInfo.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.getTarget()).thenReturn(draftEntity);
    when(draftEntity.getQualifiedName()).thenReturn("MyService.MyEntity.attachments");
    when(cdsModel.findEntity("MyService.MyEntity.attachments_drafts"))
        .thenReturn(Optional.of(draftEntity));
    when(cdsModel.findEntity("MyService.MyEntity.attachments")).thenReturn(Optional.of(cdsEntity));
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[{\"id\":\"MyService.MyEntity\",\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.get("name")).thenReturn("testURL");
    when(mockContext.get("url")).thenReturn("http://test-url");
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.USER_NOT_AUTHORISED_ERROR_LINK_MSG);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(userInfo.isSystemUser()).thenReturn(false);

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "123"));
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    when(dbQuery.getAttachmentsForUPID(any(), any(), any(), any())).thenReturn(mockResult);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockResult.listOf(Map.class)).thenReturn(Collections.emptyList());

    sdmUtilsMock
        .when(() -> SDMUtils.getAttachmentCountAndMessage(anyList(), any()))
        .thenReturn("10__null");
    sdmUtilsMock.when(() -> SDMUtils.hasRestrictedCharactersInName(anyString())).thenReturn(false);

    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), anyString())).thenReturn(repoValue);
    when(sdmService.getFolderId(any(), any(), any(), anyBoolean())).thenReturn("folderId123");

    SDMCredentials sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://test-url");
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject createResult = new JSONObject();
    createResult.put("status", "unauthorized");
    when(documentService.createDocument(
            any(CmisDocument.class), any(SDMCredentials.class), anyBoolean()))
        .thenReturn(createResult);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
  }

  @Test
  void testEditLink_UnauthorizedError() throws IOException {
    // Arrange
    when(mockContext.getModel()).thenReturn(cdsModel);
    when(mockContext.get("cqn")).thenReturn(cqnSelect);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyEntity");
    when(cdsModel.findEntity("MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
    UserInfo userInfo = Mockito.mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(mockContext.getParameterInfo()).thenReturn(parameterInfo);
    when(mockContext.getCdsRuntime()).thenReturn(cdsRuntime);
    when(cdsRuntime.getLocalizedMessage(any(), any(), any()))
        .thenReturn(SDMConstants.USER_NOT_AUTHORISED_ERROR_MSG);
    when(userInfo.isSystemUser()).thenReturn(false);

    AnalysisResult analysisResult = mock(AnalysisResult.class);
    when(analysisResult.targetKeyValues()).thenReturn(Map.of("ID", "123"));

    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    when(analyzer.analyze(any(CqnSelect.class))).thenReturn(analysisResult);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);

    when(dbQuery.getObjectIdForAttachmentID(eq(draftEntity), eq(persistenceService), eq("123")))
        .thenReturn(cmisDocument);
    when(mockContext.get("url")).thenReturn("http://newlink.com");

    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    JSONObject unauthorizedResponse = new JSONObject();
    unauthorizedResponse.put("status", "unauthorized");
    when(sdmService.editLink(any(CmisDocument.class), any(SDMCredentials.class), eq(false)))
        .thenReturn(unauthorizedResponse);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.edit(mockContext));
    verify(persistenceService, never()).run(any(Update.class));
    verify(mockContext, never()).setCompleted();
  }

  @Test
  void testHandleDraftDiscardForLinks_CallsRevertNestedEntityLinks() throws IOException {

    DraftCancelEventContext draftContext = mock(DraftCancelEventContext.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    CqnDelete cqnDelete = mock(CqnDelete.class);

    when(draftContext.getTarget()).thenReturn(parentDraftEntity);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(draftContext.getModel()).thenReturn(cdsModel);
    when(draftContext.getCqn()).thenReturn(cqnDelete);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(cqnDelete)).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "book123"));
    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.empty());

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils
                      .getAttachmentPathMapping(any(), any(), any()))
          .thenReturn(new HashMap<>());

      when(cdsModel.findEntity("AdminService.Chapters_drafts")).thenReturn(Optional.empty());
      when(cdsModel.findEntity("AdminService.Pages_drafts")).thenReturn(Optional.empty());
      assertDoesNotThrow(() -> sdmServiceGenericHandler.handleDraftDiscardForLinks(draftContext));
    }
  }

  @Test
  void testRevertNestedEntityLinks_WithNullParentId() throws IOException {

    DraftCancelEventContext draftContext = mock(DraftCancelEventContext.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    CqnDelete cqnDelete = mock(CqnDelete.class);

    when(draftContext.getTarget()).thenReturn(parentDraftEntity);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(draftContext.getModel()).thenReturn(cdsModel);
    when(draftContext.getCqn()).thenReturn(cqnDelete);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(cqnDelete)).thenReturn(analysisResult);
    // Create map with null value using HashMap since Map.of() doesn't allow null values
    Map<String, Object> rootKeys = new HashMap<>();
    rootKeys.put("ID", null);
    when(analysisResult.rootKeys()).thenReturn(rootKeys);

    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.empty());

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils
                      .getAttachmentPathMapping(any(), any(), any()))
          .thenReturn(new HashMap<>());

      assertDoesNotThrow(() -> sdmServiceGenericHandler.handleDraftDiscardForLinks(draftContext));
      verify(cdsModel, never()).findEntity("AdminService.Chapters_drafts");
      verify(cdsModel, never()).findEntity("AdminService.Pages_drafts");
    }
  }

  @Test
  void testRevertNestedEntityLinks_VerifyEntityTypesProcessed() throws IOException {

    DraftCancelEventContext draftContext = mock(DraftCancelEventContext.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    CqnDelete cqnDelete = mock(CqnDelete.class);

    when(draftContext.getTarget()).thenReturn(parentDraftEntity);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(draftContext.getModel()).thenReturn(cdsModel);
    when(draftContext.getCqn()).thenReturn(cqnDelete);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(cqnDelete)).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "validBookId"));

    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.empty());

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils
                      .getAttachmentPathMapping(any(), any(), any()))
          .thenReturn(new HashMap<>());

      // Mock dynamic compositions for parentDraftEntity
      CdsElement mockComposition1 = mock(CdsElement.class);
      CdsElement mockComposition2 = mock(CdsElement.class);
      CdsAssociationType mockAssociationType1 = mock(CdsAssociationType.class);
      CdsAssociationType mockAssociationType2 = mock(CdsAssociationType.class);
      CdsEntity mockTargetEntity1 = mock(CdsEntity.class);
      CdsEntity mockTargetEntity2 = mock(CdsEntity.class);
      when(parentDraftEntity.compositions())
          .thenReturn(Stream.of(mockComposition1, mockComposition2));
      when(mockComposition1.getType()).thenReturn(mockAssociationType1);
      when(mockComposition2.getType()).thenReturn(mockAssociationType2);
      when(mockAssociationType1.getTarget()).thenReturn(mockTargetEntity1);
      when(mockAssociationType2.getTarget()).thenReturn(mockTargetEntity2);
      when(mockTargetEntity1.getQualifiedName()).thenReturn("AdminService.Chapters");
      when(mockTargetEntity2.getQualifiedName()).thenReturn("AdminService.Pages");
      when(cdsModel.findEntity("AdminService.Chapters_drafts")).thenReturn(Optional.empty());
      when(cdsModel.findEntity("AdminService.Pages_drafts")).thenReturn(Optional.empty());

      assertDoesNotThrow(() -> sdmServiceGenericHandler.handleDraftDiscardForLinks(draftContext));
    }
  }

  @Test
  void testRevertNestedEntityLinks_ExceptionHandling() throws IOException {

    DraftCancelEventContext draftContext = mock(DraftCancelEventContext.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CqnAnalyzer analyzer = mock(CqnAnalyzer.class);
    AnalysisResult analysisResult = mock(AnalysisResult.class);
    CqnDelete cqnDelete = mock(CqnDelete.class);

    when(draftContext.getTarget()).thenReturn(parentDraftEntity);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(draftContext.getModel()).thenReturn(cdsModel);
    when(draftContext.getCqn()).thenReturn(cqnDelete);

    cqnAnalyzerMock.when(() -> CqnAnalyzer.create(cdsModel)).thenReturn(analyzer);
    when(analyzer.analyze(cqnDelete)).thenReturn(analysisResult);
    when(analysisResult.rootKeys()).thenReturn(Map.of("ID", "book123"));

    when(cdsModel.findEntity("AdminService.Books")).thenReturn(Optional.empty());

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils
                      .getAttachmentPathMapping(any(), any(), any()))
          .thenReturn(new HashMap<>());

      when(cdsModel.findEntity("AdminService.Books"))
          .thenThrow(new RuntimeException("Database error"));

      assertThrows(
          RuntimeException.class,
          () -> sdmServiceGenericHandler.handleDraftDiscardForLinks(draftContext));
    }
  }

  @Test
  void testRevertLinksForComposition() throws Exception {
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    Map<String, Object> parentKeys = new HashMap<>();
    parentKeys.put("ID", "parent123");
    String attachmentCompositionDefinition = "AdminService.Attachments";

    CdsModel model = mock(CdsModel.class);
    CdsEntity draftEntity = mock(CdsEntity.class);
    CdsEntity activeEntity = mock(CdsEntity.class);

    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Attachments_drafts")).thenReturn(Optional.of(draftEntity));
    when(model.findEntity("AdminService.Attachments")).thenReturn(Optional.of(activeEntity));

    Result draftLinksResult = mock(Result.class);
    Row draftLinkRow = mock(Row.class);
    when(draftLinksResult.iterator()).thenReturn(Arrays.asList(draftLinkRow).iterator());
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(draftLinksResult);

    when(draftLinkRow.get("ID")).thenReturn("attachment123");
    when(draftLinkRow.get("linkUrl")).thenReturn("http://draft-url.com");
    when(draftLinkRow.get("objectId")).thenReturn("object123");
    when(draftLinkRow.get("fileName")).thenReturn("test.url");

    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);
    when(activeResult.iterator()).thenReturn(Arrays.asList(activeRow).iterator());
    when(activeRow.get("linkUrl")).thenReturn("http://original-url.com");

    when(persistenceService.run(any(CqnSelect.class)))
        .thenReturn(draftLinksResult)
        .thenReturn(activeResult);

    sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(draftEntity)).thenReturn("up__ID");

    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinksForComposition", DraftCancelEventContext.class, Map.class, String.class);
    method.setAccessible(true);

    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler, context, parentKeys, attachmentCompositionDefinition);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    verify(persistenceService, times(2)).run(any(CqnSelect.class));
    verify(tokenHandler, times(1)).getSDMCredentials();
    verify(context, times(1)).getUserInfo();
  }

  @Test
  void testRevertLinksForComposition_NoLinksToRevert() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    Map<String, Object> parentKeys = new HashMap<>();
    parentKeys.put("ID", "parent123");
    String attachmentCompositionDefinition = "AdminService.Attachments";

    CdsModel model = mock(CdsModel.class);
    CdsEntity draftEntity = mock(CdsEntity.class);
    CdsEntity activeEntity = mock(CdsEntity.class);

    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Attachments_drafts")).thenReturn(Optional.of(draftEntity));
    when(model.findEntity("AdminService.Attachments")).thenReturn(Optional.of(activeEntity));

    Result emptyResult = mock(Result.class);
    when(emptyResult.iterator()).thenReturn(Collections.emptyIterator());
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(emptyResult);

    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(true);

    sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(draftEntity)).thenReturn("up__ID");

    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinksForComposition", DraftCancelEventContext.class, Map.class, String.class);
    method.setAccessible(true);

    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler, context, parentKeys, attachmentCompositionDefinition);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    verify(persistenceService, times(1)).run(any(CqnSelect.class));
    verify(tokenHandler, times(1)).getSDMCredentials();
    verify(context, times(1)).getUserInfo();
  }

  @Test
  void testRevertLinksForComposition_SameUrls() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    Map<String, Object> parentKeys = new HashMap<>();
    parentKeys.put("ID", "parent123");
    String attachmentCompositionDefinition = "AdminService.Attachments";

    CdsModel model = mock(CdsModel.class);
    CdsEntity draftEntity = mock(CdsEntity.class);
    CdsEntity activeEntity = mock(CdsEntity.class);

    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Attachments_drafts")).thenReturn(Optional.of(draftEntity));
    when(model.findEntity("AdminService.Attachments")).thenReturn(Optional.of(activeEntity));

    Result draftLinksResult = mock(Result.class);
    Row draftLinkRow = mock(Row.class);
    when(draftLinksResult.iterator()).thenReturn(Arrays.asList(draftLinkRow).iterator());

    when(draftLinkRow.get("ID")).thenReturn("attachment123");
    when(draftLinkRow.get("linkUrl")).thenReturn("http://same-url.com");
    when(draftLinkRow.get("objectId")).thenReturn("object123");
    when(draftLinkRow.get("fileName")).thenReturn("test.url");

    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);
    when(activeResult.iterator()).thenReturn(Arrays.asList(activeRow).iterator());
    when(activeRow.get("linkUrl")).thenReturn("http://same-url.com");

    when(persistenceService.run(any(CqnSelect.class)))
        .thenReturn(draftLinksResult)
        .thenReturn(activeResult);

    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(draftEntity)).thenReturn("up__ID");

    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinksForComposition", DraftCancelEventContext.class, Map.class, String.class);
    method.setAccessible(true);

    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler, context, parentKeys, attachmentCompositionDefinition);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    verify(persistenceService, times(2)).run(any(CqnSelect.class));
    verify(tokenHandler, times(1)).getSDMCredentials();
    verify(context, times(1)).getUserInfo();
  }

  @Test
  void testRevertNestedEntityLinks_MainFlow() throws Exception {
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CdsEntity parentActiveEntity = mock(CdsEntity.class);
    CdsElement composition1 = mock(CdsElement.class);
    CdsElement composition2 = mock(CdsElement.class);

    when(context.getTarget()).thenReturn(parentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(model.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));

    when(parentActiveEntity.compositions()).thenReturn(Stream.of(composition1, composition2));

    CdsAssociationType associationType1 = mock(CdsAssociationType.class);
    CdsAssociationType associationType2 = mock(CdsAssociationType.class);
    CdsEntity targetEntity1 = mock(CdsEntity.class);
    CdsEntity targetEntity2 = mock(CdsEntity.class);

    when(composition1.getType()).thenReturn(associationType1);
    when(composition2.getType()).thenReturn(associationType2);
    when(associationType1.getTarget()).thenReturn(targetEntity1);
    when(associationType2.getTarget()).thenReturn(targetEntity2);
    when(targetEntity1.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(targetEntity2.getQualifiedName()).thenReturn("AdminService.Reviews");

    CdsEntity nestedDraftEntity1 = mock(CdsEntity.class);
    CdsEntity nestedDraftEntity2 = mock(CdsEntity.class);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity1));
    when(model.findEntity("AdminService.Reviews_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity2));

    Result emptyResult1 = mock(Result.class);
    Result emptyResult2 = mock(Result.class);
    when(emptyResult1.iterator()).thenReturn(Collections.emptyIterator());
    when(emptyResult2.iterator()).thenReturn(Collections.emptyIterator());

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {

      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity1), eq(persistenceService)))
          .thenReturn(new HashMap<>());

      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity2), eq(persistenceService)))
          .thenReturn(new HashMap<>());

      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "revertNestedEntityLinks", DraftCancelEventContext.class);
      method.setAccessible(true);

      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      verify(parentDraftEntity).getQualifiedName();
      verify(model).findEntity("AdminService.Books");
      verify(parentActiveEntity).compositions();
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity1), eq(persistenceService)),
          times(1));
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity2), eq(persistenceService)),
          times(1));
    }
  }

  @Test
  void testRevertNestedEntityLinks_MissingActiveEntity() throws Exception {
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(parentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(model.findEntity("AdminService.Books")).thenReturn(Optional.empty());

    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertNestedEntityLinks", DraftCancelEventContext.class);
    method.setAccessible(true);

    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(sdmServiceGenericHandler, context);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    verify(parentDraftEntity).getQualifiedName();
    verify(model).findEntity("AdminService.Books");
  }

  @Test
  void testRevertNestedEntityLinks_EmptyCompositions() throws Exception {
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CdsEntity parentActiveEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(parentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(model.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));
    when(parentActiveEntity.compositions()).thenReturn(Stream.empty());

    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertNestedEntityLinks", DraftCancelEventContext.class);
    method.setAccessible(true);

    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(sdmServiceGenericHandler, context);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    verify(parentDraftEntity).getQualifiedName();
    verify(model).findEntity("AdminService.Books");
    verify(parentActiveEntity).compositions();
  }

  @Test
  void testRevertNestedEntityLinks_ComplexAttachments() throws Exception {
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CdsEntity parentActiveEntity = mock(CdsEntity.class);
    CdsElement composition = mock(CdsElement.class);

    when(context.getTarget()).thenReturn(parentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(model.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));
    when(parentActiveEntity.compositions()).thenReturn(Stream.of(composition));

    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);

    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");

    CdsEntity nestedDraftEntity = mock(CdsEntity.class);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity));

    Result nestedRecordsResult = mock(Result.class);
    Row nestedRecord = mock(Row.class);
    when(nestedRecordsResult.iterator()).thenReturn(Arrays.asList(nestedRecord).iterator());
    when(nestedRecord.get("ID")).thenReturn("chapter1");

    Map<String, String> attachmentMapping = new HashMap<>();
    attachmentMapping.put("AdminService.Attachments", "path1");

    CdsEntity attachmentDraftEntity = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity = mock(CdsEntity.class);

    when(model.findEntity("AdminService.Attachments_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity));
    when(model.findEntity("AdminService.Attachments"))
        .thenReturn(Optional.of(attachmentActiveEntity));

    CdsElement upElement = mock(CdsElement.class);
    CdsElement upAssociation = mock(CdsElement.class);
    CdsAssociationType upAssocType = mock(CdsAssociationType.class);
    CqnElementRef mockRef = mock(CqnElementRef.class);
    when(attachmentDraftEntity.findAssociation("up_")).thenReturn(Optional.of(upAssociation));
    when(upAssociation.getType()).thenReturn(upAssocType);
    when(upAssocType.refs()).thenReturn(Stream.of(mockRef));
    when(mockRef.path()).thenReturn("ID");
    when(attachmentDraftEntity.elements()).thenReturn(Stream.of(upElement));
    when(upElement.getName()).thenReturn("up__ID");

    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Result emptyDraftLinksResult = mock(Result.class);
    when(emptyDraftLinksResult.iterator()).thenReturn(Collections.emptyIterator());

    // Mock SDMUtils.getUpIdKey to return non-null value
    sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(attachmentDraftEntity)).thenReturn("up__ID");

    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {

      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity), eq(persistenceService)))
          .thenReturn(attachmentMapping);

      when(persistenceService.run(any(CqnSelect.class)))
          .thenReturn(nestedRecordsResult)
          .thenReturn(emptyDraftLinksResult);

      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "revertNestedEntityLinks", DraftCancelEventContext.class);
      method.setAccessible(true);

      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      // Verify interactions
      verify(parentDraftEntity).getQualifiedName();
      verify(model).findEntity("AdminService.Books");
      verify(parentActiveEntity).compositions();
      verify(persistenceService, times(2)).run(any(CqnSelect.class));
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity), eq(persistenceService)),
          times(1));
    }
  }

  @Test
  void testRevertNestedEntityLinks_ExceptionInProcessing() throws Exception {
    // Mock context and entities
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentDraftEntity = mock(CdsEntity.class);
    CdsEntity parentActiveEntity = mock(CdsEntity.class);
    CdsElement composition = mock(CdsElement.class);

    when(context.getTarget()).thenReturn(parentDraftEntity);
    when(context.getModel()).thenReturn(model);
    when(parentDraftEntity.getQualifiedName()).thenReturn("AdminService.Books_drafts");
    when(model.findEntity("AdminService.Books")).thenReturn(Optional.of(parentActiveEntity));

    // Mock composition that throws exception
    when(parentActiveEntity.compositions()).thenReturn(Stream.of(composition));
    when(composition.getType()).thenThrow(new RuntimeException("Processing error"));

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertNestedEntityLinks", DraftCancelEventContext.class);
    method.setAccessible(true);

    // Execute the test and expect RuntimeException to be thrown
    assertThrows(
        RuntimeException.class,
        () -> {
          try {
            method.invoke(sdmServiceGenericHandler, context);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void testRevertLinkInSDM() throws Exception {
    // Mock parameters
    String objectId = "test-object-id";
    String filename = "test-document.lnk";
    String originalUrl = "https://original-url.com";
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    Boolean isSystemUser = false;

    // Mock the SDM service call
    JSONObject successResponse = new JSONObject();
    successResponse.put("status", "success");
    when(sdmService.editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser)))
        .thenReturn(successResponse);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinkInSDM",
            String.class,
            String.class,
            String.class,
            SDMCredentials.class,
            Boolean.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler,
                objectId,
                filename,
                originalUrl,
                sdmCredentials,
                isSystemUser);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify interactions
    ArgumentCaptor<CmisDocument> cmisDocumentCaptor = ArgumentCaptor.forClass(CmisDocument.class);
    verify(sdmService, times(1))
        .editLink(cmisDocumentCaptor.capture(), eq(sdmCredentials), eq(isSystemUser));

    // Verify the CmisDocument properties
    CmisDocument capturedDoc = cmisDocumentCaptor.getValue();
    assertEquals(objectId, capturedDoc.getObjectId());
    assertEquals(filename, capturedDoc.getFileName());
    assertEquals(originalUrl, capturedDoc.getUrl());
    assertEquals(SDMConstants.REPOSITORY_ID, capturedDoc.getRepositoryId());
  }

  @Test
  void testRevertLinkInSDM_WithNullUrl() throws Exception {
    // Mock parameters with null URL
    String objectId = "test-object-id";
    String filename = "test-document.lnk";
    String originalUrl = null;
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    Boolean isSystemUser = true;

    // Mock the SDM service call
    JSONObject successResponse = new JSONObject();
    successResponse.put("status", "success");
    when(sdmService.editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser)))
        .thenReturn(successResponse);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinkInSDM",
            String.class,
            String.class,
            String.class,
            SDMCredentials.class,
            Boolean.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler,
                objectId,
                filename,
                originalUrl,
                sdmCredentials,
                isSystemUser);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify interactions
    ArgumentCaptor<CmisDocument> cmisDocumentCaptor = ArgumentCaptor.forClass(CmisDocument.class);
    verify(sdmService, times(1))
        .editLink(cmisDocumentCaptor.capture(), eq(sdmCredentials), eq(isSystemUser));

    // Verify the CmisDocument properties
    CmisDocument capturedDoc = cmisDocumentCaptor.getValue();
    assertEquals(objectId, capturedDoc.getObjectId());
    assertEquals(filename, capturedDoc.getFileName());
    assertNull(capturedDoc.getUrl());
    assertEquals(SDMConstants.REPOSITORY_ID, capturedDoc.getRepositoryId());
  }

  @Test
  void testRevertLinkInSDM_WithEmptyFilename() throws Exception {
    // Mock parameters with empty filename
    String objectId = "test-object-id";
    String filename = "";
    String originalUrl = "https://example.com";
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    Boolean isSystemUser = false;

    // Mock the SDM service call
    JSONObject successResponse = new JSONObject();
    successResponse.put("status", "success");
    when(sdmService.editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser)))
        .thenReturn(successResponse);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinkInSDM",
            String.class,
            String.class,
            String.class,
            SDMCredentials.class,
            Boolean.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler,
                objectId,
                filename,
                originalUrl,
                sdmCredentials,
                isSystemUser);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify interactions
    ArgumentCaptor<CmisDocument> cmisDocumentCaptor = ArgumentCaptor.forClass(CmisDocument.class);
    verify(sdmService, times(1))
        .editLink(cmisDocumentCaptor.capture(), eq(sdmCredentials), eq(isSystemUser));

    // Verify the CmisDocument properties
    CmisDocument capturedDoc = cmisDocumentCaptor.getValue();
    assertEquals(objectId, capturedDoc.getObjectId());
    assertEquals(filename, capturedDoc.getFileName());
    assertEquals(originalUrl, capturedDoc.getUrl());
    assertEquals(SDMConstants.REPOSITORY_ID, capturedDoc.getRepositoryId());
  }

  @Test
  void testRevertLinkInSDM_ServiceException() throws Exception {
    // Mock parameters
    String objectId = "test-object-id";
    String filename = "test-document.lnk";
    String originalUrl = "https://original-url.com";
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    Boolean isSystemUser = false;

    // Mock the SDM service to throw an exception
    when(sdmService.editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser)))
        .thenThrow(new IOException("Service unavailable"));

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinkInSDM",
            String.class,
            String.class,
            String.class,
            SDMCredentials.class,
            Boolean.class);
    method.setAccessible(true);

    // Execute the test and expect IOException to be thrown
    assertThrows(
        IOException.class,
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler,
                objectId,
                filename,
                originalUrl,
                sdmCredentials,
                isSystemUser);
          } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
              throw (IOException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify the service was called
    verify(sdmService, times(1))
        .editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser));
  }

  @Test
  void testRevertLinkInSDM_SystemUserTrue() throws Exception {
    // Mock parameters with system user = true
    String objectId = "system-object-id";
    String filename = "system-document.lnk";
    String originalUrl = "https://system-url.com";
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    Boolean isSystemUser = true;

    // Mock the SDM service call
    JSONObject successResponse = new JSONObject();
    successResponse.put("status", "success");
    when(sdmService.editLink(any(CmisDocument.class), eq(sdmCredentials), eq(isSystemUser)))
        .thenReturn(successResponse);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "revertLinkInSDM",
            String.class,
            String.class,
            String.class,
            SDMCredentials.class,
            Boolean.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(
                sdmServiceGenericHandler,
                objectId,
                filename,
                originalUrl,
                sdmCredentials,
                isSystemUser);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify interactions with system user flag
    ArgumentCaptor<CmisDocument> cmisDocumentCaptor = ArgumentCaptor.forClass(CmisDocument.class);
    verify(sdmService, times(1))
        .editLink(cmisDocumentCaptor.capture(), eq(sdmCredentials), eq(true));

    // Verify the CmisDocument properties
    CmisDocument capturedDoc = cmisDocumentCaptor.getValue();
    assertEquals(objectId, capturedDoc.getObjectId());
    assertEquals(filename, capturedDoc.getFileName());
    assertEquals(originalUrl, capturedDoc.getUrl());
    assertEquals(SDMConstants.REPOSITORY_ID, capturedDoc.getRepositoryId());
  }

  @Test
  void testGetOriginalUrlFromActiveTable() throws Exception {
    // Mock parameters
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-123";
    Object parentId = "parent-456";
    String upIdKey = "up__ID";

    // Mock result with a single row
    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);

    when(activeResult.rowCount()).thenReturn(1L);
    when(activeResult.single()).thenReturn(activeRow);
    when(activeRow.get("linkUrl")).thenReturn("https://example.com/original-link");

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertEquals("https://example.com/original-link", url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify persistence service was called
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
    verify(activeResult, times(1)).rowCount();
    verify(activeResult, times(1)).single();
    verify(activeRow, times(2)).get("linkUrl"); // Called twice: null check and toString()
  }

  @Test
  void testGetOriginalUrlFromActiveTable_NoRows() throws Exception {
    // Mock parameters
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-123";
    Object parentId = "parent-456";
    String upIdKey = "up__ID";

    // Mock empty result
    Result activeResult = mock(Result.class);
    when(activeResult.rowCount()).thenReturn(0L);

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertNull(url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify persistence service was called
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
    verify(activeResult, times(1)).rowCount();
    verify(activeResult, never()).single(); // Should not call single() when no rows
  }

  @Test
  void testGetOriginalUrlFromActiveTable_NullLinkUrl() throws Exception {
    // Mock parameters
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-123";
    Object parentId = "parent-456";
    String upIdKey = "up__ID";

    // Mock result with a single row that has null linkUrl
    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);

    when(activeResult.rowCount()).thenReturn(1L);
    when(activeResult.single()).thenReturn(activeRow);
    when(activeRow.get("linkUrl")).thenReturn(null);

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertNull(url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify interactions
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
    verify(activeResult, times(1)).rowCount();
    verify(activeResult, times(1)).single();
    verify(activeRow, times(1)).get("linkUrl");
  }

  @Test
  void testGetOriginalUrlFromActiveTable_DifferentUpIdKey() throws Exception {
    // Mock parameters with different upIdKey
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-789";
    Object parentId = "parent-012";
    String upIdKey = "up__parentEntityID";

    // Mock result with a single row
    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);

    when(activeResult.rowCount()).thenReturn(1L);
    when(activeResult.single()).thenReturn(activeRow);
    when(activeRow.get("linkUrl")).thenReturn("https://different-url.com");

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertEquals("https://different-url.com", url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify persistence service was called
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
  }

  @Test
  void testGetOriginalUrlFromActiveTable_NumericParentId() throws Exception {
    // Mock parameters with numeric parent ID
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-456";
    Object parentId = 12345L; // Numeric parent ID
    String upIdKey = "up__ID";

    // Mock result with a single row
    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);

    when(activeResult.rowCount()).thenReturn(1L);
    when(activeResult.single()).thenReturn(activeRow);
    when(activeRow.get("linkUrl")).thenReturn("https://numeric-parent.com");

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertEquals("https://numeric-parent.com", url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify persistence service was called
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
  }

  @Test
  void testGetOriginalUrlFromActiveTable_MultipleRowsReturnsFirst() throws Exception {
    // Mock parameters
    CdsEntity activeEntity = mock(CdsEntity.class);
    String attachmentId = "attachment-789";
    Object parentId = "parent-abc";
    String upIdKey = "up__ID";

    // Mock result with multiple rows (edge case)
    Result activeResult = mock(Result.class);
    Row activeRow = mock(Row.class);

    when(activeResult.rowCount()).thenReturn(3L); // Multiple rows
    when(activeResult.single()).thenReturn(activeRow);
    when(activeRow.get("linkUrl")).thenReturn("https://first-result.com");

    // Mock persistence service
    when(persistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "getOriginalUrlFromActiveTable",
            CdsEntity.class,
            String.class,
            Object.class,
            String.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            String url =
                (String)
                    method.invoke(
                        sdmServiceGenericHandler, activeEntity, attachmentId, parentId, upIdKey);
            assertEquals("https://first-result.com", url);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify persistence service was called
    verify(persistenceService, times(1)).run(any(CqnSelect.class));
    verify(activeResult, times(1)).rowCount();
    verify(activeResult, times(1)).single();
  }

  @Test
  void testProcessNestedEntityComposition() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsEntity nestedDraftEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity));

    // Mock nested records
    Result nestedRecordsResult = mock(Result.class);
    Row nestedRecord1 = mock(Row.class);
    Row nestedRecord2 = mock(Row.class);
    when(nestedRecordsResult.iterator())
        .thenReturn(Arrays.asList(nestedRecord1, nestedRecord2).iterator());
    when(nestedRecord1.get("ID")).thenReturn("chapter1");
    when(nestedRecord2.get("ID")).thenReturn("chapter2");

    // Mock attachment path mapping
    Map<String, String> attachmentMapping = new HashMap<>();
    attachmentMapping.put("AdminService.Attachments1", "path1");
    attachmentMapping.put("AdminService.Attachments2", "path2");

    // Mock entities for revertLinksForComposition calls
    CdsEntity attachmentDraftEntity1 = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity1 = mock(CdsEntity.class);
    CdsEntity attachmentDraftEntity2 = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity2 = mock(CdsEntity.class);

    when(model.findEntity("AdminService.Attachments1_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity1));
    when(model.findEntity("AdminService.Attachments1"))
        .thenReturn(Optional.of(attachmentActiveEntity1));
    when(model.findEntity("AdminService.Attachments2_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity2));
    when(model.findEntity("AdminService.Attachments2"))
        .thenReturn(Optional.of(attachmentActiveEntity2));

    // Mock upId key extraction for attachment entities
    CdsElement upElement1 = mock(CdsElement.class);
    CdsElement upElement2 = mock(CdsElement.class);
    CdsElement upAssociation1 = mock(CdsElement.class);
    CdsAssociationType upAssocType1 = mock(CdsAssociationType.class);
    CqnElementRef mockRef1 = mock(CqnElementRef.class);
    when(attachmentDraftEntity1.findAssociation("up_")).thenReturn(Optional.of(upAssociation1));
    when(upAssociation1.getType()).thenReturn(upAssocType1);
    when(upAssocType1.refs()).thenReturn(Stream.of(mockRef1));
    when(mockRef1.path()).thenReturn("ID");
    CdsElement upAssociation2 = mock(CdsElement.class);
    CdsAssociationType upAssocType2 = mock(CdsAssociationType.class);
    CqnElementRef mockRef2 = mock(CqnElementRef.class);
    when(attachmentDraftEntity2.findAssociation("up_")).thenReturn(Optional.of(upAssociation2));
    when(upAssociation2.getType()).thenReturn(upAssocType2);
    when(upAssocType2.refs()).thenReturn(Stream.of(mockRef2));
    when(mockRef2.path()).thenReturn("ID");
    when(attachmentDraftEntity1.elements()).thenReturn(Stream.of(upElement1));
    when(attachmentDraftEntity2.elements()).thenReturn(Stream.of(upElement2));
    when(upElement1.getName()).thenReturn("up__ID");
    when(upElement2.getName()).thenReturn("up__ID");

    // Mock SDM credentials and user info for revertLinksForComposition
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Mock the static method call
    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity), eq(persistenceService)))
          .thenReturn(attachmentMapping);

      // Mock draft links result for revertLinksForComposition calls
      Result emptyDraftLinksResult1 = mock(Result.class);
      Result emptyDraftLinksResult2 = mock(Result.class);
      Result emptyDraftLinksResult3 = mock(Result.class);
      Result emptyDraftLinksResult4 = mock(Result.class);
      when(emptyDraftLinksResult1.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult2.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult3.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult4.iterator()).thenReturn(Collections.emptyIterator());

      // Mock persistence service calls
      when(persistenceService.run(any(CqnSelect.class)))
          .thenReturn(nestedRecordsResult) // First call for nested records
          .thenReturn(emptyDraftLinksResult1) // revertLinksForComposition call 1
          .thenReturn(emptyDraftLinksResult2) // revertLinksForComposition call 2
          .thenReturn(emptyDraftLinksResult3) // revertLinksForComposition call 3
          .thenReturn(emptyDraftLinksResult4); // revertLinksForComposition call 4

      // Use reflection to invoke the private method
      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
      method.setAccessible(true);

      // Execute the test
      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context, composition);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      // Verify interactions
      verify(persistenceService, atLeast(1)).run(any(CqnSelect.class));
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity), eq(persistenceService)),
          times(1));
    }
  }

  @Test
  void testProcessNestedEntityComposition_NoDraftEntity() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts")).thenReturn(Optional.empty());

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
    method.setAccessible(true);

    // Execute the test
    assertDoesNotThrow(
        () -> {
          try {
            method.invoke(sdmServiceGenericHandler, context, composition);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });

    // Verify no persistence calls were made since no draft entity exists
    verify(persistenceService, never()).run(any(CqnSelect.class));
  }

  @Test
  void testProcessNestedEntityComposition_EmptyAttachmentMapping() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsEntity nestedDraftEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity));

    // Mock empty attachment path mapping
    Map<String, String> emptyAttachmentMapping = new HashMap<>();

    // Mock the static method call
    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity), eq(persistenceService)))
          .thenReturn(emptyAttachmentMapping);

      // Use reflection to invoke the private method
      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
      method.setAccessible(true);

      // Execute the test
      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context, composition);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      // Verify interactions
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity), eq(persistenceService)),
          times(1));
      // No persistence calls for nested records since mapping is empty
      verify(persistenceService, never()).run(any(CqnSelect.class));
    }
  }

  @Test
  void testProcessNestedEntityComposition_NoNestedRecords() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsEntity nestedDraftEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity));

    // Mock empty nested records result
    Result emptyResult = mock(Result.class);
    when(emptyResult.iterator()).thenReturn(Collections.emptyIterator());

    // Mock attachment path mapping
    Map<String, String> attachmentMapping = new HashMap<>();
    attachmentMapping.put("AdminService.Attachments", "path1");

    // Mock the static method call
    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity), eq(persistenceService)))
          .thenReturn(attachmentMapping);

      when(persistenceService.run(any(CqnSelect.class))).thenReturn(emptyResult);

      // Use reflection to invoke the private method
      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
      method.setAccessible(true);

      // Execute the test
      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context, composition);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      // Verify interactions
      verify(persistenceService, times(1))
          .run(any(CqnSelect.class)); // Only one call for nested records
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity), eq(persistenceService)),
          times(1));
    }
  }

  @Test
  void testProcessNestedEntityComposition_ExceptionHandling() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenThrow(new RuntimeException("Database error"));

    // Use reflection to invoke the private method
    Method method =
        SDMServiceGenericHandler.class.getDeclaredMethod(
            "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
    method.setAccessible(true);

    // Execute the test and expect exception
    assertThrows(
        RuntimeException.class,
        () -> {
          try {
            method.invoke(sdmServiceGenericHandler, context, composition);
          } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
              throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void testProcessNestedEntityComposition_MultipleAttachmentPaths() throws Exception {
    // Setup test data
    DraftCancelEventContext context = mock(DraftCancelEventContext.class);
    CdsElement composition = mock(CdsElement.class);
    CdsAssociationType associationType = mock(CdsAssociationType.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsEntity nestedDraftEntity = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    // Mock composition setup
    when(composition.getType()).thenReturn(associationType);
    when(associationType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("AdminService.Chapters");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("AdminService.Chapters_drafts"))
        .thenReturn(Optional.of(nestedDraftEntity));

    // Mock nested records with single record
    Result nestedRecordsResult = mock(Result.class);
    Row nestedRecord = mock(Row.class);
    when(nestedRecordsResult.iterator()).thenReturn(Arrays.asList(nestedRecord).iterator());
    when(nestedRecord.get("ID")).thenReturn("chapter1");

    // Mock multiple attachment paths
    Map<String, String> attachmentMapping = new HashMap<>();
    attachmentMapping.put("AdminService.ChapterAttachments", "path1");
    attachmentMapping.put("AdminService.ChapterDocuments", "path2");
    attachmentMapping.put("AdminService.ChapterImages", "path3");

    // Mock entities for revertLinksForComposition calls
    CdsEntity attachmentDraftEntity1 = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity1 = mock(CdsEntity.class);
    CdsEntity attachmentDraftEntity2 = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity2 = mock(CdsEntity.class);
    CdsEntity attachmentDraftEntity3 = mock(CdsEntity.class);
    CdsEntity attachmentActiveEntity3 = mock(CdsEntity.class);

    when(model.findEntity("AdminService.ChapterAttachments_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity1));
    when(model.findEntity("AdminService.ChapterAttachments"))
        .thenReturn(Optional.of(attachmentActiveEntity1));
    when(model.findEntity("AdminService.ChapterDocuments_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity2));
    when(model.findEntity("AdminService.ChapterDocuments"))
        .thenReturn(Optional.of(attachmentActiveEntity2));
    when(model.findEntity("AdminService.ChapterImages_drafts"))
        .thenReturn(Optional.of(attachmentDraftEntity3));
    when(model.findEntity("AdminService.ChapterImages"))
        .thenReturn(Optional.of(attachmentActiveEntity3));

    // Mock upId key extraction for attachment entities
    CdsElement upElement1 = mock(CdsElement.class);
    CdsElement upElement2 = mock(CdsElement.class);
    CdsElement upElement3 = mock(CdsElement.class);
    CdsElement upAssociation1 = mock(CdsElement.class);
    CdsAssociationType upAssocType1 = mock(CdsAssociationType.class);
    CqnElementRef mockRef1 = mock(CqnElementRef.class);
    when(attachmentDraftEntity1.findAssociation("up_")).thenReturn(Optional.of(upAssociation1));
    when(upAssociation1.getType()).thenReturn(upAssocType1);
    when(upAssocType1.refs()).thenReturn(Stream.of(mockRef1));
    when(mockRef1.path()).thenReturn("ID");
    CdsElement upAssociation2 = mock(CdsElement.class);
    CdsAssociationType upAssocType2 = mock(CdsAssociationType.class);
    CqnElementRef mockRef2 = mock(CqnElementRef.class);
    when(attachmentDraftEntity2.findAssociation("up_")).thenReturn(Optional.of(upAssociation2));
    when(upAssociation2.getType()).thenReturn(upAssocType2);
    when(upAssocType2.refs()).thenReturn(Stream.of(mockRef2));
    when(mockRef2.path()).thenReturn("ID");
    CdsElement upAssociation3 = mock(CdsElement.class);
    CdsAssociationType upAssocType3 = mock(CdsAssociationType.class);
    CqnElementRef mockRef3 = mock(CqnElementRef.class);
    when(attachmentDraftEntity3.findAssociation("up_")).thenReturn(Optional.of(upAssociation3));
    when(upAssociation3.getType()).thenReturn(upAssocType3);
    when(upAssocType3.refs()).thenReturn(Stream.of(mockRef3));
    when(mockRef3.path()).thenReturn("ID");
    when(attachmentDraftEntity1.elements()).thenReturn(Stream.of(upElement1));
    when(attachmentDraftEntity2.elements()).thenReturn(Stream.of(upElement2));
    when(attachmentDraftEntity3.elements()).thenReturn(Stream.of(upElement3));
    when(upElement1.getName()).thenReturn("up__ID");
    when(upElement2.getName()).thenReturn("up__ID");
    when(upElement3.getName()).thenReturn("up__ID");

    // Mock SDM credentials and user info for revertLinksForComposition
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    UserInfo userInfo = mock(UserInfo.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Mock SDMUtils.getUpIdKey to return non-null value for all attachment entities
    sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(any(CdsEntity.class))).thenReturn("up__ID");

    // Mock the static method call
    try (var attachmentUtilsMock =
        mockStatic(
            com.sap.cds.sdm.handler.applicationservice.helper.AttachmentsHandlerUtils.class)) {
      attachmentUtilsMock
          .when(
              () ->
                  AttachmentsHandlerUtils.getAttachmentPathMapping(
                      eq(model), eq(targetEntity), eq(persistenceService)))
          .thenReturn(attachmentMapping);

      // Mock draft links result for revertLinksForComposition calls
      Result emptyDraftLinksResult1 = mock(Result.class);
      Result emptyDraftLinksResult2 = mock(Result.class);
      Result emptyDraftLinksResult3 = mock(Result.class);
      Result emptyDraftLinksResult4 = mock(Result.class);
      Result emptyDraftLinksResult5 = mock(Result.class);
      Result emptyDraftLinksResult6 = mock(Result.class);
      when(emptyDraftLinksResult1.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult2.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult3.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult4.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult5.iterator()).thenReturn(Collections.emptyIterator());
      when(emptyDraftLinksResult6.iterator()).thenReturn(Collections.emptyIterator());

      // Mock persistence service calls - first for nested records, then for each
      // revertLinksForComposition call
      when(persistenceService.run(any(CqnSelect.class)))
          .thenReturn(nestedRecordsResult) // First call for nested records
          .thenReturn(emptyDraftLinksResult1) // revertLinksForComposition call 1
          .thenReturn(emptyDraftLinksResult2) // revertLinksForComposition call 2
          .thenReturn(emptyDraftLinksResult3) // revertLinksForComposition call 3
          .thenReturn(emptyDraftLinksResult4) // revertLinksForComposition call 4
          .thenReturn(emptyDraftLinksResult5) // revertLinksForComposition call 5
          .thenReturn(emptyDraftLinksResult6); // revertLinksForComposition call 6

      // Use reflection to invoke the private method
      Method method =
          SDMServiceGenericHandler.class.getDeclaredMethod(
              "processNestedEntityComposition", DraftCancelEventContext.class, CdsElement.class);
      method.setAccessible(true);

      // Execute the test
      assertDoesNotThrow(
          () -> {
            try {
              method.invoke(sdmServiceGenericHandler, context, composition);
            } catch (Exception e) {
              if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
              }
              throw new RuntimeException(e);
            }
          });

      // Verify interactions
      verify(persistenceService, atLeast(4))
          .run(any(CqnSelect.class)); // 1 for nested records + 3 for attachment paths
      attachmentUtilsMock.verify(
          () ->
              AttachmentsHandlerUtils.getAttachmentPathMapping(
                  eq(model), eq(targetEntity), eq(persistenceService)),
          times(1));
    }
  }

  // ============ Unit Tests for moveAttachments Method ============

  @Test
  void testMoveAttachments_WithAllParameters_Success() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("123");
    when(mockContext.get("sourceFolderId")).thenReturn("source-folder-id");
    when(mockContext.get("objectIds")).thenReturn("obj1, obj2, obj3");
    when(mockContext.get("sourceFacet")).thenReturn("MyService.SourceEntity.attachments");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.TargetEntity.attachments");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Map<String, Object> expectedResult = new HashMap<>();
    expectedResult.put("movedCount", 3);
    expectedResult.put("failedCount", 0);

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(expectedResult);

    // Act
    Map<String, Object> result = sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    ArgumentCaptor<MoveAttachmentInput> captor = ArgumentCaptor.forClass(MoveAttachmentInput.class);
    verify(attachmentService, times(1)).moveAttachments(captor.capture(), eq(false));

    MoveAttachmentInput input = captor.getValue();
    assertEquals("source-folder-id", input.sourceFolderId());
    assertEquals("123", input.targetUpId());
    assertEquals("MyService.TargetEntity.attachments", input.targetFacet());
    assertEquals(List.of("obj1", "obj2", "obj3"), input.objectIds());
    assertEquals(Optional.of("MyService.SourceEntity.attachments"), input.sourceFacet());

    assertEquals(expectedResult, result);
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_WithoutSourceFacet_Success() throws IOException {
    // Arrange - sourceFacet is null
    when(mockContext.get("up__ID")).thenReturn("456");
    when(mockContext.get("sourceFolderId")).thenReturn("folder-123");
    when(mockContext.get("objectIds")).thenReturn("objA, objB");
    when(mockContext.get("sourceFacet")).thenReturn(null); // No source facet
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("MyService.NewEntity.attachments");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(true);

    Map<String, Object> expectedResult = new HashMap<>();
    expectedResult.put("movedCount", 2);

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(true)))
        .thenReturn(expectedResult);

    // Act
    Map<String, Object> result = sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    ArgumentCaptor<MoveAttachmentInput> captor = ArgumentCaptor.forClass(MoveAttachmentInput.class);
    verify(attachmentService, times(1)).moveAttachments(captor.capture(), eq(true));

    MoveAttachmentInput input = captor.getValue();
    assertEquals("folder-123", input.sourceFolderId());
    assertEquals("456", input.targetUpId());
    assertEquals("MyService.NewEntity.attachments", input.targetFacet());
    assertEquals(List.of("objA", "objB"), input.objectIds());
    assertEquals(Optional.empty(), input.sourceFacet()); // Should be empty when null

    assertEquals(expectedResult, result);
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_WithSingleObjectId_Success() throws IOException {
    // Arrange - single object ID
    when(mockContext.get("up__ID")).thenReturn("999");
    when(mockContext.get("sourceFolderId")).thenReturn("src-folder");
    when(mockContext.get("objectIds")).thenReturn("single-obj-id");
    when(mockContext.get("sourceFacet")).thenReturn("Source.Entity");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target.Entity");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Map<String, Object> expectedResult = new HashMap<>();
    expectedResult.put("movedCount", 1);

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(expectedResult);

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    ArgumentCaptor<MoveAttachmentInput> captor = ArgumentCaptor.forClass(MoveAttachmentInput.class);
    verify(attachmentService, times(1)).moveAttachments(captor.capture(), eq(false));

    MoveAttachmentInput input = captor.getValue();
    assertEquals(List.of("single-obj-id"), input.objectIds());
    assertEquals(1, input.objectIds().size());

    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_WithWhitespaceInObjectIds_TrimsCorrectly() throws IOException {
    // Arrange - object IDs with various whitespace
    when(mockContext.get("up__ID")).thenReturn("100");
    when(mockContext.get("sourceFolderId")).thenReturn("folder-id");
    when(mockContext.get("objectIds")).thenReturn("  obj1  ,  obj2  ,obj3,  obj4  ");
    when(mockContext.get("sourceFacet")).thenReturn(null);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Entity.attachments");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Map<String, Object> expectedResult = new HashMap<>();
    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(expectedResult);

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    ArgumentCaptor<MoveAttachmentInput> captor = ArgumentCaptor.forClass(MoveAttachmentInput.class);
    verify(attachmentService, times(1)).moveAttachments(captor.capture(), eq(false));

    MoveAttachmentInput input = captor.getValue();
    // Verify trimming worked correctly
    assertEquals(List.of("obj1", "obj2", "obj3", "obj4"), input.objectIds());
  }

  @Test
  void testMoveAttachments_WithSystemUser_PassesCorrectFlag() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("system-user-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1");
    when(mockContext.get("sourceFacet")).thenReturn("Source");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(true); // System user

    Map<String, Object> expectedResult = new HashMap<>();
    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(true)))
        .thenReturn(expectedResult);

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    verify(attachmentService, times(1)).moveAttachments(any(MoveAttachmentInput.class), eq(true));
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_WithNonSystemUser_PassesCorrectFlag() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("regular-user-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1");
    when(mockContext.get("sourceFacet")).thenReturn(null);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false); // Non-system user

    Map<String, Object> expectedResult = new HashMap<>();
    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(expectedResult);

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    verify(attachmentService, times(1)).moveAttachments(any(MoveAttachmentInput.class), eq(false));
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ReturnsResultFromService() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("result-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1, obj2");
    when(mockContext.get("sourceFacet")).thenReturn("Source");
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    Map<String, Object> serviceResult = new HashMap<>();
    serviceResult.put("movedCount", 2);
    serviceResult.put("failedCount", 0);
    serviceResult.put("failedAttachments", Collections.emptyList());

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(serviceResult);

    // Act
    Map<String, Object> result = sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.get("movedCount"));
    assertEquals(0, result.get("failedCount"));
    assertEquals(Collections.emptyList(), result.get("failedAttachments"));
  }

  @Test
  void testMoveAttachments_ThrowsIOException_Propagates() {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("error-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1");
    when(mockContext.get("sourceFacet")).thenReturn(null);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    // Mock attachmentService to throw IOException wrapped in RuntimeException
    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenAnswer(
            invocation -> {
              throw new IOException("Move operation failed");
            });

    // Act & Assert
    assertThrows(IOException.class, () -> sdmServiceGenericHandler.moveAttachments(mockContext));

    verify(mockContext, never()).setCompleted(); // Should not be called when exception occurs
  }

  @Test
  void testMoveAttachments_ContextSetCompleted_CalledOnce() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("complete-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1");
    when(mockContext.get("sourceFacet")).thenReturn(null);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName()).thenReturn("Target");

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(new HashMap<>());

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    verify(mockContext, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_UsesTargetQualifiedName_AsTargetFacet() throws IOException {
    // Arrange
    when(mockContext.get("up__ID")).thenReturn("qname-test");
    when(mockContext.get("sourceFolderId")).thenReturn("folder");
    when(mockContext.get("objectIds")).thenReturn("obj1");
    when(mockContext.get("sourceFacet")).thenReturn(null);
    when(mockContext.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getQualifiedName())
        .thenReturn("com.example.MyService.MyEntity.attachments"); // Full qualified name

    UserInfo userInfo = mock(UserInfo.class);
    when(mockContext.getUserInfo()).thenReturn(userInfo);
    when(userInfo.isSystemUser()).thenReturn(false);

    when(attachmentService.moveAttachments(any(MoveAttachmentInput.class), eq(false)))
        .thenReturn(new HashMap<>());

    // Act
    sdmServiceGenericHandler.moveAttachments(mockContext);

    // Assert
    ArgumentCaptor<MoveAttachmentInput> captor = ArgumentCaptor.forClass(MoveAttachmentInput.class);
    verify(attachmentService, times(1)).moveAttachments(captor.capture(), eq(false));

    MoveAttachmentInput input = captor.getValue();
    assertEquals(
        "com.example.MyService.MyEntity.attachments",
        input.targetFacet()); // Uses full qualified name
  }
}
