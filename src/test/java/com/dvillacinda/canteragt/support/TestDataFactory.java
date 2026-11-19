package com.dvillacinda.canteragt.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.academy_location.entity.AcademyLocationEntity;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
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
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;

/**
 * Builders for PostgreSQL integration fixtures (academy, category, coaches, players, assignments, sessions).
 *
 * <p>Every method persists through the given {@link EntityManager}, so callers must run inside a transaction
 * (e.g. {@code TransactionTemplate}). Unique columns (names, usernames, emails, keycloak ids) carry a random
 * per-factory suffix, so tests never collide with each other or with seed data and need no cleanup.
 * Seeded rows (Sub-13 category, GK position, coach roles) are reused instead of duplicated.
 */
public final class TestDataFactory {
    private final EntityManager entityManager;
    private final String suffix = UUID.randomUUID().toString().replace("-", "");

    public TestDataFactory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String suffix() {
        return suffix;
    }

    public AcademyEntity academy(String label) {
        AcademyEntity academy = AcademyEntity.builder()
                .name(label + " academy " + suffix).status(Status.ACTIVE).build();
        entityManager.persist(academy);
        return academy;
    }

    /** ACTIVE academy category with its own ACTIVE season covering {@code today} ± 1 year. */
    public AcademyCategoryEntity academyCategory(AcademyEntity academy, String label, LocalDate today) {
        SeasonEntity season = SeasonEntity.builder()
                .name(label + "_" + suffix)
                .startDate(today.minusYears(1)).endDate(today.plusYears(1))
                .status(SeasonStatus.ACTIVE).build();
        entityManager.persist(season);
        AcademyCategoryEntity academyCategory = AcademyCategoryEntity.builder()
                .academy(academy).category(sub13Category()).season(season)
                .status(AcademyCategoryStatus.ACTIVE).build();
        entityManager.persist(academyCategory);
        return academyCategory;
    }

    public AcademyLocationEntity location(AcademyEntity academy, String label) {
        AcademyLocationEntity location = AcademyLocationEntity.builder()
                .academy(academy).name(label + " field " + suffix).address("Test address")
                .status(Status.ACTIVE).build();
        entityManager.persist(location);
        return location;
    }

    /** Persisted user whose keycloak id (the JWT subject) is {@code subject(handle)}. */
    public UserEntity user(String handle, String firstName, String lastName) {
        String unique = handle + "_" + suffix;
        UserEntity user = UserEntity.builder()
                .keycloakId(subject(handle))
                .username(unique)
                .email(unique + "@example.test")
                .firstName(firstName)
                .lastName(lastName)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        return user;
    }

    /** JWT subject (users.keycloak_id) of the user created with {@link #user(String, String, String)}. */
    public String subject(String handle) {
        // users.keycloak_id is varchar(36): a name-based UUID is stable per (handle, factory) and fits exactly
        return UUID.nameUUIDFromBytes((handle + "/" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    public CoachEntity coach(String handle, String firstName) {
        CoachEntity coach = CoachEntity.builder().user(user(handle, firstName, "Coach")).build();
        entityManager.persist(coach);
        return coach;
    }

    public CoachAssignmentEntity coachAssignment(CoachEntity coach, AcademyCategoryEntity academyCategory,
            CoachRoleCode role, CoachAssignmentStatus status, LocalDate start, LocalDate end) {
        CoachAssignmentEntity assignment = CoachAssignmentEntity.builder()
                .coach(coach).academyCategory(academyCategory).coachRole(coachRole(role))
                .status(status).startDate(start).endDate(end).build();
        entityManager.persist(assignment);
        return assignment;
    }

    /** Coach with an ACTIVE assignment that started 30 days ago and has no end date. */
    public CoachEntity currentCoach(String handle, String firstName, AcademyCategoryEntity academyCategory,
            CoachRoleCode role, LocalDate today) {
        CoachEntity coach = coach(handle, firstName);
        coachAssignment(coach, academyCategory, role, CoachAssignmentStatus.ACTIVE, today.minusDays(30), null);
        return coach;
    }

    public PlayerEntity player(String handle, String firstName) {
        PlayerEntity player = PlayerEntity.builder()
                .user(user(handle, firstName, "Player"))
                .birthDate(LocalDate.of(2013, 1, 1))
                .sex(Sex.OTHER)
                .principalPosition(goalkeeper())
                .build();
        entityManager.persist(player);
        return player;
    }

    public PlayerAssignmentEntity playerAssignment(PlayerEntity player, AcademyCategoryEntity academyCategory,
            PlayerAssignmentStatus status, LocalDate start, LocalDate end) {
        PlayerAssignmentEntity assignment = PlayerAssignmentEntity.builder()
                .player(player).academyCategory(academyCategory).status(status)
                .startDate(start).endDate(end).build();
        entityManager.persist(assignment);
        return assignment;
    }

    /** Player with an ACTIVE assignment that started 30 days ago and has no end date. */
    public PlayerEntity currentPlayer(String handle, String firstName, AcademyCategoryEntity academyCategory,
            LocalDate today) {
        PlayerEntity player = player(handle, firstName);
        playerAssignment(player, academyCategory, PlayerAssignmentStatus.ACTIVE, today.minusDays(30), null);
        return player;
    }

    /** Session persisted directly (bypassing the service); use references for ids created elsewhere. */
    public TrainingSessionEntity session(UUID academyCategoryId, UUID coachId, UUID locationId, String name,
            LocalDateTime sessionDate, TrainingSessionStatus status, LocalDateTime startedAt, LocalDateTime endedAt) {
        TrainingSessionEntity session = TrainingSessionEntity.builder()
                .academyCategory(entityManager.getReference(AcademyCategoryEntity.class, academyCategoryId))
                .createdByCoach(entityManager.getReference(CoachEntity.class, coachId))
                .location(locationId == null ? null : entityManager.getReference(AcademyLocationEntity.class, locationId))
                .name(name)
                .sessionDate(sessionDate)
                .status(status)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
        entityManager.persist(session);
        return session;
    }

    public CoachRoleEntity coachRole(CoachRoleCode code) {
        return entityManager.createQuery(
                "select r from CoachRoleEntity r where r.coachRoleCode = :code", CoachRoleEntity.class)
                .setParameter("code", code).getSingleResult();
    }

    private CategoryEntity sub13Category() {
        List<CategoryEntity> categories = entityManager.createQuery(
                "select c from CategoryEntity c where c.minAge <= 13 and c.maxAge >= 13", CategoryEntity.class)
                .setMaxResults(1).getResultList();
        if (!categories.isEmpty()) {
            return categories.getFirst();
        }
        CategoryEntity category = CategoryEntity.builder().name("Test category " + suffix)
                .minAge(12).maxAge(13).categoryCode(CategoryCode.Sub13).status(Status.ACTIVE).build();
        entityManager.persist(category);
        return category;
    }

    private PositionEntity goalkeeper() {
        List<PositionEntity> positions = entityManager.createQuery(
                "select p from PositionEntity p where p.positionCode = :code", PositionEntity.class)
                .setParameter("code", PositionCode.GK).setMaxResults(1).getResultList();
        if (!positions.isEmpty()) {
            return positions.getFirst();
        }
        PositionEntity position = PositionEntity.builder().positionCode(PositionCode.GK)
                .positionLine(PositionLine.GOALKEEPER).name("Test goalkeeper").build();
        entityManager.persist(position);
        return position;
    }
}
