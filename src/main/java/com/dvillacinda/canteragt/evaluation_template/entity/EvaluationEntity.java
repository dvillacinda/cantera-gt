package com.dvillacinda.canteragt.evaluation_template.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
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

@Entity @Table(name = "evaluations") @Getter @Setter @NoArgsConstructor
public class EvaluationEntity extends BaseEntity {
    @Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "evaluation_id") private UUID evaluationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "academy_category_id", nullable = false)
    private AcademyCategoryEntity academyCategory;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "template_id", nullable = false)
    private EvaluationTemplateEntity template;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "evaluator_coach_id", nullable = false)
    private CoachEntity evaluator;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "session_id", referencedColumnName = "session_id")
    private TrainingSessionEntity trainingSession;
    @Column(name = "evaluation_date", nullable = false) private LocalDateTime evaluationDate;
    @Column(name = "template_score", precision = 6, scale = 2) private BigDecimal templateScore;
    @Column(columnDefinition = "text") private String strengths;
    @Column(name = "improvement_areas", columnDefinition = "text") private String improvementAreas;
    @Column(name = "technical_comment", columnDefinition = "text") private String technicalComment;
    @Column(name = "physical_comment", columnDefinition = "text") private String physicalComment;
    @Column(name = "tactical_comment", columnDefinition = "text") private String tacticalComment;
    @Column(name = "psychological_comment", columnDefinition = "text") private String psychologicalComment;
    @Column(name = "social_comment", columnDefinition = "text") private String socialComment;
    @Column(name = "general_comment", columnDefinition = "text") private String generalComment;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "valid_until") private LocalDate validUntil;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "scoring_status", nullable = false, length = 30) private String scoringStatus = "PENDING";
    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluationResultEntity> results = new ArrayList<>();
}
