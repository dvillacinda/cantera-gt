package com.dvillacinda.canteragt.evaluation_template.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
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
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Requires an isolated PostgreSQL database with the application migrations applied. */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationHttpPostgresIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void coachReadsOfficialTemplateCreatesAndRecoversPersistedEvaluation() throws Exception {
        Fixture fixture = createFixture();
        var coachJwt = jwt().jwt(token -> token.subject(fixture.coachSubject()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));

        String templatesJson = mockMvc.perform(get("/api/v1/evaluation-templates").with(coachJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode templates = objectMapper.readTree(templatesJson).path("data");
        assertEquals(10, templates.size());

        String templateJson = mockMvc.perform(get("/api/v1/evaluation-templates/{id}", fixture.templateId())
                        .with(coachJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(fixture.criteria().size(), objectMapper.readTree(templateJson).path("data").path("criteria").size());

        Map<String, Object> request = evaluationRequest(fixture);
        String requestJson = objectMapper.writeValueAsString(request);
        String createdJson = mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", fixture.playerId())
                        .with(coachJwt).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdJson).path("data");
        UUID evaluationId = UUID.fromString(created.path("evaluation_id").asString());
        assertNotNull(evaluationId);
        assertEquals(fixture.playerId().toString(), created.path("player_id").asString());

        int persistedCount = jdbcTemplate.queryForObject(
                "select count(*) from evaluations where evaluation_id = ?", Integer.class, evaluationId);
        int resultCount = jdbcTemplate.queryForObject(
                "select count(*) from evaluation_results where evaluation_id = ?", Integer.class, evaluationId);
        int observationCount = jdbcTemplate.queryForObject("""
                select count(*) from evaluation_observations eo
                join evaluation_results er using (evaluation_result_id)
                where er.evaluation_id = ?
                """, Integer.class, evaluationId);
        assertEquals(1, persistedCount);
        assertEquals(fixture.criteria().size(), resultCount);
        assertEquals(fixture.observationCount(), observationCount);

        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.playerId())
                        .header("X-Academy-Id", fixture.academyId()).with(coachJwt))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.otherPlayerId())
                        .header("X-Academy-Id", fixture.academyId()).with(coachJwt))
                .andExpect(status().isNotFound());
        for (String subject : List.of(fixture.futureCoachSubject(), fixture.inactiveCoachSubject(),
                fixture.expiredCoachSubject())) {
            mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.playerId())
                            .header("X-Academy-Id", fixture.academyId())
                            .with(jwt().jwt(token -> token.subject(subject))
                                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.otherCategoryPlayerId())
                        .header("X-Academy-Id", fixture.academyId()).with(coachJwt))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/coaches/get-coach-by-id/{id}", fixture.otherCoachId())
                        .header("X-Academy-Id", fixture.academyId())
                        .with(jwt().jwt(token -> token.subject(fixture.adminSubject()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_ADMIN")))))
                .andExpect(status().isNotFound());

        String recoveredJson = mockMvc.perform(get("/api/v1/players/{playerId}/evaluations/{evaluationId}",
                        fixture.playerId(), evaluationId).with(coachJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode recovered = objectMapper.readTree(recoveredJson).path("data");
        assertEquals(created.path("evaluation_id").asString(), recovered.path("evaluation_id").asString());
        assertEquals(created.path("results"), recovered.path("results"));

        var sameAcademyCoachJwt = jwt().jwt(token -> token.subject(fixture.sameAcademyCoachSubject()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));
        String sameAcademyEvaluationJson = mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", fixture.playerId())
                        .with(sameAcademyCoachJwt).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID sameAcademyEvaluationId = UUID.fromString(objectMapper.readTree(sameAcademyEvaluationJson)
                .path("data").path("evaluation_id").asString());
        String hiddenSameAcademyEvaluation = mockMvc.perform(get(
                        "/api/v1/players/{playerId}/evaluations/{evaluationId}", fixture.playerId(),
                        sameAcademyEvaluationId).with(coachJwt))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertNoEvaluationData(hiddenSameAcademyEvaluation);

        String otherAcademyEvaluationJson = mockMvc.perform(post(
                        "/api/v1/players/{playerId}/evaluations", fixture.otherPlayerId())
                        .with(jwt().jwt(token -> token.subject(fixture.unassignedCoachSubject()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH"))))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID otherAcademyEvaluationId = UUID.fromString(objectMapper.readTree(otherAcademyEvaluationJson)
                .path("data").path("evaluation_id").asString());
        String hiddenOtherAcademyEvaluation = mockMvc.perform(get(
                        "/api/v1/players/{playerId}/evaluations/{evaluationId}", fixture.otherPlayerId(),
                        otherAcademyEvaluationId).with(coachJwt))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertNoEvaluationData(hiddenOtherAcademyEvaluation);
        String nonexistentEvaluation = mockMvc.perform(get(
                        "/api/v1/players/{playerId}/evaluations/{evaluationId}", fixture.playerId(), UUID.randomUUID())
                        .with(coachJwt))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertNoEvaluationData(nonexistentEvaluation);

        int beforeInvalid = jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class);
        Map<String, Object> invalidVersion = new HashMap<>(request);
        invalidVersion.put("template_version", 999);
        mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", fixture.playerId())
                        .with(coachJwt).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidVersion)))
                .andExpect(status().isBadRequest());
        assertEquals(beforeInvalid, jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class));

        mockMvc.perform(get("/api/v1/players/{playerId}/evaluations/{evaluationId}",
                        UUID.randomUUID(), evaluationId).with(coachJwt))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", fixture.playerId())
                        .with(jwt().jwt(token -> token.subject(fixture.unassignedCoachSubject()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH"))))
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isForbidden());

        Map<String, Object> missingRequired = new HashMap<>(request);
        List<Map<String, Object>> incompleteResults = new ArrayList<>((List<Map<String, Object>>) request.get("results"));
        EvaluationCriterionEntity mandatory = fixture.criteria().stream()
                .filter(c -> c.isActiveByDefault() && c.isScored()).findFirst().orElseThrow();
        incompleteResults.removeIf(result -> mandatory.getCriterionId().equals(result.get("criterion_id")));
        missingRequired.put("results", incompleteResults);
        assertRejectedWithoutPersistence(fixture, missingRequired, 400);

        Map<String, Object> foreignCriterion = new HashMap<>(request);
        List<Map<String, Object>> wrongCriteria = new ArrayList<>((List<Map<String, Object>>) request.get("results"));
        Map<String, Object> wrongCriterionResult = new HashMap<>(wrongCriteria.get(0));
        wrongCriterionResult.put("criterion_id", UUID.randomUUID());
        wrongCriteria.set(0, wrongCriterionResult);
        foreignCriterion.put("results", wrongCriteria);
        assertRejectedWithoutPersistence(fixture, foreignCriterion, 400);

        Map<String, Object> outOfRange = new HashMap<>(request);
        List<Map<String, Object>> invalidValues = new ArrayList<>((List<Map<String, Object>>) request.get("results"));
        int timedResult = -1;
        for (int i = 0; i < fixture.criteria().size(); i++) {
            if ("TIME".equals(fixture.criteria().get(i).getMetricType())) { timedResult = i; break; }
        }
        if (timedResult >= 0) {
            Map<String, Object> invalidTimedResult = new HashMap<>(invalidValues.get(timedResult));
            invalidTimedResult.put("raw_value", new BigDecimal("-1"));
            invalidValues.set(timedResult, invalidTimedResult);
            outOfRange.put("results", invalidValues);
            assertRejectedWithoutPersistence(fixture, outOfRange, 400);
        }

        Map<String, Object> invalidObservation = new HashMap<>(request);
        List<Map<String, Object>> invalidObservationResults = new ArrayList<>((List<Map<String, Object>>) request.get("results"));
        int observedResult = -1;
        for (int i = 0; i < invalidObservationResults.size(); i++) {
            if (invalidObservationResults.get(i).containsKey("observations")) { observedResult = i; break; }
        }
        if (observedResult >= 0) {
            Map<String, Object> result = new HashMap<>(invalidObservationResults.get(observedResult));
            result.put("observations", List.of(Map.of("sequence", 1, "valid", true)));
            invalidObservationResults.set(observedResult, result);
            invalidObservation.put("results", invalidObservationResults);
            assertRejectedWithoutPersistence(fixture, invalidObservation, 400);
        }

        Map<String, Object> nonexistentTemplate = new HashMap<>(request);
        nonexistentTemplate.put("template_id", UUID.randomUUID());
        assertRejectedWithoutPersistence(fixture, nonexistentTemplate, 404);
        assertRejectedWithoutPersistence(fixture, request, 404, UUID.randomUUID());

        assertEquals(0, jdbcTemplate.queryForObject("""
                select count(*) from evaluation_criteria c join evaluation_templates t using (template_id)
                where t.is_official and c.laterality_mode = 'REQUIRED'
                """, Integer.class),
                "The official catalog currently defines no bilateral-required criteria; do not add one just for this test.");
    }

    private void assertRejectedWithoutPersistence(Fixture fixture, Map<String, Object> request,
            int expectedStatus) throws Exception {
        assertRejectedWithoutPersistence(fixture, request, expectedStatus, fixture.playerId());
    }

    private void assertRejectedWithoutPersistence(Fixture fixture, Map<String, Object> request,
            int expectedStatus, UUID playerId) throws Exception {
        int before = jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class);
        mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", playerId)
                        .with(jwt().jwt(token -> token.subject(fixture.coachSubject()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH"))))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
        assertEquals(before, jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class));
    }

    private void assertNoEvaluationData(String responseBody) {
        assertFalse(responseBody.contains("Fast start"));
        assertFalse(responseBody.contains("player_id"));
        assertFalse(responseBody.contains("results"));
        assertFalse(responseBody.contains("observations"));
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return new TransactionTemplate(transactionManager).execute(status -> {
            AcademyEntity academy = AcademyEntity.builder().name("Integration academy " + suffix)
                    .status(Status.ACTIVE).build();
            entityManager.persist(academy);
            List<CategoryEntity> matchingCategories = entityManager.createQuery(
                    "select c from CategoryEntity c where c.minAge <= 13 and c.maxAge >= 13", CategoryEntity.class)
                    .setMaxResults(1).getResultList();
            CategoryEntity category = matchingCategories.isEmpty()
                    ? CategoryEntity.builder().name("Integration Sub 13").minAge(12).maxAge(13)
                            .categoryCode(CategoryCode.Sub13).status(Status.ACTIVE).build()
                    : matchingCategories.get(0);
            if (category.getCategoryId() == null) {
                entityManager.persist(category);
            }
            SeasonEntity season = SeasonEntity.builder().name("IT_" + suffix)
                    .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(30))
                    .status(SeasonStatus.ACTIVE).build();
            entityManager.persist(season);
            AcademyCategoryEntity academyCategory = AcademyCategoryEntity.builder().academy(academy)
                    .category(category).season(season).status(AcademyCategoryStatus.ACTIVE).build();
            entityManager.persist(academyCategory);

            UserEntity coachUser = createUser("coach", suffix);
            UserEntity playerUser = createUser("player", suffix);
            UserEntity adminUser = createUser("admin", suffix);
            UserEntity otherCoachUser = createUser("other_coach", suffix);
            UserEntity otherPlayerUser = createUser("other_player", suffix);
            UserEntity sameAcademyCoachUser = createUser("same_academy_coach", suffix);
            UserEntity futureCoachUser = createUser("future_coach", suffix);
            UserEntity inactiveCoachUser = createUser("inactive_coach", suffix);
            UserEntity expiredCoachUser = createUser("expired_coach", suffix);
            entityManager.persist(coachUser);
            entityManager.persist(playerUser);
            entityManager.persist(adminUser);
            entityManager.persist(otherCoachUser);
            entityManager.persist(otherPlayerUser);
            entityManager.persist(sameAcademyCoachUser);
            entityManager.persist(futureCoachUser);
            entityManager.persist(inactiveCoachUser);
            entityManager.persist(expiredCoachUser);
            AcademyEntity otherAcademy = AcademyEntity.builder().name("Other integration academy " + suffix)
                    .status(Status.ACTIVE).build();
            entityManager.persist(otherAcademy);
            AcademyCategoryEntity otherAcademyCategory = AcademyCategoryEntity.builder().academy(otherAcademy)
                    .category(category).season(season).status(AcademyCategoryStatus.ACTIVE).build();
            entityManager.persist(otherAcademyCategory);
            entityManager.persist(AcademyAdminEntity.builder().user(adminUser).academy(academy)
                    .status(Status.ACTIVE).build());

            CoachEntity coach = CoachEntity.builder().user(coachUser).build();
            entityManager.persist(coach);
            CoachEntity otherCoach = CoachEntity.builder().user(otherCoachUser).build();
            entityManager.persist(otherCoach);
            CoachEntity sameAcademyCoach = CoachEntity.builder().user(sameAcademyCoachUser).build();
            entityManager.persist(sameAcademyCoach);
            CoachEntity futureCoach = CoachEntity.builder().user(futureCoachUser).build();
            entityManager.persist(futureCoach);
            CoachEntity inactiveCoach = CoachEntity.builder().user(inactiveCoachUser).build();
            entityManager.persist(inactiveCoach);
            CoachEntity expiredCoach = CoachEntity.builder().user(expiredCoachUser).build();
            entityManager.persist(expiredCoach);
            List<PositionEntity> existingPositions = entityManager.createQuery(
                    "select p from PositionEntity p", PositionEntity.class).setMaxResults(1).getResultList();
            PositionEntity position = existingPositions.isEmpty()
                    ? PositionEntity.builder().positionCode(PositionCode.GK).positionLine(PositionLine.GOALKEEPER)
                            .name("Integration goalkeeper").build()
                    : existingPositions.get(0);
            if (position.getPositionId() == null) {
                entityManager.persist(position);
            }
            PlayerEntity player = PlayerEntity.builder().user(playerUser).birthDate(LocalDate.now().minusYears(13))
                    .sex(Sex.OTHER).principalPosition(position).build();
            entityManager.persist(player);
            PlayerEntity otherPlayer = PlayerEntity.builder().user(otherPlayerUser)
                    .birthDate(LocalDate.now().minusYears(13)).sex(Sex.OTHER).principalPosition(position).build();
            entityManager.persist(otherPlayer);

            CoachRoleEntity role = entityManager.createQuery(
                    "select r from CoachRoleEntity r where r.coachRoleCode = :code", CoachRoleEntity.class)
                    .setParameter("code", CoachRoleCode.HEAD_COACH).getSingleResult();
            var start = LocalDate.now().minusDays(1);
            entityManager.persist(CoachAssignmentEntity.builder().coach(coach).academyCategory(academyCategory)
                    .coachRole(role).startDate(start).status(CoachAssignmentStatus.ACTIVE).build());
            entityManager.persist(CoachAssignmentEntity.builder().coach(sameAcademyCoach)
                    .academyCategory(academyCategory).coachRole(role).startDate(start)
                    .status(CoachAssignmentStatus.ACTIVE).build());
            entityManager.persist(CoachAssignmentEntity.builder().coach(futureCoach)
                    .academyCategory(academyCategory).coachRole(role).startDate(LocalDate.now().plusDays(1))
                    .status(CoachAssignmentStatus.ACTIVE).build());
            entityManager.persist(CoachAssignmentEntity.builder().coach(inactiveCoach)
                    .academyCategory(academyCategory).coachRole(role).startDate(start)
                    .status(CoachAssignmentStatus.INACTIVE).build());
            entityManager.persist(CoachAssignmentEntity.builder().coach(expiredCoach)
                    .academyCategory(academyCategory).coachRole(role).startDate(start)
                    .endDate(LocalDate.now().minusDays(1)).status(CoachAssignmentStatus.ACTIVE).build());
            entityManager.persist(PlayerAssignmentEntity.builder().player(player).academyCategory(academyCategory)
                    .startDate(start).status(PlayerAssignmentStatus.ACTIVE).build());
            entityManager.persist(CoachAssignmentEntity.builder().coach(otherCoach)
                    .academyCategory(otherAcademyCategory).coachRole(role).startDate(start)
                    .status(CoachAssignmentStatus.ACTIVE).build());
            entityManager.persist(PlayerAssignmentEntity.builder().player(otherPlayer)
                    .academyCategory(otherAcademyCategory).startDate(start)
                    .status(PlayerAssignmentStatus.ACTIVE).build());

            EvaluationTemplateEntity template = entityManager.createQuery("""
                    select t from EvaluationTemplateEntity t
                    where t.code = 'SPRINT_10M' and t.isOfficial = true and t.status = :active
                    """, EvaluationTemplateEntity.class)
                    .setParameter("active", EvaluationTemplateStatus.ACTIVE).getSingleResult();
            List<EvaluationCriterionEntity> criteria = entityManager.createQuery("""
                    select c from EvaluationCriterionEntity c where c.template.templateId = :templateId
                    order by c.displayOrder
                    """, EvaluationCriterionEntity.class)
                    .setParameter("templateId", template.getTemplateId()).getResultList();
            List<CategoryEntity> secondaryCategories = entityManager.createQuery(
                    "select c from CategoryEntity c where c.categoryCode = :code", CategoryEntity.class)
                    .setParameter("code", CategoryCode.Sub15).setMaxResults(1).getResultList();
            CategoryEntity secondaryCategory = secondaryCategories.isEmpty()
                    ? CategoryEntity.builder().name("Integration Sub 15").minAge(14).maxAge(15)
                            .categoryCode(CategoryCode.Sub15).status(Status.ACTIVE).build()
                    : secondaryCategories.get(0);
            if (secondaryCategory.getCategoryId() == null) entityManager.persist(secondaryCategory);
            AcademyCategoryEntity otherCategory = AcademyCategoryEntity.builder().academy(academy)
                    .category(secondaryCategory).season(season).status(AcademyCategoryStatus.ACTIVE).build();
            entityManager.persist(otherCategory);
            UserEntity categoryPlayerUser = createUser("other_category_player", suffix);
            entityManager.persist(categoryPlayerUser);
            PlayerEntity categoryPlayer = PlayerEntity.builder().user(categoryPlayerUser)
                    .birthDate(LocalDate.now().minusYears(13)).sex(Sex.OTHER).principalPosition(position).build();
            entityManager.persist(categoryPlayer);
            entityManager.persist(PlayerAssignmentEntity.builder().player(categoryPlayer)
                    .academyCategory(otherCategory).startDate(start).status(PlayerAssignmentStatus.ACTIVE).build());
            entityManager.flush();
            return new Fixture(coachUser.getKeycloakId(), adminUser.getKeycloakId(), otherCoachUser.getKeycloakId(),
                    sameAcademyCoachUser.getKeycloakId(), futureCoachUser.getKeycloakId(),
                    inactiveCoachUser.getKeycloakId(), expiredCoachUser.getKeycloakId(),
                    player.getPlayerId(),
                    otherPlayer.getPlayerId(), categoryPlayer.getPlayerId(), otherCoach.getCoachId(), academy.getAcademyId(),
                    template.getTemplateId(), template.getVersion(), criteria);
        });
    }

    private UserEntity createUser(String role, String suffix) {
        return UserEntity.builder().keycloakId(UUID.randomUUID().toString())
                .username("it_" + role + "_" + suffix).email("it_" + role + "_" + suffix + "@example.test")
                .firstName("Integration").lastName(role).status(UserStatus.ACTIVE).build();
    }

    private Map<String, Object> evaluationRequest(Fixture fixture) {
        List<Map<String, Object>> results = new ArrayList<>();
        int observations = 0;
        for (EvaluationCriterionEntity criterion : fixture.criteria()) {
            Map<String, Object> result = new HashMap<>();
            result.put("criterion_id", criterion.getCriterionId());
            String type = criterion.getMetricType();
            if ("TIME".equals(type)) result.put("raw_value", new BigDecimal("1.82"));
            if ("SUCCESS_RATIO".equals(type)) {
                int opportunities = Math.max(criterion.getMinOpportunities() == null ? 8 : criterion.getMinOpportunities(), 8);
                result.put("successes", opportunities);
                result.put("opportunities", opportunities);
            }
            if ("ERROR_COUNT".equals(type)) result.put("error_count", 0);
            if ("RUBRIC".equals(type) || "ANCHORED_SCALE".equals(type)) result.put("level", criterion.getScaleMin());
            result.put("laterality", "REQUIRED".equals(criterion.getLateralityMode()) ? "LEFT" : "NONE");
            if (criterion.getAttemptsCount() != null) {
                List<Map<String, Object>> attempts = new ArrayList<>();
                for (int i = 1; i <= criterion.getAttemptsCount(); i++) {
                    Map<String, Object> attempt = new HashMap<>();
                    attempt.put("sequence", i);
                    attempt.put("valid", true);
                    if ("TIME".equals(type)) attempt.put("value", new BigDecimal("1.85"));
                    else if ("SUCCESS_RATIO".equals(type)) attempt.put("success", true);
                    else if ("ERROR_COUNT".equals(type)) attempt.put("value", BigDecimal.ZERO);
                    else attempt.put("level", criterion.getScaleMin());
                    attempts.add(attempt);
                }
                observations += attempts.size();
                result.put("observations", attempts);
            }
            results.add(result);
        }
        fixture.setObservationCount(observations);
        return Map.of("template_id", fixture.templateId(), "template_version", fixture.templateVersion(),
                "results", results, "strengths", "Fast start", "improvement_areas", "Keep posture", "general_comment", "");
    }

    private static final class Fixture {
        private final String coachSubject;
        private final String adminSubject;
        private final String unassignedCoachSubject;
        private final String sameAcademyCoachSubject;
        private final String futureCoachSubject;
        private final String inactiveCoachSubject;
        private final String expiredCoachSubject;
        private final UUID playerId;
        private final UUID otherPlayerId;
        private final UUID otherCategoryPlayerId;
        private final UUID otherCoachId;
        private final UUID academyId;
        private final UUID templateId;
        private final Integer templateVersion;
        private final List<EvaluationCriterionEntity> criteria;
        private int observationCount;
        private Fixture(String coachSubject, String adminSubject, String unassignedCoachSubject,
                String sameAcademyCoachSubject, String futureCoachSubject, String inactiveCoachSubject,
                String expiredCoachSubject, UUID playerId, UUID otherPlayerId, UUID otherCategoryPlayerId,
                UUID otherCoachId, UUID academyId, UUID templateId, Integer templateVersion,
                List<EvaluationCriterionEntity> criteria) {
            this.coachSubject = coachSubject;
            this.adminSubject = adminSubject;
            this.unassignedCoachSubject = unassignedCoachSubject;
            this.sameAcademyCoachSubject = sameAcademyCoachSubject;
            this.futureCoachSubject = futureCoachSubject;
            this.inactiveCoachSubject = inactiveCoachSubject;
            this.expiredCoachSubject = expiredCoachSubject;
            this.playerId = playerId;
            this.otherPlayerId = otherPlayerId;
            this.otherCategoryPlayerId = otherCategoryPlayerId;
            this.otherCoachId = otherCoachId;
            this.academyId = academyId;
            this.templateId = templateId;
            this.templateVersion = templateVersion;
            this.criteria = criteria;
        }
        String coachSubject() { return coachSubject; }
        String adminSubject() { return adminSubject; }
        String unassignedCoachSubject() { return unassignedCoachSubject; }
        String sameAcademyCoachSubject() { return sameAcademyCoachSubject; }
        String futureCoachSubject() { return futureCoachSubject; }
        String inactiveCoachSubject() { return inactiveCoachSubject; }
        String expiredCoachSubject() { return expiredCoachSubject; }
        UUID playerId() { return playerId; }
        UUID otherPlayerId() { return otherPlayerId; }
        UUID otherCategoryPlayerId() { return otherCategoryPlayerId; }
        UUID otherCoachId() { return otherCoachId; }
        UUID academyId() { return academyId; }
        UUID templateId() { return templateId; }
        Integer templateVersion() { return templateVersion; }
        List<EvaluationCriterionEntity> criteria() { return criteria; }
        int observationCount() { return observationCount; }
        void setObservationCount(int count) { observationCount = count; }
    }
}
