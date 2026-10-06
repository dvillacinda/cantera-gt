package com.dvillacinda.canteragt.season.entity;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.season.enums.SeasonStatus;
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
@Table (name = "seasons")
@AllArgsConstructor 
@NoArgsConstructor 
@Getter 
@Setter 
@Builder 
public class SeasonEntity extends BaseEntity{
    @Id 
    @GeneratedValue 
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    @Column (name = "season_id")
    UUID seasonId;

    @Column (name = "name", nullable = false, length = 150)
    String name;

    @Column (name = "status", nullable = false)
    @Enumerated (EnumType.STRING)
    SeasonStatus status;

    @Column (name = "start_date", nullable = false)
    LocalDate startDate;

    @Column (name = "end_date", nullable = false)
    LocalDate endDate;
}
