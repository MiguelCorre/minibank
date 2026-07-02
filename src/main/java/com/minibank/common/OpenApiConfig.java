package com.minibank.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI minibankOpenApi() {
        var bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access token from POST /api/auth/login");
        return new OpenAPI()
                .info(new Info()
                        .title("minibank API")
                        .version("v1")
                        .description("""
                                Accounts, deposits and idempotent transfers with a double-entry \
                                ledger. Authenticate via /api/auth/login and use the returned \
                                access token as a bearer token."""))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
