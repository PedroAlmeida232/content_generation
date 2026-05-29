package com.example.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
	@NotBlank(message = "Project title is required")
	@Size(max = 255, message = "Project title must not exceed 255 characters")
	String title,

	@Size(max = 4000, message = "Project description must not exceed 4000 characters")
	String description,

	@Size(max = 50, message = "Project status must not exceed 50 characters")
	String status
) {
}
