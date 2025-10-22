package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.*;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.SDMServiceGenericHandler;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
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

  private CmisDocument cmisDocument;
  private SDMCredentials sdmCredentials;
  private SDMServiceGenericHandler sdmServiceGenericHandler;
  private static final String MOCK_ENTITY = "MyService.MyEntity.attachments";

  private MockedStatic<CqnAnalyzer> cqnAnalyzerMock;
  private MockedStatic<SDMUtils> sdmUtilsMock;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    // Prepare a real list with the mock DraftService
    when(draftService.getName()).thenReturn(MOCK_ENTITY);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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

    // when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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

    // when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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

    // when(cdsModel.findEntity("MyService.MyEntity_drafts")).thenReturn(Optional.of(draftEntity));
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(true);
    RepoValue repoValue = new RepoValue();
    repoValue.setVirusScanEnabled(false);
    repoValue.setVersionEnabled(false);
    when(sdmService.checkRepositoryType(anyString(), any())).thenReturn(repoValue);
    // Act & Assert
    ServiceException ex =
        assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.create(mockContext));
    assertEquals(
        SDMConstants.linkNameConstraintMessage(Collections.singletonList("test/URL"), "created"),
        ex.getMessage());
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
    when(mockContext.getEvent()).thenReturn("createLink");
    CqnSelect cqnSelect = mock(CqnSelect.class);
    when(cqnSelect.toString())
        .thenReturn(
            "{\"SELECT\":{\"from\":{\"ref\":[\"entity1\",{\"where\":[{\"ref\":[\"ID\"]},\"=\",{\"val\":\"123\"}]},\"entity2\"]}}}");
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
    sdmUtilsMock.when(() -> SDMUtils.isRestrictedCharactersInName(anyString())).thenReturn(false);
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
}
