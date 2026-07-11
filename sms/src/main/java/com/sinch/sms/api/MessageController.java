package com.sinch.sms.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sinch.sms.message.Message;
import com.sinch.sms.service.MessageService;

import jakarta.validation.Valid;

@RestController
public class MessageController {

	private final MessageService messageService;

	public MessageController(MessageService messageService) {
		this.messageService = messageService;
	}

	@PostMapping("/messages")
	public ResponseEntity<MessageResponse> send(@Valid @RequestBody SendMessageRequest request) {
		Message message = messageService.send(request.destinationNumber(), request.content(), request.format());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(message));
	}

	@GetMapping("/messages/{id}")
	public MessageResponse get(@PathVariable UUID id) {
		return toResponse(messageService.get(id));
	}

	private MessageResponse toResponse(Message message) {
		return new MessageResponse(message.id(), message.status(), message.carrier());
	}
}
