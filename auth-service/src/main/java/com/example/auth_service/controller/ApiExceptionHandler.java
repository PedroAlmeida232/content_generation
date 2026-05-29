package com.example.auth_service.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.auth_service.dto.ErrorResponse;
import com.example.auth_service.exception.ContextAlreadyExistsException;
import com.example.auth_service.exception.ContextNotFoundException;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.InvalidProjectListRequestException;
import com.example.auth_service.exception.InvalidCredentialsException;
import com.example.auth_service.exception.InvalidProjectSlidesException;
import com.example.auth_service.exception.ProjectNotFoundException;
import com.example.auth_service.exception.UserNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ContextNotFoundException.class)
	ResponseEntity<ErrorResponse> handleContextNotFound(
		ContextNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(ContextAlreadyExistsException.class)
	ResponseEntity<ErrorResponse> handleContextAlreadyExists(
		ContextAlreadyExistsException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	ResponseEntity<ErrorResponse> handleEmailAlreadyInUse(
		EmailAlreadyInUseException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ErrorResponse> handleInvalidCredentials(
		InvalidCredentialsException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(UserNotFoundException.class)
	ResponseEntity<ErrorResponse> handleUserNotFound(
		UserNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(ProjectNotFoundException.class)
	ResponseEntity<ErrorResponse> handleProjectNotFound(
		ProjectNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(InvalidProjectSlidesException.class)
	ResponseEntity<ErrorResponse> handleInvalidProjectSlides(
		InvalidProjectSlidesException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(InvalidProjectListRequestException.class)
	ResponseEntity<ErrorResponse> handleInvalidProjectListRequest(
		InvalidProjectListRequestException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		Map<String, String> errors = new LinkedHashMap<>();

		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return error(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadableMessage(
		HttpMessageNotReadableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "Malformed JSON request", Map.of(), request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorResponse> handleMissingRequestParameter(
		MissingServletRequestParameterException exception,
		HttpServletRequest request
	) {
		String message = "Missing required request parameter: " + exception.getParameterName();
		return error(HttpStatus.BAD_REQUEST, message, Map.of(), request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleRequestParameterTypeMismatch(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		String parameterName = exception.getName();
		String message = "Invalid value for request parameter: " + parameterName;
		return error(HttpStatus.BAD_REQUEST, message, Map.of(), request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(
		ConstraintViolationException exception,
		HttpServletRequest request
	) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach(violation ->
			errors.putIfAbsent(
				violation.getPropertyPath().toString(),
				violation.getMessage()
			));

		return error(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		return error(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Unexpected server error",
			Map.of(),
			request
		);
	}

	private ResponseEntity<ErrorResponse> error(
		HttpStatus status,
		String message,
		Map<String, String> errors,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status)
			.body(new ErrorResponse(message, errors));
	}

}
