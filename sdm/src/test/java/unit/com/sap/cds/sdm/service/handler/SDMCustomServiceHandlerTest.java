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
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.handler.AttachmentCopyEventContext;
import com.sap.cds.sdm.service.handler.SDMCustomServiceHandler;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SDMCustomServiceHandlerTest {

  @Mock private AttachmentCopyEventContext mockContext;

  @Mock private SDMService sdmService;

  @Mock private TokenHandler tokenHandler;

  @Mock private DraftService draftService;

  private SDMCustomServiceHandler sdmCustomServiceHandler;

  private static final String OBJECT_ID = "mockObjectId";
  private static final String FOLDER_ID = "mockFolderId";
  private static final String UP_ID = "mockUpId";
  private static final String FACET = "mockFacet";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(draftService.getName()).thenReturn(FACET);
    // Pass a non-null list of DraftService mocks
    sdmCustomServiceHandler =
        new SDMCustomServiceHandler(sdmService, List.of(draftService), tokenHandler);
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
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(List.of("fileName", "mimeType", OBJECT_ID));

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    context.setObjectIds(List.of(OBJECT_ID));

    // Act
    sdmCustomServiceHandler.copyAttachments(context);

    // Assert
    verify(sdmService, times(1))
        .copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class));
    verify(draftService, times(1)).newDraft(any());
    verify(context, times(1)).setCompleted();
  }

  @Test
  void testCopyAttachments_InvalidFacetFormat_ThrowsException() throws IOException {
    AttachmentCopyEventContext context = mock(AttachmentCopyEventContext.class);
    when(context.getFacet()).thenReturn("invalid.facet"); // Only 2 parts
    // Other mocks not needed as exception is thrown before they're used

    ServiceException ex =
        assertThrows(
            ServiceException.class,
            () -> {
              sdmCustomServiceHandler.copyAttachments(context);
            });
    assertTrue(ex.getMessage().contains("Invalid facet format"));
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
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(List.of("fileName", "mimeType", OBJECT_ID));

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
        .copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class));
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
    when(sdmService.copyAttachment(any(), any(SDMCredentials.class), any(Boolean.class)))
        .thenReturn(List.of("fileName", "mimeType", OBJECT_ID))
        .thenThrow(new ServiceException("Copy failed"));

    // Mock context
    AttachmentCopyEventContext context = createMockContext();
    context.setObjectIds(List.of(OBJECT_ID, "mockObjectId2"));

    // Act & Assert
    try {
      sdmCustomServiceHandler.copyAttachments(context);
    } catch (ServiceException e) {
      verify(sdmService, times(1)).deleteDocument(any(String.class), any(String.class), any());
    }
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
    when(sdmService.copyAttachment(any(), any(), anyBoolean()))
        .thenThrow(new ServiceException("Copy failed"));

    AttachmentCopyEventContext context = createMockContext();
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));

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
    when(sdmService.copyAttachment(any(), any(), anyBoolean()))
        .thenReturn(List.of("fileName", "mimeType", OBJECT_ID))
        .thenThrow(new ServiceException("Copy failed"));

    AttachmentCopyEventContext context = createMockContext();
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID, "mockObjectId2"));

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

    when(context.getFacet()).thenReturn("prefix.someIdentifier." + FACET);
    when(context.getUpId()).thenReturn(UP_ID);
    when(context.getSystemUser()).thenReturn(true);
    when(context.getObjectIds()).thenReturn(List.of(OBJECT_ID));

    // Mock CdsModel and relevant entities and associations
    CdsModel model = mock(CdsModel.class);
    CdsEntity entity = mock(CdsEntity.class);

    // Setup expected behavior
    when(context.getModel()).thenReturn(model);
    when(model.findEntity(any(String.class))).thenReturn(Optional.of(entity));
    when(entity.findAssociation("up_")).thenReturn(Optional.of(mockAssociationElement));
    when(mockAssociationElement.getType()).thenReturn(mockAssociationType);
    when(mockAssociationType.refs()).thenReturn(Stream.of(mockCqnElementRef));
    when(mockCqnElementRef.path()).thenReturn("ID");

    return context;
  }
}
