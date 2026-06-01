package com.example.auth_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectSummaryResponse(
	UUID id,
	String title,
	String description,
	String status,
	LocalDateTime createdAt,
	String firstSlideImageUrl
) {
}
