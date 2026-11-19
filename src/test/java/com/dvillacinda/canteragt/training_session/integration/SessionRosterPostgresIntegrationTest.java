package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;
import com.dvillacinda.canteragt.season.enums.SeasonStatus;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.RejectedItemsException;
import com.dvillacinda.canteragt.training_session.dto.AttendanceUpdateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayersAddRequest;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.training_session.service.SessionRosterService;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;

/**
 * Roster and attendance against PostgreSQL: assignment validity by real dates, whole-batch rollback, duplicate
 * 409 and completing with PENDING players. The access policy is mocked (covered by its own tests).
 * Requires the isolated {@code postgres-test} database (see docs/testing.md).
 */
@SpringBootTest
class SessionRosterPostgresIntegrationTest {
    @Autowired private SessionRosterService rosterService;
    @Autowired private TrainingSessionService sessionService;
    @Autowired private TrainingSessionRepository trainingSessionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @MockitoBean private TrainingSessionAccessPolicy accessPolicy;

    private Fixture fx;

    @BeforeEach
    void setUp() {
        fx = createFixture();
    }

    @Test
    void onlyPlayersWithCurrentAssignmentInTheSameCategoryCanBeCalled() {
        UUID sessionId = newSession();

        RejectedItemsException ex = assertThrows(RejectedItemsException.class, () -> add(sessionId,
                List.of(fx.expired(), fx.future(), fx.transferred(), fx.otherCategory())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(Map.of(
                fx.expired().toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                fx.future().toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                fx.transferred().toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY",
                fx.otherCategory().toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY"), ex.getRejected());

        var added = add(sessionId, List.of(fx.current()));
        assertEquals(1, added.size());
        assertEquals(AttendanceStatus.PENDING, added.getFirst().attendanceStatus());
        assertEquals("Current", added.getFirst().firstName());
    }

    @Test
    void batchWithOneInvalidIdRollsBackEntirely() {
        UUID sessionId = newSession();
        long versionBefore = version(sessionId);

        RejectedItemsException ex = assertThrows(RejectedItemsException.class,
                () -> add(sessionId, List.of(fx.current(), fx.expired())));

        assertEquals(Map.of(fx.expired().toString(), "NO_CURRENT_ASSIGNMENT_IN_CATEGORY"), ex.getRejected());
        assertEquals(0, rosterRows(sessionId), "no player from the rejected batch may be persisted");
        assertEquals(versionBefore, version(sessionId), "the session lock/version bump must roll back too");
    }

    @Test
    void duplicatePlayerIs409AndRosterIsUnchanged() {
        UUID sessionId = newSession();
        add(sessionId, List.of(fx.current()));

        RejectedItemsException ex = assertThrows(RejectedItemsException.class,
                () -> add(sessionId, List.of(fx.current())));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(Map.of(fx.current().toString(), "ALREADY_IN_SESSION"), ex.getRejected());
        assertEquals(1, rosterRows(sessionId));
    }

    @Test
    void allActiveCallsOnlyCurrentPlayersAndIsIdempotent() {
        UUID sessionId = newSession();

        var first = rosterService.addPlayers(sessionId, fx.academyId(),
                new SessionPlayersAddRequest(null, true), null);
        var second = rosterService.addPlayers(sessionId, fx.academyId(),
                new SessionPlayersAddRequest(null, true), null);

        assertEquals(List.of(fx.current(), fx.current2()).stream().sorted().toList(),
                first.stream().map(p -> p.playerId()).sorted().toList());
        assertTrue(second.isEmpty());
        assertEquals(2, rosterRows(sessionId));
    }

    @Test
    void completingWithPendingPlayersFailsAndListsThemThenSucceedsOnceAttendanceIsRecorded() {
        UUID sessionId = newSession();
        add(sessionId, List.of(fx.current(), fx.current2()));
        sessionService.start(sessionId, fx.academyId(), null);
        rosterService.updateAttendance(sessionId, fx.academyId(), fx.current(),
                new AttendanceUpdateRequest(AttendanceStatus.PRESENT), null);

        ConflictException pending = assertThrows(ConflictException.class,
                () -> sessionService.complete(sessionId, fx.academyId(), null));
        assertTrue(pending.getMessage().contains(fx.current2().toString()), pending.getMessage());
        assertTrue(!pending.getMessage().contains(fx.current().toString()), pending.getMessage());

        rosterService.updateAttendance(sessionId, fx.academyId(), fx.current2(),
                new AttendanceUpdateRequest(AttendanceStatus.JUSTIFIED), null);
        var completed = sessionService.complete(sessionId, fx.academyId(), null);
        assertEquals(TrainingSessionStatus.COMPLETED, completed.status());

        // closed session: attendance and roster are frozen
        assertThrows(ConflictException.class, () -> rosterService.updateAttendance(sessionId, fx.academyId(),
                fx.current(), new AttendanceUpdateRequest(AttendanceStatus.ABSENT), null));
        assertThrows(ConflictException.class,
                () -> rosterService.removePlayer(sessionId, fx.academyId(), fx.current2(), null));
        assertEquals("PRESENT", jdbcTemplate.queryForObject(
                "SELECT attendance_status FROM session_players WHERE session_id = ? AND player_id = ?",
                String.class, sessionId, fx.current()));
    }

    @Test
    void recordedAttendanceBlocksRemovalButPendingPlayerCanBeRemovedInProgress() {
        UUID sessionId = newSession();
        add(sessionId, List.of(fx.current(), fx.current2()));
        sessionService.start(sessionId, fx.academyId(), null);
        rosterService.updateAttendance(sessionId, fx.academyId(), fx.current(),
                new AttendanceUpdateRequest(AttendanceStatus.PRESENT), null);

        assertThrows(ConflictException.class,
                () -> rosterService.removePlayer(sessionId, fx.academyId(), fx.current(), null));
        rosterService.removePlayer(sessionId, fx.academyId(), fx.current2(), null);

        assertEquals(1, rosterRows(sessionId));
    }

    @Test
    void everyRosterMutationBumpsTheSessionVersion() {
        UUID sessionId = newSession();
        long v0 = version(sessionId);

        add(sessionId, List.of(fx.current()));
        long v1 = version(sessionId);
        rosterService.removePlayer(sessionId, fx.academyId(), fx.current(), null);
        long v2 = version(sessionId);

        assertEquals(v0 + 1, v1);
        assertEquals(v1 + 1, v2);
    }

    /**
     * Race: a player is being added (uncommitted, so invisible to the PENDING check) while another request
     * completes the session. Without the session lock + version bump, complete would succeed and leave a
     * COMPLETED session with a PENDING player. Expected: complete gets 409 and the session stays IN_PROGRESS.
     */
    @Test
    void completeRacingWithAnUncommittedAddConflictsInsteadOfClosingWithPendingPlayers() throws Exception {
        UUID sessionId = newSession();
        sessionService.start(sessionId, fx.academyId(), null);

        java.util.concurrent.CountDownLatch addHoldsLock = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("completer".equals(Thread.currentThread().getName())) {
                addHoldsLock.await(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            return null;
        }).when(accessPolicy).requireManageAccess(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        java.util.concurrent.atomic.AtomicReference<Throwable> completeOutcome =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread completer = new Thread(() -> {
            try {
                sessionService.complete(sessionId, fx.academyId(), null);
            } catch (Throwable ex) {
                completeOutcome.set(ex);
            }
        }, "completer");

        transactionTemplate.executeWithoutResult(status -> {
            add(sessionId, List.of(fx.current()));   // holds FOR UPDATE on the session until this tx commits
            completer.start();
            addHoldsLock.countDown();
            try {
                Thread.sleep(1_000);                  // let complete read, pass the PENDING check and block
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        completer.join(15_000);

        assertTrue(completeOutcome.get() instanceof ConflictException,
                "complete must fail with 409, got: " + completeOutcome.get());
        assertEquals("IN_PROGRESS", jdbcTemplate.queryForObject(
                "SELECT status FROM training_sessions WHERE session_id = ?", String.class, sessionId));
        assertEquals(1, rosterRows(sessionId));
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private List<com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse> add(
            UUID sessionId, List<UUID> playerIds) {
        return rosterService.addPlayers(sessionId, fx.academyId(),
                new SessionPlayersAddRequest(playerIds, null), null);
    }

    private long rosterRows(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM session_players WHERE session_id = ?", Long.class, sessionId);
    }

    private long version(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM training_sessions WHERE session_id = ?", Long.class, sessionId);
    }

    private UUID newSession() {
        return inTransaction(() -> trainingSessionRepository.saveAndFlush(TrainingSessionEntity.builder()
                .academyCategory(entityManager.getReference(AcademyCategoryEntity.class, fx.academyCategoryId()))
                .createdByCoach(entityManager.getReference(CoachEntity.class, fx.coachId()))
                .name("Roster IT session")
                .sessionDate(LocalDateTime.now(clock))
                .status(TrainingSessionStatus.PLANNED)
                .build()).getSessionId());
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        LocalDate today = LocalDate.now(clock);
        return inTransaction(() -> {
            AcademyEntity academy = AcademyEntity.builder()
                    .name("Roster academy " + suffix).status(Status.ACTIVE).build();
            entityManager.persist(academy);

            List<CategoryEntity> categories = entityManager.createQuery(
                    "select c from CategoryEntity c where c.minAge <= 13 and c.maxAge >= 13", CategoryEntity.class)
                    .setMaxResults(1).getResultList();
            CategoryEntity category = categories.isEmpty()
                    ? CategoryEntity.builder().name("Roster category " + suffix)
                            .minAge(12).maxAge(13).categoryCode(CategoryCode.Sub13).status(Status.ACTIVE).build()
                    : categories.getFirst();
            if (category.getCategoryId() == null) entityManager.persist(category);

            SeasonEntity season = SeasonEntity.builder()
                    .name("ROSTER_IT_" + suffix)
                    .startDate(today.minusYears(1)).endDate(today.plusYears(1))
                    .status(SeasonStatus.ACTIVE).build();
            entityManager.persist(season);
            SeasonEntity otherSeason = SeasonEntity.builder()
                    .name("ROSTER_IT_OTHER_" + suffix)
                    .startDate(today.minusYears(1)).endDate(today.plusYears(1))
                    .status(SeasonStatus.ACTIVE).build();
            entityManager.persist(otherSeason);

            AcademyCategoryEntity academyCategory = AcademyCategoryEntity.builder()
                    .academy(academy).category(category).season(season)
                    .status(AcademyCategoryStatus.ACTIVE).build();
            entityManager.persist(academyCategory);
            AcademyCategoryEntity otherAcademyCategory = AcademyCategoryEntity.builder()
                    .academy(academy).category(category).season(otherSeason)
                    .status(AcademyCategoryStatus.ACTIVE).build();
            entityManager.persist(otherAcademyCategory);

            UserEntity coachUser = user("Coach", suffix);
            entityManager.persist(coachUser);
            CoachEntity coach = CoachEntity.builder().user(coachUser).build();
            entityManager.persist(coach);

            List<PositionEntity> positions = entityManager.createQuery(
                    "select p from PositionEntity p where p.positionCode = :code", PositionEntity.class)
                    .setParameter("code", PositionCode.GK).setMaxResults(1).getResultList();
            PositionEntity position = positions.isEmpty()
                    ? PositionEntity.builder().positionCode(PositionCode.GK)
                            .positionLine(PositionLine.GOALKEEPER).name("Roster goalkeeper").build()
                    : positions.getFirst();
            if (position.getPositionId() == null) entityManager.persist(position);

            UUID current = player("Current", suffix, position, academyCategory,
                    PlayerAssignmentStatus.ACTIVE, today.minusDays(30), null);
            UUID current2 = player("Current2", suffix, position, academyCategory,
                    PlayerAssignmentStatus.ACTIVE, today.minusDays(30), today);
            UUID expired = player("Expired", suffix, position, academyCategory,
                    PlayerAssignmentStatus.ACTIVE, today.minusDays(60), today.minusDays(1));
            UUID future = player("Future", suffix, position, academyCategory,
                    PlayerAssignmentStatus.ACTIVE, today.plusDays(1), null);
            UUID transferred = player("Transferred", suffix, position, academyCategory,
                    PlayerAssignmentStatus.TRANSFERRED, today.minusDays(30), null);
            UUID otherCategory = player("Other", suffix, position, otherAcademyCategory,
                    PlayerAssignmentStatus.ACTIVE, today.minusDays(30), null);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), academyCategory.getAcademyCategoryId(), coach.getCoachId(),
                    current, current2, expired, future, transferred, otherCategory);
        });
    }

    private UUID player(String name, String suffix, PositionEntity position, AcademyCategoryEntity academyCategory,
            PlayerAssignmentStatus status, LocalDate start, LocalDate end) {
        UserEntity user = user(name, suffix);
        entityManager.persist(user);
        PlayerEntity player = PlayerEntity.builder()
                .user(user).birthDate(LocalDate.of(2013, 1, 1)).sex(Sex.OTHER).principalPosition(position).build();
        entityManager.persist(player);
        entityManager.persist(PlayerAssignmentEntity.builder()
                .player(player).academyCategory(academyCategory).status(status)
                .startDate(start).endDate(end).build());
        return player.getPlayerId();
    }

    private UserEntity user(String name, String suffix) {
        String handle = "roster_it_" + name.toLowerCase() + "_" + suffix;
        return UserEntity.builder()
                .keycloakId(UUID.randomUUID().toString())
                .username(handle)
                .email(handle + "@example.test")
                .firstName(name)
                .lastName("Roster")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academyId, UUID academyCategoryId, UUID coachId, UUID current, UUID current2,
            UUID expired, UUID future, UUID transferred, UUID otherCategory) { }
}
