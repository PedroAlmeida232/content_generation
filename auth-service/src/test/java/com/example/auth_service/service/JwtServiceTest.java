package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String SECRET = "12345678901234567890123456789012";

	@Test
	void shouldGenerateAndReadTokenClaims() {
		JwtService jwtService = new JwtService(SECRET, 60_000L);
		UUID userId = UUID.randomUUID();
		String email = "user@example.com";

		String token = jwtService.generateToken(userId, email);

		assertEquals(email, jwtService.extractEmail(token));
		assertEquals(userId, jwtService.extractUserId(token));
		assertTrue(jwtService.isTokenValid(token));
	}

	@Test
	void shouldRejectExpiredToken() throws InterruptedException {
		JwtService jwtService = new JwtService(SECRET, 1L);
		String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

		Thread.sleep(10L);

		assertTrue(jwtService.isTokenExpired(token));
		assertFalse(jwtService.isTokenValid(token));
	}

	@Test
	void shouldRequireConfiguredSecret() {
		assertThrows(IllegalArgumentException.class, () -> new JwtService(" ", 60_000L));
	}

}
