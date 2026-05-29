package com.example.auth_service.exception;

/**
 * Exception thrown when the project slides payload is invalid.
 */
public class InvalidProjectSlidesException extends RuntimeException {
	public InvalidProjectSlidesException(String message) {
		super(message);
	}
}
