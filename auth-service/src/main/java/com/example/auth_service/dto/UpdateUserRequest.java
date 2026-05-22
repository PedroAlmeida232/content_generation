package com.example.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	@Size(max = 255)
	String name
) {
}
