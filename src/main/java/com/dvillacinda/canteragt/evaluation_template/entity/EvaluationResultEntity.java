package com.dvillacinda.canteragt.evaluation_template.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "evaluation_results") @Getter @Setter @NoArgsConstructor
public class EvaluationResultEntity extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "evaluation_result_id") private UUID evaluationResultId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "evaluation_id", nullable = false)
    private EvaluationEntity evaluation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "criterion_id", nullable = false)
    private EvaluationCriterionEntity criterion;
    @Column(precision = 5, scale = 2) private BigDecimal score;
    @Column(length = 500) private String comment;
    @Column(name = "raw_value", precision = 8, scale = 3) private BigDecimal rawValue;
    private Integer successes;
    private Integer opportunities;
    @Column(name = "error_count") private Integer errorCount;
    private Integer level;
    @Column(nullable = false, length = 10) private String laterality = "NONE";
    @Column(name = "scoring_status", nullable = false, length = 30) private String scoringStatus = "PENDING";
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluationObservationEntity> observations = new ArrayList<>();
}
