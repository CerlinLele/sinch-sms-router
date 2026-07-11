package com.sinch.sms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sinch.sms.message.InMemoryMessageRepository;
import com.sinch.sms.message.Message;
import com.sinch.sms.message.MessageFormat;
import com.sinch.sms.message.MessageRepository;
import com.sinch.sms.message.MessageStatus;
import com.sinch.sms.routing.Carrier;
import com.sinch.sms.routing.CarrierRouter;
import com.sinch.sms.validation.PhoneNumberValidator;

class MessageServiceTest {

	private MessageRepository messageRepository;
	private MessageService messageService;

	@BeforeEach
	void setUp() {
		messageRepository = new InMemoryMessageRepository();
		messageService = new MessageService(
			messageRepository,
			new CarrierRouter(),
			new PhoneNumberValidator()
		);
	}

	@Test
	void send_persistsAuMessageWithSentStatusAndLookupWorks() {
		Message sentMessage = messageService.send("+61491570156", "hello", MessageFormat.SMS);

		assertThat(sentMessage.id()).isNotNull();
		assertThat(sentMessage.status()).isEqualTo(MessageStatus.SENT);
		assertThat(sentMessage.carrier()).isEqualTo(Carrier.Telstra);

		UUID id = sentMessage.id();
		assertThat(messageRepository.findById(id)).contains(sentMessage);
		assertThat(messageService.get(id)).isEqualTo(sentMessage);
	}

	@Test
	void send_persistsNzMessageWithSparkCarrier() {
		Message sentMessage = messageService.send("+64211234567", "hello", MessageFormat.SMS);

		assertThat(sentMessage.id()).isNotNull();
		assertThat(sentMessage.status()).isEqualTo(MessageStatus.SENT);
		assertThat(sentMessage.carrier()).isEqualTo(Carrier.Spark);
		assertThat(messageService.get(sentMessage.id())).isEqualTo(sentMessage);
	}

	@Test
	void send_persistsGlobalMessageWithGlobalCarrier() {
		Message sentMessage = messageService.send("+15551234567", "hello", MessageFormat.SMS);

		assertThat(sentMessage.id()).isNotNull();
		assertThat(sentMessage.status()).isEqualTo(MessageStatus.SENT);
		assertThat(sentMessage.carrier()).isEqualTo(Carrier.Global);
		assertThat(messageService.get(sentMessage.id())).isEqualTo(sentMessage);
	}
}
