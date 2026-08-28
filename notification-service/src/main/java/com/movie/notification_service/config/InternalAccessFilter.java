package com.movie.notification_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalAccessFilter extends OncePerRequestFilter {

    @Value("${app.gateway-secret}")
    private String gatewaySecret;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/notifications");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // API nội bộ chỉ cho service khác gọi bằng X-Internal-Secret (vd. gửi mail, tạo thông báo)
        if (path.startsWith("/api/v1/notifications/internal")) {
            if (!internalSecret.equals(request.getHeader("X-Internal-Secret"))) {
                reject(response, "Forbidden internal notification API");
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        // API cho người dùng (xem/đánh dấu đã đọc thông báo) phải đi qua Gateway
        if (!gatewaySecret.equals(request.getHeader("X-Gateway-Secret"))) {
            reject(response, "Forbidden notification API");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}