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

@Entity @Table(name = "evaluation_observations") @Getter @Setter @NoArgsConstructor
public class EvaluationObservationEntity {
    @Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "observation_id") private UUID observationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "evaluation_result_id", nullable = false)
    private EvaluationResultEntity result;
    @Column(name = "sequence", nullable = false) private Integer sequence;
    @Column(precision = 8, scale = 3) private BigDecimal value;
    private Integer level;
    @Column(name = "is_success") private Boolean success;
    @Column(name = "is_valid", nullable = false) private boolean valid = true;
    @Column(length = 255) private String note;
}
