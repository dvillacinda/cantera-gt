package com.dvillacinda.canteragt.training_session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricsUpdateRequest;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricId;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionEvaluationEventMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionEvaluationEventRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionEventMetricRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Evaluation events (blocks) of a training session and their active metrics (T4.3; ADR-0004, DP-2.2 and DP-2.5).
 *
 * <p>Every mutation follows: authorize → {@link TrainingSessionLock#acquire} → validate → persist → flush. The lock
 * serializes event changes with the roster, the staff and the session lifecycle, so the server-assigned
 * {@code block_order} and the state checks cannot race.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionEvaluationEventService {
    static final int MAX_METRICS = 3;

    private final TrainingSessionRepository trainingSessionRepository;
    private final SessionEvaluationEventRepository eventRepository;
    private final SessionEventMetricRepository metricRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationCriterionRepository criterionRepository;
    private final TrainingSessionAccessPolicy accessPolicy;
    private final TrainingSessionLock sessionLock;
    private final SessionEvaluationEventMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SessionEvaluationEventResponse> list(UUID sessionId, UUID academyId, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireReadAccess(authentication, academyId, categoryId(session));
        List<SessionEvaluationEventEntity> events = eventRepository.findBySession(sessionId);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<EvaluationCriterionEntity>> metricsByEvent = new LinkedHashMap<>();
        metricRepository.findForEvents(events.stream().map(SessionEvaluationEventEntity::getSessionEventId).toList())
                .forEach(metric -> metricsByEvent
                        .computeIfAbsent(metric.getId().getSessionEventId(), ignored -> new ArrayList<>())
                        .add(metric.getCriterion()));
        return events.stream()
                .map(event -> mapper.toResponse(event,
                        metricsByEvent.getOrDefault(event.getSessionEventId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionEvaluationEventResponse get(UUID eventId, UUID academyId, Authentication authentication) {
        SessionEvaluationEventEntity event = findEvent(eventId, academyId);
        accessPolicy.requireReadAccess(authentication, academyId, categoryId(event.getTrainingSession()));
        return toResponse(event);
    }

    public SessionEvaluationEventResponse create(UUID sessionId, UUID academyId,
            SessionEvaluationEventCreateRequest request, Authentication authentication) {
        TrainingSessionEntity session = findSession(sessionId, academyId);
        accessPolicy.requireManageAccess(authentication, academyId, categoryId(session));
        sessionLock.acquire(session);
        requireSessionOpen(session, "Evaluation events can only be added while the session is PLANNED or IN_PROGRESS");

        EvaluationTemplateEntity template = templateRepository.findById(request.templateId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsOfficial())
                        && candidate.getStatus() == EvaluationTemplateStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Template must be an official ACTIVE template"));
        List<EvaluationCriterionEntity> metrics = request.metricCriterionIds() == null
                ? defaultMetrics(template)
                : selectMetrics(template, request.metricCriterionIds());

        int nextBlockOrder = eventRepository.findMaxBlockOrder(sessionId) + 1;
        if (nextBlockOrder > Short.MAX_VALUE) {
            throw new ConflictException("The session has reached the maximum number of evaluation events");
        }
        SessionEvaluationEventEntity event = eventRepository.save(SessionEvaluationEventEntity.builder()
                .trainingSession(session)
                .template(template)
                .blockOrder((short) nextBlockOrder)
                .status(SessionEvaluationEventStatus.PLANNED)
                .build());
        metrics.forEach(criterion -> entityManager.persist(metricRow(event, criterion)));
        eventRepository.flush();
        return mapper.toResponse(event, metrics);
    }

    public SessionEvaluationEventResponse replaceMetrics(UUID eventId, UUID academyId,
            SessionEventMetricsUpdateRequest request, Authentication authentication) {
        SessionEvaluationEventEntity event = lockEvent(eventId, academyId, authentication);
        requireEventStatus(event, SessionEvaluationEventStatus.PLANNED,
                "Event metrics can only change while the event is PLANNED");
        requireSessionOpen(event.getTrainingSession(),
                "Event metrics can only change while the session is PLANNED or IN_PROGRESS");
        List<EvaluationCriterionEntity> selected = selectMetrics(event.getTemplate(), request.metricCriterionIds());

        Set<UUID> wanted = selected.stream().map(EvaluationCriterionEntity::getCriterionId)
                .collect(Collectors.toSet());
        List<SessionEventMetricEntity> current = metricRepository.findForEvents(List.of(eventId));
        current.stream()
                .filter(metric -> !wanted.contains(metric.getId().getCriterionId()))
                .forEach(metricRepository::delete);
        // Hibernate runs inserts before deletes in one flush: flush the deletes first so the deferred
        // "at most 3 metrics" trigger and the primary key never see both sets at once
        metricRepository.flush();
        Set<UUID> kept = current.stream().map(metric -> metric.getId().getCriterionId())
                .filter(wanted::contains).collect(Collectors.toSet());
        selected.stream()
                .filter(criterion -> !kept.contains(criterion.getCriterionId()))
                .forEach(criterion -> entityManager.persist(metricRow(event, criterion)));
        metricRepository.flush();
        return mapper.toResponse(event, sorted(selected));
    }

    public SessionEvaluationEventResponse start(UUID eventId, UUID academyId, Authentication authentication) {
        SessionEvaluationEventEntity event = lockEvent(eventId, academyId, authentication);
        requireEventStatus(event, SessionEvaluationEventStatus.PLANNED, "Only planned events can be started");
        if (event.getTrainingSession().getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("An event can only start while its session is IN_PROGRESS");
        }
        if (metricRepository.findForEvents(List.of(eventId)).isEmpty()) {
            throw new ConflictException("An event needs at least one active metric to start");
        }
        event.setStatus(SessionEvaluationEventStatus.IN_PROGRESS);
        event.setStartedAt(LocalDateTime.now(clock));
        eventRepository.flush();
        return toResponse(event);
    }

    public SessionEvaluationEventResponse complete(UUID eventId, UUID academyId, Authentication authentication) {
        SessionEvaluationEventEntity event = lockEvent(eventId, academyId, authentication);
        requireEventStatus(event, SessionEvaluationEventStatus.IN_PROGRESS, "Only in-progress events can be completed");
        event.setStatus(SessionEvaluationEventStatus.COMPLETED);
        event.setEndedAt(LocalDateTime.now(clock));
        eventRepository.flush();
        return toResponse(event);
    }

    public SessionEvaluationEventResponse cancel(UUID eventId, UUID academyId, Authentication authentication) {
        SessionEvaluationEventEntity event = lockEvent(eventId, academyId, authentication);
        if (event.getStatus().isClosed()) {
            throw new ConflictException("Only planned or in-progress events can be cancelled");
        }
        if (eventRepository.hasNonVoidedEvaluations(eventId)) {
            throw new ConflictException("The event has evaluations that are not voided; void them before cancelling");
        }
        if (event.getStatus() == SessionEvaluationEventStatus.IN_PROGRESS) {
            event.setEndedAt(LocalDateTime.now(clock));
        }
        event.setStatus(SessionEvaluationEventStatus.CANCELLED);
        eventRepository.flush();
        return toResponse(event);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** Finds the event in the academy (404), authorizes management, locks its session and re-reads the event. */
    private SessionEvaluationEventEntity lockEvent(UUID eventId, UUID academyId, Authentication authentication) {
        SessionEvaluationEventEntity event = findEvent(eventId, academyId);
        TrainingSessionEntity session = event.getTrainingSession();
        accessPolicy.requireManageAccess(authentication, academyId, categoryId(session));
        sessionLock.acquire(session);
        // the status read before the lock may be stale: re-read it now that writers are serialized
        entityManager.refresh(event);
        return event;
    }

    private List<EvaluationCriterionEntity> defaultMetrics(EvaluationTemplateEntity template) {
        List<EvaluationCriterionEntity> defaults = criterionRepository.findAllForTemplate(template.getTemplateId())
                .stream().filter(c -> c.isActiveByDefault() && c.isScored()).toList();
        if (defaults.isEmpty()) {
            throw new BadRequestException("The template has no default metrics; send metric_criterion_ids");
        }
        if (defaults.size() > MAX_METRICS) {
            throw new ConflictException("The template has more than " + MAX_METRICS + " default metrics");
        }
        return defaults;
    }

    private List<EvaluationCriterionEntity> selectMetrics(EvaluationTemplateEntity template, List<UUID> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > MAX_METRICS) {
            throw new BadRequestException("An event needs between 1 and " + MAX_METRICS + " active metrics");
        }
        if (new HashSet<>(requested).size() != requested.size()) {
            throw new BadRequestException("metric_criterion_ids must not contain duplicates");
        }
        Map<UUID, EvaluationCriterionEntity> criteria = criterionRepository.findAllForTemplate(template.getTemplateId())
                .stream().collect(Collectors.toMap(EvaluationCriterionEntity::getCriterionId, Function.identity()));
        List<EvaluationCriterionEntity> selected = requested.stream().map(id -> {
            EvaluationCriterionEntity criterion = criteria.get(id);
            if (criterion == null) {
                throw new BadRequestException("Metric " + id + " does not belong to the event's template");
            }
            if (!criterion.isScored()) {
                throw new BadRequestException("Metric " + id + " is a non-scored component and cannot be activated");
            }
            return criterion;
        }).toList();
        return sorted(selected);
    }

    private static List<EvaluationCriterionEntity> sorted(List<EvaluationCriterionEntity> criteria) {
        return criteria.stream()
                .sorted(Comparator.comparing(EvaluationCriterionEntity::getDisplayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EvaluationCriterionEntity::getCode))
                .toList();
    }

    private static SessionEventMetricEntity metricRow(
            SessionEvaluationEventEntity event, EvaluationCriterionEntity criterion) {
        return SessionEventMetricEntity.builder()
                .id(new SessionEventMetricId(event.getSessionEventId(), criterion.getCriterionId()))
                .criterion(criterion)
                .templateId(event.getTemplate().getTemplateId())
                .build();
    }

    private SessionEvaluationEventResponse toResponse(SessionEvaluationEventEntity event) {
        return mapper.toResponse(event, metricRepository.findForEvents(List.of(event.getSessionEventId())).stream()
                .map(SessionEventMetricEntity::getCriterion).toList());
    }

    private TrainingSessionEntity findSession(UUID sessionId, UUID academyId) {
        return trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(sessionId, academyId)
                .orElseThrow(() -> new NotFoundException("Training session not found in the selected academy"));
    }

    private SessionEvaluationEventEntity findEvent(UUID eventId, UUID academyId) {
        return eventRepository.findInAcademy(eventId, academyId)
                .orElseThrow(() -> new NotFoundException("Evaluation event not found in the selected academy"));
    }

    private static UUID categoryId(TrainingSessionEntity session) {
        return session.getAcademyCategory().getAcademyCategoryId();
    }

    private static void requireSessionOpen(TrainingSessionEntity session, String message) {
        if (session.getStatus() != TrainingSessionStatus.PLANNED
                && session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException(message);
        }
    }

    private static void requireEventStatus(
            SessionEvaluationEventEntity event, SessionEvaluationEventStatus expected, String message) {
        if (event.getStatus() != expected) {
            throw new ConflictException(message);
        }
    }
}
