package com.dvillacinda.canteragt.shared.config;

import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;

import tools.jackson.databind.annotation.JsonNaming;

/**
 * Lets the Jackson 2 mappers used by swagger-core/springdoc honour the Jackson 3 {@link JsonNaming} annotation.
 *
 * <p>The API is serialized by Jackson 3 ({@code tools.jackson}), but swagger-core still introspects models with
 * Jackson 2 ({@code com.fasterxml.jackson.databind}), which ignores Jackson 3's {@code @JsonNaming}. Without this
 * bridge the OpenAPI schemas and Swagger examples show camelCase while the API reads and writes snake_case.
 * The strategy is mapped by its simple name ({@code SnakeCaseStrategy}, {@code KebabCaseStrategy}, ...), which is
 * the same in both Jackson versions.
 */
public class Jackson3NamingIntrospector extends NopAnnotationIntrospector {

    private static final long serialVersionUID = 1L;
    private static final String JACKSON2_STRATEGIES = "com.fasterxml.jackson.databind.PropertyNamingStrategies$";

    @Override
    public Object findNamingStrategy(AnnotatedClass ac) {
        JsonNaming naming = ac.getRawType().getAnnotation(JsonNaming.class);
        if (naming == null) {
            return null;
        }
        String strategyName = naming.value().getSimpleName();
        try {
            return Class.forName(JACKSON2_STRATEGIES + strategyName);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("No Jackson 2 equivalent for naming strategy " + naming.value().getName()
                    + " used by " + ac.getRawType().getName(), ex);
        }
    }
}
