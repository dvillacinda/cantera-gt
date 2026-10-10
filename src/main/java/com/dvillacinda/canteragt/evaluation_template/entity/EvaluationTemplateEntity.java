package com.dvillacinda.canteragt.evaluation_template.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * EvaluationTemplateEntity
 */
@Entity
@Table(name = "evaluation_templates")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder 
public class EvaluationTemplateEntity extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column (name = "status", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private EvaluationTemplateStatus status;

    @Column (name = "Code", nullable = false, length = 60)
    private String code;

    @Column (name="objective", nullable = false, length = 255)
    private String objective;

    @Column (name = "instructions", nullable = false, length = 255)
    private String instructions;

    @Column (name = "is_session_integrated", nullable = false)
    private Boolean isSessionIntegrated;

    @Column (name = "group_size_min", nullable = false)
    private Integer groupSizeMin;

    @Column (name = "group_size_max", nullable = false)
    private Integer groupSizeMax;

    @Column (name = "frequency_min_days", nullable = false)
    private Integer frequencyMinDays;

    @Column (name = "frequency_max_days", nullable = false)
    private Integer frequencyMaxDays;
    
    @Column (name = "validity_days", nullable = false)
    private Integer validityDays;

    @Column (name = "score_aggregation", nullable = false)
    private String scoreAggregation;

    @Column (name = "is_official", nullable = false)
    private Boolean isOfficial;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    private AcademyEntity academy;

    @Builder.Default
    @OneToMany(mappedBy = "parentTemplate")
    private Set<EvaluationTemplateEntity> childTemplates = new HashSet<>();


}