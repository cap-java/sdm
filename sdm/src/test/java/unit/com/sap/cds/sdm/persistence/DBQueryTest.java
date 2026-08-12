package unit.com.sap.cds.sdm.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DBQueryTest {

  @Mock private CdsEntity mockDraftEntity;
  @Mock private CdsEntity mockActiveEntity;
  @Mock private CdsElement mockCdsElement;
  @Mock private CdsModel mockCdsModel;
  @Mock private AttachmentMarkAsDeletedEventContext mockDeleteContext;
  @Mock private PersistenceService mockPersistenceService;
  @Mock private Result mockResult;
  @Mock private Row mockRow;

  private DBQuery dbQuery;

  @BeforeEach
  void setUp() {
    dbQuery = DBQuery.getDBQueryInstance();
  }

  @Test
  void testGetAttachmentsWithVirusScanInProgress_BothTables() {
    // Arrange
    String upID = "testUpID";
    String upIDkey = "up__ID";

    // Mock draft table result
    Row draftRow = mock(Row.class);
    when(draftRow.get("ID")).thenReturn("draft-id-1");
    when(draftRow.get("objectId")).thenReturn("object-1");
    when(draftRow.get("fileName")).thenReturn("draft-file.pdf");
    when(draftRow.get("folderId")).thenReturn("folder-1");
    when(draftRow.get("repositoryId")).thenReturn("repo-1");
    when(draftRow.get("mimeType")).thenReturn("application/pdf");
    when(draftRow.get("uploadStatus")).thenReturn(SDMConstants.VIRUS_SCAN_INPROGRESS);

    Result draftResult = mock(Result.class);
    when(draftResult.list()).thenReturn(List.of(draftRow));

    // Mock active table result
    Row activeRow = mock(Row.class);
    when(activeRow.get("ID")).thenReturn("active-id-1");
    when(activeRow.get("objectId")).thenReturn("object-2");
    when(activeRow.get("fileName")).thenReturn("active-file.pdf");
    when(activeRow.get("folderId")).thenReturn("folder-2");
    when(activeRow.get("repositoryId")).thenReturn("repo-1");
    when(activeRow.get("mimeType")).thenReturn("application/pdf");
    when(activeRow.get("uploadStatus")).thenReturn(SDMConstants.VIRUS_SCAN_INPROGRESS);

    Result activeResult = mock(Result.class);
    when(activeResult.list()).thenReturn(List.of(activeRow));

    when(mockPersistenceService.run(any(CqnSelect.class)))
        .thenReturn(draftResult)
        .thenReturn(activeResult);

    // Act
    List<CmisDocument> result =
        dbQuery.getAttachmentsWithVirusScanInProgress(
            mockDraftEntity, mockActiveEntity, mockPersistenceService, upID, upIDkey);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("draft-id-1", result.get(0).getAttachmentId());
    assertEquals("object-1", result.get(0).getObjectId());
    assertEquals("draft-file.pdf", result.get(0).getFileName());
    assertEquals("active-id-1", result.get(1).getAttachmentId());
    assertEquals("object-2", result.get(1).getObjectId());
    assertEquals("active-file.pdf", result.get(1).getFileName());
    verify(mockPersistenceService, times(2)).run(any(CqnSelect.class));
  }

  @Test
  void testGetAttachmentsWithVirusScanInProgress_DraftTableOnly() {
    // Arrange
    String upID = "testUpID";
    String upIDkey = "up__ID";

    Row draftRow = mock(Row.class);
    when(draftRow.get("ID")).thenReturn("draft-id-1");
    when(draftRow.get("objectId")).thenReturn("object-1");
    when(draftRow.get("fileName")).thenReturn("draft-file.pdf");
    when(draftRow.get("folderId")).thenReturn("folder-1");
    when(draftRow.get("repositoryId")).thenReturn("repo-1");
    when(draftRow.get("mimeType")).thenReturn("application/pdf");
    when(draftRow.get("uploadStatus")).thenReturn(SDMConstants.VIRUS_SCAN_INPROGRESS);

    Result draftResult = mock(Result.class);
    when(draftResult.list()).thenReturn(List.of(draftRow));
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(draftResult);

    // Act
    List<CmisDocument> result =
        dbQuery.getAttachmentsWithVirusScanInProgress(
            mockDraftEntity, null, mockPersistenceService, upID, upIDkey);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("draft-id-1", result.get(0).getAttachmentId());
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
  }

  @Test
  void testGetAttachmentsWithVirusScanInProgress_ActiveTableOnly() {
    // Arrange
    String upID = "testUpID";
    String upIDkey = "up__ID";

    Row activeRow = mock(Row.class);
    when(activeRow.get("ID")).thenReturn("active-id-1");
    when(activeRow.get("objectId")).thenReturn("object-1");
    when(activeRow.get("fileName")).thenReturn("active-file.pdf");
    when(activeRow.get("folderId")).thenReturn("folder-1");
    when(activeRow.get("repositoryId")).thenReturn("repo-1");
    when(activeRow.get("mimeType")).thenReturn("application/pdf");
    when(activeRow.get("uploadStatus")).thenReturn(SDMConstants.VIRUS_SCAN_INPROGRESS);

    Result activeResult = mock(Result.class);
    when(activeResult.list()).thenReturn(List.of(activeRow));
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(activeResult);

    // Act
    List<CmisDocument> result =
        dbQuery.getAttachmentsWithVirusScanInProgress(
            null, mockActiveEntity, mockPersistenceService, upID, upIDkey);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("active-id-1", result.get(0).getAttachmentId());
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
  }

  @Test
  void testGetAttachmentsWithVirusScanInProgress_NullEntities() {
    // Arrange
    String upID = "testUpID";
    String upIDkey = "up__ID";

    // Act
    List<CmisDocument> result =
        dbQuery.getAttachmentsWithVirusScanInProgress(
            null, null, mockPersistenceService, upID, upIDkey);

    // Assert
    assertNotNull(result);
    assertEquals(0, result.size());
    verify(mockPersistenceService, never()).run(any(CqnSelect.class));
  }

  @Test
  void testGetAttachmentsWithVirusScanInProgress_WithNullFields() {
    // Arrange
    String upID = "testUpID";
    String upIDkey = "up__ID";

    Row draftRow = mock(Row.class);
    when(draftRow.get("ID")).thenReturn(null);
    when(draftRow.get("objectId")).thenReturn(null);
    when(draftRow.get("fileName")).thenReturn(null);
    when(draftRow.get("folderId")).thenReturn(null);
    when(draftRow.get("repositoryId")).thenReturn(null);
    when(draftRow.get("mimeType")).thenReturn(null);
    when(draftRow.get("uploadStatus")).thenReturn(null);

    Result draftResult = mock(Result.class);
    when(draftResult.list()).thenReturn(List.of(draftRow));
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(draftResult);

    // Act
    List<CmisDocument> result =
        dbQuery.getAttachmentsWithVirusScanInProgress(
            mockDraftEntity, null, mockPersistenceService, upID, upIDkey);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertNull(result.get(0).getAttachmentId());
    assertNull(result.get(0).getObjectId());
    assertNull(result.get(0).getFileName());
    assertEquals(SDMConstants.UPLOAD_STATUS_IN_PROGRESS, result.get(0).getUploadStatus());
  }

  @Test
  void testUpdateUploadStatusByScanStatus_BothTables() {
    // Arrange
    String objectId = "object-123";
    SDMConstants.ScanStatus scanStatus = SDMConstants.ScanStatus.CLEAN;

    Result draftResult = mock(Result.class);
    Result activeResult = mock(Result.class);
    when(draftResult.rowCount()).thenReturn(1L);
    when(activeResult.rowCount()).thenReturn(1L);

    when(mockPersistenceService.run(any(CqnUpdate.class)))
        .thenReturn(draftResult)
        .thenReturn(activeResult);

    // Act
    Result result =
        dbQuery.updateUploadStatusByScanStatus(
            mockDraftEntity, mockActiveEntity, mockPersistenceService, objectId, scanStatus);

    // Assert
    assertNotNull(result);
    verify(mockPersistenceService, times(2)).run(any(CqnUpdate.class));
  }

  @Test
  void testUpdateUploadStatusByScanStatus_DraftTableOnly() {
    // Arrange
    String objectId = "object-123";
    SDMConstants.ScanStatus scanStatus = SDMConstants.ScanStatus.QUARANTINED;

    Result draftResult = mock(Result.class);
    when(draftResult.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnUpdate.class))).thenReturn(draftResult);

    // Act
    Result result =
        dbQuery.updateUploadStatusByScanStatus(
            mockDraftEntity, null, mockPersistenceService, objectId, scanStatus);

    // Assert
    assertNotNull(result);
    verify(mockPersistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void testUpdateUploadStatusByScanStatus_ActiveTableOnly() {
    // Arrange
    String objectId = "object-123";
    SDMConstants.ScanStatus scanStatus = SDMConstants.ScanStatus.SCANNING;

    Result activeResult = mock(Result.class);
    when(activeResult.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnUpdate.class))).thenReturn(activeResult);

    // Act
    Result result =
        dbQuery.updateUploadStatusByScanStatus(
            null, mockActiveEntity, mockPersistenceService, objectId, scanStatus);

    // Assert
    assertNotNull(result);
    verify(mockPersistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void testUpdateUploadStatusByScanStatus_NullEntities() {
    // Arrange
    String objectId = "object-123";
    SDMConstants.ScanStatus scanStatus = SDMConstants.ScanStatus.CLEAN;

    // Act
    Result result =
        dbQuery.updateUploadStatusByScanStatus(
            null, null, mockPersistenceService, objectId, scanStatus);

    // Assert
    assertNull(result);
    verify(mockPersistenceService, never()).run(any(CqnUpdate.class));
  }

  @Test
  void testUpdateUploadStatusByScanStatus_NoRecordsUpdated() {
    // Arrange
    String objectId = "object-123";
    SDMConstants.ScanStatus scanStatus = SDMConstants.ScanStatus.CLEAN;

    Result draftResult = mock(Result.class);
    when(draftResult.rowCount()).thenReturn(0L);
    when(mockPersistenceService.run(any(CqnUpdate.class))).thenReturn(draftResult);

    // Act
    Result result =
        dbQuery.updateUploadStatusByScanStatus(
            mockDraftEntity, null, mockPersistenceService, objectId, scanStatus);

    // Assert
    assertNotNull(result);
    verify(mockPersistenceService, times(1)).run(any(CqnUpdate.class));
  }

  @Test
  void testGetAttachmentsForUPID_WithIsActiveEntity() {
    String upID = "testUpID";
    String upIdKey = "up__ID";

    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.of(mockCdsElement));

    Result result = mock(Result.class);
    when(result.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(result);

    Result actual =
        dbQuery.getAttachmentsForUPID(mockDraftEntity, mockPersistenceService, upID, upIdKey);

    assertNotNull(actual);
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForUPID_WithoutIsActiveEntity() {
    String upID = "testUpID";
    String upIdKey = "up__ID";

    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.empty());

    Result result = mock(Result.class);
    when(result.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(result);

    Result actual =
        dbQuery.getAttachmentsForUPID(mockDraftEntity, mockPersistenceService, upID, upIdKey);

    assertNotNull(actual);
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForUPIDAndRepository_WithIsActiveEntity() {
    String upID = "testUpID";
    String upIdKey = "up__ID";

    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.of(mockCdsElement));

    Result result = mock(Result.class);
    when(result.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(result);

    Result actual =
        dbQuery.getAttachmentsForUPIDAndRepository(
            mockDraftEntity, mockPersistenceService, upID, upIdKey);

    assertNotNull(actual);
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForUPIDAndRepository_WithoutIsActiveEntity() {
    String upID = "testUpID";
    String upIdKey = "up__ID";

    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.empty());

    Result result = mock(Result.class);
    when(result.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(result);

    Result actual =
        dbQuery.getAttachmentsForUPIDAndRepository(
            mockDraftEntity, mockPersistenceService, upID, upIdKey);

    assertNotNull(actual);
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForFolder_DraftEntity_WithIsActiveEntity() {
    String entity = "TestEntity";
    String folderId = "folder-1";

    when(mockDeleteContext.getModel()).thenReturn(mockCdsModel);
    when(mockCdsModel.findEntity(entity + "_drafts")).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.of(mockCdsElement));

    Row row = mock(Row.class);
    when(row.get("folderId")).thenReturn("folder-1");
    when(row.get("repositoryId")).thenReturn("repo-1");
    when(row.get("fileName")).thenReturn("file.pdf");
    when(row.get("ID")).thenReturn("id-1");
    when(row.get("objectId")).thenReturn("obj-1");
    when(row.get("uploadStatus")).thenReturn("Clean");

    Result draftResult = mock(Result.class);
    when(draftResult.list()).thenReturn(List.of(row));
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(draftResult);

    List<CmisDocument> docs =
        dbQuery.getAttachmentsForFolder(
            entity, mockPersistenceService, folderId, mockDeleteContext);

    assertNotNull(docs);
    assertEquals(1, docs.size());
    assertEquals("file.pdf", docs.get(0).getFileName());
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForFolder_DraftEntity_WithoutIsActiveEntity() {
    String entity = "TestEntity";
    String folderId = "folder-1";

    when(mockDeleteContext.getModel()).thenReturn(mockCdsModel);
    when(mockCdsModel.findEntity(entity + "_drafts")).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.empty());

    Row row = mock(Row.class);
    when(row.get("folderId")).thenReturn("folder-1");
    when(row.get("repositoryId")).thenReturn("repo-1");
    when(row.get("fileName")).thenReturn("file.pdf");
    when(row.get("ID")).thenReturn("id-1");
    when(row.get("objectId")).thenReturn("obj-1");
    when(row.get("uploadStatus")).thenReturn("Clean");

    Result draftResult = mock(Result.class);
    when(draftResult.list()).thenReturn(List.of(row));
    when(mockPersistenceService.run(any(CqnSelect.class))).thenReturn(draftResult);

    List<CmisDocument> docs =
        dbQuery.getAttachmentsForFolder(
            entity, mockPersistenceService, folderId, mockDeleteContext);

    assertNotNull(docs);
    assertEquals(1, docs.size());
    assertEquals("file.pdf", docs.get(0).getFileName());
    verify(mockPersistenceService, times(1)).run(any(CqnSelect.class));
    verify(mockDraftEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForFolder_FallsBackToActiveEntity_WithIsActiveEntity() {
    String entity = "TestEntity";
    String folderId = "folder-1";

    when(mockDeleteContext.getModel()).thenReturn(mockCdsModel);
    when(mockCdsModel.findEntity(entity + "_drafts")).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.of(mockCdsElement));

    Result emptyDraftResult = mock(Result.class);
    when(emptyDraftResult.list()).thenReturn(List.of());

    when(mockCdsModel.findEntity(entity)).thenReturn(Optional.of(mockActiveEntity));
    when(mockActiveEntity.findElement("IsActiveEntity")).thenReturn(Optional.of(mockCdsElement));

    Row row = mock(Row.class);
    when(row.get("folderId")).thenReturn("folder-1");
    when(row.get("repositoryId")).thenReturn("repo-1");
    when(row.get("fileName")).thenReturn("active-file.pdf");
    when(row.get("ID")).thenReturn("id-2");
    when(row.get("objectId")).thenReturn("obj-2");
    when(row.get("uploadStatus")).thenReturn("Clean");

    Result activeResult = mock(Result.class);
    when(activeResult.list()).thenReturn(List.of(row));

    when(mockPersistenceService.run(any(CqnSelect.class)))
        .thenReturn(emptyDraftResult)
        .thenReturn(activeResult);

    List<CmisDocument> docs =
        dbQuery.getAttachmentsForFolder(
            entity, mockPersistenceService, folderId, mockDeleteContext);

    assertNotNull(docs);
    assertEquals(1, docs.size());
    assertEquals("active-file.pdf", docs.get(0).getFileName());
    verify(mockPersistenceService, times(2)).run(any(CqnSelect.class));
    verify(mockActiveEntity).findElement("IsActiveEntity");
  }

  @Test
  void testGetAttachmentsForFolder_FallsBackToActiveEntity_WithoutIsActiveEntity() {
    String entity = "TestEntity";
    String folderId = "folder-1";

    when(mockDeleteContext.getModel()).thenReturn(mockCdsModel);
    when(mockCdsModel.findEntity(entity + "_drafts")).thenReturn(Optional.of(mockDraftEntity));
    when(mockDraftEntity.findElement("IsActiveEntity")).thenReturn(Optional.empty());

    Result emptyDraftResult = mock(Result.class);
    when(emptyDraftResult.list()).thenReturn(List.of());

    when(mockCdsModel.findEntity(entity)).thenReturn(Optional.of(mockActiveEntity));
    when(mockActiveEntity.findElement("IsActiveEntity")).thenReturn(Optional.empty());

    Row row = mock(Row.class);
    when(row.get("folderId")).thenReturn("folder-1");
    when(row.get("repositoryId")).thenReturn("repo-1");
    when(row.get("fileName")).thenReturn("active-file.pdf");
    when(row.get("ID")).thenReturn("id-2");
    when(row.get("objectId")).thenReturn("obj-2");
    when(row.get("uploadStatus")).thenReturn("Clean");

    Result activeResult = mock(Result.class);
    when(activeResult.list()).thenReturn(List.of(row));

    when(mockPersistenceService.run(any(CqnSelect.class)))
        .thenReturn(emptyDraftResult)
        .thenReturn(activeResult);

    List<CmisDocument> docs =
        dbQuery.getAttachmentsForFolder(
            entity, mockPersistenceService, folderId, mockDeleteContext);

    assertNotNull(docs);
    assertEquals(1, docs.size());
    assertEquals("active-file.pdf", docs.get(0).getFileName());
    verify(mockPersistenceService, times(2)).run(any(CqnSelect.class));
    verify(mockActiveEntity).findElement("IsActiveEntity");
  }
}
