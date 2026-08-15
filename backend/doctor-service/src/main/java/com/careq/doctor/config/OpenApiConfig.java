package com.careq.doctor.config;

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
 * OpenAPI documentation configuration for doctor-service.
 *
 * All endpoints require a valid JWT (enforced by the API Gateway). A global
 * HTTP Bearer security scheme is declared so the Swagger UI shows an
 * "Authorize" button that applies to every operation. Fine-grained role
 * checks (ADMIN for CRUD, DOCTOR for availability) are enforced inside the
 * controllers via X-User-Role and documented per-operation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careqDoctorOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("CareQ Doctor Service")
                        .description("Department catalog and doctor catalog management, public doctor browsing and availability toggling.")
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
