package com.sinch.sms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ApiError> handleInvalidPhoneNumber(InvalidPhoneNumberException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_PHONE_NUMBER", exception.getMessage()));
    }

    @ExceptionHandler(InvalidMessageException.class)
    public ResponseEntity<ApiError> handleInvalidMessage(InvalidMessageException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_MESSAGE", exception.getMessage()));
    }

    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<ApiError> handleMessageNotFound(MessageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("MESSAGE_NOT_FOUND", exception.getMessage()));
    }
}
