package com.example.notifyx.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    // Simple in-memory rate limiter for standalone mode
    // Key: userId, Value: Object containing count and timestamp
    private final Map<String, RateLimitData> rateLimits = new ConcurrentHashMap<>();

    private static class RateLimitData {
        AtomicInteger count = new AtomicInteger(0);
        long windowStart = System.currentTimeMillis();
    }

    public boolean isAllowed(String userId) {
        long now = System.currentTimeMillis();
        long windowSizeMs = 60000; // 60 seconds
        int limit = 5;

        RateLimitData data = rateLimits.computeIfAbsent(userId, k -> new RateLimitData());

        synchronized (data) {
            if (now - data.windowStart > windowSizeMs) {
                // Reset window
                data.count.set(0);
                data.windowStart = now;
            }

            if (data.count.incrementAndGet() > limit) {
                return false;
            }
        }
        return true;
    }
}
