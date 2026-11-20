package com.dvillacinda.canteragt.evaluation_template.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** API view of a template dimension; keeps the JPA embeddable out of the JSON contract. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TemplateDimensionResponse(String dimensionCode, boolean primary) {
}
