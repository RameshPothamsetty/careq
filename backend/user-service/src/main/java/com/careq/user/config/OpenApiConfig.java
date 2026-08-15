package com.careq.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI documentation configuration for user-service.
 *
 * All endpoints (except the public profile-picture serving route, which is
 * whitelisted at the gateway) require a valid JWT — enforced by the API
 * Gateway. A global HTTP Bearer security scheme is declared so the Swagger
 * UI shows an "Authorize" button that works for every operation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careqUserOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("CareQ User Service")
                        .description("Patient/Doctor/Admin profile management with lazy profile creation on first access.")
                        .version("v1.0"))
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
