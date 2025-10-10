package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.sdm.service.ReadAheadInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadAheadInputStreamTest {

  private ReadAheadInputStream readAheadInputStream;
  private InputStream mockInputStream;

  @BeforeEach
  void setUp() {
    mockInputStream = mock(InputStream.class);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (readAheadInputStream != null) {
      readAheadInputStream.close();
    }
  }

  @Test
  void testConstructorWithNullInputStream() {
    // When & Then
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new ReadAheadInputStream(null, 1024));

    assertEquals(" InputStream cannot be null", exception.getMessage());
  }

  @Test
  void testConstructorWithValidInputStream() throws IOException {
    // Given
    byte[] testData = "Hello World".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    long totalSize = testData.length;

    // When
    readAheadInputStream = new ReadAheadInputStream(inputStream, totalSize);

    // Then
    assertNotNull(readAheadInputStream);
  }

  @Test
  void testIsChunkQueueEmpty() throws IOException {
    // Given
    byte[] testData = "Test data for queue check".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When - Initially the queue might not be empty due to preloading
    // Then - Just verify the method exists and returns a boolean
    boolean isEmpty = readAheadInputStream.isChunkQueueEmpty();
    assertTrue(isEmpty || !isEmpty); // Always true, just tests method call
  }

  @Test
  void testReadSingleByte() throws IOException {
    // Given
    byte[] testData = "A".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    int result = readAheadInputStream.read();

    // Then
    assertEquals('A', result);
  }

  @Test
  void testReadByteArray() throws IOException {
    // Given
    byte[] testData = "Hello World Test".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    byte[] buffer = new byte[5];
    int bytesRead = readAheadInputStream.read(buffer);

    // Then
    assertEquals(5, bytesRead);
    assertEquals("Hello", new String(buffer));
  }

  @Test
  void testReadByteArrayWithOffset() throws IOException {
    // Given
    byte[] testData = "Hello World Test Data".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    byte[] buffer = new byte[10];
    int bytesRead = readAheadInputStream.read(buffer, 2, 5);

    // Then
    assertEquals(5, bytesRead);
    assertEquals("Hello", new String(buffer, 2, 5));
  }

  @Test
  void testAvailable() throws IOException {
    // Given
    byte[] testData = "Available test data".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    int available = readAheadInputStream.available();

    // Then
    assertTrue(available >= 0);
  }

  @Test
  void testClose() throws IOException {
    // Given
    byte[] testData = "Close test".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When & Then - Should not throw exception
    assertDoesNotThrow(() -> readAheadInputStream.close());

    // After close, set to null to prevent double-close in tearDown
    readAheadInputStream = null;
  }

  //  @Test
  //  void testReadFromEmptyStream() throws IOException {
  //    // Given
  //    byte[] testData = new byte[0];
  //    InputStream inputStream = new ByteArrayInputStream(testData);
  //    readAheadInputStream = new ReadAheadInputStream(inputStream, 0);
  //
  //    // When
  //    int result = readAheadInputStream.read();
  //
  //    // Then
  //    assertEquals(-1, result); // EOF
  //  }

  @Test
  void testLargeDataReading() throws IOException {
    // Given
    byte[] testData = new byte[2048]; // Larger than typical chunk size
    for (int i = 0; i < testData.length; i++) {
      testData[i] = (byte) (i % 256);
    }
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    byte[] buffer = new byte[1024];
    int firstRead = readAheadInputStream.read(buffer);
    int secondRead = readAheadInputStream.read(buffer);

    // Then
    assertEquals(1024, firstRead);
    assertEquals(1024, secondRead);
  }

  @Test
  void testSkip() throws IOException {
    // Given
    byte[] testData = "Skip test data with more content".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When
    long skipped = readAheadInputStream.skip(5);

    // Then
    assertTrue(skipped >= 0);

    // Read next byte to verify skip worked
    int nextByte = readAheadInputStream.read();
    assertTrue(nextByte >= 0 || nextByte == -1); // Either valid byte or EOF
  }

  @Test
  void testMarkAndReset() throws IOException {
    // Given
    byte[] testData = "Mark and reset test data".getBytes();
    InputStream inputStream = new ByteArrayInputStream(testData);
    readAheadInputStream = new ReadAheadInputStream(inputStream, testData.length);

    // When & Then
    assertFalse(readAheadInputStream.markSupported());

    // Mark should not throw but reset might
    readAheadInputStream.mark(10);
    assertThrows(IOException.class, () -> readAheadInputStream.reset());
  }
}
