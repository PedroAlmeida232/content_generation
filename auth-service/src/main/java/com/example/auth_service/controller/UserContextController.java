package com.example.auth_service.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.dto.CreateContextRequest;
import com.example.auth_service.dto.UpdateContextRequest;
import com.example.auth_service.dto.UserContextDto;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import com.example.auth_service.service.UserContextService;

@RestController
@RequestMapping("/contexts")
public class UserContextController {

	private final UserContextService userContextService;

	@Autowired
	public UserContextController(UserContextService userContextService) {
		this.userContextService = userContextService;
	}

	@GetMapping
	public List<UserContextDto> getContexts(Authentication authentication) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userContextService.getContexts(principal.userId());
	}

	@GetMapping("/{id}")
	public UserContextDto getContext(Authentication authentication, @PathVariable UUID id) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userContextService.getContext(principal.userId(), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserContextDto createContext(Authentication authentication, @Valid @RequestBody CreateContextRequest request) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userContextService.createContext(principal.userId(), request);
	}

	@PutMapping("/{id}")
	public UserContextDto updateContext(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody UpdateContextRequest request) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return userContextService.updateContext(principal.userId(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteContext(Authentication authentication, @PathVariable UUID id) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		userContextService.deleteContext(principal.userId(), id);
	}

}
