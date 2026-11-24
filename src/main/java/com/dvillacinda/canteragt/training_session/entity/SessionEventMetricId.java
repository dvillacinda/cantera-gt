package com.dvillacinda.canteragt.training_session.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@code session_event_metrics}: (session_event_id, criterion_id). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SessionEventMetricId implements Serializable {
    @Column(name = "session_event_id")
    private UUID sessionEventId;

    @Column(name = "criterion_id")
    private UUID criterionId;
}
