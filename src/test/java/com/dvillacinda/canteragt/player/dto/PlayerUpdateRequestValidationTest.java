package com.dvillacinda.canteragt.player.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PlayerUpdateRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void rejectsNullSecondaryPositionId() {
        PlayerUpdateRequest request = new PlayerUpdateRequest(null, null, null, null,
                new java.util.HashSet<>(java.util.Arrays.asList(UUID.randomUUID(), null)));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void acceptsAtMostTenSecondaryPositions() {
        PlayerUpdateRequest valid = new PlayerUpdateRequest(null, null, null, null,
                Set.of(UUID.randomUUID(), UUID.randomUUID()));
        PlayerUpdateRequest invalid = new PlayerUpdateRequest(null, null, null, null,
                Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID()));

        assertTrue(validator.validate(valid).isEmpty());
        assertFalse(validator.validate(invalid).isEmpty());
    }
}
