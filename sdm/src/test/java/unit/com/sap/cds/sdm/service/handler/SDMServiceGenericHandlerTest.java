package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.DocumentUploadService;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.SDMServiceGenericHandler;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.*;

public class SDMServiceGenericHandlerTest {

  @Mock private RegisterService attachmentService;
  @Mock private PersistenceService persistenceService;
  @Mock private SDMService sdmService;
  @Mock private DocumentUploadService documentService;
  @Mock private List<DraftService> draftService;
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

  private MockedStatic<CqnAnalyzer> cqnAnalyzerMock;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    sdmServiceGenericHandler =
        new SDMServiceGenericHandler(
            attachmentService,
            persistenceService,
            sdmService,
            documentService,
            draftService,
            dbQuery,
            tokenHandler);

    // Static mock for CqnAnalyzer
    cqnAnalyzerMock = mockStatic(CqnAnalyzer.class);

    cmisDocument = new CmisDocument();
    cmisDocument.setObjectId("12345");

    sdmCredentials = new SDMCredentials();
    sdmCredentials.setUrl("http://example.com/");
  }

  @AfterEach
  void tearDown() {
    cqnAnalyzerMock.close();
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
    when(sdmService.editLink(any(CmisDocument.class), any(SDMCredentials.class), true))
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
    when(sdmService.editLink(any(CmisDocument.class), any(SDMCredentials.class), true))
        .thenReturn(failureResponse);

    // Act & Assert
    assertThrows(ServiceException.class, () -> sdmServiceGenericHandler.edit(mockContext));
    verify(persistenceService, never()).run(any(Update.class));
    verify(mockContext, never()).setCompleted();
  }
}
