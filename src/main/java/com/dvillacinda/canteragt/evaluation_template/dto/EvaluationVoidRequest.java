package com.dvillacinda.canteragt.evaluation_template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Voiding an evaluation (T4.5). The author comes from the JWT, never from the body. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationVoidRequest(@NotBlank @Size(max = 500) String reason) {
}
