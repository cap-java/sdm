package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.service.RetryUtils;
import com.sap.cds.sdm.service.exceptions.InsufficientDataException;
import com.sap.cloud.security.client.HttpClientException;
import io.reactivex.Flowable;
import java.io.EOFException;
import java.io.IOException;
import java.util.function.Predicate;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpResponseException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RetryUtilsTest {

  @Mock private HttpHostConnectException mockHttpHostConnectException;
  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private HttpClientException mockHttpClientException;

  @Test
  void testShouldRetryWithEOFException() {
    // Given
    EOFException eofException = new EOFException("End of file reached");
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(eofException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldRetryWithInsufficientDataException() {
    // Given
    InsufficientDataException insufficientDataException =
        new InsufficientDataException("Insufficient data");
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(insufficientDataException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldRetryWithHttpHostConnectException() {
    // Given
    MockitoAnnotations.openMocks(this);
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(mockHttpHostConnectException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldRetryWithHttpResponseException() {
    // Given
    MockitoAnnotations.openMocks(this);
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(mockHttpResponseException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldRetryWithHttpClientException() {
    // Given
    MockitoAnnotations.openMocks(this);
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(mockHttpClientException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldNotRetryWithNonRetryableException() {
    // Given
    IllegalArgumentException illegalArgumentException =
        new IllegalArgumentException("Invalid argument");
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(illegalArgumentException);

    // Then
    assertFalse(result);
  }

  @Test
  void testShouldNotRetryWithGenericIOException() {
    // Given
    IOException ioException = new IOException("Generic IO error");
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(ioException);

    // Then
    assertFalse(result);
  }

  @Test
  void testShouldRetryWithWrappedException() {
    // Given
    EOFException eofException = new EOFException("End of file reached");
    RuntimeException wrappedException = new RuntimeException("Wrapper", eofException);
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(wrappedException);

    // Then
    assertTrue(result);
  }

  @Test
  void testShouldNotRetryWithNullException() {
    // Given
    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When & Then
    // The current implementation doesn't handle null gracefully and throws NPE
    assertThrows(
        NullPointerException.class,
        () -> {
          shouldRetry.test(null);
        });
  }

  @Test
  void testRetryLogicWithMaxAttempts() {
    // Given
    int maxAttempts = 3;

    // When
    var retryLogic = RetryUtils.retryLogic(maxAttempts);

    // Then
    assertNotNull(retryLogic);
    // Testing the actual retry logic would require more complex setup with RxJava
    // This test verifies the method returns a non-null function
  }

  @Test
  void testRetryLogicFlowable() {
    // Given
    int maxAttempts = 2;
    InsufficientDataException testException = new InsufficientDataException("Test retry");
    Flowable<Throwable> errorFlowable = Flowable.just(testException);

    // When
    var retryFunction = RetryUtils.retryLogic(maxAttempts);
    try {
      var result = retryFunction.apply(errorFlowable);
      // Then
      assertNotNull(result);
      // The result should be a Publisher that handles retry logic
      assertTrue(result instanceof Flowable);
    } catch (Exception e) {
      fail("Should not throw exception: " + e.getMessage());
    }
  }

  @Test
  void testShouldRetryWithDeeplyNestedCause() {
    // Given
    InsufficientDataException rootCause = new InsufficientDataException("Root cause");
    RuntimeException level1 = new RuntimeException("Level 1", rootCause);
    RuntimeException level2 = new RuntimeException("Level 2", level1);
    RuntimeException level3 = new RuntimeException("Level 3", level2);

    Predicate<Throwable> shouldRetry = RetryUtils.shouldRetry();

    // When
    boolean result = shouldRetry.test(level3);

    // Then
    assertTrue(result);
  }

  @Test
  void testRetryLogicWithRetryAttemptCreation() {
    // This test indirectly covers the RetryAttempt class by using the retry mechanism
    // Given
    Flowable<String> flowable =
        Flowable.fromCallable(
            () -> {
              throw new EOFException("Test exception");
            });

    // When
    Flowable<String> retryFlowable = flowable.retryWhen(RetryUtils.retryLogic(2));

    // Then - verify the flowable is created (RetryAttempt is used internally)
    assertNotNull(retryFlowable);

    // Test that it eventually fails after retries (which exercises the RetryAttempt class)
    assertThrows(RuntimeException.class, () -> retryFlowable.blockingFirst());
  }
}
