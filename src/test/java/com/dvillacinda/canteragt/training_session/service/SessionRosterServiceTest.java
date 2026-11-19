package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.RejectedItemsException;
import com.dvillacinda.canteragt.training_session.dto.AttendanceUpdateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayersAddRequest;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionPlayerMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ExtendWith(MockitoExtension.class)
class SessionRosterServiceTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PLAYER_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    /** 2026-11-17 10:00 in Guatemala; at 00:30 UTC on the 18th it is still the 17th locally. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T00:30:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private SessionPlayerRepository sessionPlayerRepository;
    @Mock private PlayerAssignmentRepository playerAssignmentRepository;
    @Mock private TrainingSessionAccessPolicy accessPolicy;
    @Mock private EntityManager entityManager;
    @Mock private Authentication authentication;

    private SessionRosterService service;

    @BeforeEach
    void setUp() {
        service = new SessionRosterService(
                trainingSessionRepository,
                sessionPlayerRepository,
                playerAssignmentRepository,
                accessPolicy,
                new SessionPlayerMapper("America/Guatemala"),
                entityManager,
                CLOCK);
        lenient().when(entityManager.getReference(eq(PlayerEntity.class), any()))
                .thenAnswer(invocation -> PlayerEntity.builder().playerId(invocation.getArgument(1)).build());
    }

    // ---- Rule 1 + 4: only current assignments in the same category; initial attendance PENDING -------------

    @Test
    void explicitBatchAddsEligiblePlayersAsPendingUsingTheClockDate() {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of());
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A, PLAYER_B));
        when(sessionPlayerRepository.findRosterEntries(eq(SESSION_ID), any()))
                .thenReturn(List.of(entry(PLAYER_A, AttendanceStatus.PENDING), entry(PLAYER_B, AttendanceStatus.PENDING)));

        var added = service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A, PLAYER_B), null), authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionPlayerEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(sessionPlayerRepository).saveAllAndFlush(saved.capture());
        assertEquals(2, saved.getValue().size());
        assertTrue(saved.getValue().stream().allMatch(e -> e.getAttendanceStatus() == AttendanceStatus.PENDING));
        assertTrue(saved.getValue().stream().allMatch(e -> e.getTrainingSession() == session));
        assertEquals(2, added.size());
        verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
    }

    @Test
    void sessionRowIsLockedAndVersionBumpedBeforeAnyRosterCheck() {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of());
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A));

        service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A), null), authentication);

        InOrder order = inOrder(accessPolicy, entityManager, sessionPlayerRepository);
        order.verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        order.verify(entityManager).refresh(session, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).lock(session, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        order.verify(sessionPlayerRepository).findPlayerIdsBySession(SESSION_ID);
    }

    // ---- Rule 7: an invalid id fails the whole batch and names the rejected ids ------------------------------

    @Test
    void batchWithPlayerWithoutCurrentAssignmentIsRejectedAsAWholeListingTheIds() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of());
        // B: expired / transferred / other category -> the repository does not return it as current
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A));

        RejectedItemsException ex = assertThrows(RejectedItemsException.class, () -> service.addPlayers(
                SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A, PLAYER_B, PLAYER_C), null), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(2, ex.getRejected().size());
        assertEquals(SessionRosterService.NO_CURRENT_ASSIGNMENT, ex.getRejected().get(PLAYER_B.toString()));
        assertEquals(SessionRosterService.NO_CURRENT_ASSIGNMENT, ex.getRejected().get(PLAYER_C.toString()));
        verify(sessionPlayerRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void repeatedIdInsideTheRequestIsRejected() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of());
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A));

        RejectedItemsException ex = assertThrows(RejectedItemsException.class, () -> service.addPlayers(
                SESSION_ID, ACADEMY_ID, new SessionPlayersAddRequest(List.of(PLAYER_A, PLAYER_A), null),
                authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(SessionRosterService.DUPLICATED_IN_REQUEST, ex.getRejected().get(PLAYER_A.toString()));
        verify(sessionPlayerRepository, never()).saveAllAndFlush(anyList());
    }

    // ---- Rule 2: duplicates are 409, never 500 ---------------------------------------------------------------

    @Test
    void playerAlreadyInSessionIs409AndNothingIsAdded() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of(PLAYER_A));
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A, PLAYER_B));

        RejectedItemsException ex = assertThrows(RejectedItemsException.class, () -> service.addPlayers(
                SESSION_ID, ACADEMY_ID, new SessionPlayersAddRequest(List.of(PLAYER_A, PLAYER_B), null),
                authentication));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(Set.of(PLAYER_A.toString()), ex.getRejected().keySet());
        assertEquals(SessionRosterService.ALREADY_IN_SESSION, ex.getRejected().get(PLAYER_A.toString()));
        verify(sessionPlayerRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void uniqueConstraintRaceBecomesConflictNot500() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of());
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategoryIn(eq(CATEGORY_ID), any(), eq(TODAY)))
                .thenReturn(List.of(PLAYER_A));
        when(sessionPlayerRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("uk_session_player"));

        assertThrows(ConflictException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A), null), authentication));
    }

    // ---- all_active shortcut (PRE-GATE T3.5-A) ---------------------------------------------------------------

    @Test
    void allActiveAddsOnlyCurrentPlayersNotYetCalledAndIsIdempotent() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of(PLAYER_A));
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategory(CATEGORY_ID, TODAY))
                .thenReturn(List.of(PLAYER_A, PLAYER_B));

        service.addPlayers(SESSION_ID, ACADEMY_ID, new SessionPlayersAddRequest(null, true), authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(sessionPlayerRepository).findRosterEntries(eq(SESSION_ID), ids.capture());
        assertEquals(List.of(PLAYER_B), List.copyOf(ids.getValue()));
    }

    @Test
    void allActiveWithEveryoneAlreadyCalledAddsNothing() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(sessionPlayerRepository.findPlayerIdsBySession(SESSION_ID)).thenReturn(List.of(PLAYER_A));
        when(playerAssignmentRepository.findCurrentPlayerIdsForCategory(CATEGORY_ID, TODAY))
                .thenReturn(List.of(PLAYER_A));

        var added = service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(null, true), authentication);

        assertTrue(added.isEmpty());
        verify(sessionPlayerRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void requestMustCarryExactlyOneOfPlayerIdsOrAllActive() {
        assertThrows(BadRequestException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A), true), authentication));
        assertThrows(BadRequestException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(), false), authentication));
        assertThrows(BadRequestException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(null, null), authentication));
        verify(trainingSessionRepository, never()).findBySessionIdAndAcademyCategory_Academy_AcademyId(any(), any());
    }

    // ---- Rule 3: call/remove only in PLANNED or IN_PROGRESS --------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "COMPLETED", "CANCELLED" })
    void closedSessionsRejectRosterChanges(TrainingSessionStatus status) {
        givenSession(status);

        assertThrows(ConflictException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A), null), authentication));
        assertThrows(ConflictException.class,
                () -> service.removePlayer(SESSION_ID, ACADEMY_ID, PLAYER_A, authentication));
        verify(sessionPlayerRepository, never()).saveAllAndFlush(anyList());
        verify(sessionPlayerRepository, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "PLANNED", "IN_PROGRESS" })
    void pendingPlayerCanBeRemovedWhileSessionIsOpen(TrainingSessionStatus status) {
        givenSession(status);
        SessionPlayerEntity entry = entry(PLAYER_A, AttendanceStatus.PENDING);
        when(sessionPlayerRepository.findRosterEntry(SESSION_ID, PLAYER_A)).thenReturn(Optional.of(entry));

        service.removePlayer(SESSION_ID, ACADEMY_ID, PLAYER_A, authentication);

        verify(sessionPlayerRepository).delete(entry);
        verify(entityManager).flush();
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void playerWithRecordedAttendanceCannotBeRemoved(AttendanceStatus recorded) {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(sessionPlayerRepository.findRosterEntry(SESSION_ID, PLAYER_A))
                .thenReturn(Optional.of(entry(PLAYER_A, recorded)));

        assertThrows(ConflictException.class,
                () -> service.removePlayer(SESSION_ID, ACADEMY_ID, PLAYER_A, authentication));
        verify(sessionPlayerRepository, never()).delete(any());
    }

    @Test
    void removingPlayerNotInSessionIs404() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionPlayerRepository.findRosterEntry(SESSION_ID, PLAYER_A)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.removePlayer(SESSION_ID, ACADEMY_ID, PLAYER_A, authentication));
    }

    // ---- Rule 5: attendance only while IN_PROGRESS -----------------------------------------------------------

    @Test
    void attendanceIsRecordedWhileInProgress() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        SessionPlayerEntity entry = entry(PLAYER_A, AttendanceStatus.PENDING);
        when(sessionPlayerRepository.findRosterEntry(SESSION_ID, PLAYER_A)).thenReturn(Optional.of(entry));

        var response = service.updateAttendance(SESSION_ID, ACADEMY_ID, PLAYER_A,
                new AttendanceUpdateRequest(AttendanceStatus.LATE), authentication);

        assertEquals(AttendanceStatus.LATE, response.attendanceStatus());
        assertEquals(AttendanceStatus.LATE, entry.getAttendanceStatus());
        verify(entityManager).flush();
    }

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void attendanceIsRejectedOutsideInProgress(TrainingSessionStatus status) {
        givenSession(status);

        assertThrows(ConflictException.class, () -> service.updateAttendance(SESSION_ID, ACADEMY_ID, PLAYER_A,
                new AttendanceUpdateRequest(AttendanceStatus.PRESENT), authentication));
        verify(sessionPlayerRepository, never()).findRosterEntry(any(), any());
    }

    @Test
    void attendanceCannotBeSetBackToPending() {
        assertThrows(BadRequestException.class, () -> service.updateAttendance(SESSION_ID, ACADEMY_ID, PLAYER_A,
                new AttendanceUpdateRequest(AttendanceStatus.PENDING), authentication));
        verify(trainingSessionRepository, never()).findBySessionIdAndAcademyCategory_Academy_AcademyId(any(), any());
    }

    @Test
    void attendanceForPlayerNotInSessionIs404() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(sessionPlayerRepository.findRosterEntry(SESSION_ID, PLAYER_A)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.updateAttendance(SESSION_ID, ACADEMY_ID, PLAYER_A,
                new AttendanceUpdateRequest(AttendanceStatus.PRESENT), authentication));
    }

    // ---- Scope and read -------------------------------------------------------------------------------------

    @Test
    void sessionOutsideAcademyIs404BeforeAuthorization() {
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.list(SESSION_ID, ACADEMY_ID, authentication));
        assertThrows(NotFoundException.class, () -> service.addPlayers(SESSION_ID, ACADEMY_ID,
                new SessionPlayersAddRequest(List.of(PLAYER_A), null), authentication));
        verify(accessPolicy, never()).requireReadAccess(any(), any(), any());
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
    }

    @Test
    void rosterReadUsesReadAccessAndExposesOnlyNamePositionAndAttendance() {
        givenSessionForRead();
        when(sessionPlayerRepository.findRoster(SESSION_ID))
                .thenReturn(List.of(entry(PLAYER_A, AttendanceStatus.PRESENT)));

        var roster = service.list(SESSION_ID, ACADEMY_ID, authentication);

        verify(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
        assertEquals(1, roster.size());
        assertEquals("Ana", roster.getFirst().firstName());
        assertEquals("GK", roster.getFirst().principalPosition());
    }

    // ---- fixtures -------------------------------------------------------------------------------------------

    private TrainingSessionEntity givenSession(TrainingSessionStatus status) {
        TrainingSessionEntity session = givenSessionForRead();
        session.setStatus(status);
        return session;
    }

    private TrainingSessionEntity givenSessionForRead() {
        AcademyEntity academy = new AcademyEntity();
        academy.setAcademyId(ACADEMY_ID);
        AcademyCategoryEntity category = new AcademyCategoryEntity();
        category.setAcademyCategoryId(CATEGORY_ID);
        category.setAcademy(academy);
        category.setStatus(AcademyCategoryStatus.ACTIVE);
        TrainingSessionEntity session = TrainingSessionEntity.builder()
                .sessionId(SESSION_ID)
                .academyCategory(category)
                .status(TrainingSessionStatus.PLANNED)
                .version(0L)
                .build();
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        return session;
    }

    private SessionPlayerEntity entry(UUID playerId, AttendanceStatus status) {
        UserEntity user = UserEntity.builder().firstName("Ana").lastName("López")
                .email("hidden@example.test").keycloakId("hidden-kc").build();
        PositionEntity position = PositionEntity.builder().positionCode(PositionCode.GK).build();
        PlayerEntity player = PlayerEntity.builder().playerId(playerId).user(user).principalPosition(position).build();
        return SessionPlayerEntity.builder()
                .sessionPlayerId(UUID.randomUUID())
                .player(player)
                .attendanceStatus(status)
                .build();
    }
}
