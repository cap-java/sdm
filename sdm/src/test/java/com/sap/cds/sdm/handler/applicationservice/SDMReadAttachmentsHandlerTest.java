package com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.services.cds.CdsReadEventContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMReadAttachmentsHandlerTest {

  @Mock private CdsEntity cdsEntity;

  @Mock private CdsReadEventContext context;

  @Mock private CdsModel model;

  @Mock private CqnSelect cqnSelect;

  @InjectMocks private SDMReadAttachmentsHandler sdmReadAttachmentsHandler;

  private static final String REPOSITORY_ID_KEY = SDMConstants.REPOSITORY_ID;

  @Test
  void testModifyCqnForAttachmentsEntity_Success() {
    // Arrange
    String targetEntity = "attachments";
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(context.getCqn()).thenReturn(select);
    when(context.getModel()).thenReturn(model);
    when(cdsEntity.getQualifiedName()).thenReturn(targetEntity);
    when(model.findEntity(targetEntity)).thenReturn(Optional.of(cdsEntity));

    // Act
    sdmReadAttachmentsHandler.processBefore(context); // Refers to the method you provided

    // Verify the modified where clause
    // Predicate whereClause = modifiedCqnSelect.where();

    // Add assertions to validate the modification in `where` clause
    assertNotNull(select.where().isPresent());
    assertTrue(select.where().toString().contains("repositoryId"));
  }

  @Test
  void testModifyCqnForNonAttachmentsEntity() {
    // Arrange
    String targetEntity = "nonAttachments";
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
    when(context.getCqn()).thenReturn(select);
    when(cdsEntity.getQualifiedName()).thenReturn(targetEntity);
    when(context.getTarget().getQualifiedName()).thenReturn(targetEntity);

    // Act
    sdmReadAttachmentsHandler.processBefore(context); // Refers to the method you provided

    // Assert
    verify(context)
        .setCqn(select); // Ensure that the original CqnSelect is set without modification
  }
}
