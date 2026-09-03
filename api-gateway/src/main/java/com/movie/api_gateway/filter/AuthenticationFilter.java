package com.movie.api_gateway.filter;

import com.movie.api_gateway.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${app.gateway-secret}")
    private String gatewaySecret;

    public AuthenticationFilter() {
        super(Config.class);
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            log.debug("AuthenticationFilter: {} {} (hasAuthorization={})",
                    request.getMethod(), request.getURI().getPath(),
                    request.getHeaders().containsKey("Authorization"));

            if (request.getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }
            if (!request.getHeaders().containsKey("Authorization")) {
                return onError(exchange, "Thiếu header Authorization", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getOrEmpty("Authorization").get(0);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Định dạng định danh không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            if (!jwtUtils.validateJwt(token)) {
                return onError(exchange, "Token đã hết hạn hoặc không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            Claims claims = jwtUtils.getClaimsFromJwt(token);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);

            if (isAdminEndpoint(request) && !isAdmin(role)) {
                return onError(exchange, "Bạn không có quyền thực hiện chức năng quản trị", HttpStatus.FORBIDDEN);
            }

            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        // Xóa header client tự gửi để tránh giả mạo quyền
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.remove("X-Gateway-Secret");
                    })
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();

            log.debug("Gateway forward headers: X-User-Id={}, X-User-Role={}, X-Gateway-Secret={}",
                    mutatedRequest.getHeaders().getFirst("X-User-Id"),
                    mutatedRequest.getHeaders().getFirst("X-User-Role"),
                    maskSecret(mutatedRequest.getHeaders().getFirst("X-Gateway-Secret")));
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    private boolean isAdminEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (method == null) {
            return false;
        }

        boolean writeMethod = method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;

        if (!writeMethod) {
            return false;
        }

        return path.startsWith("/api/v1/catalog/movies")
                || path.startsWith("/api/v1/catalog/cinemas")
                || path.startsWith("/api/v1/catalog/rooms")
                || path.startsWith("/api/v1/catalog/seats")
                || path.startsWith("/api/v1/catalog/showtimes")
                || path.startsWith("/api/v1/catalog/snacks")
                || path.startsWith("/api/v1/catalog/snack-combos")
                || path.startsWith("/api/v1/catalog/files")
                || path.startsWith("/api/v1/booking/tickets/checkin");
    }

    private boolean isAdmin(String role) {
        return role != null && "ADMIN".equalsIgnoreCase(role);
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "NULL";
        }

        if (value.length() <= 8) {
            return "****";
        }

        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}