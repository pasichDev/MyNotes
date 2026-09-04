package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Shared retry policy for idempotent Google Drive requests. */
final class DriveRequestExecutor {

    static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 250L;
    private static final long MAX_BACKOFF_MS = 4_000L;

    interface Request<T> {
        T execute() throws IOException;
    }

    interface Sleeper {
        void sleep(long durationMs) throws InterruptedException;
    }

    interface Jitter {
        long nextLong(long upperExclusive);
    }

    private final Clock clock;
    private final Sleeper sleeper;
    private final Jitter jitter;

    DriveRequestExecutor() {
        this(
                Clock.systemUTC(),
                Thread::sleep,
                upperExclusive -> new SecureRandom().nextInt((int) upperExclusive));
    }

    DriveRequestExecutor(@NonNull Clock clock, @NonNull Sleeper sleeper, @NonNull Jitter jitter) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    /**
     * Executes only requests whose repeated execution cannot overwrite or duplicate logical data.
     * Create/upload requests deliberately use post-failure discovery instead of this method.
     */
    <T> T executeIdempotent(@NonNull Request<T> request) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            throwIfInterrupted();
            try {
                return request.execute();
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt == MAX_ATTEMPTS || !isRetryable(failure)) {
                    throw failure;
                }
                sleep(backoffDelayMs(attempt, retryAfterMs(failure)));
            }
        }
        throw lastFailure == null ? new IOException("Drive request failed") : lastFailure;
    }

    private void sleep(long delayMs) throws IOException {
        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted =
                    new InterruptedIOException("Drive request interrupted");
            interrupted.initCause(error);
            throw interrupted;
        }
        throwIfInterrupted();
    }

    private static void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Drive request interrupted");
        }
    }

    private long backoffDelayMs(int attempt, long retryAfterMs) {
        if (retryAfterMs >= 0L) {
            return Math.min(retryAfterMs, MAX_BACKOFF_MS);
        }
        long exponential = Math.min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS << (attempt - 1));
        return exponential / 2L + jitter.nextLong(exponential / 2L + 1L);
    }

    private long retryAfterMs(@NonNull IOException failure) {
        if (!(failure instanceof DriveHttpException)) {
            return -1L;
        }
        return ((DriveHttpException) failure).retryAfterMs(clock.millis());
    }

    static boolean isRetryable(@NonNull IOException failure) {
        if (failure instanceof InterruptedIOException) {
            return !Thread.currentThread().isInterrupted()
                    && !(failure instanceof SocketTimeoutException
                            && Thread.currentThread().isInterrupted());
        }
        if (failure instanceof DriveHttpException) {
            int status = ((DriveHttpException) failure).statusCode;
            return status == 429
                    || status == 500
                    || status == 502
                    || status == 503
                    || status == 504
                    || (status == 403 && ((DriveHttpException) failure).isRateLimit());
        }
        return failure instanceof ConnectException
                || failure instanceof SocketException
                || failure instanceof UnknownHostException;
    }

    static final class DriveHttpException extends IOException {
        final int statusCode;
        @Nullable final String retryAfter;
        @NonNull final String detail;

        DriveHttpException(int statusCode, @Nullable String retryAfter, @NonNull String detail) {
            super("Drive API HTTP " + statusCode + (detail.isEmpty() ? "" : ": " + detail));
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
            this.detail = detail;
        }

        boolean isRateLimit() {
            String normalized = detail.toLowerCase();
            return normalized.contains("ratelimit") || normalized.contains("rate limit");
        }

        long retryAfterMs(long nowMs) {
            if (retryAfter == null || retryAfter.trim().isEmpty()) {
                return -1L;
            }
            String value = retryAfter.trim();
            try {
                return Math.max(0L, Math.multiplyExact(Long.parseLong(value), 1_000L));
            } catch (NumberFormatException | ArithmeticException ignored) {
                try {
                    return Math.max(
                            0L,
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                            .toInstant()
                                            .toEpochMilli()
                                    - nowMs);
                } catch (RuntimeException malformedDate) {
                    return -1L;
                }
            }
        }
    }
}
