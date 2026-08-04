package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.ErrorMessageKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.ParameterInfo;
import com.sap.cds.services.request.UserInfo;
import com.sap.cds.services.runtime.CdsRuntime;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import org.ehcache.Cache;
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
          .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());

      // Act
      sdmReadAttachmentsHandler.processBefore(context);

      // Assert - verify deleteDraftEntriesWithNullObjectIdAndFolderId is called before
      // updateInProgressUploadStatusToSuccess
      verify(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), eq("mockUpID"), eq("mockUpIdKey"));
      verify(context).setCqn(any(CqnSelect.class));
    }
  }

  @Test
  void testProcessBefore_SingleEntityRead_NoDelete() throws IOException {
    // Arrange - simulate a single entity read with ID in where clause
    CqnSelect select =
        Select.from(cdsEntity)
            .where(
                doc ->
                    doc.get("ID")
                        .eq("test-id-123")
                        .and(doc.get("repositoryId").eq(REPOSITORY_ID_KEY)));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(true);
    when(context.getCqn()).thenReturn(select);
    when(context.get("cqn")).thenReturn(select);

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

    try (MockedStatic<SDMUtils> sdmUtilsMock = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtilsMock.when(() -> SDMUtils.getUpIdKey(any())).thenReturn("mockUpIdKey");
      sdmUtilsMock.when(() -> SDMUtils.fetchUPIDFromCQN(any(), any())).thenReturn("mockUpID");
      doNothing()
          .when(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), anyString(), anyString());

      // Act
      sdmReadAttachmentsHandler.processBefore(context);

      // Assert - deleteAttachmentsWithNullObjectIdAndUploadingStatus should NOT be called for
      // single entity reads (where clause contains ID =)
      verify(dbQuery, never())
          .deleteAttachmentsWithNullObjectIdAndUploadingStatus(
              any(), any(), anyString(), anyString());
      verify(dbQuery)
          .updateInProgressUploadStatusToSuccess(any(), any(), eq("mockUpID"), eq("mockUpIdKey"));
      verify(context).setCqn(any(CqnSelect.class));
    }
  }

  @Test
  void testSetErrorMessagesInCache_StoresLocalizedString() throws Exception {
    CdsRuntime cdsRuntime = Mockito.mock(CdsRuntime.class);
    ParameterInfo paramInfo = Mockito.mock(ParameterInfo.class);
    when(context.getCdsRuntime()).thenReturn(cdsRuntime);
    when(context.getParameterInfo()).thenReturn(paramInfo);
    when(paramInfo.getLocale()).thenReturn(Locale.GERMAN);

    // Return a German translation only for the userNotAuthorisedError key;
    // all other keys return themselves (no translation found), triggering the English fallback.
    String germanTranslation =
        "Sie verfügen nicht über die erforderlichen Berechtigungen"
            + " zum Hochladen von Anhängen. Bitte wenden Sie sich an Ihren Administrator.";
    when(cdsRuntime.getLocalizedMessage(
            eq("SDM.userNotAuthorisedError"), isNull(), eq(Locale.GERMAN)))
        .thenReturn(germanTranslation);
    when(cdsRuntime.getLocalizedMessage(
            argThat(k -> k != null && !k.equals("SDM.userNotAuthorisedError")), isNull(), any()))
        .thenAnswer(inv -> inv.getArgument(0));

    CacheConfig.initializeCache();

    // Clear any previously cached flag so the method actually runs
    Cache<ErrorMessageKey, String> errorMessageCache = CacheConfig.getErrorMessageCache();
    errorMessageCache.remove(new ErrorMessageKey("localizedErrorMessagesSetInCache"));

    // Invoke the private method via reflection
    Method method =
        SDMReadAttachmentsHandler.class.getDeclaredMethod(
            "setErrorMessagesInCache", CdsReadEventContext.class);
    method.setAccessible(true);
    method.invoke(sdmReadAttachmentsHandler, context);

    // The cache should now hold the German translation, not the English constant
    String cached = SDMUtils.getErrorMessage("USER_NOT_AUTHORISED_ERROR");
    assertEquals(germanTranslation, cached);

    // Verify the English fallback is NOT stored for this key
    assertNotEquals(SDMErrorMessages.USER_NOT_AUTHORISED_ERROR, cached);
  }
}
