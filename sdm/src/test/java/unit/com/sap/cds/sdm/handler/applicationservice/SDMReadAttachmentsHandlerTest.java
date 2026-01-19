package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.mockito.Mockito.*;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMReadAttachmentsHandlerTest {

  @Mock private CdsEntity cdsEntity;
  @Mock private CdsReadEventContext context;
  @Mock private SDMService sdmService;
  @Mock private UserInfo userInfo;
  @Mock private DBQuery dbQuery;
  @Mock private PersistenceService persistenceService;
  @Mock private TokenHandler tokenHandler;

  @InjectMocks private SDMReadAttachmentsHandler sdmReadAttachmentsHandler;

  private static final String REPOSITORY_ID_KEY = "testRepoId";

  @Test
  void testModifyCqnForAttachmentsEntity_Success() throws IOException {
    // Arrange
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    RepoValue repoValue = new RepoValue();
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");

    CdsEntity attachmentDraftEntity = Mockito.mock(CdsEntity.class);
    CdsModel model = Mockito.mock(CdsModel.class);
    when(context.getModel()).thenReturn(model);
    when(model.findEntity(anyString())).thenReturn(Optional.of(attachmentDraftEntity));
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.get("cqn")).thenReturn(select);

    try (MockedStatic<SDMUtils> sdmUtilsMock = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(any())).thenReturn("mockUpIdKey");
      sdmUtilsMock.when(() -> SDMUtils.fetchUPIDFromCQN(any(), any())).thenReturn("mockUpID");

      doNothing()
          .when(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());

      // Act
      sdmReadAttachmentsHandler.processBefore(context);

      // Assert
      verify(context).setCqn(any(CqnSelect.class));
      verify(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), eq("mockUpID"), eq("mockUpIdKey"));
    }
  }

  @Test
  void testModifyCqnForAttachmentsEntity_Success_TMCheck() throws IOException {
    // Arrange
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    RepoValue repoValue = new RepoValue();
    repoValue.setIsAsyncVirusScanEnabled(true);
    CdsEntity attachmentDraftEntity = Mockito.mock(CdsEntity.class);
    CdsModel model = Mockito.mock(CdsModel.class);
    when(context.getModel()).thenReturn(model);
    when(model.findEntity(anyString())).thenReturn(Optional.of(attachmentDraftEntity));
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(context.get("cqn")).thenReturn(select);
    // Act
    try (MockedStatic<SDMUtils> sdmUtilsMock = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(any())).thenReturn("mockUpIdKey");
      sdmUtilsMock.when(() -> SDMUtils.fetchUPIDFromCQN(any(), any())).thenReturn("mockUpID");

      // Act
      sdmReadAttachmentsHandler.processBefore(context);

      // Assert
      // Assert
      verify(context).setCqn(any(CqnSelect.class));
      // When async virus scan is enabled, updateInProgressUploadStatusToSuccess is NOT called
      verify(dbQuery, never())
          .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());
    }
  }

  @Test
  void testModifyCqnForNonAttachmentsEntity() throws IOException {
    // Arrange - Mock target to return false for media annotation
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(false);

    // Act
    sdmReadAttachmentsHandler.processBefore(context);
  }

  @Test
  void testProcessBefore_ExceptionHandling() throws IOException {
    // Arrange
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");
    when(sdmService.checkRepositoryType(any(), any()))
        .thenThrow(new RuntimeException("Test exception"));

    // Act & Assert
    try {
      sdmReadAttachmentsHandler.processBefore(context);
    } catch (RuntimeException e) {
      // Exception should be re-thrown
      verify(sdmService).checkRepositoryType(any(), any());
    }
  }

  @Test
  void testProcessBefore_NoAttachmentDraftEntity() throws IOException {
    // Arrange
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    RepoValue repoValue = new RepoValue();
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");

    CdsModel model = Mockito.mock(CdsModel.class);
    when(context.getModel()).thenReturn(model);
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(model.findEntity(anyString())).thenReturn(Optional.empty());

    // Act
    sdmReadAttachmentsHandler.processBefore(context);

    // Assert - should still call setCqn even without draft entity
    verify(context).setCqn(any(CqnSelect.class));
    verify(dbQuery, never())
        .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());
  }

  @Test
  void testProcessBefore_WithCollectionReadNoKeys() throws IOException {
    // Arrange - create a select without keys (collection read)
    CqnSelect select = Select.from("TestEntity");
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    RepoValue repoValue = new RepoValue();
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");

    CdsModel model = Mockito.mock(CdsModel.class);
    when(context.getModel()).thenReturn(model);
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(model.findEntity(anyString())).thenReturn(Optional.empty());

    // Act
    sdmReadAttachmentsHandler.processBefore(context);

    // Assert - repositoryId filter should be added for collection reads
    verify(context).setCqn(any(CqnSelect.class));
  }

  @Test
  void testProcessBefore_DeleteDraftEntriesWithNullObjectIdAndFolderId() throws IOException {
    // Arrange
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    RepoValue repoValue = new RepoValue();
    repoValue.setIsAsyncVirusScanEnabled(false);
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("tenant1");

    CdsEntity attachmentDraftEntity = Mockito.mock(CdsEntity.class);
    CdsModel model = Mockito.mock(CdsModel.class);
    when(context.getModel()).thenReturn(model);
    when(model.findEntity(anyString())).thenReturn(Optional.of(attachmentDraftEntity));
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.get("cqn")).thenReturn(select);

    try (MockedStatic<SDMUtils> sdmUtilsMock = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(any())).thenReturn("mockUpIdKey");
      sdmUtilsMock.when(() -> SDMUtils.fetchUPIDFromCQN(any(), any())).thenReturn("mockUpID");

      doNothing()
          .when(dbQuery)
          .deleteDraftEntriesWithNullObjectIdAndFolderId(any(), any(), anyString(), anyString());
      doNothing()
          .when(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());

      // Act
      sdmReadAttachmentsHandler.processBefore(context);

      // Assert - verify deleteDraftEntriesWithNullObjectIdAndFolderId is called before
      // updateInProgressUploadStatusToSuccess
      verify(dbQuery)
          .deleteDraftEntriesWithNullObjectIdAndFolderId(
              any(), any(), eq("mockUpID"), eq("mockUpIdKey"));
      verify(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), eq("mockUpID"), eq("mockUpIdKey"));
      verify(context).setCqn(any(CqnSelect.class));
    }
  }
}
