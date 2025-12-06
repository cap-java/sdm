package unit.com.sap.cds.sdm.service.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.service.exceptions.InsufficientDataException;
import org.junit.jupiter.api.Test;

class InsufficientDataExceptionTest {

  @Test
  void testInsufficientDataExceptionWithMessage() {
    // Given
    String expectedMessage = "Insufficient data provided for operation";

    // When
    InsufficientDataException exception = new InsufficientDataException(expectedMessage);

    // Then
    assertNotNull(exception);
    assertEquals(expectedMessage, exception.getMessage());
    assertTrue(exception instanceof java.io.IOException);
  }

  @Test
  void testInsufficientDataExceptionWithNullMessage() {
    // When
    InsufficientDataException exception = new InsufficientDataException(null);

    // Then
    assertNotNull(exception);
    assertNull(exception.getMessage());
  }

  @Test
  void testInsufficientDataExceptionWithEmptyMessage() {
    // Given
    String emptyMessage = "";

    // When
    InsufficientDataException exception = new InsufficientDataException(emptyMessage);

    // Then
    assertNotNull(exception);
    assertEquals(emptyMessage, exception.getMessage());
  }

  @Test
  void testExceptionCanBeThrown() {
    // Given
    String message = "Test exception throwing";

    // When & Then
    assertThrows(
        InsufficientDataException.class,
        () -> {
          throw new InsufficientDataException(message);
        });
  }

  @Test
  void testExceptionInheritanceChain() {
    // Given
    InsufficientDataException exception = new InsufficientDataException("Test");

    // Then
    assertTrue(exception instanceof java.io.IOException);
    assertTrue(exception instanceof Exception);
    assertTrue(exception instanceof Throwable);
  }
}
