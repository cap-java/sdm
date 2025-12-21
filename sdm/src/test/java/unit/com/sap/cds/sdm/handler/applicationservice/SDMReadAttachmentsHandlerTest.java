package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.mockito.Mockito.*;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
import java.util.Optional;
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
    // Arrange
    CqnSelect select =
        Select.from("SomeEntity").where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));

    // Mock target
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false))
        .thenReturn(false);
    when(context.getCqn()).thenReturn(select);

    // Act
    sdmReadAttachmentsHandler.processBefore(context);

    // Assert — since it enters the 'else' clause, it should call setCqn with original select
    verify(context).setCqn(select);
  }
}
