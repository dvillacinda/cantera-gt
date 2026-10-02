package com.dvillacinda.canteragt.academy_admin.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table (
    name = "academy_admins",
    uniqueConstraints = @UniqueConstraint(name = "academy_admins_user_academy_uq", columnNames = {"user_id", "academy_id"})
)
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

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "user_id", referencedColumnName = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "academy_id", referencedColumnName = "academy_id", nullable = false)
    private AcademyEntity academy;

    @Enumerated (EnumType.STRING)
    @Column (name = "status", nullable = false)
    private Status status;

}
