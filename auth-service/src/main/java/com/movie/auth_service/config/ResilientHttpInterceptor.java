package com.movie.auth_service.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * Bọc lời gọi REST nội bộ (path bắt đầu bằng /api/v1/) qua RestTemplate bằng Resilience4j:
 * - Circuit breaker "internal": khi service đích lỗi liên tục thì mở mạch, fail nhanh.
 * - Retry "internal": chỉ thử lại với GET (idempotent). Timeout đã cấu hình ở RestTemplate.
 */
public class ResilientHttpInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientHttpInterceptor(CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry) {
        this.circuitBreaker = cbRegistry.circuitBreaker("internal");
        this.retry = retryRegistry.retry("internal");
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request,
                                        @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        if (!request.getURI().getPath().startsWith("/api/v1/")) {
            return execution.execute(request, body);
        }

        CheckedSupplier<ClientHttpResponse> call =
                CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> execution.execute(request, body));

        if (request.getMethod() == HttpMethod.GET) {
            call = Retry.decorateCheckedSupplier(retry, call);
        }

        try {
            return call.get();
        } catch (IOException | RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IOException(ex);
        }
    }
}
