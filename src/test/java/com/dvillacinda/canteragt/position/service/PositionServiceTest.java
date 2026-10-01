package com.dvillacinda.canteragt.position.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.repository.PositionRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {
    @Mock
    private PositionRepository positionRepository;

    @InjectMocks
    private PositionService positionService;

    @Test
    void getEntitiesByIdsDeduplicatesAndReturnsPositions() {
        UUID id = UUID.randomUUID();
        PositionEntity position = PositionEntity.builder().positionId(id).build();
        when(positionRepository.findAllById(Set.of(id))).thenReturn(List.of(position));

        assertEquals(Set.of(position), positionService.getEntitiesByIds(List.of(id, id)));
        verify(positionRepository).findAllById(Set.of(id));
    }

    @Test
    void getEntitiesByIdsListsMissingIds() {
        UUID missing = UUID.randomUUID();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> positionService.getEntitiesByIds(List.of(missing)));

        assertEquals("Positions with ids [" + missing + "] not found", exception.getMessage());
    }

    @Test
    void getEntityByIdListsMissingId() {
        UUID missing = UUID.randomUUID();
        when(positionRepository.findById(missing)).thenReturn(Optional.empty());

        assertEquals("Position with id " + missing + " not found",
                assertThrows(NotFoundException.class, () -> positionService.getEntityById(missing)).getMessage());
    }
}
