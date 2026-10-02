package com.achintha.storeservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Public API docs. {@code /internal/**} is excluded ({@code springdoc.paths-to-exclude}). */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Store Service API", version = "v1",
        description = "Stores of the marketplace: store profiles, bank and courier master data, store bank accounts, "
                + "couriers and shipping templates, platform settings, holidays, merchant score and metrics, store "
                + "reviews and the fee ledger. Errors carry a stable 'code'."))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
