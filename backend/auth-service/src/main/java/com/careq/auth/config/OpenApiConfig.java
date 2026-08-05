package com.careq.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Day 9: OpenAPI documentation configuration for auth-service.
 *
 * The {@code servers} entry is deliberately the relative URL "/" so that
 * whichever host serves the Swagger UI (the API Gateway at :8080 when the
 * docs are aggregated, or this service directly at :8081) is the host used
 * by "Try it out" — no cross-origin JWT issues.
 *
 * Auth endpoints (signup/login/health) are intentionally public, so no
 * global security requirement is applied here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careqAuthOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareQ Auth Service")
                        .description("User registration, login and JWT issuance for the CareQ platform. " +
                                "Endpoints here are public (no Bearer token required).")
                        .version("v1.0"))
                .servers(List.of(new Server().url("/")));
    }
}
