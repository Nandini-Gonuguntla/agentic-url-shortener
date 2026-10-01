package com.agentic.orchestrator.api;

import com.agentic.orchestrator.config.OrchestratorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Optional shared-token protection for state-changing endpoints (approvals, stops, overrides).
 * Disabled when orchestrator.api-token is empty, which is the local-demo default.
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final byte[] token;

    public ApiTokenFilter(OrchestratorProperties props) {
        this.token = props.apiToken() == null || props.apiToken().isBlank()
                ? null : props.apiToken().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || "GET".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-Api-Token");
        if (provided == null || !MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid X-Api-Token");
            return;
        }
        chain.doFilter(request, response);
    }
}
