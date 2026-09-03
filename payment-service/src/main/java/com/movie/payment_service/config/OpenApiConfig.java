package com.movie.payment_service.config;

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
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service API")
                        .version("v1")
                        .description("Tạo giao dịch thanh toán MoMo (QR/link), nhận IPN callback, giả lập thanh toán (dev), "
                                + "lịch sử giao dịch, hoàn tiền tự động qua MoMo. "
                                + "Khi gọi qua API Gateway (cổng 8080) dùng prefix /api/v1/payment. "
                                + "Endpoint /api/v1/payment/momo/ipn do MoMo gọi trực tiếp, bảo vệ bằng chữ ký HMAC."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
