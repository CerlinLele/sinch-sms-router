package com.sinch.sms.service;

import com.sinch.sms.dto.SendMessageRequest;
import com.sinch.sms.exception.InvalidMessageException;
import com.sinch.sms.exception.MessageNotFoundException;
import com.sinch.sms.model.Carrier;
import com.sinch.sms.model.Message;
import com.sinch.sms.model.MessageStatus;
import com.sinch.sms.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final CarrierRoutingService carrierRoutingService;
    private final OptOutService optOutService;
    private final PhoneNumberValidator phoneNumberValidator;

    public MessageService(
            MessageRepository messageRepository,
            CarrierRoutingService carrierRoutingService,
            OptOutService optOutService,
            PhoneNumberValidator phoneNumberValidator) {
        this.messageRepository = messageRepository;
        this.carrierRoutingService = carrierRoutingService;
        this.optOutService = optOutService;
        this.phoneNumberValidator = phoneNumberValidator;
    }

    public Message sendMessage(SendMessageRequest request) {
        if (request == null) {
            throw new InvalidMessageException("Message request is required");
        }

        String destinationNumber = phoneNumberValidator.validate(request.destinationNumber());
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new InvalidMessageException("Message content is required");
        }

        if (request.format() == null) {
            throw new InvalidMessageException("Message format is required");
        }

        Carrier carrier = null;
        MessageStatus status;

        if (optOutService.isOptedOut(destinationNumber)) {
            status = MessageStatus.BLOCKED;
        } else {
            carrier = carrierRoutingService.route(destinationNumber);
            status = MessageStatus.SENT;
        }

        Message message = new Message(
                UUID.randomUUID().toString(),
                destinationNumber,
                content,
                request.format(),
                carrier,
                status);

        return messageRepository.save(message);
    }

    public Message getMessage(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidMessageException("Message id is required");
        }

        return messageRepository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + id));
    }
}
