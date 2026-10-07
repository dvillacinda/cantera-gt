package com.dvillacinda.canteragt.player_assignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentCreateRequest;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentUpdateRequest;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.player_assignment.mapper.PlayerAssignmentMapper;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;

@ExtendWith(MockitoExtension.class)
class PlayerAssignmentServiceTest {
    private static final UUID OWN_ACADEMY = UUID.randomUUID();
    private static final UUID OTHER_ACADEMY = UUID.randomUUID();
    private static final UUID OWN_PLAYER = UUID.randomUUID();

    @Mock
    private PlayerAssignmentRepository playerAssignmentRepository;
    @Mock
    private AcademyCategoryRepository academyCategoryRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private AcademyAccessService academyAccessService;
    @Mock
    private Authentication authentication;
    @Spy
    private PlayerAssignmentMapper playerAssignmentMapper = new PlayerAssignmentMapper();

    @InjectMocks
    private PlayerAssignmentService playerAssignmentService;

    private static PlayerAssignmentEntity assignmentOf(UUID playerId, UUID academyId) {
        AcademyCategoryEntity category = AcademyCategoryEntity.builder().academyCategoryId(UUID.randomUUID())
                .academy(AcademyEntity.builder().academyId(academyId).build()).build();
        return PlayerAssignmentEntity.builder().playerAssignmentId(UUID.randomUUID())
                .academyCategory(category)
                .player(PlayerEntity.builder().playerId(playerId).build())
                .startDate(LocalDate.of(2026, 1, 1)).status(PlayerAssignmentStatus.ACTIVE).build();
    }

    @Test
    void playerListsOnlyOwnAssignments() {
        PlayerAssignmentEntity own = assignmentOf(OWN_PLAYER, OWN_ACADEMY);
        when(academyAccessService.requireAuthenticatedPlayerId(authentication)).thenReturn(OWN_PLAYER);
        when(playerAssignmentRepository.findByPlayer_PlayerIdOrderByStartDateDesc(OWN_PLAYER))
                .thenReturn(List.of(own));

        var response = playerAssignmentService.getMyPlayerAssignments(authentication);

        assertEquals(1, response.size());
        assertEquals(OWN_PLAYER, response.getFirst().playerId());
        verify(playerAssignmentRepository, never()).findByAcademyCategory_Academy_AcademyId(any());
    }

    @Test
    void playerCannotReadAssignmentOfAnotherPlayer() {
        UUID teammateAssignmentId = UUID.randomUUID();
        when(academyAccessService.requireAuthenticatedPlayerId(authentication)).thenReturn(OWN_PLAYER);
        when(playerAssignmentRepository.findByPlayerAssignmentIdAndPlayer_PlayerId(teammateAssignmentId, OWN_PLAYER))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> playerAssignmentService.getMyPlayerAssignmentById(teammateAssignmentId, authentication));
    }

    @Test
    void nonPlayerIsDeniedBeforeQuerying() {
        when(academyAccessService.requireAuthenticatedPlayerId(authentication))
                .thenThrow(new UnauthorizedAccessException("Player role is required"));

        assertThrows(UnauthorizedAccessException.class,
                () -> playerAssignmentService.getMyPlayerAssignments(authentication));
        verifyNoInteractions(playerAssignmentRepository);
    }

    @Test
    void staffReadOfAssignmentFromAnotherAcademyIsNotFound() {
        UUID foreignAssignmentId = UUID.randomUUID();
        when(academyAccessService.requireAcademyReadAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(playerAssignmentRepository.findByPlayerAssignmentIdAndAcademyCategory_Academy_AcademyId(
                foreignAssignmentId, OWN_ACADEMY)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> playerAssignmentService
                .getPlayerAssignmentById(foreignAssignmentId, OWN_ACADEMY, authentication));
    }

    @Test
    void staffListingAnotherAcademyIsDeniedBeforeQuerying() {
        when(academyAccessService.requireAcademyReadAccess(authentication, OTHER_ACADEMY))
                .thenThrow(new UnauthorizedAccessException("No active assignment for the selected academy"));

        assertThrows(UnauthorizedAccessException.class,
                () -> playerAssignmentService.getPlayerAssignments(OTHER_ACADEMY, authentication));
        verifyNoInteractions(playerAssignmentRepository);
    }

    @Test
    void createRejectsAcademyCategoryOfAnotherAcademy() {
        UUID foreignCategoryId = UUID.randomUUID();
        when(academyAccessService.requireAcademyAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(foreignCategoryId, OWN_ACADEMY))
                .thenReturn(Optional.empty());

        var request = new PlayerAssignmentCreateRequest(UUID.randomUUID(), foreignCategoryId,
                PlayerAssignmentStatus.ACTIVE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThrows(NotFoundException.class,
                () -> playerAssignmentService.createPlayerAssignment(OWN_ACADEMY, request, authentication));
        verify(playerAssignmentRepository, never()).save(any());
    }

    @Test
    void updateRejectsEndDateBeforeStartDate() {
        PlayerAssignmentEntity own = assignmentOf(OWN_PLAYER, OWN_ACADEMY);
        when(academyAccessService.requireAcademyAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(playerAssignmentRepository.findByPlayerAssignmentIdAndAcademyCategory_Academy_AcademyId(
                own.getPlayerAssignmentId(), OWN_ACADEMY)).thenReturn(Optional.of(own));

        var request = new PlayerAssignmentUpdateRequest(null, null, null, null, LocalDate.of(2025, 1, 1));

        assertThrows(BadRequestException.class, () -> playerAssignmentService
                .updatePlayerAssignment(own.getPlayerAssignmentId(), OWN_ACADEMY, request, authentication));
        verify(playerAssignmentRepository, never()).save(any());
    }
}
