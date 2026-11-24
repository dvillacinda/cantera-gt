package com.dvillacinda.canteragt.training_session.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evaluation block inside a training session (V3 {@code session_evaluation_events}, V9 integrity checks).
 *
 * <p>No {@code @Version}: every mutation runs under {@code TrainingSessionLock} (ADR-0004, DP-2.5).
 */
@Entity
@Table(name = "session_evaluation_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SessionEvaluationEventEntity extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "session_event_id")
    private UUID sessionEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", nullable = false)
    private TrainingSessionEntity trainingSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", referencedColumnName = "template_id", nullable = false)
    private EvaluationTemplateEntity template;

    @Column(name = "block_order", nullable = false)
    private Short blockOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionEvaluationEventStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
