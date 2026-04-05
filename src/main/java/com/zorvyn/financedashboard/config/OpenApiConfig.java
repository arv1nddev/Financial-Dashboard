package com.zorvyn.financedashboard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ============================================================================
 * OPENAPI / SWAGGER CONFIGURATION
 * ============================================================================
 *
 * Pre-configures the Swagger UI with:
 *   1. API metadata (title, version, description)
 *   2. Bearer Token security scheme (so the "Authorize" button in Swagger UI
 *      allows testers to paste their JWT and test authenticated endpoints)
 *   3. Server information for the API base URL
 *
 * This configuration enables the Swagger UI at:
 *   http://localhost:8080/api/swagger-ui.html
 *
 * And the raw OpenAPI spec at:
 *   http://localhost:8080/api/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    /**
     * Custom OpenAPI specification bean.
     *
     * The SecurityScheme of type HTTP with scheme "bearer" tells Swagger UI
     * to render the "Authorize" button. When a user clicks it and pastes
     * their JWT, Swagger automatically adds the "Authorization: Bearer <token>"
     * header to all subsequent API calls from the UI.
     */
    @Bean
    public OpenAPI financeOpenAPI() {
        final String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()
            // Metadata visible in the Swagger UI header
            .info(new Info()
                .title("Finance Dashboard API")
                .description("""
                    Production-grade Finance Dashboard REST API built with Spring Boot 3.x.
                    
                    ## Authentication
                    1. Call `POST /auth/login` with your credentials to get a JWT token.
                    2. Click the **Authorize** button above and paste the token.
                    3. All subsequent API calls will include the token automatically.
                    
                    ## Test Accounts
                    | Email | Password | Role |
                    |-------|----------|------|
                    | admin@zorvyn.com | Admin@123 | ADMIN |
                    | analyst@zorvyn.com | Analyst@123 | ANALYST |
                    | viewer@zorvyn.com | Viewer@123 | VIEWER |
                    
                    ## Rate Limiting
                    Dashboard summary endpoints are rate-limited to **20 requests/minute** per client IP.
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Zorvyn Engineering")
                    .email("engineering@zorvyn.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://zorvyn.com/license")))

            // Server configuration
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080/api")
                    .description("Local Development Server")
            ))

            // Global security requirement — every endpoint requires Bearer auth
            // Individual endpoints can override this with @SecurityRequirements({})
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))

            // Define the Bearer token security scheme
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter your JWT token obtained from the /auth/login endpoint")
                )
            );
    }
}
