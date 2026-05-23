package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

	private static final String SECRET = "12345678901234567890123456789012";
	private static final String OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz123456";
	private static final String INVALID_TOKEN = "invalid.jwt.token";

	@Test
	void generateAndReadTokenClaims() {
		JwtService jwtService = new JwtService(SECRET, 60_000L);
		UUID userId = UUID.randomUUID();
		String email = "user@example.com";

		String token = jwtService.generateToken(userId, email);

		assertEquals(email, jwtService.extractEmail(token));
		assertEquals(userId, jwtService.extractUserId(token));
		assertTrue(jwtService.isTokenValid(token));
	}

	@Test
	void rejectsExpiredTokenBuiltWithPastExpiration() {
		JwtService jwtService = new JwtService(SECRET, 60_000L);
		String expiredToken = buildExpiredToken(SECRET);

		assertTrue(jwtService.isTokenExpired(expiredToken));
		assertFalse(jwtService.isTokenValid(expiredToken));
	}

	@Test
	void rejectsTokenWithZeroExpirationWindow() {
		JwtService jwtService = new JwtService(SECRET, 0L);
		String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

		assertTrue(jwtService.isTokenExpired(token));
		assertFalse(jwtService.isTokenValid(token));
	}

	@NullSource
	@ParameterizedTest
	@MethodSource("blankSecrets")
	void constructorRejectsInvalidSecret(String secret) {
		assertThrows(IllegalArgumentException.class, () -> new JwtService(secret, 60_000L));
	}

	static Stream<String> blankSecrets() {
		return Stream.of("", " ", "   ");
	}

	@Test
	void isTokenValidReturnsFalseForMalformedToken() {
		JwtService jwtService = new JwtService(SECRET, 60_000L);

		assertFalse(jwtService.isTokenValid(INVALID_TOKEN));
	}

	@Test
	void isTokenValidReturnsFalseForWrongSignature() {
		JwtService jwtService = new JwtService(SECRET, 60_000L);
		JwtService otherSigner = new JwtService(OTHER_SECRET, 60_000L);

		String token = otherSigner.generateToken(UUID.randomUUID(), "user@example.com");

		assertFalse(jwtService.isTokenValid(token));
	}

	@Test
	void getExpirationMsReturnsConfiguredValue() {
		long expirationMs = 123_456L;
		JwtService jwtService = new JwtService(SECRET, expirationMs);

		assertEquals(expirationMs, jwtService.getExpirationMs());
	}

	private static String buildExpiredToken(String secret) {
		SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		Instant past = Instant.now().minusSeconds(3600);

		return Jwts.builder()
			.claim("userId", UUID.randomUUID().toString())
			.subject("user@example.com")
			.issuedAt(Date.from(past.minusSeconds(1800)))
			.expiration(Date.from(past))
			.signWith(signingKey)
			.compact();
	}

}
