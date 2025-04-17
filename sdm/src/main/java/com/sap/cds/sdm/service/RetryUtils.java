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
    return throwable -> {
      logger.info("Evaluating shouldRetry for: {}", throwable.toString());

      Throwable cause = throwable;
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
      return false;
    };
  }

  public static Function<Flowable<Throwable>, Publisher<?>> retryLogic(int maxAttempts) {
    return errors ->
        errors.flatMap(
            error ->
                Flowable.range(1, maxAttempts + 1)
                    .concatMap(
                        attempt -> {
                          if (shouldRetry().test(error) && attempt <= maxAttempts) {
                            long delay =
                                (long)
                                    Math.pow(2, attempt); // Exponential backoff: 2^attempt seconds
                            logger.info(
                                "Retry attempt {} failed. Retrying in {} seconds. Error: {}",
                                attempt,
                                delay,
                                error.getMessage(),
                                error);
                            return Flowable.timer(delay, TimeUnit.SECONDS).map(ignored -> error);
                          } else {
                            logger.error(
                                "Max attempts reached or no retry condition matched. Exiting with error.");
                            return Flowable.error(error);
                          }
                        })
                    .onErrorResumeNext(Flowable.just(error)));
  }
}
