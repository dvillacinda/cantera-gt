package com.dvillacinda.canteragt.player_assignment.entity;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
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
@Table (name = "player_assignments")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class PlayerAssignmentEntity extends BaseEntity {
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "player_assignment_id")
    private UUID playerAssignmentId;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "player_id", referencedColumnName = "player_id", nullable = false)
    private PlayerEntity player;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "academy_category_id", referencedColumnName = "academy_category_id", nullable = false)
    private AcademyCategoryEntity academyCategory;

    @Column (name = "status", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private PlayerAssignmentStatus status;

    @Column (name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column (name = "end_date")
    private LocalDate endDate;


}
