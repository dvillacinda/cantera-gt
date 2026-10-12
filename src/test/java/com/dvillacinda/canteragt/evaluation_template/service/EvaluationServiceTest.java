package com.dvillacinda.canteragt.evaluation_template.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    @Mock EvaluationRepository evaluationRepository;
    @Mock EvaluationTemplateRepository templateRepository;
    @Mock EvaluationCriterionRepository criterionRepository;
    @Mock PlayerRepository playerRepository;
    @Mock PlayerAssignmentRepository playerAssignmentRepository;
    @Mock CoachRepository coachRepository;
    @Mock CoachAssignmentRepository coachAssignmentRepository;
    @Mock UserRepository userRepository;
    @Mock AcademyAccessService academyAccessService;
    private EvaluationService service;
    private UserEntity user;
    private PlayerEntity player;
    private CoachEntity coach;
    private AcademyCategoryEntity category;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evaluationRepository, templateRepository, criterionRepository,
                playerRepository, playerAssignmentRepository, coachRepository, coachAssignmentRepository,
                userRepository, academyAccessService,
                Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC));
        user = UserEntity.builder().userId(UUID.randomUUID()).keycloakId("coach-a").status(UserStatus.ACTIVE).build();
        player = PlayerEntity.builder().playerId(PLAYER_ID).build();
        coach = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        category = AcademyCategoryEntity.builder().academyCategoryId(CATEGORY_ID)
                .academy(AcademyEntity.builder().academyId(ACADEMY_ID).build()).build();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("coach-a").build();
        auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COACH")));
    }

    private void arrangeCurrentPlayerAndCoach() {
        when(userRepository.findByKeycloakId("coach-a")).thenReturn(Optional.of(user));
        when(coachRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(coach));
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(playerAssignmentRepository.findCurrentAssignments(PLAYER_ID, TODAY)).thenReturn(List.of(
                PlayerAssignmentEntity.builder().player(player).academyCategory(category)
                        .status(PlayerAssignmentStatus.ACTIVE).startDate(TODAY).build()));
    }

    @Test
    void coachFromAnotherAcademyCannotSubmitEvaluation() {
        arrangeCurrentPlayerAndCoach();
        when(academyAccessService.requireAcademyReadAccess(auth, ACADEMY_ID))
                .thenThrow(new UnauthorizedAccessException("No active assignment for the selected academy"));

        assertThrows(UnauthorizedAccessException.class,
                () -> service.create(PLAYER_ID, new EvaluationCreateRequest(TEMPLATE_ID, 1, List.of(), null, null, null), auth));

        verify(evaluationRepository, never()).save(org.mockito.ArgumentMatchers.any(EvaluationEntity.class));
        verify(templateRepository, never()).findByTemplateIdAndIsOfficialTrueAndStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestCannotSelectAnObsoleteTemplateVersion() {
        arrangeCurrentPlayerAndCoach();
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(coach.getCoachId(), CATEGORY_ID, TODAY))
                .thenReturn(true);
        when(templateRepository.findByTemplateIdAndIsOfficialTrueAndStatus(TEMPLATE_ID, EvaluationTemplateStatus.ACTIVE))
                .thenReturn(Optional.of(EvaluationTemplateEntity.builder().templateId(TEMPLATE_ID).version(2)
                        .status(EvaluationTemplateStatus.ACTIVE).isOfficial(true).build()));

        assertThrows(BadRequestException.class,
                () -> service.create(PLAYER_ID, new EvaluationCreateRequest(TEMPLATE_ID, 1, List.of(), null, null, null), auth));

        verify(evaluationRepository, never()).save(org.mockito.ArgumentMatchers.any(EvaluationEntity.class));
    }
}
