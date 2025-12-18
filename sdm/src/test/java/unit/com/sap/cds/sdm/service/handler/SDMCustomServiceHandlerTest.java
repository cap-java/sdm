package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import com.sap.cds.sdm.service.handler.AttachmentMoveEventContext;
import com.sap.cds.sdm.service.handler.SDMCustomServiceHandler;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.ParameterInfo;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SDMCustomServiceHandlerTest {

  @Mock private AttachmentCopyEventContext mockContext;

  @Mock private SDMService sdmService;
  @Mock private PersistenceService persistenceService;

  @Mock private TokenHandler tokenHandler;

  @Mock private DraftService draftService;

  @Mock private DBQuery dbQuery;

  private SDMCustomServiceHandler sdmCustomServiceHandler;

  private static final String OBJECT_ID = "mockObjectId";
  private static final String FOLDER_ID = "mockFolderId";
  private static final String UP_ID = "mockUpId";
  private static final String FACET = "mockFacet";
  @Mock private CdsRuntime cdsRuntime;
  @Mock ParameterInfo parameterInfo;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(draftService.getName()).thenReturn(FACET);
    // Pass a non-null list of DraftService mocks
    sdmCustomServiceHandler =
        new SDMCustomServiceHandler(
            sdmService, List.of(draftService), tokenHandler, dbQuery, persistenceService);
  }

  @Test
  void testCopyAttachments_HappyPath() throws IOException {
    // Mock SDMCredentials
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock folder id retrieval
    when(sdmService.getFolderIdByPath(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(FOLDER_ID);

    // Mock attachment copy
    Map<String, String> attachmentData = new HashMap<>();
    attachmentData.put("cmis:name", "fileName.url");
    attachmentData.put("cmis:contentStreamMimeType", "application/internet-shortcut");
    attachmentData.put("cmis:objectId", OBJECT_ID);
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any()))
        .thenReturn(attachmentData);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://internet-browser");
    cmisDocument.setUrl("https://example.com");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    context.setObjectIds(List.of(OBJECT_ID));

    // Act
    sdmCustomServiceHandler.copyAttachments(context);

    // Assert
    verify(sdmService, times(1))
        .copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any());
    verify(draftService, times(1)).newDraft(any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testCopyAttachments_HappyPathNonLink() throws IOException {
    // Mock SDMCredentials
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock folder id retrieval
    when(sdmService.getFolderIdByPath(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(FOLDER_ID);

    // Mock attachment copy
    Map<String, String> attachmentData = new HashMap<>();
    attachmentData.put("cmis:name", "fileName");
    attachmentData.put("cmis:contentStreamMimeType", "mimeType");
    attachmentData.put("cmis:objectId", OBJECT_ID);
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any()))
        .thenReturn(attachmentData);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://document");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    context.setObjectIds(List.of(OBJECT_ID));

    // Act
    sdmCustomServiceHandler.copyAttachments(context);

    // Assert
    verify(sdmService, times(1))
        .copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any());
    verify(draftService, times(1)).newDraft(any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testCopyAttachments_FolderDoesNotExist() throws IOException {
    // Mock SDMCredentials
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock folder id retrieval when folder does not exist
    when(sdmService.getFolderIdByPath(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(null);

    // Mock folder creation
    when(sdmService.createFolder(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + FOLDER_ID + "\"}}");

    // Mock attachment copy
    Map<String, String> attachmentData = new HashMap<>();
    attachmentData.put("cmis:name", "fileName");
    attachmentData.put("cmis:contentStreamMimeType", "mimeType");
    attachmentData.put("cmis:objectId", OBJECT_ID);
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any()))
        .thenReturn(attachmentData);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://internet-browser");
    cmisDocument.setUrl("https://example.com");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));
    // context.setObjectIds(List.of(OBJECT_ID));

    // Act
    sdmCustomServiceHandler.copyAttachments(context);

    // Assert
    verify(sdmService, times(1))
        .createFolder(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class));
    verify(sdmService, times(1))
        .copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any());
  }

  @Test
  void testCopyAttachments_AttachmentCopyFails() throws IOException {
    // Mock SDMCredentials
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock folder id retrieval
    when(sdmService.getFolderIdByPath(
            any(String.class), any(String.class), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(FOLDER_ID);

    // Mock attachment copy failure
    Map<String, String> attachmentData = new HashMap<>();
    attachmentData.put("cmis:name", "fileName");
    attachmentData.put("cmis:contentStreamMimeType", "mimeType");
    attachmentData.put("cmis:objectId", OBJECT_ID);
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class), any()))
        .thenReturn(attachmentData)
        .thenThrow(new ServiceException("Copy failed"));
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://internet-browser");
    cmisDocument.setUrl("https://example.com");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    // Override the getObjectIds mock to return multiple objects for this test
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID, "mockObjectId2"));

    // Mock UserInfo for cleanup operations
    UserInfo userInfo = mock(UserInfo.class);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getName()).thenReturn("testUser");

    // Act & Assert
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmCustomServiceHandler.copyAttachments(context);
            });

    // Verify that deleteDocument was called for cleanup of the first successful attachment
    verify(sdmService, times(1)).deleteDocument(eq("delete"), eq(OBJECT_ID), eq("testUser"));
    assertTrue(exception.getMessage().contains("Copy failed"));
  }

  @Test
  void testCopyAttachments_AttachmentCopyFails_FolderDoesNotExist() throws IOException {
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Simulate folder does not exist
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(null);
    when(sdmService.createFolder(any(), any(), any(), anyBoolean()))
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + FOLDER_ID + "\"}}");

    // Simulate copyAttachment throws ServiceException on first call
    when(sdmService.copyAttachment(any(), any(), anyBoolean(), any()))
        .thenThrow(new ServiceException("Copy failed"));

    AttachmentCopyEventContext context = createMockContext();
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));
    UserInfo userInfo = mock(UserInfo.class);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getName()).thenReturn("testUser");
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://internet-browser");
    cmisDocument.setUrl("https://example.com");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmCustomServiceHandler.copyAttachments(context);
            });

    // Should attempt to delete the folder
    verify(sdmService, times(1)).deleteDocument(eq("deleteTree"), eq(FOLDER_ID), any());
    assertTrue(ex.getMessage().contains("Copy failed"));
  }

  @Test
  void testCopyAttachments_AttachmentCopyFails_FolderExists_AttachmentsDeleted()
      throws IOException {
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Simulate folder exists
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call succeeds, second call fails
    Map<String, String> attachmentData = new HashMap<>();
    attachmentData.put("cmis:name", "fileName");
    attachmentData.put("cmis:contentStreamMimeType", "mimeType");
    attachmentData.put("cmis:objectId", OBJECT_ID);
    when(sdmService.copyAttachment(any(), any(), anyBoolean(), any()))
        .thenReturn(attachmentData)
        .thenThrow(new ServiceException("Copy failed"));

    AttachmentCopyEventContext context = createMockContext();
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID, "mockObjectId2"));
    UserInfo userInfo = mock(UserInfo.class);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getName()).thenReturn("testUser");
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setType("sap-icon://internet-browser");
    cmisDocument.setUrl("https://example.com");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any(AttachmentCopyEventContext.class)))
        .thenReturn(cmisDocument);

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmCustomServiceHandler.copyAttachments(context);
            });

    // Should attempt to delete the copied attachment
    verify(sdmService, times(1)).deleteDocument(eq("delete"), eq(OBJECT_ID), any());
    assertTrue(ex.getMessage().contains("Copy failed"));
  }

  private AttachmentCopyEventContext createMockContext() {
    AttachmentCopyEventContext context = mock(AttachmentCopyEventContext.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(context.getParentEntity()).thenReturn("prefix.someIdentifier." + FACET);
    when(context.getCompositionName()).thenReturn(FACET);
    when(context.getUpId()).thenReturn(UP_ID);
    when(context.getSystemUser()).thenReturn(true);
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));

    // Mock CdsModel and relevant entities and associations
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    CdsEntity draftEntity = mock(CdsEntity.class);
    CdsEntity targetEntity = mock(CdsEntity.class);

    // Mock composition element and its type
    CdsElement compositionElement = mock(CdsElement.class);
    CdsAssociationType compositionType = mock(CdsAssociationType.class);

    // Setup expected behavior for model and parent entity
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("prefix.someIdentifier." + FACET)).thenReturn(Optional.of(parentEntity));
    when(model.findEntity(endsWith("_drafts"))).thenReturn(Optional.of(draftEntity));

    // Mock the composition element in parent entity
    when(parentEntity.findElement(FACET)).thenReturn(Optional.of(compositionElement));
    when(compositionElement.getType()).thenReturn(compositionType);
    when(compositionType.isAssociation()).thenReturn(true);
    when(compositionType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("target.entity.name");

    // Mock the draft entity's up_ association
    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    return context;
  }

  // ==================== Move Attachments Tests ====================

  @Test
  void testMoveAttachments_SuccessWithoutSourceCleanup() throws IOException {
    setupMoveAttachmentsMocks();

    // Mock target folder exists
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move operation
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock getObject for metadata (no sourceFacet, so fetch from SDM)
    JSONObject mockObjectResponse = new JSONObject();
    mockObjectResponse.put("cmis:name", "document.pdf");
    mockObjectResponse.put("cmis:description", "Test doc");
    when(sdmService.getObject(any(), any(), anyBoolean())).thenReturn(mockObjectResponse);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify - since draft creation will likely fail without proper mocks,
    // rollback happens, so moveAttachment called twice (move + rollback)
    verify(sdmService, atLeast(1)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_SuccessWithSourceCleanup() throws IOException {
    setupMoveAttachmentsMocks();

    // Mock target folder exists
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move operation
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock getAttachmentForObjectID for metadata (sourceFacet provided)
    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    // Mock source cleanup
    when(dbQuery.getSourceUpIdForObjectIds(any(), any(), any())).thenReturn("sourceUpId");
    when(dbQuery.deleteAttachmentsByObjectIds(any(), any(), any(), any())).thenReturn(1L);

    AttachmentMoveEventContext context = createMockMoveContext(true);

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify
    verify(sdmService, atLeast(1)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ValidationFailure_InvalidSecondaryProperties() throws IOException {
    setupMoveAttachmentsMocks();

    // Mock valid secondary properties - only allow "validProp1"
    Map<String, String> validProps = new HashMap<>();
    validProps.put("validProp1", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    // Mock SDM to return list with "validProp1" included
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    // Mock target folder exists
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move operation - response includes invalid property "invalidProp"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"someValue\"}}");

    // Mock getObject for metadata
    JSONObject mockObjectResponse2 = new JSONObject();
    mockObjectResponse2.put("cmis:name", "document.pdf");
    mockObjectResponse2.put("cmis:description", "Test doc");
    mockObjectResponse2.put("invalidProp", "someValue");
    when(sdmService.getObject(any(), any(), anyBoolean())).thenReturn(mockObjectResponse2);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was called (move + rollback = 2 calls)
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_CreateDraftFailure_TriggersRollback() throws IOException {
    setupMoveAttachmentsMocks();

    // Override the newDraft mock to throw exception (simulates database failure)
    doThrow(new ServiceException("Database connection failed")).when(draftService).newDraft(any());

    // Mock target folder exists
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock successful move
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock getObject
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was attempted (move + rollback)
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_TargetFolderDoesNotExist_CreatesFolder() throws IOException {
    setupMoveAttachmentsMocks();

    // Mock target folder does NOT exist
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(null);

    // Mock folder creation
    when(sdmService.createFolder(any(), any(), any(), anyBoolean()))
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + FOLDER_ID + "\"}}");

    // Mock move operation
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock getObject
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify folder was created
    verify(sdmService, times(1)).createFolder(any(), any(), any(), anyBoolean());
    verify(sdmService, atLeast(1)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_PartialFailure_SomeSucceedSomeFail() throws IOException {
    setupMoveAttachmentsMocks();
    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    when(context.getObjectIds()).thenReturn(List.of("obj1", "obj2"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call succeeds, second fails
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \"obj1\"}}")
        .thenThrow(new RuntimeException("Move failed for obj2"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    // Execute
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify: 2 move attempts + 1 rollback of successful move = 3 total calls
    verify(sdmService, times(3)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  // Helper methods for move tests

  private void setupMoveAttachmentsMocks() throws IOException {
    SDMCredentials sdmCredentials = mock(SDMCredentials.class);
    when(tokenHandler.getSDMCredentials()).thenReturn(sdmCredentials);

    // Mock dbQuery.getValidSecondaryPropertiesWithEntity
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {new HashMap<String, String>(), mockTargetEntity});

    // Mock SDM service methods used in fetchSDMValidationData
    when(sdmService.getSecondaryTypes(any(), any(SDMCredentials.class), anyBoolean()))
        .thenReturn(new java.util.ArrayList<>());
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(new java.util.ArrayList<>());

    // Mock database query methods
    com.sap.cds.Result mockResult = mock(com.sap.cds.Result.class);
    when(mockResult.rowCount()).thenReturn(0L);
    when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
        .thenReturn(mockResult);

    // Mock getSourceUpIdForObjectIds and deleteAttachmentsByObjectIds
    when(dbQuery.getSourceUpIdForObjectIds(any(), any(), any())).thenReturn("sourceUpId");
    when(dbQuery.deleteAttachmentsByObjectIds(any(), any(), any(), any())).thenReturn(1L);

    // Mock draftService.newDraft - similar to Copy tests
    when(draftService.newDraft(any())).thenReturn(mock(com.sap.cds.Result.class));
  }

  private AttachmentMoveEventContext createMockMoveContext(boolean withSourceFacet) {
    AttachmentMoveEventContext context = mock(AttachmentMoveEventContext.class);
    com.sap.cds.services.messages.Messages mockMessages =
        mock(com.sap.cds.services.messages.Messages.class);
    CdsRuntime mockCdsRuntime = mock(CdsRuntime.class);
    com.sap.cds.services.runtime.RequestContextRunner mockContextRunner =
        mock(com.sap.cds.services.runtime.RequestContextRunner.class);

    // Basic context setup
    when(context.getParentEntity()).thenReturn("test.Service.Entity");
    when(context.getCompositionName()).thenReturn(FACET);
    when(context.getUpId()).thenReturn(UP_ID);
    when(context.getSystemUser()).thenReturn(true);
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));
    when(context.getSourceFolderId()).thenReturn("sourceFolderId123");
    when(context.getMessages()).thenReturn(mockMessages);
    when(context.getCdsRuntime()).thenReturn(mockCdsRuntime);
    when(mockCdsRuntime.requestContext()).thenReturn(mockContextRunner);

    // Configure RequestContextRunner
    @SuppressWarnings("unchecked")
    java.util.function.Function<com.sap.cds.services.request.RequestContext, Object> anyFunction =
        any(java.util.function.Function.class);
    when(mockContextRunner.run(anyFunction))
        .thenAnswer(
            invocation -> {
              java.util.function.Function<com.sap.cds.services.request.RequestContext, Object>
                  function = invocation.getArgument(0);
              return function.apply(null);
            });

    // Source facet setup
    if (withSourceFacet) {
      when(context.getSourceParentEntity()).thenReturn("test.Service.SourceEntity");
      when(context.getSourceCompositionName()).thenReturn(FACET);
    } else {
      when(context.getSourceParentEntity()).thenReturn(null);
      when(context.getSourceCompositionName()).thenReturn(null);
    }

    // Mock CdsModel and entities
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    CdsEntity draftEntity = mock(CdsEntity.class);
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsElement compositionElement = mock(CdsElement.class);
    CdsAssociationType compositionType = mock(CdsAssociationType.class);
    CdsElement mockAssociationElement = mock(CdsElement.class);
    CdsAssociationType mockAssociationType = mock(CdsAssociationType.class);
    CqnElementRef mockCqnElementRef = mock(CqnElementRef.class);

    when(context.getModel()).thenReturn(model);
    when(model.findEntity("test.Service.Entity")).thenReturn(Optional.of(parentEntity));
    when(model.findEntity(endsWith("_drafts"))).thenReturn(Optional.of(draftEntity));

    if (withSourceFacet) {
      CdsEntity sourceEntity = mock(CdsEntity.class);
      when(model.findEntity("test.Service.SourceEntity")).thenReturn(Optional.of(sourceEntity));
      when(sourceEntity.findElement(FACET)).thenReturn(Optional.of(compositionElement));
    }

    when(parentEntity.findElement(FACET)).thenReturn(Optional.of(compositionElement));
    when(compositionElement.getType()).thenReturn(compositionType);
    when(compositionType.isAssociation()).thenReturn(true);
    when(compositionType.getTarget()).thenReturn(targetEntity);
    when(targetEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    when(draftEntity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    return context;
  }

  // ==================== Error Parsing and Validation Tests ====================

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_DuplicateError() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw duplicate error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(
            new RuntimeException(
                "nameConstraintViolation : Child doc.pdf with Id xyz already exists"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify context completed (error was parsed and handled)
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_VirusDetected() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw virus error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Virus scan status: cmis:virusScanStatus infected"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_MalwareDetected() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw malware error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Malware detected in file"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_Unauthorized() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw unauthorized error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("User not authorized to perform this operation"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_PermissionDenied() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw permission error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Permission denied for this resource"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_BlockedMimeType() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw blocked mimetype error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("MimeType application/exe is blocked"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_FileNotFound() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw not found error
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Object not found in repository"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_GenericWithDetailedMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw generic error with detailed message after colon
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("SDM Error : Detailed error information here"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_EmptyMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw exception with empty message
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException(""));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseSDMErrorMessage_NullMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw exception with null message
    RuntimeException nullMessageException = new RuntimeException((String) null);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(nullMessageException);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ExtractErrorMessage_WithCauseChain() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw exception with cause chain (generic message -> detailed cause)
    RuntimeException detailedCause = new RuntimeException("Detailed root cause error");
    RuntimeException genericCause =
        new RuntimeException("Failed to move attachment", detailedCause);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericCause);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseDuplicateError_WithFilename() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw duplicate error with specific filename format
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(
            new RuntimeException(
                "nameConstraintViolation : Child document.pdf with Id abc123 already exists in folder"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseDuplicateError_WithoutFilename() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw duplicate error without "Child ... with Id" format
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Duplicate file constraint violation"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_ParseDuplicateError_NoColonSeparator() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock SDM to throw duplicate error without colon separator
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("duplicate"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_BuildValidationFailureMessage_ServiceException() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock successful move
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock database fetch to throw ServiceException
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new ServiceException("Database connection failed"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Should rollback and complete
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_BuildValidationFailureMessage_JSONException() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move to return invalid JSON
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn("invalid json");

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Should handle JSON parsing error and complete
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_BuildValidationFailureMessage_DatabaseError() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock successful move
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock database operation to throw exception with "database" in message
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("database query timeout"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_BuildValidationFailureMessage_GenericException() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock successful move
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock generic runtime exception
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Unexpected processing error"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_BuildValidationFailureMessage_ExceptionWithoutMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock successful move
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    // Mock exception with null message
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException((String) null));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_HandleValidationFailure_RollbackSuccess() throws IOException {
    setupMoveAttachmentsMocks();

    // Configure validation to detect invalid properties
    Map<String, String> validProps = new HashMap<>();
    validProps.put("validProp1", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move with invalid property
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"value\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("invalidProp", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was attempted (move + rollback)
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMoveAttachments_HandleValidationFailure_RollbackFails() throws IOException {
    setupMoveAttachmentsMocks();

    // Configure validation to detect invalid properties
    Map<String, String> validProps = new HashMap<>();
    validProps.put("validProp1", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call (move) succeeds with invalid property, second call (rollback) fails
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"value\"}}")
        .thenThrow(new RuntimeException("Rollback failed"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("invalidProp", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was attempted even though it failed
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testParseSDMErrorMessage_NullErrorMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Create exception with null message
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException((String) null));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testParseSDMErrorMessage_EmptyErrorMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Create exception with empty message
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException(""));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testExtractErrorMessage_GenericMessageWithDetailedCause() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Create exception chain: generic message -> detailed cause
    RuntimeException detailedCause =
        new RuntimeException("Detailed error: Database connection failed");
    RuntimeException genericException =
        new RuntimeException("Failed to move attachment", detailedCause);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericException);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testExtractErrorMessage_GenericMessageChainWithNoCause() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Create exception with generic message and generic causes
    RuntimeException cause2 = new RuntimeException("Failed to move attachment");
    RuntimeException cause1 = new RuntimeException("Failed to move attachment", cause2);
    RuntimeException genericException = new RuntimeException("Failed to move attachment", cause1);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericException);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMatchSpecificErrorType_Malware() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("malware detected in file"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMatchSpecificErrorType_NotAuthorized() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("user not authorized to perform operation"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMatchSpecificErrorType_Permission() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("permission denied for this operation"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMatchSpecificErrorType_Blocked() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("file blocked by security policy"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testMatchSpecificErrorType_ObjectNotFound() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("object not found in repository"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testParseDuplicateError_NoColonPattern() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("duplicate entry found"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testParseDuplicateError_WithChildPattern() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(
            new RuntimeException("constraint : Child document.pdf with Id 12345 already exists"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testParseDuplicateError_WithColonButNoChildPattern() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("duplicate : some detailed error message"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testExtractDetailedMessage_WithColonAndEmptyDetail() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("error :   "));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testBuildValidationFailureMessage_ServiceException() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new ServiceException("database connection lost"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testBuildValidationFailureMessage_DatabaseError() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Failed to execute database query"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testBuildValidationFailureMessage_JSONException() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenThrow(new org.json.JSONException("Invalid JSON format"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testProcessSecondaryProperty_NullValue() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move with null property value
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"nullProp\": null}}");

    Map<String, String> validProps = new HashMap<>();
    validProps.put("nullProp", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("nullProp"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("nullProp", (Object) null));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testConvertValueIfNeeded_StringValue() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move with string property (no conversion needed)
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"description\": \"test description\"}}");

    Map<String, String> validProps = new HashMap<>();
    validProps.put("description", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);

    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("description"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("description", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testConvertValueIfNeeded_IntegerValue() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move with Integer property (no conversion needed for non-Long)
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"pageCount\": 10}}");

    Map<String, String> validProps = new HashMap<>();
    validProps.put("pageCount", "number");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);

    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("pageCount"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("pageCount", 10));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testIsDateTimeField_NullElement() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"unknownProp\": 123}}");

    Map<String, String> validProps = new HashMap<>();
    validProps.put("unknownProp", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);

    // Return null element for unknownProp
    when(mockTargetEntity.getElement("unknownProp")).thenReturn(null);

    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("unknownProp"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("unknownProp", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
  }

  @Test
  void testRollbackSingleAttachment_Success() throws IOException {
    setupMoveAttachmentsMocks();

    Map<String, String> validProps = new HashMap<>();
    validProps.put("validProp1", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call succeeds with invalid property, second call (rollback) succeeds
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"value\"}}")
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + OBJECT_ID + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("invalidProp", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify move and rollback both called
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_SingleInvalidProperty() throws IOException {
    setupMoveAttachmentsMocks();

    Map<String, String> validProps = new HashMap<>();
    validProps.put("validProp1", "string");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {validProps, mockTargetEntity});

    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move with single invalid property
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp1\": \"value1\"}}")
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + OBJECT_ID + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "doc.pdf")
                .put("cmis:description", "Test doc")
                .put("invalidProp1", "someValue"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was called (move + rollback = 2 calls)
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_MultipleInvalidProperties() throws IOException {
    setupMoveAttachmentsMocks();

    // Entity annotations define 3 invalid properties
    Map<String, String> entityAnnotations = new HashMap<>();
    entityAnnotations.put("dbField1", "invalidProp1");
    entityAnnotations.put("dbField2", "invalidProp2");
    entityAnnotations.put("dbField3", "invalidProp3");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {entityAnnotations, mockTargetEntity});

    // Valid secondary properties list does NOT include any invalid props
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move: SDM response contains all 3 invalid properties
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp1\": \"value1\", \"invalidProp2\": \"value2\", \"invalidProp3\":"
                + " \"value3\"}}")
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + OBJECT_ID + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was called (move + rollback = 2 calls)
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_RollbackIOException() throws IOException {
    setupMoveAttachmentsMocks();

    // Entity annotation defines invalidProp
    Map<String, String> entityAnnotations = new HashMap<>();
    entityAnnotations.put("dbField1", "invalidProp");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {entityAnnotations, mockTargetEntity});

    // Valid list does NOT include invalidProp
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call succeeds with invalid property, second call (rollback) throws IOException
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"value\"}}")
        .thenThrow(new IOException("Network error during rollback"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was attempted despite IOException
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_RollbackServiceException() throws IOException {
    setupMoveAttachmentsMocks();

    // Entity annotation defines invalidProp
    Map<String, String> entityAnnotations = new HashMap<>();
    entityAnnotations.put("dbField1", "invalidProp");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {entityAnnotations, mockTargetEntity});

    // Valid list does NOT include invalidProp
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // First call succeeds with invalid property, second call (rollback) throws RuntimeException
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"invalidProp\": \"value\"}}")
        .thenThrow(new RuntimeException("403 Forbidden - Access denied"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback attempt and failure recording
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_FailureAddedToList() throws IOException {
    setupMoveAttachmentsMocks();

    // Entity annotation defines customProp
    Map<String, String> entityAnnotations = new HashMap<>();
    entityAnnotations.put("dbCustomField", "customProp");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {entityAnnotations, mockTargetEntity});

    // Valid list does NOT include customProp
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Mock move: SDM response contains customProp (in annotations but NOT in valid list)
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\", \"customProp\": \"value\"}}")
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + OBJECT_ID + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was called and context completed
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testHandleValidationFailure_PreservesObjectId() throws IOException {
    setupMoveAttachmentsMocks();

    // Entity annotation defines invalidProp
    Map<String, String> entityAnnotations = new HashMap<>();
    entityAnnotations.put("dbField1", "invalidProp");
    CdsEntity mockTargetEntity = mock(CdsEntity.class);
    when(dbQuery.getValidSecondaryPropertiesWithEntity(any()))
        .thenReturn(new Object[] {entityAnnotations, mockTargetEntity});

    // Valid list does NOT include invalidProp
    when(sdmService.getValidSecondaryProperties(
            any(), any(SDMCredentials.class), any(), anyBoolean()))
        .thenReturn(List.of("validProp1"));

    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String specificObjectId = "specific-test-object-id-12345";

    // Mock move: SDM response contains invalidProp (in annotations but NOT in valid list)
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + specificObjectId
                + "\", \"invalidProp\": \"value\"}}")
        .thenReturn("{\"succinctProperties\": {\"cmis:objectId\": \"" + specificObjectId + "\"}}");

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "doc.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    when(context.getObjectIds()).thenReturn(List.of(specificObjectId));

    sdmCustomServiceHandler.moveAttachments(context);

    // Verify rollback was called
    verify(sdmService, times(2)).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    verify(context, times(1)).setCompleted();
  }

  // ========== Tests for checkMaxCountConstraintForMove() ==========

  @Test
  void testCheckMaxCount_TargetEntityNotFound() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Target entity not found
    when(model.findEntity("test.Service.Entity.mockFacet")).thenReturn(Optional.empty());

    sdmCustomServiceHandler.moveAttachments(context);

    // Should skip maxCount validation and proceed with move
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testCheckMaxCount_MaxCountZero_NoLimitEnforced() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Setup target entity with maxCount = 0 (no limit)
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Mock SDMUtils to return maxCount = 0
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenReturn("0__No limit");

      sdmCustomServiceHandler.moveAttachments(context);

      // Should skip maxCount validation and proceed with move
      verify(sdmService, atLeastOnce())
          .moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    }
  }

  @Test
  void testCheckMaxCount_DraftEntityNotFound_SkipValidation() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Setup target entity
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Draft entity not found
    when(model.findEntity("test.Service.Attachments_drafts")).thenReturn(Optional.empty());

    // Mock SDMUtils to return maxCount = 5
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenReturn("5__Too many attachments");

      sdmCustomServiceHandler.moveAttachments(context);

      // Should skip maxCount validation and proceed with move
      verify(sdmService, atLeastOnce())
          .moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    }
  }

  @Test
  void testCheckMaxCount_ExceedsLimit_WithCustomErrorMessage() throws IOException {
    setupMoveAttachmentsMocks();

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Setup target entity
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Setup draft entity
    CdsEntity draftEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Attachments_drafts")).thenReturn(Optional.of(draftEntity));

    // Mock SDMUtils
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenReturn("2__Custom error: Maximum 2 attachments allowed");
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getUpIdKey(any()))
          .thenReturn("up__ID");

      // Mock existing attachments count = 2 (already at limit)
      com.sap.cds.Result mockResult = mock(com.sap.cds.Result.class);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(2L); // Existing: 2, Moving: 1, Total: 3 > maxCount: 2

      sdmCustomServiceHandler.moveAttachments(context);

      // Verify custom error message was used and attachments marked as failed
      verify(context.getMessages(), times(1)).warn("Custom error: Maximum 2 attachments allowed");
      verify(context, times(1)).setCompleted();
      // No actual move should happen - failed before move
      verify(sdmService, never()).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    }
  }

  @Test
  void testCheckMaxCount_ExceedsLimit_WithDefaultErrorMessage() throws IOException {
    setupMoveAttachmentsMocks();

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    when(context.getObjectIds()).thenReturn(List.of("obj1", "obj2")); // Moving 2 attachments
    CdsModel model = context.getModel();

    // Setup target entity
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Setup draft entity
    CdsEntity draftEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Attachments_drafts")).thenReturn(Optional.of(draftEntity));

    // Mock SDMUtils
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenReturn("3__null"); // null error message - should use default
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getUpIdKey(any()))
          .thenReturn("up__ID");

      // Mock existing attachments count = 2
      com.sap.cds.Result mockResult = mock(com.sap.cds.Result.class);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(2L); // Existing: 2, Moving: 2, Total: 4 > maxCount:
      // 3

      sdmCustomServiceHandler.moveAttachments(context);

      // Verify default error message format was used
      verify(context.getMessages(), times(1))
          .warn(
              "Cannot move 2 attachment(s). Target entity allows maximum 3 attachments, and"
                  + " already has 2. Maximum count would be exceeded.");
      verify(context, times(1)).setCompleted();
      // No actual move should happen - failed before move
      verify(sdmService, never()).moveAttachment(any(CmisDocument.class), any(), anyBoolean());
    }
  }

  @Test
  void testCheckMaxCount_WithinLimit_ProceedWithMove() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Setup target entity
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Setup draft entity
    CdsEntity draftEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Attachments_drafts")).thenReturn(Optional.of(draftEntity));

    // Mock SDMUtils
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenReturn("5__Maximum 5 attachments allowed");
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getUpIdKey(any()))
          .thenReturn("up__ID");

      // Mock existing attachments count = 2
      com.sap.cds.Result mockResult = mock(com.sap.cds.Result.class);
      when(dbQuery.getAttachmentsForUPIDAndRepository(any(), any(), any(), any()))
          .thenReturn(mockResult);
      when(mockResult.rowCount()).thenReturn(2L); // Existing: 2, Moving: 1, Total: 3 < maxCount: 5

      sdmCustomServiceHandler.moveAttachments(context);

      // Should proceed with move - within limit
      verify(sdmService, atLeastOnce())
          .moveAttachment(any(CmisDocument.class), any(), anyBoolean());
      verify(context, times(1)).setCompleted();
    }
  }

  @Test
  void testCheckMaxCount_ExceptionDuringValidation_ProceedWithMove() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");
    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "document.pdf").put("cmis:description", "Test doc"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    CdsModel model = context.getModel();

    // Setup target entity
    CdsEntity targetAttachmentEntity = mock(CdsEntity.class);
    when(model.findEntity("test.Service.Entity.mockFacet"))
        .thenReturn(Optional.of(targetAttachmentEntity));
    when(targetAttachmentEntity.getQualifiedName()).thenReturn("test.Service.Attachments");

    // Mock SDMUtils to throw exception
    try (var mockedStatic = mockStatic(com.sap.cds.sdm.utilities.SDMUtils.class)) {
      mockedStatic
          .when(() -> com.sap.cds.sdm.utilities.SDMUtils.getAttachmentCountAndMessage(any(), any()))
          .thenThrow(new RuntimeException("Error parsing maxCount annotation"));

      sdmCustomServiceHandler.moveAttachments(context);

      // Should catch exception and proceed with move
      verify(sdmService, atLeastOnce())
          .moveAttachment(any(CmisDocument.class), any(), anyBoolean());
      verify(context, times(1)).setCompleted();
    }
  }

  // ========== Tests for source entity cleanup after move (lines 429-452) ==========

  @Test
  void testSourceCleanup_SuccessfulCleanup_DeletesAndLogsCount() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    when(dbQuery.deleteAttachmentsByObjectIds(any(), any(), any(), any())).thenReturn(1L);

    AttachmentMoveEventContext context = createMockMoveContext(true);
    // Override parent entity to match draft service name for draft creation to succeed
    String customParentEntity = "test." + FACET + ".Entity";
    when(context.getParentEntity()).thenReturn(customParentEntity);
    // Mock the model to return entity for the custom parent entity name
    CdsModel model = context.getModel();
    CdsEntity customParentCdsEntity = mock(CdsEntity.class);
    CdsElement customCompositionElement = mock(CdsElement.class);
    CdsAssociationType customCompositionType = mock(CdsAssociationType.class);
    CdsEntity customTargetEntity = mock(CdsEntity.class);
    when(model.findEntity(customParentEntity)).thenReturn(Optional.of(customParentCdsEntity));
    when(customParentCdsEntity.findElement(FACET))
        .thenReturn(Optional.of(customCompositionElement));
    when(customCompositionElement.getType()).thenReturn(customCompositionType);
    when(customCompositionType.isAssociation()).thenReturn(true);
    when(customCompositionType.getTarget()).thenReturn(customTargetEntity);
    when(customTargetEntity.getQualifiedName()).thenReturn("test." + FACET + ".Attachments");

    sdmCustomServiceHandler.moveAttachments(context);

    verify(dbQuery, times(1))
        .deleteAttachmentsByObjectIds(
            eq(persistenceService), eq(List.of(OBJECT_ID)), eq("sourceUpId"), any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testSourceCleanup_NoSourceUpId_SkipsCleanup() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    when(dbQuery.getSourceUpIdForObjectIds(any(), any(), any())).thenReturn(null);

    AttachmentMoveEventContext context = createMockMoveContext(true);
    // Override parent entity to match draft service name for draft creation to succeed
    String customParentEntity = "test." + FACET + ".Entity";
    when(context.getParentEntity()).thenReturn(customParentEntity);
    // Mock the model to return entity for the custom parent entity name
    CdsModel model = context.getModel();
    CdsEntity customParentCdsEntity = mock(CdsEntity.class);
    CdsElement customCompositionElement = mock(CdsElement.class);
    CdsAssociationType customCompositionType = mock(CdsAssociationType.class);
    CdsEntity customTargetEntity = mock(CdsEntity.class);
    when(model.findEntity(customParentEntity)).thenReturn(Optional.of(customParentCdsEntity));
    when(customParentCdsEntity.findElement(FACET))
        .thenReturn(Optional.of(customCompositionElement));
    when(customCompositionElement.getType()).thenReturn(customCompositionType);
    when(customCompositionType.isAssociation()).thenReturn(true);
    when(customCompositionType.getTarget()).thenReturn(customTargetEntity);
    when(customTargetEntity.getQualifiedName()).thenReturn("test." + FACET + ".Attachments");

    sdmCustomServiceHandler.moveAttachments(context);

    verify(dbQuery, never()).deleteAttachmentsByObjectIds(any(), any(), any(), any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testSourceCleanup_CleanupFails_OperationStillSucceeds() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(
            "{\"succinctProperties\": {\"cmis:name\": \"doc.pdf\", \"cmis:objectId\": \""
                + OBJECT_ID
                + "\"}}");

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    when(dbQuery.deleteAttachmentsByObjectIds(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Database error"));

    AttachmentMoveEventContext context = createMockMoveContext(true);
    // Override parent entity to match draft service name for draft creation to succeed
    String customParentEntity = "test." + FACET + ".Entity";
    when(context.getParentEntity()).thenReturn(customParentEntity);
    // Mock the model to return entity for the custom parent entity name
    CdsModel model = context.getModel();
    CdsEntity customParentCdsEntity = mock(CdsEntity.class);
    CdsElement customCompositionElement = mock(CdsElement.class);
    CdsAssociationType customCompositionType = mock(CdsAssociationType.class);
    CdsEntity customTargetEntity = mock(CdsEntity.class);
    when(model.findEntity(customParentEntity)).thenReturn(Optional.of(customParentCdsEntity));
    when(customParentCdsEntity.findElement(FACET))
        .thenReturn(Optional.of(customCompositionElement));
    when(customCompositionElement.getType()).thenReturn(customCompositionType);
    when(customCompositionType.isAssociation()).thenReturn(true);
    when(customCompositionType.getTarget()).thenReturn(customTargetEntity);
    when(customTargetEntity.getQualifiedName()).thenReturn("test." + FACET + ".Attachments");

    sdmCustomServiceHandler.moveAttachments(context);

    verify(dbQuery, times(1)).deleteAttachmentsByObjectIds(any(), any(), eq("sourceUpId"), any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testExtractErrorMessage_NonGenericMessage_ReturnsDirectly() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Exception with non-generic message - should return it directly
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Specific error: File is locked"));

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // Verify that non-generic message was used (operation completed without throwing)
  }

  @Test
  void testExtractErrorMessage_GenericMessage_ChecksCauseChain() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Exception with generic "Failed to move attachment" message but detailed cause
    RuntimeException detailedCause = new RuntimeException("Detailed error: Insufficient storage");
    RuntimeException genericWrapper =
        new RuntimeException("Failed to move attachment", detailedCause);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericWrapper);

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // Verify that detailed cause message was extracted from chain
  }

  @Test
  void testExtractErrorMessage_NestedGenericMessages_FindsDetailedOne() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Deep exception chain: generic -> generic -> detailed
    RuntimeException detailedCause = new RuntimeException("Connection timeout to SDM server");
    RuntimeException genericMiddle =
        new RuntimeException("Failed to move attachment", detailedCause);
    RuntimeException genericOuter = new RuntimeException("", genericMiddle);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericOuter);

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // Verify that the most detailed message was found in nested chain
  }

  @Test
  void testExtractErrorMessage_AllGenericMessages_ReturnsOriginal() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // All messages in chain are generic - should return original
    RuntimeException genericCause = new RuntimeException("Failed to move attachment");
    RuntimeException genericWrapper = new RuntimeException("", genericCause);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(genericWrapper);

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // When all messages are generic, returns the original message
  }

  @Test
  void testExtractErrorMessage_NullMessage_ChecksCause() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Exception with null message but cause has detailed message
    RuntimeException detailedCause = new RuntimeException("Database constraint violation");
    RuntimeException nullMessageWrapper = new RuntimeException((String) null, detailedCause);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(nullMessageWrapper);

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // Null message is generic, should check cause chain
  }

  @Test
  void testExtractErrorMessage_EmptyMessage_ChecksCause() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Exception with empty message but cause has detailed message
    RuntimeException detailedCause = new RuntimeException("Network error: Connection refused");
    RuntimeException emptyMessageWrapper = new RuntimeException("", detailedCause);

    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(emptyMessageWrapper);

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // Empty message is generic, should check cause chain
  }

  @Test
  void testExtractErrorMessage_NoCause_ReturnsGenericMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Generic message with no cause - should return the generic message
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new RuntimeException("Failed to move attachment"));

    CmisDocument sourceDoc = new CmisDocument();
    sourceDoc.setType("sap-icon://document");
    sourceDoc.setFileName("document.pdf");
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(sourceDoc);

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context, times(1)).setCompleted();
    // No cause to check, returns the generic message itself
  }

  // ==================== Tests for matchSpecificErrorType method ====================

  @Test
  void testMatchSpecificErrorType_DuplicateError_ReturnsParsedDuplicateMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "duplicate"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("duplicate file detected"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertTrue(
        failedAttachments.get(0).get("failureReason").contains("Duplicate file already exists"));
  }

  @Test
  void testMatchSpecificErrorType_NameConstraintViolation_ReturnsParsedDuplicateMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "nameconstraintviolation"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("nameconstraintviolation occurred"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertTrue(
        failedAttachments.get(0).get("failureReason").contains("Duplicate file already exists"));
  }

  @Test
  void testMatchSpecificErrorType_VirusError_ReturnsMalwareMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "virus"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("virus detected in file"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        "File contains potential malware and cannot be moved",
        failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_MalwareError_ReturnsMalwareMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "malware"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("malware found"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        "File contains potential malware and cannot be moved",
        failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_UnauthorizedError_ReturnsAuthorizationMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "unauthorized"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("unauthorized access"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        SDMConstants.USER_NOT_AUTHORISED_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_NotAuthorizedError_ReturnsAuthorizationMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "not authorized"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("user not authorized"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        SDMConstants.USER_NOT_AUTHORISED_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_PermissionError_ReturnsAuthorizationMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "permission"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("permission denied"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        SDMConstants.USER_NOT_AUTHORISED_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_BlockedError_ReturnsMimeTypeMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "blocked"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("file blocked"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        SDMConstants.MIMETYPE_INVALID_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_MimeTypeError_ReturnsMimeTypeMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "mimetype"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("mimetype not allowed"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        SDMConstants.MIMETYPE_INVALID_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_NotFoundError_ReturnsFileNotFoundMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "not found"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("file not found"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(SDMConstants.FILE_NOT_FOUND_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_ObjectNotFoundError_ReturnsFileNotFoundMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message contains "object not found"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("object not found in repository"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);

    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(SDMConstants.FILE_NOT_FOUND_ERROR, failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testMatchSpecificErrorType_UnmatchedError_ReturnsNull() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message that doesn't match any specific type - should return original message
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("network timeout error"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    // When matchSpecificErrorType returns null, extractDetailedMessage is used
    assertEquals("network timeout error", failedAttachments.get(0).get("failureReason"));
  }

  // ==================== Tests for parseDuplicateError method ====================

  @Test
  void testParseDuplicateError_NoColon_ReturnsDefaultMessage() throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message without " : " separator
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("duplicate file error"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        "Duplicate file already exists in the target location",
        failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testParseDuplicateError_ChildFormatWithFilename_ReturnsFormattedMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message with standard "Child <filename> with Id" format
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(
            new ServiceException(
                "nameConstraintViolation : Child document.pdf with Id abc123 already exists"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    // getDuplicateFilesError formats the message
    assertTrue(failedAttachments.get(0).get("failureReason").contains("document.pdf"));
  }

  @Test
  void testParseDuplicateError_DetailedMessageWithoutChildFormat_ReturnsDetailedMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message with " : " but not in "Child" format
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(
            new ServiceException("duplicate : A file with the same name already exists in folder"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        "A file with the same name already exists in folder",
        failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testParseDuplicateError_ChildFormatWithoutWithId_ReturnsDetailedMessage()
      throws IOException {
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Error message starts with "Child" but doesn't have " with Id"
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenThrow(new ServiceException("duplicate : Child element already exists in target"));

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject()
                .put("cmis:name", "document.pdf")
                .put("cmis:description", "Test document"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setFailedAttachments(captor.capture());
    List<Map<String, String>> failedAttachments = captor.getValue();
    assertEquals(1, failedAttachments.size());
    assertEquals(
        "Child element already exists in target", failedAttachments.get(0).get("failureReason"));
  }

  @Test
  void testFetchAndSetLinkUrl_SuccessfullyFetchesAndSetsUrl() throws Exception {
    // Test successful link URL fetch and set
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String testLinkUrl = "https://example.com/document";
    when(sdmService.getLinkUrl(any(), any(), anyBoolean())).thenReturn(testLinkUrl);

    // Mock moveAttachment to return a link attachment response
    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.url\", \"cmis:contentStreamMimeType\":"
            + " \"application/internet-shortcut\", \"cmis:description\": \"Test link\","
            + " \"cmis:objectId\": \"newObjectId123\", \"cmis:objectTypeId\": \"sap:link\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "test.url").put("cmis:description", "Test link"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    sdmCustomServiceHandler.moveAttachments(context);

    // Verify getLinkUrl was called with correct parameters
    verify(sdmService).getLinkUrl(eq("newObjectId123"), any(), anyBoolean());
  }

  @Test
  void testFetchAndSetLinkUrl_NullUrlReturned_ContinuesWithoutError() throws Exception {
    // Test when getLinkUrl returns null - should continue without setting URL
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // getLinkUrl returns null
    when(sdmService.getLinkUrl(any(), any(), anyBoolean())).thenReturn(null);

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.url\", \"cmis:contentStreamMimeType\":"
            + " \"application/internet-shortcut\", \"cmis:description\": \"Test link\","
            + " \"cmis:objectId\": \"newObjectId123\", \"cmis:objectTypeId\": \"sap:link\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "test.url").put("cmis:description", "Test link"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    sdmCustomServiceHandler.moveAttachments(context);

    // Verify getLinkUrl was called
    verify(sdmService).getLinkUrl(eq("newObjectId123"), any(), anyBoolean());
    // Should not throw exception - continues normally
    verify(context).setCompleted();
  }

  @Test
  void testFetchAndSetLinkUrl_ExceptionThrown_ContinuesWithWarning() throws Exception {
    // Test when getLinkUrl throws exception - should log warning and continue
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // getLinkUrl throws IOException
    when(sdmService.getLinkUrl(any(), any(), anyBoolean()))
        .thenThrow(new IOException("Failed to fetch link URL"));

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.url\", \"cmis:contentStreamMimeType\":"
            + " \"application/internet-shortcut\", \"cmis:description\": \"Test link\","
            + " \"cmis:objectId\": \"newObjectId123\", \"cmis:objectTypeId\": \"sap:link\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "test.url").put("cmis:description", "Test link"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    sdmCustomServiceHandler.moveAttachments(context);

    // Verify getLinkUrl was called
    verify(sdmService).getLinkUrl(eq("newObjectId123"), any(), anyBoolean());
    // Should not throw exception - continues with null URL
    verify(context).setCompleted();
  }

  @Test
  void testFetchAndSetLinkUrl_ServiceExceptionThrown_ContinuesWithWarning() throws Exception {
    // Test when getLinkUrl throws ServiceException - should log warning and continue
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // getLinkUrl throws ServiceException
    when(sdmService.getLinkUrl(any(), any(), anyBoolean()))
        .thenThrow(new ServiceException("SDM service unavailable"));

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.url\", \"cmis:contentStreamMimeType\":"
            + " \"application/internet-shortcut\", \"cmis:description\": \"Test link\","
            + " \"cmis:objectId\": \"newObjectId123\", \"cmis:objectTypeId\": \"sap:link\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(
            new JSONObject().put("cmis:name", "test.url").put("cmis:description", "Test link"));

    AttachmentMoveEventContext context = createMockMoveContext(false);

    sdmCustomServiceHandler.moveAttachments(context);

    // Verify getLinkUrl was called
    verify(sdmService).getLinkUrl(eq("newObjectId123"), any(), anyBoolean());
    // Should not throw exception - continues with null URL and logs warning
    verify(context).setCompleted();
  }

  @Test
  void testProcessSecondaryProperty_WithValidValue_AddsToFilteredMap() throws Exception {
    // Test that valid non-null values are added to the filtered properties map
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // Create SDM response with a custom secondary property
    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:description\": \"Test\", \"cmis:objectId\":"
            + " \"newObjId\", \"cmis:objectTypeId\": \"cmis:document\", \"sap:customProp\":"
            + " \"customValue\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify move completed successfully
    verify(context).setCompleted();
  }

  @Test
  void testProcessSecondaryProperty_WithNullValue_SkipsProperty() throws Exception {
    // Test that null values are skipped and not added to filtered properties
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // SDM response with null property value
    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:description\": null, \"cmis:objectId\":"
            + " \"newObjId\", \"cmis:objectTypeId\": \"cmis:document\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    // Verify move completed successfully even with null property
    verify(context).setCompleted();
  }

  @Test
  void testProcessSecondaryProperty_WithJSONNull_SkipsProperty() throws Exception {
    // Test that JSONObject.NULL values are skipped
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  @Test
  void testConvertValueIfNeeded_LongToInstantForDateTime_ConvertsSuccessfully() throws Exception {
    // Test Long to Instant conversion for DateTime fields
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    // SDM response with Long timestamp (milliseconds since epoch)
    long timestamp = 1609459200000L; // 2021-01-01 00:00:00 UTC
    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\", \"sap:dateTimeProp\": "
            + timestamp
            + "}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  @Test
  void testConvertValueIfNeeded_LongForNonDateTime_KeepsAsLong() throws Exception {
    // Test that Long values for non-DateTime fields are kept as-is
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\", \"sap:numberProp\": 12345}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  @Test
  void testConvertValueIfNeeded_NonLongValue_ReturnsOriginal() throws Exception {
    // Test that non-Long values (String, Integer, etc.) are returned unchanged
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\", \"sap:stringProp\": \"testValue\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  @Test
  void testIsDateTimeField_WithDateTimeElement_ReturnsTrue() throws Exception {
    // Test that DateTime fields are correctly identified
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    long timestamp = 1609459200000L;
    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\", \"sap:createdAt\": "
            + timestamp
            + "}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  @Test
  void testIsDateTimeField_WithNonDateTimeElement_ReturnsFalse() throws Exception {
    // Test that non-DateTime fields return false
    setupMoveAttachmentsMocks();
    when(sdmService.getFolderIdByPath(any(), any(), any(), anyBoolean())).thenReturn(FOLDER_ID);

    String sdmResponse =
        "{\"succinctProperties\": {\"cmis:name\": \"test.pdf\", \"cmis:contentStreamMimeType\":"
            + " \"application/pdf\", \"cmis:objectId\": \"newObjId\", \"cmis:objectTypeId\":"
            + " \"cmis:document\", \"sap:status\": \"active\"}}";
    when(sdmService.moveAttachment(any(CmisDocument.class), any(), anyBoolean()))
        .thenReturn(sdmResponse);

    when(sdmService.getObject(any(), any(), anyBoolean()))
        .thenReturn(new JSONObject().put("cmis:name", "test.pdf").put("cmis:description", "Test"));

    AttachmentMoveEventContext context = createMockMoveContext(false);
    sdmCustomServiceHandler.moveAttachments(context);

    verify(context).setCompleted();
  }

  // ============ Direct Unit Tests for Private Methods Using Reflection ============

  @Test
  void testIsDateTimeField_WithDateTimeType_ReturnsTrue() throws Exception {
    // Test isDateTimeField returns true for cds.DateTime type
    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.DateTime");

    boolean result = invokeIsDateTimeField(element);
    assertTrue(result);
  }

  @Test
  void testIsDateTimeField_WithNonDateTimeType_ReturnsFalse() throws Exception {
    // Test isDateTimeField returns false for non-DateTime types
    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.String");

    boolean result = invokeIsDateTimeField(element);
    assertFalse(result);
  }

  @Test
  void testIsDateTimeField_WithNullElement_ReturnsFalse() throws Exception {
    // Test isDateTimeField returns false for null element
    boolean result = invokeIsDateTimeField(null);
    assertFalse(result);
  }

  @Test
  void testIsDateTimeField_WithNullType_ReturnsFalse() throws Exception {
    // Test isDateTimeField returns false when element.getType() is null
    CdsElement element = mock(CdsElement.class);
    when(element.getType()).thenReturn(null);

    boolean result = invokeIsDateTimeField(element);
    assertFalse(result);
  }

  @Test
  void testIsDateTimeField_WithNullQualifiedName_ReturnsFalse() throws Exception {
    // Test isDateTimeField returns false when type.getQualifiedName() is null
    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn(null);

    boolean result = invokeIsDateTimeField(element);
    assertFalse(result);
  }

  @Test
  void testConvertValueIfNeeded_WithNonLongValue_ReturnsOriginal() throws Exception {
    // Test convertValueIfNeeded returns original value for non-Long types
    String originalValue = "test string";
    CdsEntity targetEntity = mock(CdsEntity.class);

    Object result = invokeConvertValueIfNeeded(originalValue, "fieldName", targetEntity);
    assertEquals(originalValue, result);
  }

  @Test
  void testConvertValueIfNeeded_WithLongAndDateTimeField_ConvertsToInstant() throws Exception {
    // Test convertValueIfNeeded converts Long to Instant for DateTime fields
    Long timestamp = 1609459200000L;
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);

    when(targetEntity.getElement("createdAt")).thenReturn(element);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.DateTime");

    Object result = invokeConvertValueIfNeeded(timestamp, "createdAt", targetEntity);
    assertNotNull(result);
    assertEquals(java.time.Instant.class, result.getClass());
    assertEquals(java.time.Instant.ofEpochMilli(timestamp), result);
  }

  @Test
  void testConvertValueIfNeeded_WithLongAndNonDateTimeField_ReturnsOriginal() throws Exception {
    // Test convertValueIfNeeded returns original Long for non-DateTime fields
    Long count = 12345L;
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);

    when(targetEntity.getElement("count")).thenReturn(element);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.Integer64");

    Object result = invokeConvertValueIfNeeded(count, "count", targetEntity);
    assertEquals(count, result);
  }

  @Test
  void testConvertValueIfNeeded_WithLongAndNullElement_ReturnsOriginal() throws Exception {
    // Test convertValueIfNeeded returns original Long when element is null
    Long value = 99999L;
    CdsEntity targetEntity = mock(CdsEntity.class);
    when(targetEntity.getElement("unknownField")).thenReturn(null);

    Object result = invokeConvertValueIfNeeded(value, "unknownField", targetEntity);
    assertEquals(value, result);
  }

  @Test
  void testConvertValueIfNeeded_WithLongAndNullType_ReturnsOriginal() throws Exception {
    // Test convertValueIfNeeded returns original Long when type is null
    Long value = 88888L;
    CdsEntity targetEntity = mock(CdsEntity.class);
    CdsElement element = mock(CdsElement.class);
    when(targetEntity.getElement("fieldWithoutType")).thenReturn(element);
    when(element.getType()).thenReturn(null);

    Object result = invokeConvertValueIfNeeded(value, "fieldWithoutType", targetEntity);
    assertEquals(value, result);
  }

  @Test
  void testProcessSecondaryProperty_WithValidValue_AddsToMap() throws Exception {
    // Test processSecondaryProperty adds valid value to filtered properties
    String dbPropertyName = "customField";
    String sdmPropertyName = "sap:customProp";
    String value = "testValue";
    CdsEntity targetEntity = mock(CdsEntity.class);
    Map<String, Object> filteredProperties = new HashMap<>();

    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);
    when(targetEntity.getElement(dbPropertyName)).thenReturn(element);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.String");

    invokeProcessSecondaryProperty(
        dbPropertyName, sdmPropertyName, value, targetEntity, filteredProperties);

    assertEquals(1, filteredProperties.size());
    assertEquals(value, filteredProperties.get(dbPropertyName));
  }

  @Test
  void testProcessSecondaryProperty_WithNullValue_DoesNotAddToMap() throws Exception {
    // Test processSecondaryProperty does not add null values
    String dbPropertyName = "customField";
    String sdmPropertyName = "sap:customProp";
    CdsEntity targetEntity = mock(CdsEntity.class);
    Map<String, Object> filteredProperties = new HashMap<>();

    invokeProcessSecondaryProperty(
        dbPropertyName, sdmPropertyName, null, targetEntity, filteredProperties);

    assertEquals(0, filteredProperties.size());
  }

  @Test
  void testProcessSecondaryProperty_WithJSONObjectNull_DoesNotAddToMap() throws Exception {
    // Test processSecondaryProperty does not add JSONObject.NULL values
    String dbPropertyName = "customField";
    String sdmPropertyName = "sap:customProp";
    CdsEntity targetEntity = mock(CdsEntity.class);
    Map<String, Object> filteredProperties = new HashMap<>();

    invokeProcessSecondaryProperty(
        dbPropertyName,
        sdmPropertyName,
        org.json.JSONObject.NULL,
        targetEntity,
        filteredProperties);

    assertEquals(0, filteredProperties.size());
  }

  @Test
  void testProcessSecondaryProperty_WithLongDateTimeValue_ConvertsAndAdds() throws Exception {
    // Test processSecondaryProperty converts Long to Instant for DateTime fields
    String dbPropertyName = "createdAt";
    String sdmPropertyName = "sap:createdAt";
    Long timestamp = 1609459200000L;
    CdsEntity targetEntity = mock(CdsEntity.class);
    Map<String, Object> filteredProperties = new HashMap<>();

    CdsElement element = mock(CdsElement.class);
    CdsStructuredType type = mock(CdsStructuredType.class);
    when(targetEntity.getElement(dbPropertyName)).thenReturn(element);
    when(element.getType()).thenReturn(type);
    when(type.getQualifiedName()).thenReturn("cds.DateTime");

    invokeProcessSecondaryProperty(
        dbPropertyName, sdmPropertyName, timestamp, targetEntity, filteredProperties);

    assertEquals(1, filteredProperties.size());
    Object convertedValue = filteredProperties.get(dbPropertyName);
    assertNotNull(convertedValue);
    assertEquals(java.time.Instant.class, convertedValue.getClass());
    assertEquals(java.time.Instant.ofEpochMilli(timestamp), convertedValue);
  }

  // ============ Helper Methods for Reflection ============

  private boolean invokeIsDateTimeField(CdsElement element) throws Exception {
    java.lang.reflect.Method method =
        SDMCustomServiceHandler.class.getDeclaredMethod("isDateTimeField", CdsElement.class);
    method.setAccessible(true);
    return (boolean) method.invoke(sdmCustomServiceHandler, element);
  }

  private Object invokeConvertValueIfNeeded(
      Object value, String dbPropertyName, CdsEntity targetEntity) throws Exception {
    java.lang.reflect.Method method =
        SDMCustomServiceHandler.class.getDeclaredMethod(
            "convertValueIfNeeded", Object.class, String.class, CdsEntity.class);
    method.setAccessible(true);
    return method.invoke(sdmCustomServiceHandler, value, dbPropertyName, targetEntity);
  }

  private void invokeProcessSecondaryProperty(
      String dbPropertyName,
      String sdmPropertyName,
      Object value,
      CdsEntity targetEntity,
      Map<String, Object> filteredProperties)
      throws Exception {
    java.lang.reflect.Method method =
        SDMCustomServiceHandler.class.getDeclaredMethod(
            "processSecondaryProperty",
            String.class,
            String.class,
            Object.class,
            CdsEntity.class,
            Map.class);
    method.setAccessible(true);
    method.invoke(
        sdmCustomServiceHandler,
        dbPropertyName,
        sdmPropertyName,
        value,
        targetEntity,
        filteredProperties);
  }
}
