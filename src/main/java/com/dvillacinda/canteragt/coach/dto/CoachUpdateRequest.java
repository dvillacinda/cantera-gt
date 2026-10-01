package com.dvillacinda.canteragt.coach.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH semantics: null fields are left unchanged; provided fields must not be blank.
 */

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachUpdateRequest(
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String firstName,
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String lastName
) {

}
