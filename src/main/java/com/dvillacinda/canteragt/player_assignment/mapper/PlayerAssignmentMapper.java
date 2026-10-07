package com.dvillacinda.canteragt.player_assignment.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentCreateRequest;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentResponse;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;

/** Pure mapping: related entities are resolved (and tenant-checked) by the service, never here. */
@Component
public class PlayerAssignmentMapper {

    public PlayerAssignmentEntity toEntity(PlayerAssignmentCreateRequest request, PlayerEntity player,
            AcademyCategoryEntity academyCategory) {
        return PlayerAssignmentEntity.builder()
                .player(player)
                .academyCategory(academyCategory)
                .status(request.status())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
    }

    public PlayerAssignmentResponse toResponse(PlayerAssignmentEntity entity) {
        return new PlayerAssignmentResponse(
                entity.getPlayerAssignmentId(),
                entity.getPlayer().getPlayerId(),
                entity.getAcademyCategory().getAcademyCategoryId(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
