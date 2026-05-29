package com.example.auth_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectSlideResponse(
	UUID id,
	int slideOrder,
	String imageUrl,
	String caption,
	String promptUsed,
	LocalDateTime generatedAt
) {
}
