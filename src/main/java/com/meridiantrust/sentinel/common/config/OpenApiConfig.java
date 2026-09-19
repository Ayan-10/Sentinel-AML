package com.meridiantrust.sentinel.common.config;

import com.meridiantrust.sentinel.common.security.Roles;
import com.meridiantrust.sentinel.transaction.model.Transaction;

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

/** Deliverable D4: the OpenAPI document served at /v3/api-docs and /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    public OpenAPI sentinelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel AML — Transaction Monitoring System")
                        .version("v1")
                        .description("""
                                Real-time money-laundering detection for MeridianTrust Bank.

                                **Flow:** ingestion → detection → risk-scored alert → case disposition.

                                **Roles** (RBAC is enforced at this API layer, not only in the UI):
                                - `ANALYST` — alert queue (PII masked), cases, dispositions
                                - `SENIOR_ANALYST` — the above, plus unmasked PII and SAR escalation
                                - `COMPLIANCE_ADMIN` — the above, plus ingestion and rule configuration

                                Detection thresholds are tunable at runtime via
                                `PATCH /api/v1/admin/rules/{ruleCode}` — no redeployment required.
                                """)
                        .contact(new Contact().name("MeridianTrust Financial Crime Engineering"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic. Demo users: analyst / senior / admin.")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
