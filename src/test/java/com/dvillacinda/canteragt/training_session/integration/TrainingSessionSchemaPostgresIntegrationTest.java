package com.dvillacinda.canteragt.training_session.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.academy_location.entity.AcademyLocationEntity;
import com.dvillacinda.canteragt.academy_location.repository.AcademyLocationRepository;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;
import com.dvillacinda.canteragt.season.enums.SeasonStatus;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;

/** Requires an isolated PostgreSQL database; Flyway applies all migrations on context startup. */
@SpringBootTest
class TrainingSessionSchemaPostgresIntegrationTest {
    private static final LocalDateTime SESSION_DATE = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TrainingSessionRepository trainingSessionRepository;
    @Autowired private SessionPlayerRepository sessionPlayerRepository;
    @Autowired private SessionStaffRepository sessionStaffRepository;
    @Autowired private AcademyLocationRepository academyLocationRepository;

    @Test
    void persistsSessionRosterAndStaffAndIncrementsOptimisticVersion() {
        Fixture fixture = createFixture();

        UUID sessionId = inTransaction(() -> {
            TrainingSessionEntity session = saveSession(fixture, TrainingSessionStatus.PLANNED, null, null);
            assertEquals(0L, session.getVersion());

            SessionPlayerEntity player = sessionPlayerRepository.saveAndFlush(SessionPlayerEntity.builder()
                    .trainingSession(session)
                    .player(entityManager.getReference(PlayerEntity.class, fixture.playerId()))
                    .build());
            assertEquals(AttendanceStatus.PENDING, player.getAttendanceStatus());

            SessionStaffEntity staff = sessionStaffRepository.saveAndFlush(SessionStaffEntity.builder()
                    .trainingSession(session)
                    .coach(entityManager.getReference(CoachEntity.class, fixture.coachId()))
                    .participationType(SessionParticipationType.LEAD)
                    .build());
            assertNotNull(staff.getCreatedAt());

            session.setNotes("Updated before start");
            trainingSessionRepository.saveAndFlush(session);
            assertEquals(1L, session.getVersion());
            UUID savedSessionId = session.getSessionId();

            entityManager.clear();
            TrainingSessionEntity recovered = trainingSessionRepository
                    .findBySessionIdAndAcademyCategory_AcademyCategoryId(savedSessionId, fixture.academyCategoryId())
                    .orElseThrow();
            assertEquals("Updated before start", recovered.getNotes());
            assertEquals(1L, recovered.getVersion());
            assertEquals(1, sessionPlayerRepository
                    .findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
                            savedSessionId, fixture.academyCategoryId()).size());
            assertEquals(1, sessionStaffRepository
                    .findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
                            savedSessionId, fixture.academyCategoryId()).size());
            assertEquals(1, academyLocationRepository.findAllByAcademy_AcademyIdAndStatus(
                    fixture.academyId(), Status.ACTIVE).size());
            return savedSessionId;
        });

        assertNotNull(sessionId);
    }

    @Test
    void rejectsDuplicatePlayerAndCoachWithinOneSession() {
        Fixture fixture = createFixture();
        UUID sessionId = inTransaction(() -> saveSession(fixture, TrainingSessionStatus.PLANNED, null, null)
                .getSessionId());

        inTransaction(() -> sessionPlayerRepository.saveAndFlush(SessionPlayerEntity.builder()
                .trainingSession(entityManager.getReference(TrainingSessionEntity.class, sessionId))
                .player(entityManager.getReference(PlayerEntity.class, fixture.playerId()))
                .build()));
        assertThrows(DataIntegrityViolationException.class, () -> inTransaction(() ->
                sessionPlayerRepository.saveAndFlush(SessionPlayerEntity.builder()
                        .trainingSession(entityManager.getReference(TrainingSessionEntity.class, sessionId))
                        .player(entityManager.getReference(PlayerEntity.class, fixture.playerId()))
                        .build())));

        inTransaction(() -> sessionStaffRepository.saveAndFlush(SessionStaffEntity.builder()
                .trainingSession(entityManager.getReference(TrainingSessionEntity.class, sessionId))
                .coach(entityManager.getReference(CoachEntity.class, fixture.coachId()))
                .participationType(SessionParticipationType.LEAD)
                .build()));
        assertThrows(DataIntegrityViolationException.class, () -> inTransaction(() ->
                sessionStaffRepository.saveAndFlush(SessionStaffEntity.builder()
                        .trainingSession(entityManager.getReference(TrainingSessionEntity.class, sessionId))
                        .coach(entityManager.getReference(CoachEntity.class, fixture.coachId()))
                        .participationType(SessionParticipationType.ASSISTANT)
                        .build())));
    }

    @Test
    void rejectsIncoherentTimestampsAndUnknownDatabaseStatuses() {
        Fixture fixture = createFixture();

        assertThrows(DataIntegrityViolationException.class, () -> inTransaction(() ->
                saveSession(fixture, TrainingSessionStatus.IN_PROGRESS, null, null)));
        assertThrows(DataIntegrityViolationException.class, () -> inTransaction(() ->
                saveSession(fixture, TrainingSessionStatus.COMPLETED,
                        SESSION_DATE.plusHours(2), SESSION_DATE.plusHours(1))));

        UUID sessionId = inTransaction(() -> saveSession(fixture, TrainingSessionStatus.PLANNED, null, null)
                .getSessionId());
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO "session_players"
                  ("session_id", "player_id", "attendance_status", "created_at", "updated_at")
                VALUES (?, ?, 'UNKNOWN', now(), now())
                """, sessionId, fixture.playerId()));
    }

    @Test
    void cancelledSessionsRecordWhetherTheyStarted() {
        Fixture fixture = createFixture();

        UUID cancelledBeforeStart = inTransaction(() -> saveSession(
                fixture, TrainingSessionStatus.CANCELLED, null, null).getSessionId());
        UUID cancelledAfterStart = inTransaction(() -> saveSession(
                fixture, TrainingSessionStatus.CANCELLED,
                SESSION_DATE.minusHours(1), SESSION_DATE).getSessionId());

        assertNotNull(cancelledBeforeStart);
        assertNotNull(cancelledAfterStart);
    }

    @Test
    void databaseDefaultsNewAttendanceToPending() {
        Fixture fixture = createFixture();
        UUID sessionId = inTransaction(() -> saveSession(fixture, TrainingSessionStatus.PLANNED, null, null)
                .getSessionId());

        String status = jdbcTemplate.queryForObject("""
                INSERT INTO "session_players"
                  ("session_id", "player_id", "created_at", "updated_at")
                VALUES (?, ?, now(), now())
                RETURNING "attendance_status"
                """, String.class, sessionId, fixture.playerId());

        assertEquals("PENDING", status);
    }

    private TrainingSessionEntity saveSession(
            Fixture fixture, TrainingSessionStatus status, LocalDateTime startedAt, LocalDateTime endedAt) {
        TrainingSessionEntity session = TrainingSessionEntity.builder()
                .academyCategory(entityManager.getReference(AcademyCategoryEntity.class, fixture.academyCategoryId()))
                .location(entityManager.getReference(AcademyLocationEntity.class, fixture.locationId()))
                .createdByCoach(entityManager.getReference(CoachEntity.class, fixture.coachId()))
                .name("Schema test session")
                .sessionDate(SESSION_DATE)
                .status(status)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
        return trainingSessionRepository.saveAndFlush(session);
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return inTransaction(() -> {
            AcademyEntity academy = AcademyEntity.builder()
                    .name("Session schema academy " + suffix)
                    .status(Status.ACTIVE)
                    .build();
            entityManager.persist(academy);

            List<CategoryEntity> matchingCategories = entityManager.createQuery(
                    "select c from CategoryEntity c where c.minAge <= 13 and c.maxAge >= 13", CategoryEntity.class)
                    .setMaxResults(1).getResultList();
            CategoryEntity category = matchingCategories.isEmpty()
                    ? CategoryEntity.builder().name("Session schema category " + suffix)
                            .minAge(12).maxAge(13).categoryCode(CategoryCode.Sub13).status(Status.ACTIVE).build()
                    : matchingCategories.getFirst();
            if (category.getCategoryId() == null) entityManager.persist(category);

            SeasonEntity season = SeasonEntity.builder()
                    .name("SESSION_IT_" + suffix)
                    .startDate(LocalDate.of(2026, 1, 1))
                    .endDate(LocalDate.of(2027, 1, 1))
                    .status(SeasonStatus.ACTIVE)
                    .build();
            entityManager.persist(season);

            AcademyCategoryEntity academyCategory = AcademyCategoryEntity.builder()
                    .academy(academy)
                    .category(category)
                    .season(season)
                    .status(AcademyCategoryStatus.ACTIVE)
                    .build();
            entityManager.persist(academyCategory);

            AcademyLocationEntity location = AcademyLocationEntity.builder()
                    .academy(academy)
                    .name("Training field " + suffix)
                    .address("Test address")
                    .status(Status.ACTIVE)
                    .build();
            entityManager.persist(location);

            UserEntity coachUser = createUser("coach", suffix);
            UserEntity playerUser = createUser("player", suffix);
            entityManager.persist(coachUser);
            entityManager.persist(playerUser);

            CoachEntity coach = CoachEntity.builder().user(coachUser).build();
            entityManager.persist(coach);

            List<PositionEntity> existingPositions = entityManager.createQuery(
                    "select p from PositionEntity p where p.positionCode = :code", PositionEntity.class)
                    .setParameter("code", PositionCode.GK).setMaxResults(1).getResultList();
            PositionEntity position = existingPositions.isEmpty()
                    ? PositionEntity.builder().positionCode(PositionCode.GK)
                            .positionLine(PositionLine.GOALKEEPER).name("Schema test goalkeeper").build()
                    : existingPositions.getFirst();
            if (position.getPositionId() == null) entityManager.persist(position);

            PlayerEntity player = PlayerEntity.builder()
                    .user(playerUser)
                    .birthDate(LocalDate.of(2013, 1, 1))
                    .sex(Sex.OTHER)
                    .principalPosition(position)
                    .build();
            entityManager.persist(player);
            entityManager.flush();
            return new Fixture(academy.getAcademyId(), academyCategory.getAcademyCategoryId(),
                    location.getLocationId(), coach.getCoachId(), player.getPlayerId());
        });
    }

    private UserEntity createUser(String role, String suffix) {
        return UserEntity.builder()
                .keycloakId(UUID.randomUUID().toString())
                .username("session_it_" + role + "_" + suffix)
                .email("session_it_" + role + "_" + suffix + "@example.test")
                .firstName("Session")
                .lastName(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(ignored -> supplier.get());
    }

    private record Fixture(UUID academyId, UUID academyCategoryId, UUID locationId, UUID coachId, UUID playerId) { }
}
