package com.dvillacinda.canteragt.coach.entity;

import java.util.UUID;

import com.dvillacinda.canteragt.shared.entity.BaseEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table (name = "coaches")
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class CoachEntity extends BaseEntity {
    @Id 
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID coachId;

    @OneToOne(optional = false)
    @JoinColumn (name = "user_id", referencedColumnName = "user_id", nullable = false, unique = true) 
    private UserEntity user;

    @Column (name = "first_name", nullable = false) 
    private String firstName;

    @Column (name = "last_name", nullable = false) 
    private String lastName;

}
