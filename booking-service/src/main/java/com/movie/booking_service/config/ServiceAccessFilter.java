package com.movie.booking_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServiceAccessFilter extends OncePerRequestFilter {

    @Value("${app.gateway-secret}")
    private String gatewaySecret;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/booking");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // API nội bộ chỉ cho service khác gọi bằng X-Internal-Secret
        if (path.startsWith("/api/v1/booking/internal")) {
            if (!isValidInternalRequest(request)) {
                reject(response, "Forbidden internal booking API");
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        // API booking bình thường:
        // - Frontend gọi qua Gateway: có X-Gateway-Secret
        // - Payment Service gọi nội bộ: có X-Internal-Secret
        if (!isValidGatewayRequest(request) && !isValidInternalRequest(request)) {
            reject(response, "Forbidden booking API");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidGatewayRequest(HttpServletRequest request) {
        return secretMatches(gatewaySecret, request.getHeader("X-Gateway-Secret"));
    }

    private boolean isValidInternalRequest(HttpServletRequest request) {
        return secretMatches(internalSecret, request.getHeader("X-Internal-Secret"));
    }

    // So sánh chuỗi bí mật theo thời gian hằng số để tránh lộ thông tin qua timing attack.
    private static boolean secretMatches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}