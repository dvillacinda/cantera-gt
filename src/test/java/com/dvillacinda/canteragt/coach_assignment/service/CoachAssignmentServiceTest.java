package com.dvillacinda.canteragt.coach_assignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentCreateRequest;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentUpdateRequest;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_assignment.mapper.CoachAssignmentMapper;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.repository.CoachRoleRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;

@ExtendWith(MockitoExtension.class)
class CoachAssignmentServiceTest {
    private static final UUID OWN_ACADEMY = UUID.randomUUID();
    private static final UUID OTHER_ACADEMY = UUID.randomUUID();

    @Mock
    private CoachAssignmentRepository coachAssignmentRepository;
    @Mock
    private AcademyCategoryRepository academyCategoryRepository;
    @Mock
    private CoachRepository coachRepository;
    @Mock
    private CoachRoleRepository coachRoleRepository;
    @Mock
    private AcademyAccessService academyAccessService;
    @Mock
    private Authentication authentication;
    @Spy
    private CoachAssignmentMapper coachAssignmentMapper = new CoachAssignmentMapper();

    @InjectMocks
    private CoachAssignmentService coachAssignmentService;

    private static CoachAssignmentEntity assignmentIn(UUID academyId) {
        AcademyCategoryEntity category = AcademyCategoryEntity.builder().academyCategoryId(UUID.randomUUID())
                .academy(AcademyEntity.builder().academyId(academyId).build()).build();
        return CoachAssignmentEntity.builder().coachAssignmentId(UUID.randomUUID())
                .academyCategory(category)
                .coach(CoachEntity.builder().coachId(UUID.randomUUID()).build())
                .coachRole(CoachRoleEntity.builder().coachRoleId(UUID.randomUUID()).build())
                .startDate(LocalDate.of(2026, 1, 1)).status(CoachAssignmentStatus.ACTIVE).build();
    }

    @Test
    void readOfAssignmentFromAnotherAcademyIsNotFound() {
        UUID foreignAssignmentId = UUID.randomUUID();
        when(academyAccessService.requireAcademyReadAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(coachAssignmentRepository.findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(
                foreignAssignmentId, OWN_ACADEMY)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> coachAssignmentService
                .getCoachAssignmentById(foreignAssignmentId, OWN_ACADEMY, authentication));
    }

    @Test
    void readIsScopedToTheAcademyReturnedByAccessCheck() {
        CoachAssignmentEntity own = assignmentIn(OWN_ACADEMY);
        when(academyAccessService.requireAcademyReadAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(coachAssignmentRepository.findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(
                own.getCoachAssignmentId(), OWN_ACADEMY)).thenReturn(Optional.of(own));

        assertEquals(own.getCoachAssignmentId(), coachAssignmentService
                .getCoachAssignmentById(own.getCoachAssignmentId(), OWN_ACADEMY, authentication).coachAssignmentId());
    }

    @Test
    void listingAnotherAcademyIsDeniedBeforeQuerying() {
        when(academyAccessService.requireAcademyReadAccess(authentication, OTHER_ACADEMY))
                .thenThrow(new UnauthorizedAccessException("No active assignment for the selected academy"));

        assertThrows(UnauthorizedAccessException.class,
                () -> coachAssignmentService.getCoachAssignments(OTHER_ACADEMY, authentication));
        verifyNoInteractions(coachAssignmentRepository);
    }

    @Test
    void createRejectsAcademyCategoryOfAnotherAcademy() {
        UUID foreignCategoryId = UUID.randomUUID();
        when(academyAccessService.requireAcademyAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(foreignCategoryId, OWN_ACADEMY))
                .thenReturn(Optional.empty());

        var request = new CoachAssignmentCreateRequest(foreignCategoryId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), CoachAssignmentStatus.ACTIVE);

        assertThrows(NotFoundException.class,
                () -> coachAssignmentService.createCoachAssignment(OWN_ACADEMY, request, authentication));
        verify(coachAssignmentRepository, never()).save(any());
    }

    @Test
    void updateCannotMoveAssignmentIntoAnotherAcademyCategory() {
        CoachAssignmentEntity own = assignmentIn(OWN_ACADEMY);
        UUID foreignCategoryId = UUID.randomUUID();
        when(academyAccessService.requireAcademyAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(coachAssignmentRepository.findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(
                own.getCoachAssignmentId(), OWN_ACADEMY)).thenReturn(Optional.of(own));
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(foreignCategoryId, OWN_ACADEMY))
                .thenReturn(Optional.empty());

        var request = new CoachAssignmentUpdateRequest(foreignCategoryId, null, null, null, null, null);

        assertThrows(NotFoundException.class, () -> coachAssignmentService
                .updateCoachAssignment(own.getCoachAssignmentId(), OWN_ACADEMY, request, authentication));
        verify(coachAssignmentRepository, never()).save(any());
    }

    @Test
    void updateRejectsEndDateBeforeStartDate() {
        CoachAssignmentEntity own = assignmentIn(OWN_ACADEMY);
        when(academyAccessService.requireAcademyAccess(authentication, OWN_ACADEMY)).thenReturn(OWN_ACADEMY);
        when(coachAssignmentRepository.findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(
                own.getCoachAssignmentId(), OWN_ACADEMY)).thenReturn(Optional.of(own));

        var request = new CoachAssignmentUpdateRequest(null, null, null, null, LocalDate.of(2025, 1, 1), null);

        assertThrows(BadRequestException.class, () -> coachAssignmentService
                .updateCoachAssignment(own.getCoachAssignmentId(), OWN_ACADEMY, request, authentication));
        verify(coachAssignmentRepository, never()).save(any());
    }
}
