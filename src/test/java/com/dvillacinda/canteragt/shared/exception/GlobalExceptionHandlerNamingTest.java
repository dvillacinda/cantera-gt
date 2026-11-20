package com.dvillacinda.canteragt.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.dvillacinda.canteragt.shared.dto.PageResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Error and page bodies use the same snake_case as the rest of the API. */
class GlobalExceptionHandlerNamingTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void validationPathsAreReportedWithTheJsonNamesTheClientSent() {
        assertEquals("name", GlobalExceptionHandler.toJsonPath("name"));
        assertEquals("player_ids", GlobalExceptionHandler.toJsonPath("playerIds"));
        assertEquals("player_ids[0]", GlobalExceptionHandler.toJsonPath("playerIds[0]"));
        assertEquals("results[2].raw_value", GlobalExceptionHandler.toJsonPath("results[2].rawValue"));
        assertEquals("results[0].observations[1].sequence",
                GlobalExceptionHandler.toJsonPath("results[0].observations[1].sequence"));
    }

    @Test
    void errorBodyUsesFieldErrorsInSnakeCase() {
        JsonNode body = mapper.valueToTree(new ErrorResponse(400, "Validation failed", "invalid",
                Map.of("player_ids", "must not be empty")));
        assertEquals("must not be empty", body.path("field_errors").path("player_ids").asString());
        assertFalse(body.has("fieldErrors"));

        JsonNode withoutFields = mapper.valueToTree(new ErrorResponse(404, "Not Found", "missing"));
        assertFalse(withoutFields.has("field_errors"), "null field_errors is omitted");
    }

    @Test
    void pageBodyUsesSnakeCaseMetadata() {
        JsonNode body = mapper.valueToTree(PageResponse.from(new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 3)));
        assertEquals(2, body.path("content").size());
        JsonNode page = body.path("page");
        assertEquals(2, page.path("size").asInt());
        assertEquals(0, page.path("number").asInt());
        assertEquals(3, page.path("total_elements").asInt());
        assertEquals(2, page.path("total_pages").asInt());
        assertTrue(!page.has("totalElements") && !body.has("pageable"));
    }
}
