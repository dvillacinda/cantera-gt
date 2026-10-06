package com.dvillacinda.canteragt.coach_role.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name = "coach_roles")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
public class CoachRoleEntity {

    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "coach_role_id")

    private UUID coachRoleId; 

    @Column (name = "name", nullable = false, length = 100)
    private String name;

    @Column (name = "code", nullable = false)
    @Enumerated (value = EnumType.STRING)
    private CoachRoleCode coachRoleCode;

    @Column (name = "description", nullable = false, length = 500)
    private String description;
    
}
