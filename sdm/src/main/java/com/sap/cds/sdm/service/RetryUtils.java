package com.sap.cds.sdm.service;

import com.sap.cloud.security.client.HttpClientException;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpResponseException;
import org.reactivestreams.Publisher;

public class RetryUtils {

  public static Predicate<Throwable> shouldRetry() {
    return throwable ->
        throwable instanceof HttpHostConnectException
            || throwable instanceof HttpResponseException
            || throwable instanceof HttpClientException;
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
