package com.sap.cds.sdm.service;

import com.sap.cds.sdm.service.exceptions.InsufficientDataException;
import com.sap.cloud.security.client.HttpClientException;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.io.EOFException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpResponseException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Removed incorrect import for ClientAbortException

public class RetryUtils {

  private RetryUtils() {
    // Doesn't do anything
  }

  private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

  public static Predicate<Throwable> shouldRetry() {
    logger.debug("START: shouldRetry predicate created");
    return throwable -> {
      logger.info("Evaluating shouldRetry for: {}", throwable.toString());

      Throwable cause = throwable;
      // while loop to check if the cause is wrapped in another exception
      // and check if the cause is one of the specified exceptions
      while (cause != null) {
        logger.info("Checking cause: {}", cause.getClass().getSimpleName());
        if (cause instanceof EOFException
            || cause instanceof InsufficientDataException
            || cause instanceof HttpHostConnectException
            || cause instanceof HttpResponseException
            || cause instanceof HttpClientException) {
          logger.info("Retrying due to: {}", cause.getClass().getSimpleName());
          return true;
        }
        cause = cause.getCause();
      }
      logger.debug("No retryable exception found, returning false");
      return false;
    };
  }

  public static Function<Flowable<Throwable>, Publisher<?>> retryLogic(int maxAttempts) {
    logger.debug("START: retryLogic with maxAttempts: {}", maxAttempts);
    return errors ->
        errors
            .zipWith(
                Flowable.range(1, maxAttempts),
                (error, attempt) -> new RetryAttempt(error, attempt))
            .flatMap(
                retry -> {
                  Throwable error = retry.error;
                  int attempt = retry.attempt;

                  if (shouldRetry().test(error)) {
                    long delay = (long) Math.pow(2, attempt); // exponential backoff
                    logger.info(
                        "Retry attempt {} failed. Retrying in {} seconds. Error: {}",
                        attempt,
                        delay,
                        error.getMessage());
                    return Flowable.timer(delay, TimeUnit.SECONDS);
                  } else {
                    logger.error(
                        "No retry condition matched or max attempts reached. Failing permanently. Error: {}",
                        error.getMessage());
                    return Flowable.error(error);
                  }
                });
  }

  private static class RetryAttempt {
    final Throwable error;
    final int attempt;

    RetryAttempt(Throwable error, int attempt) {
      this.error = error;
      this.attempt = attempt;
    }
  }
}
