package com.dvillacinda.canteragt.training_session.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.json.JsonMapper;

class SessionEvaluationEventRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void createRequiresTemplateAndAcceptsOmittedMetrics() {
        assertEquals(1, validator.validate(new SessionEvaluationEventCreateRequest(null, null)).size());
        assertTrue(validator.validate(new SessionEvaluationEventCreateRequest(UUID.randomUUID(), null)).isEmpty());
    }

    @Test
    void metricListsAcceptOneToThreeIdsWithoutNulls() {
        UUID template = UUID.randomUUID();
        for (int size = 1; size <= 3; size++) {
            List<UUID> ids = ids(size);
            assertTrue(validator.validate(new SessionEvaluationEventCreateRequest(template, ids)).isEmpty());
            assertTrue(validator.validate(new SessionEventMetricsUpdateRequest(ids)).isEmpty());
        }
        for (List<UUID> invalid : List.of(ids(0), ids(4), new ArrayList<>(Collections.singletonList((UUID) null)))) {
            assertEquals(1, validator.validate(new SessionEvaluationEventCreateRequest(template, invalid)).size());
            assertEquals(1, validator.validate(new SessionEventMetricsUpdateRequest(invalid)).size());
        }
        assertEquals(1, validator.validate(new SessionEventMetricsUpdateRequest(null)).size());
    }

    @Test
    void requestsUseSnakeCase() {
        UUID template = UUID.randomUUID();
        UUID metric = UUID.randomUUID();

        var create = jsonMapper.readValue("{\"template_id\":\"" + template + "\",\"metric_criterion_ids\":[\""
                + metric + "\"]}", SessionEvaluationEventCreateRequest.class);
        var withoutMetrics = jsonMapper.readValue("{\"template_id\":\"" + template + "\"}",
                SessionEvaluationEventCreateRequest.class);
        var update = jsonMapper.readValue("{\"metric_criterion_ids\":[\"" + metric + "\"]}",
                SessionEventMetricsUpdateRequest.class);

        assertEquals(template, create.templateId());
        assertEquals(List.of(metric), create.metricCriterionIds());
        assertNull(withoutMetrics.metricCriterionIds());
        assertEquals(List.of(metric), update.metricCriterionIds());
    }

    private static List<UUID> ids(int size) {
        return IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList();
    }
}
