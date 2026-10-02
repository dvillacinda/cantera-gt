package com.dvillacinda.canteragt.academy_admin.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table (name = "academy_admin")
@NoArgsConstructor 
@AllArgsConstructor 
@Getter 
@Setter 
@Builder 
public class AcademyAdminEntity extends BaseEntity {
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "academy_admin_id")
    private UUID academyAdminId;

    @Builder.Default
    @ManyToMany (mappedBy = "academyAdmins")
    private Set<AcademyEntity> academies = new HashSet<>();

    @Builder.Default
    @ManyToMany (mappedBy = "academyAdmins")
    private Set<UserEntity> users = new HashSet<>();

    @Column (name = "status", nullable = false)
    private Status status;

    
}
