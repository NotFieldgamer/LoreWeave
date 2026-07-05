package com.loreweave.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Caps how fast a signed-in user can take turns (the expensive Gemini path). Runs in the MVC chain
 * after Spring Security, so the key is the authenticated user id (the JWT subject); falls back to the
 * remote address if somehow unauthenticated. Over the limit → 429 with a Retry-After header.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final ObjectMapper json;

    public RateLimitInterceptor(RateLimiter limiter, ObjectMapper json) {
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key = (auth != null && auth.isAuthenticated()) ? auth.getName() : req.getRemoteAddr();
        if (limiter.allow(key)) return true;

        long retry = limiter.retryAfterSeconds(key);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retry));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getWriter(), Map.of(
            "error", "You're taking turns faster than the free tier allows. Give it a moment.",
            "retryAfterSeconds", retry));
        return false;
    }
}
