package com.movie.api_gateway.filter;

import com.movie.api_gateway.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtils jwtUtils;

    public AuthenticationFilter() {
        super(Config.class);
    }

    public static class Config {
        // Cấu hình nếu cần truyền tham số vào Filter
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Kiểm tra xem Request có gửi kèm Header Authorization không
            if (!request.getHeaders().containsKey("Authorization")) {
                return onError(exchange, "Thiếu header Authorization", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getOrEmpty("Authorization").get(0);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Định dạng định danh không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            // 2. Kiểm tra tính hợp lệ của Token
            if (!jwtUtils.validateJwt(token)) {
                return onError(exchange, "Token đã hết hạn hoặc không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            // 3. Trích xuất thông tin Claims từ Token
            Claims claims = jwtUtils.getClaimsFromJwt(token);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);

            // 4. TIÊN TIÊM (INJECT) HEADER MỚI VÀO REQUEST TRƯỚC KHI ĐẨY ĐI
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .build();

            // Thay thế request cũ bằng request mới đã được tiêm header
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}
