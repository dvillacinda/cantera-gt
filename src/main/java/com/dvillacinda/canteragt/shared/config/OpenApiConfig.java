package com.dvillacinda.canteragt.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("CanteraGt API")
                        .description("This is the rest API for CanteraGT, including API for players and DT's ")
                        .version("v0.0.1")
                        .license(new License().name("Spring Boot 4.1.1")));
    }

}
