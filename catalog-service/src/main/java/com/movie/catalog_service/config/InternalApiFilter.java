package com.movie.catalog_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiFilter extends OncePerRequestFilter {

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Chỉ bảo vệ các API nội bộ giữa service với service
        return !(
                path.matches("/api/v1/catalog/showtimes/.*/seats/.*/price")
                        || path.matches("/api/v1/catalog/snacks/.*/price")
                        || path.matches("/api/v1/catalog/snack-combos/.*/price")
                        || path.matches("/api/v1/catalog/rooms/internal/.*/seats")
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String secret = request.getHeader("X-Internal-Secret");

        if (!internalSecret.equals(secret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Forbidden internal API");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
