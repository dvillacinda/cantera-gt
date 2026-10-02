package com.dvillacinda.canteragt.academy.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;

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
@Table (name = "academies")
@AllArgsConstructor 
@NoArgsConstructor 
@Getter 
@Setter 
@Builder 
public class AcademyEntity extends BaseEntity {
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "academy_id")
    private UUID academyId;

    @Column (name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated (EnumType.STRING)
    @Column (name = "status", nullable = false)
    Status status;

}
