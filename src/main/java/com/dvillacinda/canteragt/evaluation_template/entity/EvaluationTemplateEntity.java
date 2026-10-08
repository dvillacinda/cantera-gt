package com.dvillacinda.canteragt.evaluation_template.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.category.entity.CategoryEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * EvaluationTemplateEntity
 */
@Entity 
@Table (name = "evaluation_templates")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class EvaluationTemplateEntity {
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "evaluation_template_id")
    private UUID evaluationTemplateId;

    @Column (name = "name", nullable = false, length = 100)
    private String name;

    // @ManyToOne (fetch = FetchType.LAZY, optional = false)
    // @JoinColumn (name = "category_id", referencedColumnName = "category_id", nullable = false)
    // private CategoryEntity category;

    // @ManyToOne (fetch = FetchType.LAZY, optional = false)
    // @JoinColumn (name = "season_id", referencedColumnName = "season_id", nullable = false)
    // private SeasonEntity season;


    
}