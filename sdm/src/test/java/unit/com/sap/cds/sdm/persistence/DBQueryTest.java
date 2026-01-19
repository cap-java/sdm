package unit.com.sap.cds.sdm.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DBQueryTest {

  @Mock private CdsEntity mockDraftEntity;
  @Mock private CdsEntity mockActiveEntity;
  @Mock private PersistenceService mockPersistenceService;
  @Mock private Result mockResult;
  @Mock private Row mockRow;

  private DBQuery dbQuery;

  @BeforeEach
  void setUp() {
    dbQuery = DBQuery.getDBQueryInstance();
  }

  @Test
  void testDeleteDraftEntriesWithNullObjectIdAndFolderId_Success() {
    // Arrange
    String upID = "testUpID";
    String upIdKey = "up__ID";
    when(mockResult.rowCount()).thenReturn(2L);
    when(mockPersistenceService.run(any(CqnDelete.class))).thenReturn(mockResult);

    // Act
    dbQuery.deleteDraftEntriesWithNullObjectIdAndFolderId(
        mockDraftEntity, mockPersistenceService, upID, upIdKey);

    // Assert
    verify(mockPersistenceService).run(any(CqnDelete.class));
  }

  @Test
  void testDeleteDraftEntriesWithNullObjectIdAndFolderId_NoRecordsDeleted() {
    // Arrange
    String upID = "testUpID";
    String upIdKey = "up__ID";
    when(mockResult.rowCount()).thenReturn(0L);
    when(mockPersistenceService.run(any(CqnDelete.class))).thenReturn(mockResult);

    // Act
    dbQuery.deleteDraftEntriesWithNullObjectIdAndFolderId(
        mockDraftEntity, mockPersistenceService, upID, upIdKey);

    // Assert
    verify(mockPersistenceService).run(any(CqnDelete.class));
    verify(mockResult).rowCount();
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
  void testUpdateUploadStatusByScanStatus_AllScanStatuses() {
    // Test all scan status mappings
    String objectId = "object-123";
    Result mockResult = mock(Result.class);
    when(mockResult.rowCount()).thenReturn(1L);
    when(mockPersistenceService.run(any(CqnUpdate.class))).thenReturn(mockResult);

    // Test QUARANTINED -> UPLOAD_STATUS_VIRUS_DETECTED
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity,
        null,
        mockPersistenceService,
        objectId,
        SDMConstants.ScanStatus.QUARANTINED);

    // Test PENDING -> UPLOAD_STATUS_IN_PROGRESS
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity, null, mockPersistenceService, objectId, SDMConstants.ScanStatus.PENDING);

    // Test SCANNING -> VIRUS_SCAN_INPROGRESS
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity, null, mockPersistenceService, objectId, SDMConstants.ScanStatus.SCANNING);

    // Test FAILED -> UPLOAD_STATUS_SCAN_FAILED
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity, null, mockPersistenceService, objectId, SDMConstants.ScanStatus.FAILED);

    // Test CLEAN -> UPLOAD_STATUS_SUCCESS
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity, null, mockPersistenceService, objectId, SDMConstants.ScanStatus.CLEAN);

    // Test BLANK -> UPLOAD_STATUS_SUCCESS
    dbQuery.updateUploadStatusByScanStatus(
        mockDraftEntity, null, mockPersistenceService, objectId, SDMConstants.ScanStatus.BLANK);

    // Verify all updates were called
    verify(mockPersistenceService, times(6)).run(any(CqnUpdate.class));
  }
}
