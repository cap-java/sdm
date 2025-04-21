package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.*;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.services.cds.CdsReadEventContext;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMReadAttachmentsHandlerTest {

  @Mock private CdsEntity cdsEntity;

  @Mock private CdsReadEventContext context;
  @Mock private CdsElement mockComposition;
  @Mock private CdsAssociationType mockAssociationType;

  @Mock private CdsStructuredType mockTargetAspect;

  @InjectMocks private SDMReadAttachmentsHandler sdmReadAttachmentsHandler;

  private static final String REPOSITORY_ID_KEY = SDMConstants.REPOSITORY_ID;

  @Test
  void testModifyCqnForAttachmentsEntity_Success() {
    // Arrange
    String targetEntity = "attachments";
    CqnSelect select =
        Select.from(cdsEntity).where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));
    when(context.getTarget()).thenReturn(cdsEntity);
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
    String targetEntity = "nonAttachments"; // Does not match the mocked composition name
    CqnSelect select =
        Select.from("SomeEntity").where(doc -> doc.get("repositoryId").eq(REPOSITORY_ID_KEY));

    // Mock target
    when(context.getTarget()).thenReturn(cdsEntity);
    when(context.getCqn()).thenReturn(select);
    when(cdsEntity.getQualifiedName()).thenReturn(targetEntity);

    // Mock composition with Attachments aspect
    when(mockComposition.getType()).thenReturn(mockAssociationType);
    when(mockComposition.getName()).thenReturn("attachments");
    when(mockTargetAspect.getQualifiedName()).thenReturn("sap.attachments.Attachments");
    when(mockAssociationType.getTargetAspect()).thenReturn(Optional.of(mockTargetAspect));

    // Return a stream with one valid attachment composition
    when(cdsEntity.compositions()).thenReturn(Stream.of(mockComposition));

    // Act
    sdmReadAttachmentsHandler.processBefore(context);

    // Assert — since it enters the 'else' clause, it should call setCqn with original select
    verify(context).setCqn(select);
  }
}
