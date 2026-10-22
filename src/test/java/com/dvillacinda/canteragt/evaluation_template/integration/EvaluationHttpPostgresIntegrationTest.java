package com.dvillacinda.canteragt.evaluation_template.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import com.dvillacinda.canteragt.auth.service.KeycloakUserService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Requires an isolated PostgreSQL database with the application migrations applied. */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationHttpPostgresIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private KeycloakUserService keycloakUserService;

    @Test
    void coachReadsOfficialTemplateCreatesAndRecoversPersistedEvaluation() throws Exception {
        Fixture fixture = createFixture();
        var coachJwt = jwt().jwt(token -> token.subject(fixture.coachSubject()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH")));
        var playerJwt = jwt().jwt(token -> token.subject(fixture.playerSubject()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        String ownProfileJson = mockMvc.perform(get("/api/v1/players/me").with(playerJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode ownProfile = objectMapper.readTree(ownProfileJson).path("data");
        assertEquals(fixture.playerId().toString(), ownProfile.path("player_id").asString());
        assertEquals("Integration", ownProfile.path("first_name").asString());
        assertEquals("it_player_" + fixture.suffix() + "@example.test", ownProfile.path("email").asString());
        assertFalse(ownProfile.has("user_id"));
        assertFalse(ownProfile.has("keycloak_id"));
        assertFalse(ownProfile.has("status"));
        assertFalse(ownProfile.has("academy_id"));
        String ownProfileWithForeignId = mockMvc.perform(get("/api/v1/players/me")
                        .param("player_id", fixture.otherPlayerId().toString()).with(playerJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(fixture.playerId().toString(), objectMapper.readTree(ownProfileWithForeignId)
                .path("data").path("player_id").asString());
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.playerId())
                        .header("X-Academy-Id", fixture.academyId()).with(playerJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/players/get-player-by-id/{id}", fixture.otherPlayerId())
                        .header("X-Academy-Id", fixture.academyId()).with(playerJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/players/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/players/me").with(coachJwt)).andExpect(status().isForbidden());

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

        var academyAdminJwt = jwt().jwt(token -> token.subject(fixture.adminSubject()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ACADEMY_ADMIN")));
        mockMvc.perform(patch("/api/v1/coaches/update-coach-by-id/{coachId}", fixture.otherCoachId())
                        .with(academyAdminJwt).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first_name\":\"Academy edit\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/coaches/update-coach-status-by-id/{coachId}/INACTIVE", fixture.otherCoachId())
                        .with(academyAdminJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/coaches/delete-coach-by-id/{coachId}", fixture.otherCoachId())
                        .with(academyAdminJwt))
                .andExpect(status().isForbidden());
        String coachCreateJson = """
                {"user_create_request":{"email":"new-coach-%s@example.test","username":"new-coach-%s","status":"ACTIVE"},
                 "first_name":"Admin-created","last_name":"Coach"}
                """.formatted(fixture.suffix(), fixture.suffix());
        mockMvc.perform(post("/api/v1/coaches/create-coach").with(academyAdminJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(coachCreateJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/update-user/{userId}", fixture.coachUserId())
                        .header("X-Academy-Id", fixture.academyId()).with(academyAdminJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"first_name\":\"Bypass\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/update-user-status-by-id/{userId}/INACTIVE", fixture.coachUserId())
                        .header("X-Academy-Id", fixture.academyId()).with(academyAdminJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/coach-assignments/update-coach-assignment-by-id/{id}",
                        fixture.otherCoachAssignmentId())
                        .header("X-Academy-Id", fixture.academyId()).with(academyAdminJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "select status from coach_assignments where coach_assignment_id = ?", String.class,
                fixture.otherCoachAssignmentId()));
        mockMvc.perform(patch("/api/v1/coach-assignments/update-coach-assignment-by-id/{id}",
                        fixture.ownCoachAssignmentId())
                        .header("X-Academy-Id", fixture.academyId()).with(academyAdminJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        var systemAdminJwt = jwt().jwt(token -> token.subject("it-system-admin"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        String createdKeycloakId = UUID.nameUUIDFromBytes(("it-created-" + fixture.suffix()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        org.mockito.Mockito.when(keycloakUserService.createUser(
                org.mockito.ArgumentMatchers.any(UserCreateRequest.class),
                org.mockito.ArgumentMatchers.eq("Admin-created"), org.mockito.ArgumentMatchers.eq("Coach")))
                .thenReturn(createdKeycloakId);
        String globalCoachCreateJson = mockMvc.perform(post("/api/v1/coaches/create-coach").with(systemAdminJwt)
                        .contentType(MediaType.APPLICATION_JSON).content(coachCreateJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode createdCoach = objectMapper.readTree(globalCoachCreateJson).path("data");
        UUID createdCoachId = UUID.fromString(createdCoach.path("coach_id").asString());
        UUID createdCoachUserId = UUID.fromString(createdCoach.path("user_response").path("user_id").asString());
        mockMvc.perform(patch("/api/v1/coaches/update-coach-by-id/{coachId}", fixture.otherCoachId())
                        .with(systemAdminJwt).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first_name\":\"Global change\"}"))
                .andExpect(status().isOk());
        assertEquals("Global change", jdbcTemplate.queryForObject("""
                select u.first_name from users u join coaches c using (user_id) where c.coach_id = ?
                """, String.class, fixture.otherCoachId()));
        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                "select status from coach_assignments where coach_assignment_id = ?", String.class,
                fixture.otherCoachAssignmentId()));
        mockMvc.perform(patch("/api/v1/coaches/update-coach-status-by-id/{coachId}/ACTIVE", fixture.otherCoachId())
                        .with(systemAdminJwt))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/coaches/delete-coach-by-id/{coachId}", createdCoachId)
                        .with(systemAdminJwt))
                .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from coaches where coach_id = ?",
                Integer.class, createdCoachId));
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from users where user_id = ?",
                Integer.class, createdCoachUserId));

        String recoveredJson = mockMvc.perform(get("/api/v1/players/{playerId}/evaluations/{evaluationId}",
                        fixture.playerId(), evaluationId).with(coachJwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode recovered = objectMapper.readTree(recoveredJson).path("data");
        assertEquals(created.path("evaluation_id").asString(), recovered.path("evaluation_id").asString());
        assertEquals(created.path("results"), recovered.path("results"));

        TemplateData optionalLateralityTemplate = new TransactionTemplate(transactionManager).execute(status -> {
            EvaluationTemplateEntity template = entityManager.createQuery("""
                    select t from EvaluationTemplateEntity t where t.code = 'PASE_RECEPCION'
                    and t.isOfficial = true and t.status = :active
                    """, EvaluationTemplateEntity.class)
                    .setParameter("active", EvaluationTemplateStatus.ACTIVE).getSingleResult();
            List<EvaluationCriterionEntity> criteria = entityManager.createQuery("""
                    select c from EvaluationCriterionEntity c where c.template.templateId = :templateId
                    order by c.displayOrder
                    """, EvaluationCriterionEntity.class)
                    .setParameter("templateId", template.getTemplateId()).getResultList();
            return new TemplateData(template.getTemplateId(), template.getVersion(), criteria);
        });
        assertFalse(optionalLateralityTemplate.criteria().stream()
                .noneMatch(criterion -> "OPTIONAL".equals(criterion.getLateralityMode())));
        for (String side : List.of("LEFT", "RIGHT")) {
            Map<String, Object> sideRequest = evaluationRequest(optionalLateralityTemplate.templateId(),
                    optionalLateralityTemplate.version(), optionalLateralityTemplate.criteria(), side);
            String sideCreatedJson = mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", fixture.playerId())
                            .with(coachJwt).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sideRequest)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode sideCreated = objectMapper.readTree(sideCreatedJson).path("data");
            UUID sideEvaluationId = UUID.fromString(sideCreated.path("evaluation_id").asString());
            JsonNode optionalResult = findResultWithLateralityMode(sideCreated.path("results"),
                    optionalLateralityTemplate.criteria(), "OPTIONAL");
            assertEquals(side, optionalResult.path("laterality").asString());
            String sideRecoveredJson = mockMvc.perform(get("/api/v1/players/{playerId}/evaluations/{evaluationId}",
                            fixture.playerId(), sideEvaluationId).with(coachJwt))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode recoveredOptionalResult = findResultWithLateralityMode(
                    objectMapper.readTree(sideRecoveredJson).path("data").path("results"),
                    optionalLateralityTemplate.criteria(), "OPTIONAL");
            assertEquals(side, recoveredOptionalResult.path("laterality").asString());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from evaluations where evaluation_id = ?", Integer.class, sideEvaluationId));
        }

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

        ObjectNode missingRequired = copyRequest(requestJson);
        ArrayNode incompleteResults = checkedResults(missingRequired);
        EvaluationCriterionEntity mandatory = fixture.criteria().stream()
                .filter(c -> c.isActiveByDefault() && c.isScored()).findFirst().orElseThrow();
        for (int i = incompleteResults.size() - 1; i >= 0; i--) {
            ObjectNode result = checkedObject(incompleteResults.get(i), "results[" + i + "]");
            if (mandatory.getCriterionId().toString().equals(result.path("criterion_id").asString())) {
                incompleteResults.remove(i);
            }
        }
        assertRejectedWithoutPersistence(fixture, missingRequired, 400);

        ObjectNode foreignCriterion = copyRequest(requestJson);
        ArrayNode wrongCriteria = checkedResults(foreignCriterion);
        checkedObject(wrongCriteria.get(0), "results[0]").put("criterion_id", UUID.randomUUID().toString());
        assertRejectedWithoutPersistence(fixture, foreignCriterion, 400);

        ObjectNode outOfRange = copyRequest(requestJson);
        ArrayNode invalidValues = checkedResults(outOfRange);
        int timedResult = -1;
        for (int i = 0; i < fixture.criteria().size(); i++) {
            if ("TIME".equals(fixture.criteria().get(i).getMetricType())) { timedResult = i; break; }
        }
        if (timedResult >= 0) {
            checkedObject(invalidValues.get(timedResult), "results[" + timedResult + "]")
                    .put("raw_value", new BigDecimal("-1"));
            assertRejectedWithoutPersistence(fixture, outOfRange, 400);
        }

        ObjectNode invalidObservation = copyRequest(requestJson);
        ArrayNode invalidObservationResults = checkedResults(invalidObservation);
        int observedResult = -1;
        for (int i = 0; i < invalidObservationResults.size(); i++) {
            if (invalidObservationResults.get(i).has("observations")) { observedResult = i; break; }
        }
        if (observedResult >= 0) {
            ArrayNode invalidObservations = objectMapper.createArrayNode();
            invalidObservations.addObject().put("sequence", 1).put("valid", true);
            checkedObject(invalidObservationResults.get(observedResult), "results[" + observedResult + "]")
                    .set("observations", invalidObservations);
            assertRejectedWithoutPersistence(fixture, invalidObservation, 400);
        }

        ObjectNode nonexistentTemplate = copyRequest(requestJson);
        nonexistentTemplate.put("template_id", UUID.randomUUID().toString());
        assertRejectedWithoutPersistence(fixture, nonexistentTemplate, 404);
        assertRejectedWithoutPersistence(fixture, copyRequest(requestJson), 404, UUID.randomUUID());

        assertEquals(0, jdbcTemplate.queryForObject("""
                select count(*) from evaluation_criteria c join evaluation_templates t using (template_id)
                where t.is_official and c.laterality_mode = 'REQUIRED'
                """, Integer.class),
                "The official catalog currently defines no bilateral-required criteria; do not add one just for this test.");
    }

    private void assertRejectedWithoutPersistence(Fixture fixture, JsonNode request, int expectedStatus)
            throws Exception {
        assertRejectedWithoutPersistence(fixture, request, expectedStatus, fixture.playerId());
    }

    private void assertRejectedWithoutPersistence(Fixture fixture, JsonNode request, int expectedStatus,
            UUID playerId) throws Exception {
        int before = jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class);
        mockMvc.perform(post("/api/v1/players/{playerId}/evaluations", playerId)
                        .with(jwt().jwt(token -> token.subject(fixture.coachSubject()))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COACH"))))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
        assertEquals(before, jdbcTemplate.queryForObject("select count(*) from evaluations", Integer.class));
    }

    private ObjectNode copyRequest(String requestJson) throws Exception {
        JsonNode parsed = objectMapper.readTree(requestJson);
        if (!(parsed instanceof ObjectNode objectNode)) {
            throw new AssertionError("Evaluation request must be a JSON object");
        }
        ObjectNode copy = objectNode.deepCopy();
        checkedResults(copy);
        return copy;
    }

    private ArrayNode checkedResults(ObjectNode request) {
        JsonNode resultsNode = request.get("results");
        if (!(resultsNode instanceof ArrayNode results)) {
            throw new AssertionError("Evaluation request field 'results' must be an array");
        }
        for (int i = 0; i < results.size(); i++) {
            ObjectNode result = checkedObject(results.get(i), "results[" + i + "]");
            JsonNode criterionId = result.get("criterion_id");
            if (criterionId == null || !criterionId.isString()) {
                throw new AssertionError("results[" + i + "].criterion_id must be a UUID string");
            }
            try {
                UUID.fromString(criterionId.asString());
            } catch (IllegalArgumentException invalidUuid) {
                throw new AssertionError("results[" + i + "].criterion_id must be a UUID string", invalidUuid);
            }
            for (String field : List.of("raw_value", "successes", "opportunities", "error_count", "level")) {
                JsonNode value = result.get(field);
                if (value != null && !value.isNull() && !value.isNumber()) {
                    throw new AssertionError("results[" + i + "]." + field + " must be numeric");
                }
            }
            for (String field : List.of("laterality", "comment")) {
                JsonNode value = result.get(field);
                if (value != null && !value.isNull() && !value.isString()) {
                    throw new AssertionError("results[" + i + "]." + field + " must be text");
                }
            }
            JsonNode observationsNode = result.get("observations");
            if (observationsNode == null || observationsNode.isNull()) continue;
            if (!(observationsNode instanceof ArrayNode observations)) {
                throw new AssertionError("results[" + i + "].observations must be an array");
            }
            for (int j = 0; j < observations.size(); j++) {
                ObjectNode observation = checkedObject(observations.get(j),
                        "results[" + i + "].observations[" + j + "]");
                for (String field : List.of("sequence", "level")) {
                    JsonNode value = observation.get(field);
                    if (value != null && !value.isNull() && !value.isIntegralNumber()) {
                        throw new AssertionError("observation field '" + field + "' must be an integer");
                    }
                }
                JsonNode value = observation.get("value");
                if (value != null && !value.isNull() && !value.isNumber()) {
                    throw new AssertionError("observation field 'value' must be numeric");
                }
                for (String field : List.of("success", "valid")) {
                    JsonNode booleanValue = observation.get(field);
                    if (booleanValue != null && !booleanValue.isNull() && !booleanValue.isBoolean()) {
                        throw new AssertionError("observation field '" + field + "' must be boolean");
                    }
                }
                JsonNode note = observation.get("note");
                if (note != null && !note.isNull() && !note.isString()) {
                    throw new AssertionError("observation field 'note' must be text");
                }
            }
        }
        return results;
    }

    private ObjectNode checkedObject(JsonNode value, String location) {
        if (!(value instanceof ObjectNode objectNode)) {
            throw new AssertionError(location + " must be a JSON object");
        }
        return objectNode;
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
            CoachAssignmentEntity ownCoachAssignment = CoachAssignmentEntity.builder().coach(coach)
                    .academyCategory(academyCategory).coachRole(role).startDate(start)
                    .status(CoachAssignmentStatus.ACTIVE).build();
            entityManager.persist(ownCoachAssignment);
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
            CoachAssignmentEntity otherCoachAssignment = CoachAssignmentEntity.builder().coach(otherCoach)
                    .academyCategory(otherAcademyCategory).coachRole(role).startDate(start)
                    .status(CoachAssignmentStatus.ACTIVE).build();
            entityManager.persist(otherCoachAssignment);
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
            return new Fixture(suffix, coachUser.getUserId(), coachUser.getKeycloakId(), playerUser.getKeycloakId(),
                    adminUser.getKeycloakId(), otherCoachUser.getKeycloakId(),
                    sameAcademyCoachUser.getKeycloakId(), futureCoachUser.getKeycloakId(),
                    inactiveCoachUser.getKeycloakId(), expiredCoachUser.getKeycloakId(),
                    player.getPlayerId(),
                    otherPlayer.getPlayerId(), categoryPlayer.getPlayerId(), otherCoach.getCoachId(), academy.getAcademyId(),
                    ownCoachAssignment.getCoachAssignmentId(), otherCoachAssignment.getCoachAssignmentId(),
                    template.getTemplateId(), template.getVersion(), criteria);
        });
    }

    private UserEntity createUser(String role, String suffix) {
        return UserEntity.builder().keycloakId(UUID.randomUUID().toString())
                .username("it_" + role + "_" + suffix).email("it_" + role + "_" + suffix + "@example.test")
                .firstName("Integration").lastName(role).status(UserStatus.ACTIVE).build();
    }

    private Map<String, Object> evaluationRequest(Fixture fixture) {
        int observations = 0;
        for (EvaluationCriterionEntity criterion : fixture.criteria()) {
            Integer attemptsCount = criterion.getAttemptsCount();
            if (attemptsCount != null) observations += attemptsCount;
        }
        fixture.setObservationCount(observations);
        return evaluationRequest(fixture.templateId(), fixture.templateVersion(), fixture.criteria(), "NONE");
    }

    private Map<String, Object> evaluationRequest(UUID templateId, Integer templateVersion,
            List<EvaluationCriterionEntity> criteria, String optionalLaterality) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (EvaluationCriterionEntity criterion : criteria) {
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
            String laterality = switch (criterion.getLateralityMode()) {
                case "REQUIRED" -> "LEFT";
                case "OPTIONAL" -> optionalLaterality;
                default -> "NONE";
            };
            result.put("laterality", laterality);
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
                result.put("observations", attempts);
            }
            results.add(result);
        }
        return Map.of("template_id", templateId, "template_version", templateVersion,
                "results", results, "strengths", "Fast start", "improvement_areas", "Keep posture", "general_comment", "");
    }

    private JsonNode findResultWithLateralityMode(JsonNode results, List<EvaluationCriterionEntity> criteria,
            String lateralityMode) {
        if (!results.isArray()) throw new AssertionError("Evaluation response results must be an array");
        for (EvaluationCriterionEntity criterion : criteria) {
            if (!lateralityMode.equals(criterion.getLateralityMode())) continue;
            for (JsonNode result : results) {
                if (criterion.getCriterionId().toString().equals(result.path("criterion_id").asString())) {
                    return result;
                }
            }
        }
        throw new AssertionError("No result found for laterality mode " + lateralityMode);
    }

    private record TemplateData(UUID templateId, Integer version, List<EvaluationCriterionEntity> criteria) {}

    private static final class Fixture {
        private final String suffix;
        private final UUID coachUserId;
        private final String coachSubject;
        private final String playerSubject;
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
        private final UUID ownCoachAssignmentId;
        private final UUID otherCoachAssignmentId;
        private final UUID templateId;
        private final Integer templateVersion;
        private final List<EvaluationCriterionEntity> criteria;
        private int observationCount;
        private Fixture(String suffix, UUID coachUserId, String coachSubject, String playerSubject,
                String adminSubject, String unassignedCoachSubject,
                String sameAcademyCoachSubject, String futureCoachSubject, String inactiveCoachSubject,
                String expiredCoachSubject, UUID playerId, UUID otherPlayerId, UUID otherCategoryPlayerId,
                UUID otherCoachId, UUID academyId, UUID ownCoachAssignmentId, UUID otherCoachAssignmentId,
                UUID templateId, Integer templateVersion,
                List<EvaluationCriterionEntity> criteria) {
            this.suffix = suffix;
            this.coachUserId = coachUserId;
            this.coachSubject = coachSubject;
            this.playerSubject = playerSubject;
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
            this.ownCoachAssignmentId = ownCoachAssignmentId;
            this.otherCoachAssignmentId = otherCoachAssignmentId;
            this.templateId = templateId;
            this.templateVersion = templateVersion;
            this.criteria = criteria;
        }
        String coachSubject() { return coachSubject; }
        String playerSubject() { return playerSubject; }
        String suffix() { return suffix; }
        UUID coachUserId() { return coachUserId; }
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
        UUID ownCoachAssignmentId() { return ownCoachAssignmentId; }
        UUID otherCoachAssignmentId() { return otherCoachAssignmentId; }
        UUID templateId() { return templateId; }
        Integer templateVersion() { return templateVersion; }
        List<EvaluationCriterionEntity> criteria() { return criteria; }
        int observationCount() { return observationCount; }
        void setObservationCount(int count) { observationCount = count; }
    }
}
