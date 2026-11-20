package com.dvillacinda.canteragt.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfig {

    static {
        // swagger-core resolves models with these Jackson 2 mappers (OpenAPI 3.0 and 3.1). They must honour the
        // Jackson 3 @JsonNaming of our DTOs so the documented schemas match the snake_case the API really uses.
        registerJackson3Naming(Json.mapper());
        registerJackson3Naming(Json31.mapper());
    }

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("CanteraGt API")
                        .description("This is the rest API for CanteraGT, including API for players and DT's ")
                        .version("v0.0.1")
                        .license(new License().name("Spring Boot 4.1.1")));
    }

    static void registerJackson3Naming(ObjectMapper mapper) {
        AnnotationIntrospector current = mapper.getSerializationConfig().getAnnotationIntrospector();
        boolean alreadyRegistered = current.allIntrospectors().stream()
                .anyMatch(Jackson3NamingIntrospector.class::isInstance);
        if (!alreadyRegistered) {
            mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(new Jackson3NamingIntrospector(), current));
        }
    }
}
