package com.sinch.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sinch.sms.message.InMemoryMessageRepository;
import com.sinch.sms.message.MessageRepository;
import com.sinch.sms.routing.CarrierRouter;
import com.sinch.sms.service.MessageService;
import com.sinch.sms.service.OptOutService;
import com.sinch.sms.validation.PhoneNumberValidator;

@Configuration
public class SmsConfiguration {

	@Bean
	PhoneNumberValidator phoneNumberValidator() {
		return new PhoneNumberValidator();
	}

	@Bean
	CarrierRouter carrierRouter() {
		return new CarrierRouter();
	}

	@Bean
	OptOutService optOutService(PhoneNumberValidator phoneNumberValidator) {
		return new OptOutService(phoneNumberValidator);
	}

	@Bean
	MessageRepository messageRepository() {
		return new InMemoryMessageRepository();
	}

	@Bean
	MessageService messageService(
		MessageRepository messageRepository,
		CarrierRouter carrierRouter,
		PhoneNumberValidator phoneNumberValidator,
		OptOutService optOutService
	) {
		return new MessageService(messageRepository, carrierRouter, phoneNumberValidator, optOutService);
	}
}
