package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;

import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;

public record TrainingSessionFilter(
        TrainingSessionStatus status,
        OffsetDateTime from,
        OffsetDateTime to) {
}
