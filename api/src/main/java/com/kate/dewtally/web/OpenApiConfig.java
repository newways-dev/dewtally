package com.kate.dewtally.web;

import com.kate.dewtally.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Machine-readable API description (OpenAPI) at /v3/api-docs and Swagger UI at /swagger-ui.html.
 * The web UI generates its TypeScript types from it, so a changed field breaks the UI build, not production.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer";

    static {
        // CurrentUser comes from the token, never from the request: do not show it as a parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    OpenAPI dewtallyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("dewtally API")
                        .version("v1")
                        .description("Incoming invoices and e-invoices (XRechnung, ZUGFeRD) checked with rules, "
                                + "supplier master data and controlled AI. Every call needs a token from Keycloak."))
                // Swagger UI shows an "Authorize" button: paste a token and try the API as that user
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}