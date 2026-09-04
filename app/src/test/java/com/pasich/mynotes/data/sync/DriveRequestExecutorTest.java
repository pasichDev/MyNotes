package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class DriveRequestExecutorTest {

    @Test
    public void retriesTransientHttpFailuresAndHonorsRetryAfter() throws Exception {
        List<Long> delays = new ArrayList<>();
        DriveRequestExecutor executor =
                executor(delays, Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
        AtomicInteger attempts = new AtomicInteger();

        String result =
                executor.executeIdempotent(
                        () -> {
                            if (attempts.getAndIncrement() == 0) {
                                throw new DriveRequestExecutor.DriveHttpException(
                                        429, "2", "rateLimitExceeded");
                            }
                            return "ok";
                        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(delays).containsExactly(2_000L);
    }

    @Test
    public void retriesConnectionFailuresButNotAuthenticationOrPermanentForbidden() {
        assertThat(DriveRequestExecutor.isRetryable(new SocketTimeoutException())).isTrue();
        assertThat(
                        DriveRequestExecutor.isRetryable(
                                new DriveRequestExecutor.DriveHttpException(500, null, "")))
                .isTrue();
        assertThat(
                        DriveRequestExecutor.isRetryable(
                                new DriveRequestExecutor.DriveHttpException(
                                        403, null, "rateLimitExceeded")))
                .isTrue();
        assertThat(
                        DriveRequestExecutor.isRetryable(
                                new DriveRequestExecutor.DriveHttpException(401, null, "")))
                .isFalse();
        assertThat(
                        DriveRequestExecutor.isRetryable(
                                new DriveRequestExecutor.DriveHttpException(
                                        403, null, "forbidden")))
                .isFalse();
    }

    @Test
    public void doesNotRetryPermanentFailure() {
        DriveRequestExecutor executor = executor(new ArrayList<>(), Clock.systemUTC());
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                IOException.class,
                () ->
                        executor.executeIdempotent(
                                () -> {
                                    attempts.incrementAndGet();
                                    throw new DriveRequestExecutor.DriveHttpException(
                                            401, null, "unauthorized");
                                }));

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    public void interruptionIsPropagatedWithoutAnotherAttempt() {
        DriveRequestExecutor executor = executor(new ArrayList<>(), Clock.systemUTC());
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> executor.executeIdempotent(() -> "never"));
        } finally {
            Thread.interrupted();
        }
    }

    private static DriveRequestExecutor executor(List<Long> delays, Clock clock) {
        return new DriveRequestExecutor(clock, delays::add, upperExclusive -> 0L);
    }
}
