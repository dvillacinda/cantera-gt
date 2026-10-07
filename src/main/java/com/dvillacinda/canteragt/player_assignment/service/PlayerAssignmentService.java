package com.dvillacinda.canteragt.player_assignment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentCreateRequest;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentResponse;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentUpdateRequest;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.player_assignment.mapper.PlayerAssignmentMapper;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Staff methods first resolve the academy the caller may act on, then only touch rows of that academy.
 * Player ("my") methods resolve the caller's own player profile and only touch rows of that player, so a
 * player never sees teammates of the same category or academy.
 * Resources outside the caller's scope answer 404 (not 403) so their existence is not disclosed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerAssignmentService {
    private final PlayerAssignmentRepository playerAssignmentRepository;
    private final AcademyCategoryRepository academyCategoryRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAssignmentMapper playerAssignmentMapper;
    private final AcademyAccessService academyAccessService;

    public PlayerAssignmentResponse createPlayerAssignment(UUID requestedAcademyId,
            PlayerAssignmentCreateRequest request, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);

        AcademyCategoryEntity academyCategory = findAcademyCategoryInAcademy(request.academyCategoryId(), academyId);
        PlayerEntity player = findPlayer(request.playerId());
        requireValidDateRange(request.startDate(), request.endDate());

        if (request.status() == PlayerAssignmentStatus.ACTIVE
                && playerAssignmentRepository.existsByPlayer_PlayerIdAndAcademyCategory_AcademyCategoryIdAndStatus(
                        player.getPlayerId(), academyCategory.getAcademyCategoryId(), PlayerAssignmentStatus.ACTIVE)) {
            throw new ConflictException("Player already has an active assignment in this academy category");
        }

        return playerAssignmentMapper.toResponse(playerAssignmentRepository
                .save(playerAssignmentMapper.toEntity(request, player, academyCategory)));
    }

    @Transactional(readOnly = true)
    public PlayerAssignmentResponse getPlayerAssignmentById(UUID playerAssignmentId, UUID requestedAcademyId,
            Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyReadAccess(authentication, requestedAcademyId);
        return playerAssignmentMapper.toResponse(findPlayerAssignmentInAcademy(playerAssignmentId, academyId));
    }

    @Transactional(readOnly = true)
    public List<PlayerAssignmentResponse> getPlayerAssignments(UUID requestedAcademyId,
            Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyReadAccess(authentication, requestedAcademyId);
        return playerAssignmentRepository.findByAcademyCategory_Academy_AcademyId(academyId).stream()
                .map(playerAssignmentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerAssignmentResponse getMyPlayerAssignmentById(UUID playerAssignmentId,
            Authentication authentication) {
        UUID playerId = academyAccessService.requireAuthenticatedPlayerId(authentication);
        return playerAssignmentMapper.toResponse(playerAssignmentRepository
                .findByPlayerAssignmentIdAndPlayer_PlayerId(playerAssignmentId, playerId)
                .orElseThrow(() -> new NotFoundException(
                        "Player assignment with id " + playerAssignmentId + " not found")));
    }

    @Transactional(readOnly = true)
    public List<PlayerAssignmentResponse> getMyPlayerAssignments(Authentication authentication) {
        UUID playerId = academyAccessService.requireAuthenticatedPlayerId(authentication);
        return playerAssignmentRepository.findByPlayer_PlayerIdOrderByStartDateDesc(playerId).stream()
                .map(playerAssignmentMapper::toResponse)
                .toList();
    }

    public PlayerAssignmentResponse updatePlayerAssignment(UUID playerAssignmentId, UUID requestedAcademyId,
            PlayerAssignmentUpdateRequest request, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);
        PlayerAssignmentEntity existing = findPlayerAssignmentInAcademy(playerAssignmentId, academyId);

        if (request.academyCategoryId() != null) {
            // Prevents moving an assignment into a category of another academy.
            existing.setAcademyCategory(findAcademyCategoryInAcademy(request.academyCategoryId(), academyId));
        }
        if (request.playerId() != null) {
            existing.setPlayer(findPlayer(request.playerId()));
        }
        if (request.startDate() != null) {
            existing.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            existing.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            existing.setStatus(request.status());
        }
        requireValidDateRange(existing.getStartDate(), existing.getEndDate());

        return playerAssignmentMapper.toResponse(playerAssignmentRepository.save(existing));
    }

    public PlayerAssignmentResponse updatePlayerAssignmentStatusById(UUID playerAssignmentId,
            UUID requestedAcademyId, PlayerAssignmentStatus status, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);
        PlayerAssignmentEntity existing = findPlayerAssignmentInAcademy(playerAssignmentId, academyId);
        existing.setStatus(status);
        return playerAssignmentMapper.toResponse(playerAssignmentRepository.save(existing));
    }

    private PlayerAssignmentEntity findPlayerAssignmentInAcademy(UUID playerAssignmentId, UUID academyId) {
        return playerAssignmentRepository
                .findByPlayerAssignmentIdAndAcademyCategory_Academy_AcademyId(playerAssignmentId, academyId)
                .orElseThrow(() -> new NotFoundException(
                        "Player assignment with id " + playerAssignmentId + " not found"));
    }

    private AcademyCategoryEntity findAcademyCategoryInAcademy(UUID academyCategoryId, UUID academyId) {
        return academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(academyCategoryId, academyId)
                .orElseThrow(() -> new NotFoundException(
                        "AcademyCategory with id " + academyCategoryId + " not found"));
    }

    private PlayerEntity findPlayer(UUID playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player with id " + playerId + " not found"));
    }

    private void requireValidDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
    }
}
