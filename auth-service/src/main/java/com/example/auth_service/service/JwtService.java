package com.example.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final String USER_ID_CLAIM = "userId";

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtService(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.expiration-ms}") long expirationMs
	) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("JWT secret must be configured");
		}

		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public String generateToken(UUID userId, String email) {
		Instant now = Instant.now();
		Instant expiration = now.plusMillis(expirationMs);

		return Jwts.builder()
			.claim(USER_ID_CLAIM, userId.toString())
			.subject(email)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiration))
			.signWith(signingKey)
			.compact();
	}

	public String extractEmail(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public UUID extractUserId(String token) {
		return UUID.fromString(extractClaim(token, claims -> claims.get(USER_ID_CLAIM, String.class)));
	}

	public boolean isTokenValid(String token) {
		try {
			extractAllClaims(token);
			return !isTokenExpired(token);
		} catch (RuntimeException ex) {
			return false;
		}
	}

	public boolean isTokenExpired(String token) {
		try {
			return extractClaim(token, Claims::getExpiration).before(new Date());
		} catch (ExpiredJwtException ex) {
			return true;
		}
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		return claimsResolver.apply(extractAllClaims(token));
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

}
