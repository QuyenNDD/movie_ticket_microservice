package com.movie.booking_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Service API")
                        .version("v1")
                        .description("Xem sơ đồ ghế, giữ chỗ tạm thời (Redis TTL), xác nhận/hủy thanh toán, "
                                + "hủy vé đã thanh toán + hoàn tiền, lịch sử đặt vé, vé điện tử QR, check-in tại rạp. "
                                + "Khi gọi qua API Gateway (cổng 8080) dùng prefix /api/v1/booking và header Authorization: Bearer <JWT>."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
