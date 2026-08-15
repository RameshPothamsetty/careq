package com.careq.notification.config;

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
 * OpenAPI documentation configuration for notification-service.
 *
 * All endpoints require a valid JWT (enforced by the API Gateway). A global
 * HTTP Bearer security scheme is declared so the Swagger UI shows an
 * "Authorize" button that applies to every operation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careqNotificationOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("CareQ Notification Service")
                        .description("Persisted in-app notifications. Consumes queue events from RabbitMQ " +
                                "(careq.events → careq.notifications) and serves them to the caller's bell.")
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
