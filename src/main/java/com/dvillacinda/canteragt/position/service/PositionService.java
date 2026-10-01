package com.dvillacinda.canteragt.position.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.repository.PositionRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    @Transactional(readOnly = true)
    public Set<PositionEntity> getEntitiesByIds(Collection<UUID> positionIds) {
        Set<UUID> requestedIds = new LinkedHashSet<>(positionIds);
        Set<PositionEntity> positions = new LinkedHashSet<>(
                positionRepository.findAllById(new LinkedHashSet<>(requestedIds)));
        Set<UUID> foundIds = positions.stream().map((position) -> position.getPositionId()).collect(Collectors.toSet());
        requestedIds.removeAll(foundIds);
        if (!requestedIds.isEmpty()) {
            throw new NotFoundException("Positions with ids " + requestedIds + " not found");
        }
        return positions;
    }

    @Transactional(readOnly = true)
    public PositionEntity getEntityById(UUID positionId) {
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new NotFoundException("Position with id " + positionId + " not found"));
    }

}
