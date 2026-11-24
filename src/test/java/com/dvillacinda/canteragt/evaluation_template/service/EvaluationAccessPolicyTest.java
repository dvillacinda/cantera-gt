package com.dvillacinda.canteragt.evaluation_template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationAccessPolicy.Level;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EvaluationAccessPolicyTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T16:00:00Z"), ZoneId.of("America/Guatemala"));

    @Mock UserRepository userRepository;
    @Mock CoachRepository coachRepository;
    @Mock CoachAssignmentRepository coachAssignmentRepository;
    @Mock AcademyAccessService academyAccessService;

    private EvaluationAccessPolicy policy;
    private EvaluationEntity evaluation;
    private CoachEntity creator;

    @BeforeEach
    void setUp() {
        policy = new EvaluationAccessPolicy(userRepository, coachRepository, coachAssignmentRepository,
                academyAccessService, CLOCK);
        creator = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        evaluation = new EvaluationEntity();
        evaluation.setEvaluator(creator);
        evaluation.setAcademyCategory(AcademyCategoryEntity.builder().academyCategoryId(CATEGORY_ID)
                .academy(AcademyEntity.builder().academyId(ACADEMY_ID).build()).build());
    }

    @Test
    void creatorCanVoid() {
        var auth = givenCoach("creator", creator);

        var access = policy.accessOf(evaluation, auth);

        assertEquals(Level.VOID, access.level());
        assertEquals(creator, access.coach());
        verify(coachAssignmentRepository, never()).existsCurrentAssignmentForCategoryAndRole(any(), any(), any(), any());
    }

    /** DP-2.4: the category's current HEAD_COACH reads and voids what an assistant recorded (fixes R-5). */
    @Test
    void currentHeadCoachOfTheCategoryCanVoid() {
        CoachEntity head = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        var auth = givenCoach("head", head);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                head.getCoachId(), CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(true);

        assertEquals(Level.VOID, policy.accessOf(evaluation, auth).level());
    }

    @Test
    void anotherCoachWithoutHeadAssignmentGetsNothing() {
        CoachEntity assistant = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        var auth = givenCoach("assistant", assistant);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                assistant.getCoachId(), CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(false);

        assertEquals(Level.NONE, policy.accessOf(evaluation, auth).level());
    }

    @Test
    void adminInScopeCanOnlyRead() {
        var auth = token("admin", "ROLE_ACADEMY_ADMIN");
        when(userRepository.findByKeycloakId("admin")).thenReturn(Optional.of(user()));

        var access = policy.accessOf(evaluation, auth);

        assertEquals(Level.READ, access.level());
        assertNull(access.coach());
        verify(academyAccessService).requireAcademyAccess(auth, ACADEMY_ID);
    }

    @Test
    void adminOutOfScopeGetsNothing() {
        var auth = token("admin", "ROLE_ACADEMY_ADMIN");
        when(userRepository.findByKeycloakId("admin")).thenReturn(Optional.of(user()));
        when(academyAccessService.requireAcademyAccess(auth, ACADEMY_ID))
                .thenThrow(new UnauthorizedAccessException("No active administration assignment"));

        assertEquals(Level.NONE, policy.accessOf(evaluation, auth).level());
    }

    /** A coach is never treated as an admin: academy read access for coaches does not grant evaluation reads. */
    @Test
    void coachRoleAloneNeverReachesTheAdminCheck() {
        CoachEntity other = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        var auth = givenCoach("other", other);

        assertEquals(Level.NONE, policy.accessOf(evaluation, auth).level());
        verify(academyAccessService, never()).requireAcademyAccess(any(), any());
    }

    @Test
    void unknownUserOrMissingJwtGetsNothing() {
        when(userRepository.findByKeycloakId("ghost")).thenReturn(Optional.empty());
        assertEquals(Level.NONE, policy.accessOf(evaluation, token("ghost", "ROLE_COACH")).level());
        assertEquals(Level.NONE, policy.accessOf(evaluation, null).level());
    }

    private JwtAuthenticationToken givenCoach(String subject, CoachEntity coach) {
        UserEntity user = user();
        when(userRepository.findByKeycloakId(subject)).thenReturn(Optional.of(user));
        lenient().when(coachRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(coach));
        return token(subject, "ROLE_COACH");
    }

    private static UserEntity user() {
        return UserEntity.builder().userId(UUID.randomUUID()).build();
    }

    private static JwtAuthenticationToken token(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }
}
