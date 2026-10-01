package com.dvillacinda.canteragt.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(Authentication authentication) {
        CurrentUserResponse response = currentUserService
                .getCurrentUser(AuthenticatedUser.fromAuthentication(authentication));
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Current user found successfully", response));
    }
}
