package com.caseflow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI caseFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CaseFlow API")
                        .description("Ticket and email-based case management system (V2). " +
                                     "Authenticate via POST /api/auth/login to obtain a Bearer JWT access token.")
                        .version("2.0.0")
                        .contact(new Contact().name("CaseFlow Team")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token — obtain from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .tags(List.of(
                        new Tag().name("Auth").description("Authentication — login, refresh, logout, me"),
                        new Tag().name("Tickets").description("Ticket lifecycle management"),
                        new Tag().name("Customers").description("Customer account management"),
                        new Tag().name("Contacts").description("Customer contact management"),
                        new Tag().name("Users").description("User account management"),
                        new Tag().name("Groups").description("User group management"),
                        new Tag().name("Notes").description("Internal ticket notes"),
                        new Tag().name("Assignments").description("Ticket assignment workflow"),
                        new Tag().name("Transfers").description("Ticket transfer workflow"),
                        new Tag().name("Emails").description("Email document queries and ingest"),
                        new Tag().name("Attachments").description("Attachment upload and download")
                ));
    }
}
