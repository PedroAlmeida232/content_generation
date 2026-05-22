package com.example.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateContextRequest(
	@NotBlank(message = "Context key is required")
	@Size(max = 100, message = "Context key must not exceed 100 characters")
	String contextKey,

	String contextValue
) {
}
