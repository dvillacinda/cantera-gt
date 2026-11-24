package com.dvillacinda.canteragt.evaluation_template.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationObservationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationResultEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.evaluation_template.service.SessionEventEvaluationContextService.EventContext;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvaluationService {
    private final EvaluationRepository evaluationRepository;
    private final EvaluationTemplateRepository templateRepository;
    private final EvaluationCriterionRepository criterionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAssignmentRepository playerAssignmentRepository;
    private final CoachRepository coachRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final UserRepository userRepository;
    private final AcademyAccessService academyAccessService;
    private final SessionEventEvaluationContextService eventContextService;
    private final Clock clock;

    /** Evaluation outside any event: the original behavior, unchanged by Sprint 4 (regression E7). */
    @Transactional
    public EvaluationResponse create(UUID playerId, EvaluationCreateRequest request, Authentication authentication) {
        return create(playerId, null, request, authentication);
    }

    /**
     * Records an evaluation. Without {@code session_event_id} the original rules apply and {@code academyId} is
     * ignored. With it, the event rules of T4.4 apply (ADR-0004, DP-2.3) and {@code academyId} is required.
     */
    @Transactional
    public EvaluationResponse create(UUID playerId, UUID academyId, EvaluationCreateRequest request,
            Authentication authentication) {
        CoachEntity coach = requireActiveCoach(authentication);
        if (request.sessionEventId() != null) {
            return createInEvent(playerId, academyId, request, coach, authentication);
        }
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player with id " + playerId + " not found"));
        LocalDate today = LocalDate.now(clock);
        PlayerAssignmentEntity assignment = playerAssignmentRepository.findCurrentAssignments(playerId, today)
                .stream().findFirst()
                .orElseThrow(() -> new UnauthorizedAccessException("Player has no current academy assignment"));
        UUID playerAcademyId = assignment.getAcademyCategory().getAcademy().getAcademyId();
        academyAccessService.requireAcademyReadAccess(authentication, playerAcademyId);
        if (!coachAssignmentRepository.existsCurrentAssignmentForCategory(coach.getCoachId(),
                assignment.getAcademyCategory().getAcademyCategoryId(), today)) {
            throw new UnauthorizedAccessException("Coach has no current assignment for the player's category");
        }

        EvaluationTemplateEntity template = templateRepository
                .findByTemplateIdAndIsOfficialTrueAndStatus(request.templateId(), EvaluationTemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active official template not found"));
        if (!template.getVersion().equals(request.templateVersion())) {
            throw new BadRequestException("templateVersion does not match the active official version");
        }
        var criteria = criterionRepository.findAllForTemplate(template.getTemplateId());
        var criteriaById = validateResults(criteria, request.results(), null);

        EvaluationEntity evaluation = buildEvaluation(player, assignment.getAcademyCategory(), coach, template,
                request, criteriaById, today);
        return toResponse(evaluationRepository.save(evaluation), List.of());
    }

    /**
     * Rules 1–10 of T4.4. Order: event in the academy (404) → data entry access (403/404) → session lock → session
     * and event IN_PROGRESS (409) → player (404) → convened PRESENT/LATE and no duplicate (409) → the event's
     * template (400) → the event's metrics (400) → warnings → save. The session and category come from the event.
     */
    private EvaluationResponse createInEvent(UUID playerId, UUID academyId, EvaluationCreateRequest request,
            CoachEntity coach, Authentication authentication) {
        EventContext context = eventContextService.open(request.sessionEventId(), academyId, authentication);
        PlayerEntity player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player with id " + playerId + " not found"));
        eventContextService.requireEvaluable(context, playerId);

        // The event keeps its template row (template rows are immutable in V3), even if it was retired later
        EvaluationTemplateEntity template = context.event().getTemplate();
        if (!template.getTemplateId().equals(request.templateId())
                || !template.getVersion().equals(request.templateVersion())) {
            throw new BadRequestException("template_id and template_version must match the event's template");
        }
        var criteria = criterionRepository.findAllForTemplate(template.getTemplateId());
        var criteriaById = validateResults(criteria, request.results(), context.metricCriterionIds());

        LocalDate today = LocalDate.now(clock);
        List<EvaluationResponse.Warning> warnings = eventContextService.warnings(context, playerId, today);
        EvaluationEntity evaluation = buildEvaluation(player, context.session().getAcademyCategory(), coach,
                template, request, criteriaById, today);
        evaluation.setTrainingSession(context.session());
        evaluation.setSessionEvent(context.event());
        // flush now so the partial unique index (safety net for the duplicate rule) fails inside this call
        return toResponse(evaluationRepository.saveAndFlush(evaluation), warnings);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse get(UUID evaluationId, Authentication authentication) {
        UserEntity user = requireCurrentUser(authentication);
        CoachEntity coach = coachRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new UnauthorizedAccessException("Authenticated user has no coach profile"));
        EvaluationEntity evaluation = evaluationRepository
                .findByEvaluationIdAndEvaluator_CoachId(evaluationId, coach.getCoachId())
                .orElseThrow(() -> new NotFoundException("Evaluation not found"));
        return toResponse(evaluation, List.of());
    }

    private CoachEntity requireActiveCoach(Authentication authentication) {
        UserEntity user = requireCurrentUser(authentication);
        if (user.getStatus() == null || !user.getStatus().canLogIn()) {
            throw new UnauthorizedAccessException("User account is not active");
        }
        return coachRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new UnauthorizedAccessException("Authenticated user has no coach profile"));
    }

    private UserEntity requireCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedAccessException("Authenticated JWT is required");
        }
        return userRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new UnauthorizedAccessException("User is not registered in this application"));
    }

    /**
     * Validates every result against the template and returns the template's criteria by id.
     *
     * @param eventMetricIds the event's active metrics, or {@code null} outside an event. Outside an event, every
     *        {@code activeByDefault && scored} criterion is required. In an event, the event's metrics are required
     *        and a scored result outside them is rejected (fixes R-1).
     */
    private Map<UUID, EvaluationCriterionEntity> validateResults(List<EvaluationCriterionEntity> criteria,
            List<EvaluationCreateRequest.ResultRequest> results, Set<UUID> eventMetricIds) {
        var criteriaById = criteria.stream().collect(Collectors.toMap(
                EvaluationCriterionEntity::getCriterionId, criterion -> criterion));
        var suppliedIds = new HashSet<UUID>();
        var suppliedCriterionLaterality = new HashSet<String>();
        results.forEach(result -> {
            suppliedIds.add(result.criterionId());
            String laterality = result.laterality() == null ? "NONE" : result.laterality();
            if (!suppliedCriterionLaterality.add(result.criterionId() + ":" + laterality))
                throw new BadRequestException("Duplicate result for criterion and laterality");
            var criterion = criteriaById.get(result.criterionId());
            if (criterion == null) throw new BadRequestException("Result criterion is not part of the selected template");
            if (eventMetricIds != null && criterion.isScored() && !eventMetricIds.contains(criterion.getCriterionId()))
                throw new BadRequestException("Result criterion is not an active metric of the event: "
                        + criterion.getCode());
            validateResult(criterion, result);
        });
        for (var criterion : criteria) {
            boolean required = eventMetricIds == null
                    ? criterion.isActiveByDefault() && criterion.isScored()
                    : eventMetricIds.contains(criterion.getCriterionId());
            if (required && !suppliedIds.contains(criterion.getCriterionId())) {
                throw new BadRequestException("Missing required criterion result: " + criterion.getCode());
            }
        }
        return criteriaById;
    }

    private EvaluationEntity buildEvaluation(PlayerEntity player, AcademyCategoryEntity academyCategory,
            CoachEntity coach, EvaluationTemplateEntity template, EvaluationCreateRequest request,
            Map<UUID, EvaluationCriterionEntity> criteriaById, LocalDate today) {
        EvaluationEntity evaluation = new EvaluationEntity();
        evaluation.setPlayer(player);
        evaluation.setAcademyCategory(academyCategory);
        evaluation.setEvaluator(coach);
        evaluation.setTemplate(template); // Template rows are immutable in V3; this retains the exact version.
        evaluation.setEvaluationDate(LocalDateTime.now(clock));
        evaluation.setStatus("COMPLETED");
        evaluation.setCompletedAt(LocalDateTime.now(clock));
        evaluation.setValidUntil(today.plusDays(template.getValidityDays()));
        evaluation.setScoringStatus("PENDING");
        evaluation.setStrengths(request.strengths());
        evaluation.setImprovementAreas(request.improvementAreas());
        evaluation.setGeneralComment(request.generalComment());
        request.results().forEach(input -> {
            EvaluationResultEntity result = new EvaluationResultEntity();
            result.setEvaluation(evaluation);
            result.setCriterion(criteriaById.get(input.criterionId()));
            result.setRawValue(input.rawValue());
            result.setSuccesses(input.successes());
            result.setOpportunities(input.opportunities());
            result.setErrorCount(input.errorCount());
            result.setLevel(input.level());
            result.setLaterality(input.laterality() == null ? "NONE" : input.laterality());
            result.setComment(input.comment());
            if (input.observations() != null) input.observations().forEach(o -> {
                EvaluationObservationEntity observation = new EvaluationObservationEntity();
                observation.setResult(result);
                observation.setSequence(o.sequence());
                observation.setValue(o.value());
                observation.setLevel(o.level());
                observation.setSuccess(o.success());
                observation.setValid(o.valid() == null || o.valid());
                observation.setNote(o.note());
                result.getObservations().add(observation);
            });
            evaluation.getResults().add(result);
        });
        return evaluation;
    }

    private void validateResult(EvaluationCriterionEntity c, EvaluationCreateRequest.ResultRequest r) {
        String type = c.getMetricType();
        if ("TIME".equals(type) && (r.rawValue() == null || r.rawValue().signum() < 0)) {
            throw new BadRequestException("A non-negative rawValue is required for " + c.getCode());
        }
        if ("SUCCESS_RATIO".equals(type) && (r.successes() == null || r.opportunities() == null
                || r.opportunities() < c.getMinOpportunities() || r.successes() < 0
                || r.successes() > r.opportunities())) {
            throw new BadRequestException("Successes/opportunities are invalid for " + c.getCode());
        }
        if ("ERROR_COUNT".equals(type) && (r.errorCount() == null || r.errorCount() < 0)) {
            throw new BadRequestException("A non-negative errorCount is required for " + c.getCode());
        }
        if (("RUBRIC".equals(type) || "ANCHORED_SCALE".equals(type))
                && (r.level() == null || r.level() < c.getScaleMin() || r.level() > c.getScaleMax())) {
            throw new BadRequestException("level is outside the allowed scale for " + c.getCode());
        }
        if (c.isCommentRequired() && (r.comment() == null || r.comment().isBlank())) {
            throw new BadRequestException("comment is required for " + c.getCode());
        }
        String laterality = r.laterality() == null ? "NONE" : r.laterality();
        if (!List.of("NONE", "LEFT", "RIGHT").contains(laterality)
                || ("REQUIRED".equals(c.getLateralityMode()) && "NONE".equals(laterality))
                || ("NONE".equals(c.getLateralityMode()) && !"NONE".equals(laterality))) {
            throw new BadRequestException("laterality is invalid for " + c.getCode());
        }
        if (c.getAttemptsCount() != null
                && (r.observations() == null || r.observations().size() != c.getAttemptsCount())) {
            throw new BadRequestException("Expected " + c.getAttemptsCount() + " observations for " + c.getCode());
        }
        if (r.observations() != null) {
            HashSet<Integer> sequences = new HashSet<>();
            r.observations().forEach(o -> {
                if (o == null || o.sequence() == null || o.sequence() < 1 || !sequences.add(o.sequence())
                        || (o.value() == null && o.level() == null && o.success() == null)) {
                    throw new BadRequestException("Observation sequence or payload is invalid for " + c.getCode());
                }
                if ((o.value() != null && o.value().signum() < 0)
                        || (o.level() != null && (c.getScaleMin() == null || c.getScaleMax() == null
                                || o.level() < c.getScaleMin() || o.level() > c.getScaleMax()))) {
                    throw new BadRequestException("Observation value is outside the allowed range for " + c.getCode());
                }
            });
        }
    }

    private EvaluationResponse toResponse(EvaluationEntity evaluation, List<EvaluationResponse.Warning> warnings) {
        return new EvaluationResponse(evaluation.getEvaluationId(), evaluation.getPlayer().getPlayerId(),
                evaluation.getTemplate().getTemplateId(), evaluation.getTemplate().getVersion(),
                evaluation.getEvaluationDate(), evaluation.getStatus(), evaluation.getEvaluator().getCoachId(),
                evaluation.getResults().stream().map(r -> new EvaluationResponse.Result(
                        r.getCriterion().getCriterionId(), r.getRawValue(), r.getSuccesses(), r.getOpportunities(),
                        r.getErrorCount(), r.getLevel(), r.getLaterality(), r.getComment(),
                        r.getObservations().stream().map(o -> new EvaluationResponse.Observation(o.getSequence(),
                                o.getValue(), o.getLevel(), o.getSuccess(), o.isValid(), o.getNote())).toList()))
                        .toList(),
                evaluation.getStrengths(), evaluation.getImprovementAreas(), evaluation.getGeneralComment(),
                evaluation.getTrainingSession() == null ? null : evaluation.getTrainingSession().getSessionId(),
                evaluation.getSessionEvent() == null ? null : evaluation.getSessionEvent().getSessionEventId(),
                warnings);
    }
}
