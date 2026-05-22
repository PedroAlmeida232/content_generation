package com.example.auth_service.exception;

/**
 * Exception thrown when a user tries to create/update a context with a key that already exists for them.
 */
public class ContextAlreadyExistsException extends RuntimeException {
	public ContextAlreadyExistsException(String message) {
		super(message);
	}
}
