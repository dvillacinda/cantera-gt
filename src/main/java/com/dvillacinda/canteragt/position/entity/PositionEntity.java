package com.dvillacinda.canteragt.position.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "positions")
@Getter 
@Setter 
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class PositionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "position_id")
    private UUID positionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false)
    private PositionCode positionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "line", nullable = false)
    private PositionLine positionLine;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Builder.Default
    @ManyToMany(mappedBy = "secondaryPositions")
    private Set<PlayerEntity> players = new HashSet<>();
}
