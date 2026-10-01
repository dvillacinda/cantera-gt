package com.dvillacinda.canteragt.shared.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import com.dvillacinda.canteragt.user.dto.UserUpdateRequest;

import tools.jackson.databind.json.JsonMapper;

/** Spring Boot 4 uses Jackson 3: DTOs must use tools.jackson annotations or snake_case is silently ignored. */
class JsonNamingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void responsesAreSerializedInSnakeCase() {
        String json = mapper.writeValueAsString(
                new PositionResponse(UUID.randomUUID(), PositionCode.GK, PositionLine.GOALKEEPER, "Portero"));

        assertTrue(json.contains("\"position_code\":\"GK\""), json);
    }

    @Test
    void requestsAreReadInSnakeCase() {
        UserUpdateRequest request = mapper.readValue("{\"first_name\":\"Ana\"}", UserUpdateRequest.class);

        assertEquals("Ana", request.firstName());
    }
}
