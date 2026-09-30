package com.dvillacinda.canteragt.auth.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;


@RestController 
@RequestMapping ("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return AuthenticatedUser.fromAuthentication(authentication);
    }

    
    
}
