package com.movie.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleRateLimitFilter implements GlobalFilter, Ordered {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final long WINDOW_SECONDS = 60;

    @Override
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        RateLimitRule rule = resolveRule(request);

        if (rule == null) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(request);
        String key = clientIp + ":" + rule.key();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {
            long now = Instant.now().getEpochSecond();

            if (now - bucket.windowStart >= WINDOW_SECONDS) {
                bucket.windowStart = now;
                bucket.count = 0;
            }

            bucket.count++;

            if (bucket.count > rule.maxRequests()) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }

    private RateLimitRule resolveRule(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? "" : request.getMethod().name();

        if (path.equals("/api/v1/auth/login") && method.equals("POST")) {
            return new RateLimitRule("auth-login", 5);
        }

        if (path.equals("/api/v1/auth/register") && method.equals("POST")) {
            return new RateLimitRule("auth-register", 3);
        }

        if (path.equals("/api/v1/payment/momo/create") && method.equals("POST")) {
            return new RateLimitRule("payment-create", 10);
        }

        if (path.startsWith("/api/v1/booking") && method.equals("POST")) {
            return new RateLimitRule("booking-post", 20);
        }

        if (path.startsWith("/api/v1/catalog") && isWriteMethod(method)) {
            return new RateLimitRule("catalog-write", 60);
        }

        return null;
    }

    private boolean isWriteMethod(String method) {
        return method.equals("POST")
                || method.equals("PUT")
                || method.equals("PATCH")
                || method.equals("DELETE");
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        if (request.getRemoteAddress() == null) {
            return "unknown";
        }

        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static class Bucket {
        long windowStart = Instant.now().getEpochSecond();
        int count = 0;
    }

    private record RateLimitRule(String key, int maxRequests) {
    }
}