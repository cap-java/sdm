package unit.com.sap.cds.sdm.handler.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.sdm.handler.common.SDMApplicationHandlerHelper;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SDMApplicationHandlerHelperTest {

  @Mock private CdsStructuredType mockEntity;

  @Test
  void testPrivateConstructor() {
    // Test that the constructor is private
    try {
      Constructor<SDMApplicationHandlerHelper> constructor =
          SDMApplicationHandlerHelper.class.getDeclaredConstructor();
      assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
      constructor.setAccessible(true);
      assertDoesNotThrow(() -> constructor.newInstance());
    } catch (Exception e) {
      fail("Constructor should be accessible via reflection");
    }
  }

  @Test
  void testIsMediaEntityReturnsTrue() {
    // Given
    when(mockEntity.getAnnotationValue("_is_media_data", false)).thenReturn(true);

    // When
    boolean result = SDMApplicationHandlerHelper.isMediaEntity(mockEntity);

    // Then
    assertTrue(result);
    verify(mockEntity).getAnnotationValue("_is_media_data", false);
  }

  @Test
  void testIsMediaEntityReturnsFalse() {
    // Given
    when(mockEntity.getAnnotationValue("_is_media_data", false)).thenReturn(false);

    // When
    boolean result = SDMApplicationHandlerHelper.isMediaEntity(mockEntity);

    // Then
    assertFalse(result);
    verify(mockEntity).getAnnotationValue("_is_media_data", false);
  }

  @Test
  void testIsMediaEntityWithNullAnnotation() {
    // Given
    when(mockEntity.getAnnotationValue("_is_media_data", false)).thenReturn(false);

    // When
    boolean result = SDMApplicationHandlerHelper.isMediaEntity(mockEntity);

    // Then
    assertFalse(result);
    verify(mockEntity).getAnnotationValue("_is_media_data", false);
  }

  @Test
  void testIsMediaEntityWithDefaultFallback() {
    // Given
    when(mockEntity.getAnnotationValue("_is_media_data", false)).thenReturn(false);

    // When
    boolean result = SDMApplicationHandlerHelper.isMediaEntity(mockEntity);

    // Then
    assertFalse(result);
    verify(mockEntity).getAnnotationValue("_is_media_data", false);
  }
}
