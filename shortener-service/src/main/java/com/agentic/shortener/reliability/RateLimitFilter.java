package com.agentic.shortener.reliability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limits link creation (the abuse-prone write path). Redirects are not limited here:
 * they are cache-backed and read-only. The key is the socket address; X-Forwarded-For is
 * deliberately not trusted because clients can forge it without a trusted proxy in front.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/links"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Duration wait = rateLimiter.tryAcquire(request.getRemoteAddr());
        if (wait.isZero()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"urn:problem:rate-limited","title":"Too Many Requests","status":429,\
                "detail":"Link creation rate limit exceeded; retry after %d seconds"}"""
                .formatted(retryAfterSeconds));
    }
}
