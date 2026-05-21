package com.example.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
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
		this.signingKey = buildSigningKey(secret);
		this.expirationMs = expirationMs;
	}

	public String generateToken(UUID userId, String email) {
		return generateToken(Map.of(), userId, email);
	}

	public String generateToken(Map<String, Object> extraClaims, UUID userId, String email) {
		Instant now = Instant.now();
		Instant expiration = now.plusMillis(expirationMs);

		return Jwts.builder()
			.claims(extraClaims)
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
		String userId = extractClaim(token, claims -> claims.get(USER_ID_CLAIM, String.class));
		return UUID.fromString(userId);
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	public boolean isTokenValid(String token, UUID expectedUserId, String expectedEmail) {
		try {
			return expectedUserId.equals(extractUserId(token))
				&& expectedEmail.equals(extractEmail(token))
				&& !isTokenExpired(token);
		} catch (RuntimeException ex) {
			return false;
		}
	}

	public boolean isTokenExpired(String token) {
		try {
			return extractExpiration(token).before(new Date());
		} catch (ExpiredJwtException ex) {
			return true;
		}
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private SecretKey buildSigningKey(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("JWT secret must be configured");
		}

		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		return Keys.hmacShaKeyFor(keyBytes);
	}

	Key getSigningKey() {
		return signingKey;
	}

}
