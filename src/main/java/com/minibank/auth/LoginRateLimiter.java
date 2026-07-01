package com.minibank.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.minibank.common.error.TooManyLoginAttemptsException;

/**
 * Fixed-window limiter for failed logins, keyed by email. In-memory by
 * design: it protects password guessing on a single node; a clustered
 * deployment would back this with Redis instead.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Window(Instant start, int failures) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void checkAllowed(String key) {
        Window window = windows.get(key);
        if (window == null || expired(window)) {
            return;
        }
        if (window.failures() >= MAX_FAILURES) {
            long retryAfter = Duration.between(Instant.now(), window.start().plus(WINDOW)).toSeconds();
            throw new TooManyLoginAttemptsException(Math.max(retryAfter, 1));
        }
    }

    public void recordFailure(String key) {
        windows.compute(key, (ignored, window) ->
                window == null || expired(window)
                        ? new Window(Instant.now(), 1)
                        : new Window(window.start(), window.failures() + 1));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private static boolean expired(Window window) {
        return window.start().plus(WINDOW).isBefore(Instant.now());
    }
}
