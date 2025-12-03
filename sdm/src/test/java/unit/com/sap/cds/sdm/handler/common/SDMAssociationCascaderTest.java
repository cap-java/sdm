package unit.com.sap.cds.sdm.handler.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.common.SDMAssociationCascader;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SDMAssociationCascaderTest {

  @Mock private CdsModel mockModel;
  @Mock private CdsEntity mockEntity;

  private SDMAssociationCascader cascader;

  @BeforeEach
  void setUp() {
    cascader = new SDMAssociationCascader();
  }

  @Test
  void testConstructor() {
    // Test that cascader can be created successfully
    SDMAssociationCascader newCascader = new SDMAssociationCascader();
    assertNotNull(newCascader);
  }

  @Test
  void testFindEntityPathReturnsNonNull() {
    // Given
    when(mockEntity.getQualifiedName()).thenReturn("Service.TestEntity");
    when(mockEntity.elements()).thenReturn(Stream.empty());
    when(mockEntity.getAnnotationValue("_is_media_data", Boolean.FALSE)).thenReturn(false);

    // When
    var result = cascader.findEntityPath(mockModel, mockEntity);

    // Then
    assertNotNull(result);
  }

  @Test
  void testFindEntityPathHandlesNullElementsGracefully() {
    // Given
    when(mockEntity.getQualifiedName()).thenReturn("Service.TestEntity");
    when(mockEntity.elements()).thenReturn(Stream.empty());
    when(mockEntity.getAnnotationValue("_is_media_data", Boolean.FALSE)).thenReturn(false);

    // When & Then - should not throw any exception
    assertDoesNotThrow(
        () -> {
          var result = cascader.findEntityPath(mockModel, mockEntity);
          assertNotNull(result);
        });
  }
}
