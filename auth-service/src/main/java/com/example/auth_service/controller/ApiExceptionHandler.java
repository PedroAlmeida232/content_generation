package com.example.auth_service.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.auth_service.dto.ErrorResponse;
import com.example.auth_service.exception.ContextAlreadyExistsException;
import com.example.auth_service.exception.ContextNotFoundException;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.InvalidCredentialsException;
import com.example.auth_service.exception.InvalidProjectSlidesException;
import com.example.auth_service.exception.ProjectNotFoundException;
import com.example.auth_service.exception.UserNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ContextNotFoundException.class)
	ResponseEntity<ErrorResponse> handleContextNotFound(ContextNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(ContextAlreadyExistsException.class)
	ResponseEntity<ErrorResponse> handleContextAlreadyExists(ContextAlreadyExistsException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	ResponseEntity<ErrorResponse> handleEmailAlreadyInUse(EmailAlreadyInUseException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(ProjectNotFoundException.class)
	ResponseEntity<ErrorResponse> handleProjectNotFound(ProjectNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(InvalidProjectSlidesException.class)
	ResponseEntity<ErrorResponse> handleInvalidProjectSlides(InvalidProjectSlidesException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(exception.getMessage(), Map.of()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();

		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.badRequest()
			.body(new ErrorResponse("Validation failed", errors));
	}

}
