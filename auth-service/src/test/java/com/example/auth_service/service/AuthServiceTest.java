package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.RegisterResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.repository.UserRepository;

class AuthServiceTest {

	private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final AuthService authService = new AuthService(userRepository, passwordEncoder);

	@Test
	void registerHashesPasswordAndReturnsSafeResponse() {
		RegisterRequest request = new RegisterRequest(" USER@example.com ", "plain-text-password", "Pedro");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		RegisterResponse response = authService.register(request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertEquals("user@example.com", savedUser.getEmail());
		assertEquals("Pedro", savedUser.getName());
		assertNotEquals("plain-text-password", savedUser.getPasswordHash());
		assertTrue(passwordEncoder.matches("plain-text-password", savedUser.getPasswordHash()));
		assertEquals(response.email(), savedUser.getEmail());
		assertEquals(response.name(), savedUser.getName());
	}

	@Test
	void registerRejectsDuplicateEmail() {
		RegisterRequest request = new RegisterRequest("user@example.com", "plain-text-password", "Pedro");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		assertThrows(EmailAlreadyInUseException.class, () -> authService.register(request));
		verify(userRepository, never()).save(any(User.class));
	}

}
