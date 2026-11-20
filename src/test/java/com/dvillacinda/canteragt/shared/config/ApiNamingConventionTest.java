package com.dvillacinda.canteragt.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.exception.ErrorResponse;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;

/**
 * Guards the API naming convention for every endpoint of every module: each request/response body (and every
 * nested type) is serialized in snake_case, and the OpenAPI schema (Swagger examples) documents the same names.
 * Runs without Spring context or Docker.
 */
class ApiNamingConventionTest {

    private static final String BASE_PACKAGE = "com.dvillacinda.canteragt";
    private static final Pattern SNAKE_CASE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    private static final ObjectMapper JACKSON3 = JsonMapper.builder().build();

    private static Set<Class<?>> apiTypes;

    @BeforeAll
    static void collectApiTypes() throws ClassNotFoundException {
        OpenApiConfig.registerJackson3Naming(Json.mapper());
        OpenApiConfig.registerJackson3Naming(Json31.mapper());

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> types = new LinkedHashSet<>();
        int controllers = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            controllers++;
            for (Method method : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                collect(method.getGenericReturnType(), types);
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        collect(parameter.getParameterizedType(), types);
                    }
                }
            }
        }
        collect(ErrorResponse.class, types);
        assertTrue(controllers > 10, "controller scan found only " + controllers + " controllers");
        apiTypes = types;
    }

    @Test
    void everyApiBodyIsSerializedInSnakeCase() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : apiTypes) {
            for (String name : jackson3PropertyNames(type)) {
                if (!SNAKE_CASE.matcher(name).matches()) {
                    violations.add(type.getName() + "." + name);
                }
            }
        }
        assertTrue(violations.isEmpty(), "Properties not in snake_case: " + violations);
    }

    @Test
    void openApiSchemasDocumentTheSameNamesTheApiUses() {
        assertFalse(apiTypes.isEmpty());
        for (boolean openApi31 : new boolean[] { false, true }) {
            ModelConverters converters = ModelConverters.getInstance(openApi31);
            for (Class<?> type : apiTypes) {
                Schema<?> schema = converters.resolveAsResolvedSchema(new AnnotatedType(type)).schema;
                Map<String, Schema> properties = schema == null ? null : schema.getProperties();
                Set<String> documented = properties == null ? Set.of() : new TreeSet<>(properties.keySet());
                assertEquals(jackson3PropertyNames(type), documented,
                        "OpenAPI " + (openApi31 ? "3.1" : "3.0") + " schema of " + type.getName()
                                + " does not match its JSON body");
            }
        }
    }

    private static Set<String> jackson3PropertyNames(Class<?> type) {
        Set<String> names = new TreeSet<>();
        JACKSON3.acceptJsonFormatVisitor(type, new JsonFormatVisitorWrapper.Base() {
            @Override
            public JsonObjectFormatVisitor expectObjectFormat(JavaType javaType) {
                return new JsonObjectFormatVisitor.Base(getContext()) {
                    @Override
                    public void property(BeanProperty writer) {
                        names.add(writer.getName());
                    }

                    @Override
                    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) {
                        names.add(name);
                    }

                    @Override
                    public void optionalProperty(BeanProperty writer) {
                        names.add(writer.getName());
                    }

                    @Override
                    public void optionalProperty(String name, JsonFormatVisitable handler,
                            JavaType propertyTypeHint) {
                        names.add(name);
                    }
                };
            }
        });
        return names;
    }

    /** Collects every project type reachable from {@code type}: generic arguments, record components, fields. */
    private static void collect(Type type, Set<Class<?>> types) {
        if (type instanceof ParameterizedType parameterized) {
            collect(parameterized.getRawType(), types);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, types);
            }
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collect(bound, types);
            }
        } else if (type instanceof Class<?> cls) {
            if (cls.isArray()) {
                collect(cls.getComponentType(), types);
                return;
            }
            if (cls.isEnum() || cls.getPackageName().indexOf(BASE_PACKAGE) != 0 || !types.add(cls)) {
                return;
            }
            if (cls.isRecord()) {
                for (RecordComponent component : cls.getRecordComponents()) {
                    collect(component.getGenericType(), types);
                }
            } else {
                for (Field field : cls.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        collect(field.getGenericType(), types);
                    }
                }
            }
        }
    }
}
