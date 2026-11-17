package com.dvillacinda.canteragt.training_session.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionResponse;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

class TrainingSessionRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void createRequiresNonBlankNameDateAndLimitsTextLengths() {
        TrainingSessionCreateRequest invalid = new TrainingSessionCreateRequest(
                " ", null, null, "n".repeat(2001));

        assertEquals(3, validator.validate(invalid).size());
    }

    @Test
    void updateAllowsPartialFieldsButLimitsFieldLengths() {
        TrainingSessionUpdateRequest valid = new TrainingSessionUpdateRequest(null, null, null, null);
        TrainingSessionUpdateRequest invalid = new TrainingSessionUpdateRequest(
                "n".repeat(151), null, null, "n".repeat(2001));

        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(2, validator.validate(invalid).size());
    }

    @Test
    void requestUsesSnakeCaseAndDoesNotAcceptCreatorId() {
        UUID locationId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        TrainingSessionCreateRequest request = jsonMapper.readValue("""
                {
                  "name": "Practice",
                  "session_date": "2026-11-17T10:00:00-06:00",
                  "location_id": "%s",
                  "notes": "Passing",
                  "created_by_coach_id": "%s"
                }
                """.formatted(locationId, creatorId), TrainingSessionCreateRequest.class);

        assertEquals(locationId, request.locationId());
        assertTrue(request.sessionDate().isEqual(OffsetDateTime.parse("2026-11-17T10:00:00-06:00")));
        assertFalse(jsonMapper.writeValueAsString(request).contains("created_by_coach_id"));
    }

    @Test
    void responseUsesSnakeCaseAndKeepsGuatemalaOffset() {
        TrainingSessionResponse response = new TrainingSessionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Practice",
                OffsetDateTime.parse("2026-11-17T10:00:00-06:00"),
                TrainingSessionStatus.PLANNED,
                null,
                null,
                null,
                null,
                null,
                0L);

        String json = jsonMapper.writeValueAsString(response);

        assertTrue(json.contains("\"session_id\""), json);
        assertTrue(json.contains("\"academy_category_id\""), json);
        assertTrue(json.contains("\"session_date\":\"2026-11-17T10:00:00-06:00\""), json);
        assertFalse(json.contains("\"sessionDate\""), json);
    }
}
