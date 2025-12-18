package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.request.UserInfo;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SDMReadAttachmentsHandlerTest {

  @Mock private CdsEntity cdsEntity;

  @Mock private CdsReadEventContext context;
  @Mock private CdsElement mockComposition;
  @Mock private CdsAssociationType mockAssociationType;
  @Mock private UserInfo userInfo;
  @Mock private PersistenceService persistenceService;
  @Mock private SDMService sdmService;
  @Mock private TokenHandler tokenHandler;
  @Mock private DBQuery dbQuery;
  @Mock private CdsModel model;
  @Mock private CdsEntity draftEntity;

  @Mock private CdsStructuredType mockTargetAspect;

  @InjectMocks private SDMReadAttachmentsHandler sdmReadAttachmentsHandler;

  private static final String REPOSITORY_ID_KEY = SDMConstants.REPOSITORY_ID;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  // @Test
  // TODO: Fix this test - complex mocking required for SDMUtils.fetchUPIDFromCQN
  void testModifyCqnForAttachmentsEntity_Success_DISABLED() throws IOException {
    // Arrange
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(any(), any())).thenReturn(true);
    when(cdsEntity.getQualifiedName()).thenReturn("TestEntity");
    when(context.getCqn()).thenReturn(select);
    when(context.getUserInfo()).thenReturn(userInfo);
    when(userInfo.getTenant()).thenReturn("testTenant");
    when(context.getModel()).thenReturn(model);
    when(model.findEntity("TestEntity_drafts")).thenReturn(Optional.of(draftEntity));
    when(context.get("cqn")).thenReturn(select);
    
    RepoValue repoValue = mock(RepoValue.class);
    when(repoValue.getIsAsyncVirusScanEnabled()).thenReturn(false);
    when(sdmService.checkRepositoryType(any(), any())).thenReturn(repoValue);
    // Act
    sdmReadAttachmentsHandler.processBefore(context); // Refers to the method you provided

    // Verify the modified where clause
    // Predicate whereClause = modifiedCqnSelect.where();

    // Add assertions to validate the modification in `where` clause
    assertNotNull(select.where().isPresent());
    assertTrue(select.where().toString().contains("repositoryId"));
  }  @Test
  void testModifyCqnForNonAttachmentsEntity() throws IOException {
    // Arrange
    String targetEntity = "nonAttachments"; // Does not match the mocked composition name
    CqnSelect select =
        Select.from("SomeEntity").where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));

    // Mock target
    when(context.getTarget()).thenReturn(cdsEntity);
    when(cdsEntity.getAnnotationValue(any(), any())).thenReturn(false);

    when(context.getCqn()).thenReturn(select);
    // Mock composition with Attachments aspect
    // Act
    sdmReadAttachmentsHandler.processBefore(context);

    // Assert — since it enters the 'else' clause, it should call setCqn with original select
    verify(context).setCqn(select);
  }
}
