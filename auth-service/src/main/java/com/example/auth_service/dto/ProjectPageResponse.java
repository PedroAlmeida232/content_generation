package com.example.auth_service.dto;

import java.util.List;

public record ProjectPageResponse(
	List<ProjectSummaryResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
