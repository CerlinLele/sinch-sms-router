package com.sinch.sms.controller;

import com.sinch.sms.dto.MessageResponse;
import com.sinch.sms.dto.SendMessageRequest;
import com.sinch.sms.model.Message;
import com.sinch.sms.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        Message message = messageService.sendMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(message));
    }

    @GetMapping("/{id}")
    public MessageResponse getMessage(@PathVariable String id) {
        return toResponse(messageService.getMessage(id));
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getDestinationNumber(),
                message.getContent(),
                message.getFormat(),
                message.getStatus(),
                message.getCarrier());
    }
}
