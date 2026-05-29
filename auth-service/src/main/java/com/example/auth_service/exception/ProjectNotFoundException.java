package com.example.auth_service.exception;

/**
 * Exception thrown when a project cannot be found for the authenticated user.
 */
public class ProjectNotFoundException extends RuntimeException {
	public ProjectNotFoundException(String message) {
		super(message);
	}
}
