package com.example.auth_service.dto;

import java.util.Map;

public record ErrorResponse(
	String message,
	Map<String, String> errors
) {
}
