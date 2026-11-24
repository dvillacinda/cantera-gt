package com.dvillacinda.canteragt.training_session.enums;

/**
 * Lifecycle of an evaluation event (ADR-0004, DP-2.2): {@code PLANNED → IN_PROGRESS → COMPLETED} and
 * {@code PLANNED | IN_PROGRESS → CANCELLED}. Closed events are immutable.
 */
public enum SessionEvaluationEventStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }
}
