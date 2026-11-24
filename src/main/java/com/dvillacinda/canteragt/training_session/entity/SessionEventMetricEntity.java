package com.dvillacinda.canteragt.training_session.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Active metric of an evaluation event (V3 {@code session_event_metrics}).
 *
 * <p>The table has {@code created_at} but no {@code updated_at}, so this entity does not extend {@code BaseEntity}.
 * {@code template_id} is kept as a plain column: V3's composite FKs use it to guarantee that the metric belongs to
 * the event's template, and a deferred trigger allows at most 3 scored metrics per event.
 */
@Entity
@Table(name = "session_event_metrics")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SessionEventMetricEntity {
    @EmbeddedId
    private SessionEventMetricId id;

    @MapsId("criterionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", referencedColumnName = "criterion_id", nullable = false)
    private EvaluationCriterionEntity criterion;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
