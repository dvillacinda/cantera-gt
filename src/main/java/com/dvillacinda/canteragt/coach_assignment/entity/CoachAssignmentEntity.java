package com.dvillacinda.canteragt.coach_assignment.entity;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
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

/**
 * CoachAssignmentEntity
 */
@Entity 
@Table (name = "coach_assignments")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class CoachAssignmentEntity extends BaseEntity{

    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "coach_assignment_id")
    private UUID coachAssignmentId;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "coach_id", referencedColumnName = "coach_id", nullable = false)
    private CoachEntity coach;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "academy_category_id", referencedColumnName = "academy_category_id", nullable = false)
    private AcademyCategoryEntity academyCategory;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "coach_role_id", referencedColumnName = "coach_role_id", nullable = false)
    private CoachRoleEntity coachRole;

    @Column (name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column (name = "end_date")
    private LocalDate endDate;

    @Column (name = "status", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private CoachAssignmentStatus status;
}