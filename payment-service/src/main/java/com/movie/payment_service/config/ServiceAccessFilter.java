package com.movie.payment_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ServiceAccessFilter extends OncePerRequestFilter {

    @Value("${app.gateway-secret}")
    private String gatewaySecret;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/payment");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // MoMo cần gọi IPN trực tiếp từ ngoài vào.
        // IPN đã được bảo vệ bằng chữ ký MoMo trong MomoServiceImpl.
        if ("/api/v1/payment/momo/ipn".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // API hoàn tiền được booking-service gọi nội bộ khi hủy vé đã thanh toán.
        if (path.matches("^/api/v1/payment/momo/refund/[^/]+$")) {
            if (!isValidInternalRequest(request)) {
                reject(response, "Forbidden internal payment API");
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        // API tạo thanh toán phải đi qua Gateway.
        if (!isValidGatewayRequest(request)) {
            reject(response, "Forbidden payment API");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidGatewayRequest(HttpServletRequest request) {
        String secret = request.getHeader("X-Gateway-Secret");
        return gatewaySecret.equals(secret);
    }

    private boolean isValidInternalRequest(HttpServletRequest request) {
        String secret = request.getHeader("X-Internal-Secret");
        return internalSecret.equals(secret);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}