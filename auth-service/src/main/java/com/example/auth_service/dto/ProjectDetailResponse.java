package com.example.auth_service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectDetailResponse(
	UUID id,
	String title,
	String description,
	String status,
	LocalDateTime createdAt,
	List<ProjectSlideResponse> slides
) {
}
