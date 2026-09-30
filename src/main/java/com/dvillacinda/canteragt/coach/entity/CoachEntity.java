package com.dvillacinda.canteragt.coach.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
public class CoachEntity {
    @Id 
    @GeneratedValue (strategy = GenerationType.UUID) 
    private UUID coachId;

    @OneToOne(optional = false)
    @JoinColumn (name = "user_id", referencedColumnName = "user_id", nullable = false, unique = true) 
    private UserEntity user;

    @Column (name = "first_name", nullable = false) 
    private String firstName;

    @Column (name = "last_name", nullable = false) 
    private String lastName;

    @Column (name = "created_at", nullable = false, updatable = false) 
    private LocalDateTime createdAt;

    @Column (name = "updated_at", nullable = false) 
    private LocalDateTime updatedAt;

    @PrePersist 
    void setCreationDates() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate 
    void setUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }

}
