package com.example.auth_service.exception;

/**
 * Exception thrown when project list query parameters are invalid.
 */
public class InvalidProjectListRequestException extends RuntimeException {
	public InvalidProjectListRequestException(String message) {
		super(message);
	}
}
