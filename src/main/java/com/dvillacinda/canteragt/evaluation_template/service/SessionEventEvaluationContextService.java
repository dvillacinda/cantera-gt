package com.dvillacinda.canteragt.evaluation_template.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationWarningCode;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.SessionEvaluationEventRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionEventMetricRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionLock;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Context rules for an evaluation recorded in an evaluation event (T4.4; ADR-0004, DP-2.1, DP-2.3 and DP-2.5).
 *
 * <p>Must run inside the caller's transaction: {@link #open} locks the event's session with
 * {@link TrainingSessionLock}, so the state, roster and duplicate checks cannot race with other writers.
 */
@Service
@RequiredArgsConstructor
public class SessionEventEvaluationContextService {
    static final Set<AttendanceStatus> EVALUABLE_ATTENDANCE = EnumSet.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE);
    static final String VOIDED = "VOIDED";

    private final SessionEvaluationEventRepository eventRepository;
    private final SessionEventMetricRepository metricRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final EvaluationRepository evaluationRepository;
    private final TrainingSessionAccessPolicy accessPolicy;
    private final TrainingSessionLock sessionLock;
    private final EntityManager entityManager;

    /** Event, its session and the ids of its active metrics, read under the session lock. */
    public record EventContext(SessionEvaluationEventEntity event, TrainingSessionEntity session,
            Set<UUID> metricCriterionIds) {
    }

    /**
     * Rules 1, 2 and 4 of T4.4: the event exists in the academy (404), the caller may enter data in its session
     * ({@code requireDataEntryAccess}: 403/404), and, under the session lock, both the session and the event are
     * IN_PROGRESS (409). Authorization runs before the state checks, so staff outside an in-progress session get 403.
     */
    public EventContext open(UUID eventId, UUID academyId, Authentication authentication) {
        if (academyId == null) {
            throw new BadRequestException("X-Academy-Id header is required when session_event_id is sent");
        }
        SessionEvaluationEventEntity event = eventRepository.findInAcademy(eventId, academyId)
                .orElseThrow(() -> new NotFoundException("Evaluation event not found in the selected academy"));
        TrainingSessionEntity session = event.getTrainingSession();
        accessPolicy.requireDataEntryAccess(authentication, session);
        sessionLock.acquire(session);
        // the states read before the lock may be stale: re-read the event now that writers are serialized
        entityManager.refresh(event);
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS) {
            throw new ConflictException("Evaluations can only be recorded while the session is IN_PROGRESS");
        }
        if (event.getStatus() != SessionEvaluationEventStatus.IN_PROGRESS) {
            throw new ConflictException("Evaluations can only be recorded while the event is IN_PROGRESS");
        }
        Set<UUID> metricIds = metricRepository.findForEvents(List.of(eventId)).stream()
                .map(metric -> metric.getId().getCriterionId())
                .collect(Collectors.toUnmodifiableSet());
        return new EventContext(event, session, metricIds);
    }

    /**
     * Rules 3 and 7 of T4.4: the player is convened with attendance PRESENT or LATE (409, T4.4-D2) and has no
     * evaluation in the event that is not voided (409).
     */
    public void requireEvaluable(EventContext context, UUID playerId) {
        SessionPlayerEntity entry = sessionPlayerRepository
                .findByTrainingSession_SessionIdAndPlayer_PlayerId(context.session().getSessionId(), playerId)
                .orElseThrow(() -> new ConflictException("Player is not convened in the event's session"));
        if (!EVALUABLE_ATTENDANCE.contains(entry.getAttendanceStatus())) {
            throw new ConflictException("Player attendance must be PRESENT or LATE to be evaluated, but it is "
                    + entry.getAttendanceStatus());
        }
        if (evaluationRepository.existsBySessionEvent_SessionEventIdAndPlayer_PlayerIdAndStatusNot(
                context.event().getSessionEventId(), playerId, VOIDED)) {
            throw new ConflictException("Player already has an evaluation in this event that is not voided");
        }
    }

    /** Rule 9 of T4.4 (T4.4-D3): warnings that never block. Must be computed before the new evaluation is saved. */
    public List<EvaluationResponse.Warning> warnings(EventContext context, UUID playerId, LocalDate today) {
        EvaluationTemplateEntity template = context.event().getTemplate();
        List<EvaluationResponse.Warning> warnings = new ArrayList<>();
        evaluationRepository.findFirstByPlayer_PlayerIdAndTemplate_CodeAndStatusNotOrderByEvaluationDateDesc(
                playerId, template.getCode(), VOIDED).ifPresent(last -> {
            long days = ChronoUnit.DAYS.between(last.getEvaluationDate().toLocalDate(), today);
            if (template.getFrequencyMinDays() != null && days < template.getFrequencyMinDays()) {
                warnings.add(new EvaluationResponse.Warning(EvaluationWarningCode.FREQUENCY_TOO_SOON,
                        "The last evaluation with this template was " + days + " days ago; the minimum is "
                                + template.getFrequencyMinDays() + " days"));
            }
            if (last.getValidUntil() != null && !last.getValidUntil().isBefore(today)) {
                warnings.add(new EvaluationResponse.Warning(EvaluationWarningCode.PREVIOUS_STILL_VALID,
                        "The previous evaluation with this template is valid until " + last.getValidUntil()));
            }
        });
        if (template.getGroupSizeMin() != null) {
            long available = sessionPlayerRepository.countByTrainingSession_SessionIdAndAttendanceStatusIn(
                    context.session().getSessionId(), EVALUABLE_ATTENDANCE);
            if (available < template.getGroupSizeMin()) {
                warnings.add(new EvaluationResponse.Warning(EvaluationWarningCode.GROUP_TOO_SMALL,
                        "Only " + available + " players are PRESENT or LATE; the template expects at least "
                                + template.getGroupSizeMin()));
            }
        }
        return warnings;
    }
}
