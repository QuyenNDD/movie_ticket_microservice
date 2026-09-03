package com.movie.catalog_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI catalogServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Catalog Service API")
                        .version("v1")
                        .description("Quản lý phim, rạp, phòng chiếu, suất chiếu, ghế, snack, combo, review, favorite. "
                                + "Khi gọi qua API Gateway (cổng 8080) dùng prefix /api/v1/catalog và header Authorization: Bearer <JWT>."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
