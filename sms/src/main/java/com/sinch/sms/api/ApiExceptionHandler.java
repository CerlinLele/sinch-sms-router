package com.sinch.sms.api;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.sinch.sms.message.MessageNotFoundException;
import com.sinch.sms.validation.DomainValidationException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse("MALFORMED_JSON", "Malformed JSON request"));
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		MethodArgumentTypeMismatchException.class,
		ConstraintViolationException.class,
		DomainValidationException.class
	})
	public ResponseEntity<ApiErrorResponse> handleValidationFailures(Exception ex) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR", messageFor(ex)));
	}

	@ExceptionHandler(MessageNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleMessageNotFound(MessageNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("MESSAGE_NOT_FOUND", ex.getMessage()));
	}

	private String messageFor(Exception ex) {
		if (ex instanceof MethodArgumentNotValidException manve) {
			String fieldMessage = manve.getBindingResult().getAllErrors().stream()
				.map(error -> error.getDefaultMessage())
				.filter(message -> message != null && !message.isBlank())
				.findFirst()
				.orElse("Validation failed");
			return fieldMessage;
		}
		if (ex instanceof ConstraintViolationException cve) {
			String violationMessage = cve.getConstraintViolations().stream()
				.map(violation -> violation.getMessage())
				.filter(message -> message != null && !message.isBlank())
				.collect(Collectors.joining(", "));
			return violationMessage.isBlank() ? "Validation failed" : violationMessage;
		}
		return ex.getMessage() == null || ex.getMessage().isBlank() ? "Validation failed" : ex.getMessage();
	}
}
