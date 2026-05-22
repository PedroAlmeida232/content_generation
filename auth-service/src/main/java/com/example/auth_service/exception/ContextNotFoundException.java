package com.example.auth_service.exception;

/**
 * Exception thrown when a context cannot be found for the authenticated user.
 */
public class ContextNotFoundException extends RuntimeException {
	public ContextNotFoundException(String message) {
		super(message);
	}
}
