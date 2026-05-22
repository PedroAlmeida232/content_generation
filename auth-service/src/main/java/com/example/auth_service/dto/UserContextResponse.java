package com.example.auth_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserContextResponse(
	UUID id,
	String contextKey,
	String contextValue,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
}
