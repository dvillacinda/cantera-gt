package com.dvillacinda.canteragt.evaluation_template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationWarningCode;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.evaluation_template.service.SessionEventEvaluationContextService.EventContext;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

/** Rules of T4.4 in EvaluationService when session_event_id is sent; the context rules are mocked here. */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceEventTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T16:00:00Z"), ZoneId.of("America/Guatemala"));

    @Mock EvaluationRepository evaluationRepository;
    @Mock EvaluationTemplateRepository templateRepository;
    @Mock EvaluationCriterionRepository criterionRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerAssignmentRepository playerAssignmentRepository;
    @Mock CoachRepository coachRepository;
    @Mock CoachAssignmentRepository coachAssignmentRepository;
    @Mock UserRepository userRepository;
    @Mock AcademyAccessService academyAccessService;
    @Mock SessionEventEvaluationContextService eventContextService;
    @Mock EvaluationAccessPolicy accessPolicy;

    private EvaluationService service;
    private Authentication auth;
    private EvaluationTemplateEntity template;
    private AcademyCategoryEntity sessionCategory;
    private EventContext context;
    /** Template with two default metrics, one optional metric and one non-scored component. */
    private EvaluationCriterionEntity defaultA;
    private EvaluationCriterionEntity defaultB;
    private EvaluationCriterionEntity optional;
    private EvaluationCriterionEntity component;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evaluationRepository, templateRepository, criterionRepository,
                playerRepository, playerAssignmentRepository, coachRepository, coachAssignmentRepository,
                userRepository, academyAccessService, eventContextService, accessPolicy, CLOCK);
        UserEntity user = UserEntity.builder().userId(UUID.randomUUID()).keycloakId("coach-a")
                .status(UserStatus.ACTIVE).build();
        CoachEntity coach = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        lenient().when(userRepository.findByKeycloakId("coach-a")).thenReturn(Optional.of(user));
        lenient().when(coachRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(coach));
        lenient().when(playerRepository.findById(PLAYER_ID))
                .thenReturn(Optional.of(PlayerEntity.builder().playerId(PLAYER_ID).build()));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("coach-a").build();
        auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COACH")));

        // a RETIRED template: an event keeps working with its template after it is retired (T4.3)
        template = EvaluationTemplateEntity.builder().templateId(TEMPLATE_ID).version(3).code("JUEGO_REDUCIDO")
                .status(EvaluationTemplateStatus.RETIRED).isOfficial(true).validityDays(30).build();
        sessionCategory = AcademyCategoryEntity.builder().academyCategoryId(UUID.randomUUID()).build();
        TrainingSessionEntity session = TrainingSessionEntity.builder().sessionId(SESSION_ID)
                .academyCategory(sessionCategory).build();
        SessionEvaluationEventEntity event = SessionEvaluationEventEntity.builder().sessionEventId(EVENT_ID)
                .trainingSession(session).template(template).build();
        defaultA = criterion("A", true, true);
        defaultB = criterion("B", true, true);
        optional = criterion("C", false, true);
        component = criterion("D", false, false);
        lenient().when(criterionRepository.findAllForTemplate(TEMPLATE_ID))
                .thenReturn(List.of(defaultA, defaultB, optional, component));
        // the event activates one default metric and the optional one
        context = new EventContext(event, session, Set.of(defaultA.getCriterionId(), optional.getCriterionId()));
        lenient().when(eventContextService.open(EVENT_ID, ACADEMY_ID, auth)).thenReturn(context);
        lenient().when(evaluationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));
    }

    /** E1 and E2 (fixes R-1): only the event's metrics are required, not the template defaults. */
    @Test
    void eventMetricsAreRequiredInsteadOfTheTemplateDefaults() {
        var warning = new EvaluationResponse.Warning(EvaluationWarningCode.GROUP_TOO_SMALL, "few players");
        when(eventContextService.warnings(context, PLAYER_ID, LocalDate.of(2026, 11, 23))).thenReturn(List.of(warning));

        EvaluationResponse response = service.create(PLAYER_ID, ACADEMY_ID,
                request(defaultA, optional), auth);

        ArgumentCaptor<EvaluationEntity> saved = ArgumentCaptor.forClass(EvaluationEntity.class);
        verify(evaluationRepository).saveAndFlush(saved.capture());
        assertEquals(context.session(), saved.getValue().getTrainingSession(), "rule 8: session from the event");
        assertEquals(context.event(), saved.getValue().getSessionEvent());
        assertEquals(sessionCategory, saved.getValue().getAcademyCategory(), "category of the event's session");
        assertEquals(SESSION_ID, response.sessionId());
        assertEquals(EVENT_ID, response.sessionEventId());
        assertEquals(List.of(warning), response.warnings(), "rule 9: warnings are returned, never thrown");
        verify(evaluationRepository, never()).save(any());
        verifyNoInteractions(playerAssignmentRepository, coachAssignmentRepository, templateRepository);
    }

    @Test
    void contextRulesRunBeforeTheBodyIsValidated() {
        service.create(PLAYER_ID, ACADEMY_ID, request(defaultA, optional), auth);

        InOrder order = inOrder(eventContextService, playerRepository, criterionRepository, evaluationRepository);
        order.verify(eventContextService).open(EVENT_ID, ACADEMY_ID, auth);
        order.verify(playerRepository).findById(PLAYER_ID);
        order.verify(eventContextService).requireEvaluable(context, PLAYER_ID);
        order.verify(criterionRepository).findAllForTemplate(TEMPLATE_ID);
        order.verify(eventContextService).warnings(any(), any(), any());
        order.verify(evaluationRepository).saveAndFlush(any());
    }

    @Test
    void missingEventMetricIs400() {
        assertThrows(BadRequestException.class,
                () -> service.create(PLAYER_ID, ACADEMY_ID, request(defaultA), auth));
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    void scoredResultOutsideTheEventMetricsIs400ButAComponentIsAccepted() {
        assertThrows(BadRequestException.class,
                () -> service.create(PLAYER_ID, ACADEMY_ID, request(defaultA, optional, defaultB), auth));

        service.create(PLAYER_ID, ACADEMY_ID, request(defaultA, optional, component), auth);
        verify(evaluationRepository).saveAndFlush(any());
    }

    @Test
    void templateOrVersionDifferentFromTheEventIs400() {
        for (var wrong : List.of(
                new EvaluationCreateRequest(UUID.randomUUID(), 3, results(defaultA, optional), null, null, null, EVENT_ID),
                new EvaluationCreateRequest(TEMPLATE_ID, 2, results(defaultA, optional), null, null, null, EVENT_ID))) {
            assertThrows(BadRequestException.class, () -> service.create(PLAYER_ID, ACADEMY_ID, wrong, auth));
        }
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    void contextFailuresStopBeforeAnythingIsWritten() {
        when(eventContextService.open(EVENT_ID, ACADEMY_ID, auth))
                .thenThrow(new ConflictException("Evaluations can only be recorded while the event is IN_PROGRESS"));

        assertThrows(ConflictException.class,
                () -> service.create(PLAYER_ID, ACADEMY_ID, request(defaultA, optional), auth));
        verify(playerRepository, never()).findById(any());
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownPlayerIs404BeforeTheRosterCheck() {
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.create(PLAYER_ID, ACADEMY_ID, request(defaultA, optional), auth));
        verify(eventContextService, never()).requireEvaluable(any(), any());
    }

    @Test
    void withoutEventTheAcademyHeaderIsIgnoredAndTheContextIsNeverUsed() {
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(PLAYER_ID, ACADEMY_ID,
                new EvaluationCreateRequest(TEMPLATE_ID, 3, results(defaultA), null, null, null), auth));
        verifyNoInteractions(eventContextService);
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private static EvaluationEntity withId(EvaluationEntity evaluation) {
        evaluation.setEvaluationId(UUID.randomUUID());
        return evaluation;
    }

    private EvaluationCreateRequest request(EvaluationCriterionEntity... criteria) {
        return new EvaluationCreateRequest(TEMPLATE_ID, 3, results(criteria), null, null, null, EVENT_ID);
    }

    private static List<EvaluationCreateRequest.ResultRequest> results(EvaluationCriterionEntity... criteria) {
        return java.util.Arrays.stream(criteria).map(c -> new EvaluationCreateRequest.ResultRequest(
                c.getCriterionId(), null, null, null, 1, null, null, null, null)).toList();
    }

    private EvaluationCriterionEntity criterion(String code, boolean activeByDefault, boolean scored) {
        EvaluationCriterionEntity criterion = new EvaluationCriterionEntity();
        criterion.setCriterionId(UUID.randomUUID());
        criterion.setTemplate(template);
        criterion.setCode(code);
        criterion.setMetricType("ERROR_COUNT");
        criterion.setLateralityMode("NONE");
        criterion.setActiveByDefault(activeByDefault);
        criterion.setScored(scored);
        return criterion;
    }
}
