package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.support.TestDataFactory;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffAddRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionCreateRequest;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.service.SessionStaffService;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

import jakarta.persistence.EntityManager;

/**
 * Session staff against PostgreSQL: creator auto-registered as LEAD, coach assignment validity by real dates,
 * duplicate 409, single LEAD under concurrency, LEAD removal rules and the session_staff schema (no updated_at).
 * The access policy and current-user lookup are mocked (covered by their own tests).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest
class SessionStaffPostgresIntegrationTest {
    @Autowired private SessionStaffService staffService;
    @Autowired private TrainingSessionService sessionService;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @MockitoBean private TrainingSessionAccessPolicy accessPolicy;
    @MockitoBean private CurrentUserService currentUserService;

    private Fixture fx;
    private TestDataFactory factory;

    @BeforeEach
    void setUp() {
        fx = createFixture();
    }

    @Test
    void creatorIsRegisteredAsLeadWhenTheSessionIsCreated() {
        when(currentUserService.getCurrentUser(any())).thenReturn(new CurrentUserResponse(
                List.of("COACH"), List.of(), null, null, new CurrentUserResponse.CoachProfile(fx.head())));

        var created = sessionService.create(fx.academyCategoryId(), fx.academyId(),
                new TrainingSessionCreateRequest("Created by head", OffsetDateTime.now(clock), null, null),
                coachAuthentication());

        List<SessionStaffResponse> staff = staffService.list(created.sessionId(), fx.academyId(), null);
        assertEquals(1, staff.size());
        assertEquals(fx.head(), staff.getFirst().coachId());
        assertEquals(SessionParticipationType.LEAD, staff.getFirst().participationType());
        assertNotNull(staff.getFirst().createdAt());
    }

    @Test
    void onlyCoachesWithCurrentAssignmentInTheCategoryCanJoin() {
        UUID sessionId = newSession();

        for (UUID coachId : List.of(fx.expired(), fx.ended(), fx.future(), fx.otherCategory())) {
            assertThrows(BadRequestException.class, () -> add(sessionId, coachId, SessionParticipationType.ASSISTANT),
                    "coach " + coachId + " must be rejected");
        }
        assertEquals(0, staffRows(sessionId));

        var added = add(sessionId, fx.assistant(), SessionParticipationType.ASSISTANT);
        assertEquals("Assistant", added.firstName());
        assertEquals(1, staffRows(sessionId));
    }

    @Test
    void duplicateCoachIs409AndSecondLeadIs409() {
        UUID sessionId = newSession();
        add(sessionId, fx.head(), SessionParticipationType.LEAD);
        add(sessionId, fx.assistant(), SessionParticipationType.ASSISTANT);

        assertThrows(ConflictException.class, () -> add(sessionId, fx.assistant(), SessionParticipationType.SUPPORT));
        assertThrows(ConflictException.class, () -> add(sessionId, fx.support(), SessionParticipationType.LEAD));
        assertEquals(2, staffRows(sessionId));
        assertEquals(1, leadRows(sessionId));
    }

    /** No DB constraint enforces a single LEAD (T3.6-A): the session row lock must serialize the check. */
    @Test
    void concurrentLeadRequestsLeaveExactlyOneLead() throws Exception {
        UUID sessionId = newSession();
        CyclicBarrier bothRead = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothRead.await(10, TimeUnit.SECONDS);
            return null;
        }).when(accessPolicy).requireManageAccess(any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            List<Callable<SessionStaffResponse>> calls = List.of(
                    () -> add(sessionId, fx.head(), SessionParticipationType.LEAD),
                    () -> add(sessionId, fx.assistant(), SessionParticipationType.LEAD));
            for (Future<SessionStaffResponse> future : executor.invokeAll(calls, 30, TimeUnit.SECONDS)) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException ex) {
                    outcomes.add(ex.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, outcomes.stream().filter(SessionStaffResponse.class::isInstance).count(), outcomes.toString());
        assertInstanceOf(ConflictException.class,
                outcomes.stream().filter(o -> !(o instanceof SessionStaffResponse)).findFirst().orElseThrow());
        assertEquals(1, leadRows(sessionId));
    }

    @Test
    void leadIsProtectedWhileInProgressButReplaceableWhilePlanned() {
        UUID sessionId = newSession();
        add(sessionId, fx.head(), SessionParticipationType.LEAD);
        add(sessionId, fx.assistant(), SessionParticipationType.ASSISTANT);

        // PLANNED: the LEAD can be replaced (remove + add)
        staffService.removeStaff(sessionId, fx.academyId(), fx.head(), null);
        add(sessionId, fx.support(), SessionParticipationType.LEAD);

        sessionService.start(sessionId, fx.academyId(), null);
        assertThrows(ConflictException.class,
                () -> staffService.removeStaff(sessionId, fx.academyId(), fx.support(), null));
        staffService.removeStaff(sessionId, fx.academyId(), fx.assistant(), null);

        sessionService.cancel(sessionId, fx.academyId(), null);
        assertThrows(ConflictException.class, () -> add(sessionId, fx.head(), SessionParticipationType.SUPPORT));
        assertEquals(1, staffRows(sessionId));
        assertEquals(1, leadRows(sessionId));
    }

    @Test
    void sessionStaffTableHasCreatedAtButNoUpdatedAt() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'session_staff'
                """, String.class);

        assertFalse(columns.contains("updated_at"), columns.toString());
        assertEquals(true, columns.contains("created_at"), columns.toString());
        UUID sessionId = newSession();
        assertNotNull(add(sessionId, fx.assistant(), SessionParticipationType.ASSISTANT).createdAt());
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private SessionStaffResponse add(UUID sessionId, UUID coachId, SessionParticipationType type) {
        return staffService.addStaff(sessionId, fx.academyId(), new SessionStaffAddRequest(coachId, type), null);
    }

    private long staffRows(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM session_staff WHERE session_id = ?", Long.class, sessionId);
    }

    private long leadRows(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM session_staff WHERE session_id = ? AND participation_type = 'LEAD'",
                Long.class, sessionId);
    }

    private JwtAuthenticationToken coachAuthentication() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("staff-it-head")
                .claim("email", "staff-it@example.test").claim("preferred_username", "staff-it").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private UUID newSession() {
        return inTransaction(() -> factory.session(fx.academyCategoryId(), fx.head(), null, "Staff IT session",
                LocalDateTime.now(clock), TrainingSessionStatus.PLANNED, null, null).getSessionId());
    }

    private Fixture createFixture() {
        factory = new TestDataFactory(entityManager);
        LocalDate today = LocalDate.now(clock);
        return inTransaction(() -> {
            AcademyEntity academy = factory.academy("Staff");
            AcademyCategoryEntity academyCategory = factory.academyCategory(academy, "STAFF_IT", today);
            AcademyCategoryEntity otherAcademyCategory = factory.academyCategory(academy, "STAFF_IT_OTHER", today);

            // Category role (CoachRoleCode) is the coach's permanent role and is independent of the per-session
            // participation type: an ASSISTANT can join as ASSISTANT/SUPPORT, a FITNESS_COACH typically as SUPPORT,
            // and in tests below even a non-head coach may be recorded as session LEAD.
            UUID head = coach("Head", academyCategory, CoachRoleCode.HEAD_COACH,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            UUID assistant = coach("Assistant", academyCategory, CoachRoleCode.ASSISTANT,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            UUID support = coach("Support", academyCategory, CoachRoleCode.FITNESS_COACH,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), today);
            UUID expired = coach("Expired", academyCategory, CoachRoleCode.ASSISTANT,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(60), today.minusDays(1));
            UUID ended = coach("Ended", academyCategory, CoachRoleCode.ASSISTANT,
                    CoachAssignmentStatus.ENDED, today.minusDays(30), null);
            UUID future = coach("Future", academyCategory, CoachRoleCode.ASSISTANT,
                    CoachAssignmentStatus.ACTIVE, today.plusDays(1), null);
            UUID otherCategory = coach("Other", otherAcademyCategory, CoachRoleCode.ASSISTANT,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), academyCategory.getAcademyCategoryId(),
                    head, assistant, support, expired, ended, future, otherCategory);
        });
    }

    private UUID coach(String name, AcademyCategoryEntity academyCategory, CoachRoleCode role,
            CoachAssignmentStatus status, LocalDate start, LocalDate end) {
        CoachEntity coach = factory.coach("staff-" + name.toLowerCase(), name);
        factory.coachAssignment(coach, academyCategory, role, status, start, end);
        return coach.getCoachId();
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academyId, UUID academyCategoryId, UUID head, UUID assistant, UUID support,
            UUID expired, UUID ended, UUID future, UUID otherCategory) { }
}
