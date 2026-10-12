package com.dvillacinda.canteragt.evaluation_template.entity;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.dvillacinda.canteragt.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evaluation_criteria")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationCriterionEntity extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "criterion_id")
    private UUID criterionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private EvaluationTemplateEntity template;

    @Column(name = "dimension_code", nullable = false, length = 30)
    private String dimensionCode;
    @Column(nullable = false, length = 60)
    private String code;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal weight;
    @Column(name = "min_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal minScore;
    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
    @Column(name = "metric_type", nullable = false, length = 30)
    private String metricType;
    @Column(nullable = false, length = 20)
    private String unit;
    @Column(name = "better_direction", nullable = false, length = 10)
    private String betterDirection;
    @Column(name = "attempts_count")
    private Integer attemptsCount;
    @Column(name = "attempt_aggregation", nullable = false, length = 20)
    private String attemptAggregation;
    @Column(name = "min_opportunities")
    private Integer minOpportunities;
    @Column(name = "scale_min")
    private Integer scaleMin;
    @Column(name = "scale_max")
    private Integer scaleMax;
    @Column(name = "comment_required", nullable = false)
    private boolean commentRequired;
    @Column(name = "is_active_by_default", nullable = false)
    private boolean activeByDefault;
    @Column(name = "is_scored", nullable = false)
    private boolean scored;
    @Column(name = "laterality_mode", nullable = false, length = 10)
    private String lateralityMode;
    @Column(name = "scoring_method", nullable = false, length = 40)
    private String scoringMethod;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scoring_config", nullable = false, columnDefinition = "jsonb")
    private String scoringConfig;
}
