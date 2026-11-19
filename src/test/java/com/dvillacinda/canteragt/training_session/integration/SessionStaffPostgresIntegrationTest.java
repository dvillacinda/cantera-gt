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
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;
import com.dvillacinda.canteragt.season.enums.SeasonStatus;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffAddRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionCreateRequest;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.training_session.service.SessionStaffService;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;
import com.dvillacinda.canteragt.user.entity.UserEntity;

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
    @Autowired private TrainingSessionRepository trainingSessionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @MockitoBean private TrainingSessionAccessPolicy accessPolicy;
    @MockitoBean private CurrentUserService currentUserService;

    private Fixture fx;

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
        return inTransaction(() -> trainingSessionRepository.saveAndFlush(TrainingSessionEntity.builder()
                .academyCategory(entityManager.getReference(AcademyCategoryEntity.class, fx.academyCategoryId()))
                .createdByCoach(entityManager.getReference(CoachEntity.class, fx.head()))
                .name("Staff IT session")
                .sessionDate(LocalDateTime.now(clock))
                .status(TrainingSessionStatus.PLANNED)
                .build()).getSessionId());
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        LocalDate today = LocalDate.now(clock);
        return inTransaction(() -> {
            AcademyEntity academy = AcademyEntity.builder()
                    .name("Staff academy " + suffix).status(Status.ACTIVE).build();
            entityManager.persist(academy);

            List<CategoryEntity> categories = entityManager.createQuery(
                    "select c from CategoryEntity c where c.minAge <= 13 and c.maxAge >= 13", CategoryEntity.class)
                    .setMaxResults(1).getResultList();
            CategoryEntity category = categories.isEmpty()
                    ? CategoryEntity.builder().name("Staff category " + suffix)
                            .minAge(12).maxAge(13).categoryCode(CategoryCode.Sub13).status(Status.ACTIVE).build()
                    : categories.getFirst();
            if (category.getCategoryId() == null) entityManager.persist(category);

            AcademyCategoryEntity academyCategory = academyCategory(academy, category, "STAFF_IT_" + suffix, today);
            AcademyCategoryEntity otherAcademyCategory =
                    academyCategory(academy, category, "STAFF_IT_OTHER_" + suffix, today);

            // Category role (CoachRoleCode) is the coach's permanent role and is independent of the per-session
            // participation type: an ASSISTANT can join as ASSISTANT/SUPPORT, a FITNESS_COACH typically as SUPPORT,
            // and in tests below even a non-head coach may be recorded as session LEAD.
            CoachRoleEntity headRole = role(CoachRoleCode.HEAD_COACH);
            CoachRoleEntity assistantRole = role(CoachRoleCode.ASSISTANT);
            CoachRoleEntity fitnessRole = role(CoachRoleCode.FITNESS_COACH);

            UUID head = coach("Head", suffix, academyCategory, headRole,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            UUID assistant = coach("Assistant", suffix, academyCategory, assistantRole,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            UUID support = coach("Support", suffix, academyCategory, fitnessRole,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), today);
            UUID expired = coach("Expired", suffix, academyCategory, assistantRole,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(60), today.minusDays(1));
            UUID ended = coach("Ended", suffix, academyCategory, assistantRole,
                    CoachAssignmentStatus.ENDED, today.minusDays(30), null);
            UUID future = coach("Future", suffix, academyCategory, assistantRole,
                    CoachAssignmentStatus.ACTIVE, today.plusDays(1), null);
            UUID otherCategory = coach("Other", suffix, otherAcademyCategory, assistantRole,
                    CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), academyCategory.getAcademyCategoryId(),
                    head, assistant, support, expired, ended, future, otherCategory);
        });
    }

    private AcademyCategoryEntity academyCategory(
            AcademyEntity academy, CategoryEntity category, String seasonName, LocalDate today) {
        SeasonEntity season = SeasonEntity.builder().name(seasonName)
                .startDate(today.minusYears(1)).endDate(today.plusYears(1)).status(SeasonStatus.ACTIVE).build();
        entityManager.persist(season);
        AcademyCategoryEntity academyCategory = AcademyCategoryEntity.builder()
                .academy(academy).category(category).season(season).status(AcademyCategoryStatus.ACTIVE).build();
        entityManager.persist(academyCategory);
        return academyCategory;
    }

    private CoachRoleEntity role(CoachRoleCode code) {
        return entityManager.createQuery(
                "select r from CoachRoleEntity r where r.coachRoleCode = :code", CoachRoleEntity.class)
                .setParameter("code", code).getSingleResult();
    }

    private UUID coach(String name, String suffix, AcademyCategoryEntity academyCategory, CoachRoleEntity role,
            CoachAssignmentStatus status, LocalDate start, LocalDate end) {
        String handle = "staff_it_" + name.toLowerCase() + "_" + suffix;
        UserEntity user = UserEntity.builder()
                .keycloakId(UUID.randomUUID().toString()).username(handle).email(handle + "@example.test")
                .firstName(name).lastName("Staff").status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        CoachEntity coach = CoachEntity.builder().user(user).build();
        entityManager.persist(coach);
        entityManager.persist(CoachAssignmentEntity.builder()
                .coach(coach).academyCategory(academyCategory).coachRole(role).status(status)
                .startDate(start).endDate(end).build());
        return coach.getCoachId();
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academyId, UUID academyCategoryId, UUID head, UUID assistant, UUID support,
            UUID expired, UUID ended, UUID future, UUID otherCategory) { }
}
