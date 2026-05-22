package com.example.auth_service.controller;

import com.example.auth_service.dto.UserResponse;
import com.example.auth_service.domain.User;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class UserController {

    private final UserRepository userRepository;

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/me")
    public UserResponse getAuthenticatedUser(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        UUID userId = principal.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
