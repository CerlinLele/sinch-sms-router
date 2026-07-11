package com.sinch.sms.service;

import java.util.UUID;

import com.sinch.sms.message.Message;
import com.sinch.sms.message.MessageFormat;
import com.sinch.sms.message.MessageNotFoundException;
import com.sinch.sms.message.MessageRepository;
import com.sinch.sms.message.MessageStatus;
import com.sinch.sms.routing.Carrier;
import com.sinch.sms.routing.CarrierRouter;
import com.sinch.sms.validation.PhoneNumberValidator;

public class MessageService {

	private final MessageRepository messageRepository;
	private final CarrierRouter carrierRouter;
	private final PhoneNumberValidator phoneNumberValidator;

	public MessageService(
		MessageRepository messageRepository,
		CarrierRouter carrierRouter,
		PhoneNumberValidator phoneNumberValidator
	) {
		this.messageRepository = messageRepository;
		this.carrierRouter = carrierRouter;
		this.phoneNumberValidator = phoneNumberValidator;
	}

	public Message send(String destinationNumber, String content, MessageFormat format) {
		String validatedNumber = phoneNumberValidator.validate(destinationNumber);
		Carrier carrier = carrierRouter.route(validatedNumber);
		Message message = new Message(
			UUID.randomUUID(),
			validatedNumber,
			content,
			format,
			MessageStatus.SENT,
			carrier
		);
		return messageRepository.save(message);
	}

	public Message get(UUID id) {
		return messageRepository.findById(id)
			.orElseThrow(() -> new MessageNotFoundException("Message not found: " + id));
	}
}
