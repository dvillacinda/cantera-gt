package com.dvillacinda.canteragt.evaluation_template.entity;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

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
@Table(name = "metric_scale_levels")
@Getter
@Setter
@NoArgsConstructor
public class MetricScaleLevelEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "scale_level_id")
    private UUID scaleLevelId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private EvaluationCriterionEntity criterion;
    @Column(nullable = false)
    private Integer level;
    @Column(nullable = false, length = 80)
    private String label;
    @Column(length = 500)
    private String descriptor;
    @Column(precision = 5, scale = 2)
    private BigDecimal score;
}
