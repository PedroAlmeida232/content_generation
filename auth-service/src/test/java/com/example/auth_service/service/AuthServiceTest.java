package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.RegisterResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.InvalidCredentialsException;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;

class AuthServiceTest {

	private static final String JWT_SECRET = "12345678901234567890123456789012";
	private static final long EXPIRATION_MS = 86_400_000L;

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final JwtService jwtService = new JwtService(JWT_SECRET, EXPIRATION_MS);
	private final AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);

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
	void registerSetsIsActiveTrue() {
		RegisterRequest request = new RegisterRequest("user@example.com", "plain-text-password", "Pedro");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		authService.register(request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertEquals(Boolean.TRUE, userCaptor.getValue().getIsActive());
	}

	@Test
	void registerTrimsName() {
		RegisterRequest request = new RegisterRequest("user@example.com", "plain-text-password", "  Pedro  ");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		RegisterResponse response = authService.register(request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertEquals("Pedro", userCaptor.getValue().getName());
		assertEquals("Pedro", response.name());
	}

	@Test
	void registerReturnsSavedUserId() {
		UUID expectedId = UUID.randomUUID();
		RegisterRequest request = new RegisterRequest("user@example.com", "plain-text-password", "Pedro");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(expectedId);
			return user;
		});

		RegisterResponse response = authService.register(request);

		assertEquals(expectedId, response.id());
	}

	@Test
	void registerRejectsDuplicateEmail() {
		RegisterRequest request = new RegisterRequest("user@example.com", "plain-text-password", "Pedro");

		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		assertThrows(EmailAlreadyInUseException.class, () -> authService.register(request));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void loginReturnsJwtForValidCredentials() {
		User user = activeUser("user@example.com", "plain-text-password");

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		LoginResponse response = authService.login(new LoginRequest(" USER@example.com ", "plain-text-password"));

		assertEquals("Bearer", response.tokenType());
		assertEquals(user.getId(), response.userId());
		assertEquals(user.getEmail(), response.email());
		assertEquals(EXPIRATION_MS, response.expiresIn());
		assertTrue(jwtService.isTokenValid(response.token()));
		assertEquals(user.getEmail(), jwtService.extractEmail(response.token()));
		assertEquals(user.getId(), jwtService.extractUserId(response.token()));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void loginDoesNotPersistUser() {
		User user = activeUser("user@example.com", "plain-text-password");

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		authService.login(new LoginRequest("user@example.com", "plain-text-password"));

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void loginRejectsUnknownEmail() {
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

		assertThrows(InvalidCredentialsException.class,
			() -> authService.login(new LoginRequest("user@example.com", "plain-text-password")));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void loginRejectsWrongPassword() {
		User user = activeUser("user@example.com", "different-password");

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		assertThrows(InvalidCredentialsException.class,
			() -> authService.login(new LoginRequest("user@example.com", "plain-text-password")));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void refreshReturnsValidJwtForAuthenticatedPrincipal() {
		JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), "user@example.com");

		LoginResponse response = authService.refresh(principal);

		assertEquals("Bearer", response.tokenType());
		assertEquals(principal.userId(), response.userId());
		assertEquals(principal.email(), response.email());
		assertEquals(EXPIRATION_MS, response.expiresIn());
		assertTrue(jwtService.isTokenValid(response.token()));
		assertEquals(principal.email(), jwtService.extractEmail(response.token()));
		assertEquals(principal.userId(), jwtService.extractUserId(response.token()));
	}

	@Test
	void refreshDoesNotCallUserRepository() {
		JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), "user@example.com");

		authService.refresh(principal);

		verify(userRepository, never()).findById(any());
		verify(userRepository, never()).findByEmail(any());
		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).existsByEmail(any());
	}

	private User activeUser(String email, String rawPassword) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setIsActive(Boolean.TRUE);
		return user;
	}

}
