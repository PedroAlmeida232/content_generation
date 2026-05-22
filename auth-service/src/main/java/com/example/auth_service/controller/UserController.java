package com.example.auth_service.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.dto.UpdateUserRequest;
import com.example.auth_service.dto.UserResponse;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import com.example.auth_service.service.UserService;

@RestController
public class UserController {

	private final UserService userService;

	@Autowired
	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/users/me")
	public UserResponse getAuthenticatedUser(Authentication authentication) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userService.getUserProfile(principal.userId());
	}

	@PutMapping("/users/me")
	public UserResponse updateAuthenticatedUser(Authentication authentication, @Valid @RequestBody UpdateUserRequest request) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userService.updateUserProfile(principal.userId(), request);
	}
}
