package com.dvillacinda.canteragt.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import com.dvillacinda.canteragt.position.mapper.PositionMapper;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;
import com.dvillacinda.canteragt.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {
    private static final AuthenticatedUser PLAYER_TOKEN = new AuthenticatedUser(
            "kc-player", "player@example.com", "player", List.of("PLAYER"));

    @Mock
    private UserRepository userRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private CoachRepository coachRepository;
    @Mock
    private AcademyAdminRepository academyAdminRepository;
    @Spy
    private UserMapper userMapper = new UserMapper();
    @Spy
    private PositionMapper positionMapper = new PositionMapper();

    @InjectMocks
    private CurrentUserService currentUserService;

    @Test
    void returnsPlayerProfileForRegisteredPlayer() {
        UserEntity user = UserEntity.builder().userId(UUID.randomUUID()).keycloakId("kc-player")
                .username("player").status(UserStatus.ACTIVE).build();
        PositionEntity striker = PositionEntity.builder().positionId(UUID.randomUUID())
                .positionCode(PositionCode.ST).positionLine(PositionLine.FORWARD).name("Delantero centro").build();
        PlayerEntity player = PlayerEntity.builder().playerId(UUID.randomUUID()).user(user)
                .birthDate(LocalDate.of(2012, 5, 1)).sex(Sex.MALE).principalPosition(striker)
                .secondaryPositions(Set.of()).build();
        when(userRepository.findByKeycloakId("kc-player")).thenReturn(Optional.of(user));
        when(playerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(player));
        when(coachRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.empty());

        CurrentUserResponse response = currentUserService.getCurrentUser(PLAYER_TOKEN);

        assertEquals(List.of("PLAYER"), response.roles());
        assertEquals(user.getUserId(), response.user().userId());
        assertEquals(player.getPlayerId(), response.player().playerId());
        assertEquals(PositionCode.ST, response.player().principalPosition().positionCode());
        assertNull(response.coach());
    }

    @Test
    void returnsCoachProfileForRegisteredCoach() {
        UserEntity user = UserEntity.builder().userId(UUID.randomUUID()).keycloakId("kc-coach").build();
        CoachEntity coach = CoachEntity.builder().coachId(UUID.randomUUID()).user(user).build();
        when(userRepository.findByKeycloakId("kc-coach")).thenReturn(Optional.of(user));
        when(playerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.empty());
        when(coachRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(coach));

        CurrentUserResponse response = currentUserService.getCurrentUser(
                new AuthenticatedUser("kc-coach", "coach@example.com", "coach", List.of("COACH")));

        assertEquals(coach.getCoachId(), response.coach().coachId());
        assertNull(response.player());
    }

    @Test
    void returnsOnlyTokenRolesWhenUserIsNotRegistered() {
        when(userRepository.findByKeycloakId("kc-admin")).thenReturn(Optional.empty());

        CurrentUserResponse response = currentUserService.getCurrentUser(
                new AuthenticatedUser("kc-admin", "admin@example.com", "admin", List.of("SYSTEM_ADMIN")));

        assertEquals(List.of("SYSTEM_ADMIN"), response.roles());
        assertNull(response.user());
        assertNull(response.player());
        assertNull(response.coach());
        verifyNoInteractions(playerRepository, coachRepository);
    }
}
