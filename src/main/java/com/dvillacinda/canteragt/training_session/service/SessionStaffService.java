package com.dvillacinda.canteragt.training_session.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffAddRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionStaffMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Coaches taking part in a session (session_staff). Data entry by staff is enabled in Sprint 4; here only the
 * membership is managed.
 *
 * <p>Like the roster, every mutation locks the session row and bumps its version, so the single-LEAD rule
 * (DECISION T3.6-A, enforced in the application, no DB constraint) cannot be broken by concurrent requests and
 * staff changes cannot interleave with lifecycle transitions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionStaffService {
    private final TrainingSessionRepository trainingSessionRepository;
    private final SessionStaffRepository sessionStaffRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final TrainingSessionAccessPolicy accessPolicy;
    private final SessionStaffMapper mapper;
    private final EntityManager entityManager;
    private final TrainingSessionLock sessionLock;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SessionStaffResponse> list(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireReadAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        return sessionStaffRepository.findStaff(sessionId).stream().map(mapper::toResponse).toList();
    }

    public SessionStaffResponse addStaff(
            UUID sessionId, UUID academyId, SessionStaffAddRequest request, Authentication authentication) {
        TrainingSessionEntity session = lockManageableSession(sessionId, academyId, authentication);
        requireStaffEditable(session);
        UUID academyCategoryId = session.getAcademyCategory().getAcademyCategoryId();

        if (!coachAssignmentRepository.existsCurrentAssignmentForCategory(
                request.coachId(), academyCategoryId, LocalDate.now(clock))) {
            throw new BadRequestException("Coach has no current assignment in the session's category");
        }
        if (sessionStaffRepository.existsByTrainingSession_SessionIdAndCoach_CoachId(sessionId, request.coachId())) {
            throw new ConflictException("Coach is already part of the session staff");
        }
        if (request.participationType() == SessionParticipationType.LEAD
                && sessionStaffRepository.existsByTrainingSession_SessionIdAndParticipationType(
                        sessionId, SessionParticipationType.LEAD)) {
            throw new ConflictException("The session already has a LEAD coach");
        }

        try {
            sessionStaffRepository.saveAndFlush(SessionStaffEntity.builder()
                    .trainingSession(session)
                    .coach(entityManager.getReference(CoachEntity.class, request.coachId()))
                    .participationType(request.participationType())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(session_id, coach_id): only reachable if the row lock was bypassed; never a 500
            throw new ConflictException("Coach is already part of the session staff");
        }
        return mapper.toResponse(findEntry(sessionId, request.coachId()));
    }

    public void removeStaff(UUID sessionId, UUID academyId, UUID coachId, Authentication authentication) {
        TrainingSessionEntity session = lockManageableSession(sessionId, academyId, authentication);
        requireStaffEditable(session);
        SessionStaffEntity entry = findEntry(sessionId, coachId);
        if (entry.getParticipationType() == SessionParticipationType.LEAD
                && session.getStatus() == TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("The LEAD coach cannot be removed while the session is IN_PROGRESS");
        }
        sessionStaffRepository.delete(entry);
        sessionLock.flush();
    }

    private TrainingSessionEntity lockManageableSession(
            UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireManageAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        return sessionLock.acquire(session);
    }

    private TrainingSessionEntity findSession(UUID sessionId, UUID academyId) {
        return trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(sessionId, academyId)
                .orElseThrow(() -> new NotFoundException("Training session not found in the selected academy"));
    }

    private SessionStaffEntity findEntry(UUID sessionId, UUID coachId) {
        return sessionStaffRepository.findStaffEntry(sessionId, coachId)
                .orElseThrow(() -> new NotFoundException("Coach is not part of this session's staff"));
    }

    private static void requireStaffEditable(TrainingSessionEntity session) {
        if (session.getStatus() != TrainingSessionStatus.PLANNED
                && session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("Session staff can only change while the session is PLANNED or IN_PROGRESS");
        }
    }
}
