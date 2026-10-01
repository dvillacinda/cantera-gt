package com.dvillacinda.canteragt.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.dvillacinda.canteragt.auth.service.KeycloakUserService;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.user.dto.UserUpdateRequest;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;
import com.dvillacinda.canteragt.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Spy
    private UserMapper userMapper = new UserMapper();
    @Mock
    private UserRepository userRepository;
    @Mock
    private CoachRepository coachRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private KeycloakUserService keycloakUserService;

    @InjectMocks
    private UserService userService;

    private UserEntity activeUser() {
        return UserEntity.builder().userId(UUID.randomUUID()).keycloakId("kc-1").email("old@example.com")
                .username("user").firstName("Ana").lastName("López").status(Status.ACTIVE).build();
    }

    @Test
    void inactivatingUserDisablesKeycloakAccount() {
        UserEntity user = activeUser();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        userService.updateStatus(user.getUserId(), Status.INACTIVE);

        verify(keycloakUserService).updateUser("kc-1", "old@example.com", "Ana", "López", false);
        assertEquals(Status.INACTIVE, user.getStatus());
    }

    @Test
    void updatingEmailIsSynchronizedWithKeycloak() {
        UserEntity user = activeUser();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        userService.updateUser(user.getUserId(), new UserUpdateRequest("new@example.com", null, null, null));

        verify(keycloakUserService).updateUser("kc-1", "new@example.com", "Ana", "López", true);
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    void keycloakIsRevertedWhenDatabaseWriteFails() {
        UserEntity user = activeUser();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("duplicated"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userService.updateStatus(user.getUserId(), Status.LOCKED));

        InOrder order = inOrder(keycloakUserService);
        order.verify(keycloakUserService).updateUser("kc-1", "old@example.com", "Ana", "López", false);
        order.verify(keycloakUserService).updateUser("kc-1", "old@example.com", "Ana", "López", true);
    }
}
