package com.dvillacinda.canteragt.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.user.dto.UserResponse;
import com.dvillacinda.canteragt.user.dto.UserUpdateRequest;
import com.dvillacinda.canteragt.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'SYSTEM_ADMIN')")
    @GetMapping("/get-user/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID userId) {
        log.info("Getting user with id {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "User found successfully", response));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'SYSTEM_ADMIN')")
    @DeleteMapping("/delete-user/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserById(@PathVariable UUID userId) {
        log.info("Deleting user with id {}", userId);
        userService.deleteUserById(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "User deleted successfully", null));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'SYSTEM_ADMIN')")
    @PatchMapping("/update-user/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserById(@PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequest user) {
        log.info("Updating user with id {}", userId);
        UserResponse response = userService.updateUser(userId, user);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "User updated successfully", response));
    }

}
