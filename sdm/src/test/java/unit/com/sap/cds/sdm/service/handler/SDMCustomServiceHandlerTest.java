package unit.com.sap.cds.sdm.service.handler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
    when(dbQuery.getAttachmentForObjectID(any(), any(), any())).thenReturn(cmisDocument);

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
}
