package com.movie.catalog_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
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
        return !path.startsWith("/api/v1/catalog");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isInternalCatalogApi(path)) {
            if (!isValidInternalRequest(request)) {
                reject(response, "Forbidden internal catalog API");
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        // Cho public xem dữ liệu catalog bằng GET:
        // danh sách phim, rạp, suất chiếu, phòng, snack...
        if (HttpMethod.GET.matches(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Các API ghi dữ liệu catalog phải đi qua Gateway.
        if (!isValidGatewayRequest(request)) {
            reject(response, "Forbidden catalog write API");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalCatalogApi(String path) {
        return path.matches("^/api/v1/catalog/showtimes/[^/]+/seats/[^/]+/price$")
                || path.matches("^/api/v1/catalog/snacks/[^/]+/price$")
                || path.matches("^/api/v1/catalog/snack-combos/[^/]+/price$")
                || path.matches("^/api/v1/catalog/rooms/internal/[^/]+/seats$");
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