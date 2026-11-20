package com.dvillacinda.canteragt.training_session.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.academy_location.entity.AcademyLocationEntity;
import com.dvillacinda.canteragt.academy_location.repository.AcademyLocationRepository;
import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionResponse;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionUpdateRequest;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.TrainingSessionMapper;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingSessionService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "session_date", "sessionDate",
            "status", "status",
            "name", "name",
            "created_at", "createdAt");

    private final TrainingSessionRepository trainingSessionRepository;
    private final AcademyCategoryRepository academyCategoryRepository;
    private final AcademyLocationRepository academyLocationRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final SessionStaffRepository sessionStaffRepository;
    private final CurrentUserService currentUserService;
    private final TrainingSessionAccessPolicy accessPolicy;
    private final TrainingSessionMapper mapper;
    private final Clock clock;

    @Value("${app.sessions.max-backdate-days:7}")
    private int maxBackdateDays;

    @Value("${app.sessions.max-future-days:365}")
    private int maxFutureDays;

    public TrainingSessionResponse create(
            UUID academyCategoryId,
            UUID academyId,
            TrainingSessionCreateRequest request,
            Authentication authentication) {
        AcademyCategoryEntity category = findCategory(academyCategoryId, academyId);
        accessPolicy.requireManageAccess(authentication, academyId, academyCategoryId);
        requireActiveCategory(category);
        validateSessionDate(request.sessionDate());

        var currentUser = currentUserService.getCurrentUser(AuthenticatedUser.fromAuthentication(authentication));
        if (currentUser.coach() == null) {
            throw new UnauthorizedAccessException("Creating a training session requires a coach profile");
        }

        TrainingSessionEntity session = TrainingSessionEntity.builder()
                .academyCategory(category)
                .createdByCoach(com.dvillacinda.canteragt.coach.entity.CoachEntity.builder()
                        .coachId(currentUser.coach().coachId()).build())
                .location(request.locationId() == null ? null : findActiveLocation(request.locationId(), academyId))
                .name(request.name().trim())
                .sessionDate(mapper.toLocalDateTime(request.sessionDate()))
                .status(TrainingSessionStatus.PLANNED)
                .notes(request.notes())
                .version(0L)
                .build();
        TrainingSessionEntity saved = trainingSessionRepository.saveAndFlush(session);
        // DECISION T3.6-B: the creator is registered as LEAD in the same transaction. No assignment re-check:
        // the creator already passed requireManageAccess for this category.
        sessionStaffRepository.saveAndFlush(SessionStaffEntity.builder()
                .trainingSession(saved)
                .coach(saved.getCreatedByCoach())
                .participationType(SessionParticipationType.LEAD)
                .build());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TrainingSessionResponse> list(
            UUID academyCategoryId,
            UUID academyId,
            TrainingSessionFilter filter,
            Pageable pageable,
            Authentication authentication) {
        accessPolicy.requireReadAccess(authentication, academyId, academyCategoryId);
        requirePageSize(pageable);
        Pageable safePageable = normalizePageable(pageable);
        LocalDateTime from = filter.from() == null ? null : mapper.toLocalDateTime(filter.from());
        LocalDateTime to = filter.to() == null ? null : mapper.toLocalDateTime(filter.to());
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The from date must not be after the to date");
        }
        return trainingSessionRepository.searchByCategory(
                        academyCategoryId, filter.status(), from, to, safePageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TrainingSessionResponse get(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireReadAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        return mapper.toResponse(session);
    }

    public TrainingSessionResponse update(
            UUID sessionId,
            UUID academyId,
            TrainingSessionUpdateRequest request,
            Authentication authentication) {
        TrainingSessionEntity session = findManageableSession(sessionId, academyId, authentication);
        requireStatus(session, TrainingSessionStatus.PLANNED, "Only planned sessions can be edited");
        if (request.name() == null && request.sessionDate() == null
                && request.locationId() == null && request.notes() == null) {
            throw new BadRequestException("At least one session field must be provided");
        }
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Session name must not be blank");
            }
            session.setName(request.name().trim());
        }
        if (request.sessionDate() != null) {
            validateSessionDate(request.sessionDate());
            session.setSessionDate(mapper.toLocalDateTime(request.sessionDate()));
        }
        if (request.locationId() != null) {
            session.setLocation(findActiveLocation(
                    request.locationId(), session.getAcademyCategory().getAcademy().getAcademyId()));
        }
        if (request.notes() != null) {
            session.setNotes(request.notes());
        }
        return mapper.toResponse(saveVersioned(session));
    }

    public TrainingSessionResponse start(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findManageableSession(sessionId, academyId, authentication);
        requireStatus(session, TrainingSessionStatus.PLANNED, "Only planned sessions can be started");
        session.setStatus(TrainingSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now(clock));
        return mapper.toResponse(saveVersioned(session));
    }

    public TrainingSessionResponse complete(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findManageableSession(sessionId, academyId, authentication);
        requireStatus(session, TrainingSessionStatus.IN_PROGRESS, "Only in-progress sessions can be completed");
        List<UUID> pendingPlayerIds = sessionPlayerRepository.findPlayerIdsBySessionAndAttendanceStatus(
                sessionId,
                session.getAcademyCategory().getAcademyCategoryId(),
                AttendanceStatus.PENDING);
        if (!pendingPlayerIds.isEmpty()) {
            throw new ConflictException("Cannot complete session with players still PENDING: " + pendingPlayerIds);
        }
        session.setStatus(TrainingSessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now(clock));
        return mapper.toResponse(saveVersioned(session));
    }

    public TrainingSessionResponse cancel(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findManageableSession(sessionId, academyId, authentication);
        if (session.getStatus() != TrainingSessionStatus.PLANNED
                && session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("Only planned or in-progress sessions can be cancelled");
        }
        if (session.getStatus() == TrainingSessionStatus.IN_PROGRESS) {
            session.setEndedAt(LocalDateTime.now(clock));
        }
        session.setStatus(TrainingSessionStatus.CANCELLED);
        return mapper.toResponse(saveVersioned(session));
    }

    private TrainingSessionEntity findManageableSession(
            UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireManageAccess(
                authentication, academyId, session.getAcademyCategory().getAcademyCategoryId());
        return session;
    }

    private TrainingSessionEntity findSession(UUID sessionId, UUID academyId) {
        return trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(sessionId, academyId)
                .orElseThrow(() -> new NotFoundException("Training session not found in the selected academy"));
    }

    private AcademyCategoryEntity findCategory(UUID academyCategoryId, UUID academyId) {
        return academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(academyCategoryId, academyId)
                .orElseThrow(() -> new NotFoundException("Academy category not found in the selected academy"));
    }

    private void requireActiveCategory(AcademyCategoryEntity category) {
        if (category.getStatus() != AcademyCategoryStatus.ACTIVE) {
            throw new ConflictException("Training sessions can only be created for an ACTIVE academy category");
        }
    }

    private AcademyLocationEntity findActiveLocation(UUID locationId, UUID academyId) {
        return academyLocationRepository.findByLocationIdAndAcademy_AcademyIdAndStatus(
                        locationId, academyId, Status.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active location not found in the selected academy"));
    }

    private void validateSessionDate(OffsetDateTime requestedDate) {
        LocalDate requestedDay = mapper.toLocalDateTime(requestedDate).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        if (requestedDay.isBefore(today.minusDays(maxBackdateDays))) {
            throw new BadRequestException("Session date is older than the allowed backdate period");
        }
        if (requestedDay.isAfter(today.plusDays(maxFutureDays))) {
            throw new BadRequestException("Session date is beyond the allowed future period");
        }
    }

    private void requirePageSize(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must not exceed " + MAX_PAGE_SIZE);
        }
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "sessionDate"));
        }
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORT_FIELDS.get(order.getProperty());
                    if (property == null) {
                        throw new BadRequestException("Unsupported session sort field: " + order.getProperty());
                    }
                    return new Sort.Order(order.getDirection(), property, order.getNullHandling());
                })
                .collect(Collectors.toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private void requireStatus(TrainingSessionEntity session, TrainingSessionStatus expected, String message) {
        if (session.getStatus() != expected) {
            throw new ConflictException(message);
        }
    }

    private TrainingSessionEntity saveVersioned(TrainingSessionEntity session) {
        try {
            return trainingSessionRepository.saveAndFlush(session);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("Training session was modified concurrently; reload and retry");
        }
    }
}
