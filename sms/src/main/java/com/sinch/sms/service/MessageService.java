package com.sinch.sms.service;

import java.util.UUID;

import com.sinch.sms.message.Message;
import com.sinch.sms.message.MessageFormat;
import com.sinch.sms.message.MessageNotFoundException;
import com.sinch.sms.message.MessageRepository;
import com.sinch.sms.message.MessageStatus;
import com.sinch.sms.routing.Carrier;
import com.sinch.sms.routing.CarrierRouter;
import com.sinch.sms.service.OptOutService;
import com.sinch.sms.validation.DomainValidationException;
import com.sinch.sms.validation.PhoneNumberValidator;

public class MessageService {

	private final MessageRepository messageRepository;
	private final CarrierRouter carrierRouter;
	private final PhoneNumberValidator phoneNumberValidator;
	private final OptOutService optOutService;

	public MessageService(
		MessageRepository messageRepository,
		CarrierRouter carrierRouter,
		PhoneNumberValidator phoneNumberValidator
	) {
		this(messageRepository, carrierRouter, phoneNumberValidator, new OptOutService(phoneNumberValidator));
	}

	public MessageService(
		MessageRepository messageRepository,
		CarrierRouter carrierRouter,
		PhoneNumberValidator phoneNumberValidator,
		OptOutService optOutService
	) {
		this.messageRepository = messageRepository;
		this.carrierRouter = carrierRouter;
		this.phoneNumberValidator = phoneNumberValidator;
		this.optOutService = optOutService;
	}

	public Message send(String destinationNumber, String content, MessageFormat format) {
		String validatedNumber = phoneNumberValidator.validate(destinationNumber);
		validateContent(content);
		validateFormat(format);

		boolean optedOut = optOutService.isOptedOut(validatedNumber);
		Carrier carrier = optedOut ? null : carrierRouter.route(validatedNumber);
		MessageStatus status = optedOut ? MessageStatus.BLOCKED : MessageStatus.SENT;
		Message message = new Message(
			UUID.randomUUID(),
			validatedNumber,
			content,
			format,
			status,
			carrier
		);
		return messageRepository.save(message);
	}

	public Message get(UUID id) {
		return messageRepository.findById(id)
			.orElseThrow(() -> new MessageNotFoundException("Message not found: " + id));
	}

	private void validateContent(String content) {
		if (content == null || content.isBlank()) {
			throw new DomainValidationException("Message content must not be blank");
		}
	}

	private void validateFormat(MessageFormat format) {
		if (format == null) {
			throw new DomainValidationException("Message format must be provided");
		}
		if (format != MessageFormat.SMS) {
			throw new DomainValidationException("Only SMS format is supported");
		}
	}
}
