package com.example.auth_service.exception;

/**
 * Exception thrown when a user with the given identifier cannot be found.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
