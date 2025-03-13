package com.sap.cds.sdm.service;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.reactivestreams.Publisher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

public class RetryUtils {

  public static Predicate<Throwable> shouldRetry() {
    return throwable ->
        throwable instanceof HttpClientErrorException
            || throwable instanceof HttpServerErrorException;
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
                            return Flowable.timer(delay, TimeUnit.SECONDS).map(ignored -> error);
                          } else {
                            return Flowable.error(error);
                          }
                        }));
  }
}
