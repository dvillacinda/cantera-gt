package com.dvillacinda.canteragt.training_session.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.json.JsonMapper;

class SessionRosterRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void batchAcceptsUpToSixtyPlayersAndRejectsMore() {
        List<UUID> sixty = IntStream.range(0, 60).mapToObj(i -> UUID.randomUUID()).toList();
        List<UUID> sixtyOne = new ArrayList<>(sixty);
        sixtyOne.add(UUID.randomUUID());

        assertTrue(validator.validate(new SessionPlayersAddRequest(sixty, null)).isEmpty());
        assertEquals(1, validator.validate(new SessionPlayersAddRequest(sixtyOne, null)).size());
    }

    @Test
    void batchRejectsNullIds() {
        List<UUID> withNull = new ArrayList<>(Collections.singletonList(null));

        assertEquals(1, validator.validate(new SessionPlayersAddRequest(withNull, null)).size());
    }

    @Test
    void attendanceStatusIsRequired() {
        assertEquals(1, validator.validate(new AttendanceUpdateRequest(null)).size());
        assertTrue(validator.validate(new AttendanceUpdateRequest(AttendanceStatus.PRESENT)).isEmpty());
    }

    @Test
    void requestsUseSnakeCase() {
        UUID playerId = UUID.randomUUID();
        SessionPlayersAddRequest explicit = jsonMapper.readValue(
                "{\"player_ids\":[\"" + playerId + "\"]}", SessionPlayersAddRequest.class);
        SessionPlayersAddRequest allActive = jsonMapper.readValue(
                "{\"all_active\":true}", SessionPlayersAddRequest.class);
        AttendanceUpdateRequest attendance = jsonMapper.readValue(
                "{\"attendance_status\":\"JUSTIFIED\"}", AttendanceUpdateRequest.class);

        assertEquals(List.of(playerId), explicit.playerIds());
        assertFalse(explicit.isAllActive());
        assertTrue(allActive.isAllActive());
        assertEquals(AttendanceStatus.JUSTIFIED, attendance.attendanceStatus());
    }

    @Test
    void rosterResponseExposesOnlyStaffVisibleFieldsInSnakeCase() {
        SessionPlayerResponse response = new SessionPlayerResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Ana", "López", "GK", AttendanceStatus.PRESENT,
                OffsetDateTime.parse("2026-11-17T10:00:00-06:00"));

        String json = jsonMapper.writeValueAsString(response);

        assertTrue(json.contains("\"session_player_id\""));
        assertTrue(json.contains("\"first_name\":\"Ana\""));
        assertTrue(json.contains("\"principal_position\":\"GK\""));
        assertTrue(json.contains("\"attendance_status\":\"PRESENT\""));
        assertTrue(json.contains("-06:00"));
        assertFalse(json.contains("email"));
        assertFalse(json.contains("keycloak"));
        assertFalse(json.contains("username"));
    }
}
