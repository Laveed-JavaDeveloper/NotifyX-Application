package com.example.notifyx.config;

import com.example.notifyx.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Only apply rate limiting to the POST notification endpoint
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) && path.startsWith("/api/notifications"))) {
            return true;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.trim().isEmpty()) {
            userId = request.getRemoteAddr();
        }

        if (!rateLimiterService.isAllowed(userId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded: max 5 requests per minute.\",\"retryAfter\":60}");
            return false;
        }

        return true;
    }
}
