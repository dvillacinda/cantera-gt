package com.dvillacinda.canteragt.academy_category.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;

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
@Entity 
@Table (name = "academy_categories")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class AcademyCategoryEntity extends BaseEntity {
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "academy_category_id")
    private UUID academyCategoryId;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "academy_id", referencedColumnName = "academy_id", nullable = false)
    private AcademyEntity academy;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "category_id", referencedColumnName = "category_id", nullable = false)
    private CategoryEntity category;

    @ManyToOne (fetch = FetchType.LAZY , optional = false)
    @JoinColumn (name = "season_id", referencedColumnName = "season_id", nullable = false)
    private SeasonEntity season;

    @Enumerated (EnumType.STRING)
    @Column (name = "status", nullable = false)
    private AcademyCategoryStatus status;

}
