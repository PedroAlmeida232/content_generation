package com.example.auth_service.dto;

import java.util.UUID;

public record LoginResponse(
	String token,
	String tokenType,
	long expiresIn,
	UUID userId,
	String email
) {
}
