package com.dvillacinda.canteragt.training_session.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.RejectedItemsException;
import com.dvillacinda.canteragt.training_session.dto.AttendanceUpdateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayersAddRequest;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionPlayerMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Session roster (convocatoria) and attendance.
 *
 * <p>Every mutation locks the session row ({@code SELECT … FOR UPDATE}) and bumps its {@code @Version}. Roster
 * changes therefore serialize with each other, and a concurrent {@code complete} either sees the change or fails
 * with 409 on its optimistic version check, so a session can never be completed while a PENDING player is being
 * added, or have its attendance edited after completion.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionRosterService {
    static final String NO_CURRENT_ASSIGNMENT = "NO_CURRENT_ASSIGNMENT_IN_CATEGORY";
    static final String ALREADY_IN_SESSION = "ALREADY_IN_SESSION";
    static final String DUPLICATED_IN_REQUEST = "DUPLICATED_IN_REQUEST";

    private final TrainingSessionRepository trainingSessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final PlayerAssignmentRepository playerAssignmentRepository;
    private final TrainingSessionAccessPolicy accessPolicy;
    private final SessionPlayerMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SessionPlayerResponse> list(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireReadAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        return sessionPlayerRepository.findRoster(sessionId).stream().map(mapper::toResponse).toList();
    }

    public List<SessionPlayerResponse> addPlayers(
            UUID sessionId, UUID academyId, SessionPlayersAddRequest request, Authentication authentication) {
        if (request.isAllActive() == request.hasPlayerIds()) {
            throw new BadRequestException("Send either a non-empty player_ids list or all_active=true, not both");
        }
        TrainingSessionEntity session = lockManageableSession(sessionId, academyId, authentication);
        requireRosterEditable(session);

        UUID academyCategoryId = session.getAcademyCategory().getAcademyCategoryId();
        LocalDate today = LocalDate.now(clock);
        Set<UUID> alreadyInSession = new HashSet<>(sessionPlayerRepository.findPlayerIdsBySession(sessionId));

        List<UUID> toAdd = request.isAllActive()
                ? playerAssignmentRepository.findCurrentPlayerIdsForCategory(academyCategoryId, today).stream()
                        .filter(playerId -> !alreadyInSession.contains(playerId))
                        .sorted()
                        .toList()
                : validateExplicitBatch(request.playerIds(), academyCategoryId, today, alreadyInSession);

        if (toAdd.isEmpty()) {
            return List.of();
        }
        List<SessionPlayerEntity> entries = new ArrayList<>(toAdd.size());
        for (UUID playerId : toAdd) {
            entries.add(SessionPlayerEntity.builder()
                    .trainingSession(session)
                    .player(entityManager.getReference(PlayerEntity.class, playerId))
                    .attendanceStatus(AttendanceStatus.PENDING)
                    .build());
        }
        try {
            sessionPlayerRepository.saveAllAndFlush(entries);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(session_id, player_id): only reachable if the row lock was bypassed; never a 500
            throw new ConflictException("One or more players are already in the session");
        }
        entityManager.clear();
        return sessionPlayerRepository.findRosterEntries(sessionId, toAdd).stream()
                .map(mapper::toResponse).toList();
    }

    public void removePlayer(UUID sessionId, UUID academyId, UUID playerId, Authentication authentication) {
        TrainingSessionEntity session = lockManageableSession(sessionId, academyId, authentication);
        requireRosterEditable(session);
        SessionPlayerEntity entry = findEntry(sessionId, playerId);
        if (entry.getAttendanceStatus() != AttendanceStatus.PENDING) {
            throw new ConflictException(
                    "Player attendance is already recorded (" + entry.getAttendanceStatus()
                            + "); only players still PENDING can be removed");
        }
        sessionPlayerRepository.delete(entry);
        flushVersioned();
    }

    public SessionPlayerResponse updateAttendance(
            UUID sessionId,
            UUID academyId,
            UUID playerId,
            AttendanceUpdateRequest request,
            Authentication authentication) {
        if (request.attendanceStatus() == AttendanceStatus.PENDING) {
            throw new BadRequestException("Attendance cannot be set back to PENDING");
        }
        TrainingSessionEntity session = lockManageableSession(sessionId, academyId, authentication);
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("Attendance can only be recorded while the session is IN_PROGRESS");
        }
        SessionPlayerEntity entry = findEntry(sessionId, playerId);
        entry.setAttendanceStatus(request.attendanceStatus());
        flushVersioned();
        return mapper.toResponse(entry);
    }

    private List<UUID> validateExplicitBatch(
            List<UUID> requested, UUID academyCategoryId, LocalDate today, Set<UUID> alreadyInSession) {
        Map<String, String> rejected = new LinkedHashMap<>();
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID playerId : requested) {
            if (!unique.add(playerId)) {
                rejected.put(playerId.toString(), DUPLICATED_IN_REQUEST);
            }
        }
        Set<UUID> eligible = new HashSet<>(
                playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(academyCategoryId, unique, today));
        boolean invalid = !rejected.isEmpty();
        for (UUID playerId : unique) {
            if (!eligible.contains(playerId)) {
                rejected.put(playerId.toString(), NO_CURRENT_ASSIGNMENT);
                invalid = true;
            } else if (alreadyInSession.contains(playerId)) {
                rejected.put(playerId.toString(), ALREADY_IN_SESSION);
            }
        }
        if (invalid) {
            throw new RejectedItemsException(HttpStatus.BAD_REQUEST,
                    "Batch rejected: no player was added. See rejected player ids", rejected);
        }
        if (!rejected.isEmpty()) {
            throw new RejectedItemsException(HttpStatus.CONFLICT,
                    "Batch rejected: some players are already in the session. No player was added", rejected);
        }
        return List.copyOf(unique);
    }

    private TrainingSessionEntity lockManageableSession(
            UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireManageAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        try {
            // Reload under a row lock so the status checks below see the latest committed state, then bump the
            // version so a concurrent optimistic transition (e.g. complete) conflicts instead of interleaving.
            entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
            entityManager.lock(session, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ConflictException("Training session was modified concurrently; reload and retry");
        }
        return session;
    }

    private void flushVersioned() {
        try {
            entityManager.flush();
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ConflictException("Training session was modified concurrently; reload and retry");
        }
    }

    private TrainingSessionEntity findSession(UUID sessionId, UUID academyId) {
        return trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(sessionId, academyId)
                .orElseThrow(() -> new NotFoundException("Training session not found in the selected academy"));
    }

    private SessionPlayerEntity findEntry(UUID sessionId, UUID playerId) {
        return sessionPlayerRepository.findRosterEntry(sessionId, playerId)
                .orElseThrow(() -> new NotFoundException("Player is not in this training session"));
    }

    private static void requireRosterEditable(TrainingSessionEntity session) {
        if (session.getStatus() != TrainingSessionStatus.PLANNED
                && session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("Players can only be added or removed while the session is PLANNED or IN_PROGRESS");
        }
    }
}
