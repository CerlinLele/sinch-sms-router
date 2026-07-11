package com.sinch.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sinch.sms.message.Message;
import com.sinch.sms.message.MessageFormat;
import com.sinch.sms.message.MessageNotFoundException;
import com.sinch.sms.message.MessageRepository;
import com.sinch.sms.message.MessageStatus;
import com.sinch.sms.routing.Carrier;
import com.sinch.sms.routing.CarrierRouter;
import com.sinch.sms.validation.DomainValidationException;
import com.sinch.sms.validation.PhoneNumberValidator;

class MessageServiceTest {

	private RecordingMessageRepository messageRepository;
	private OptOutService optOutService;
	private MessageService messageService;

	@BeforeEach
	void setUp() {
		messageRepository = new RecordingMessageRepository();
		optOutService = new OptOutService(new PhoneNumberValidator());
		messageService = new MessageService(
			messageRepository,
			new CarrierRouter(),
			new PhoneNumberValidator(),
			optOutService
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

	@Test
	void send_persistsBlockedMessageForOptedOutNumberAndDoesNotConsumeAuCarrier() {
		optOutService.optOut("+61491570156");

		Message blockedMessage = messageService.send("+61491570156", "hello", MessageFormat.SMS);

		assertThat(blockedMessage.id()).isNotNull();
		assertThat(blockedMessage.status()).isEqualTo(MessageStatus.BLOCKED);
		assertThat(blockedMessage.carrier()).isNull();
		assertThat(messageRepository.findById(blockedMessage.id())).contains(blockedMessage);

		Message nextAuMessage = messageService.send("+61491570157", "hello again", MessageFormat.SMS);

		assertThat(nextAuMessage.status()).isEqualTo(MessageStatus.SENT);
		assertThat(nextAuMessage.carrier()).isEqualTo(Carrier.Telstra);
	}

	@Test
	void get_throwsForUnknownId() {
		assertThatThrownBy(() -> messageService.get(UUID.randomUUID()))
			.isInstanceOf(MessageNotFoundException.class);
	}

	@Test
	void send_rejectsInvalidDestinationNumberAndDoesNotSave() {
		assertThatThrownBy(() -> messageService.send("61491570156", "hello", MessageFormat.SMS))
			.isInstanceOf(DomainValidationException.class);

		assertThat(messageRepository.getSaveCount()).isZero();
	}

	@Test
	void send_rejectsBlankContentAndDoesNotSave() {
		assertThatThrownBy(() -> messageService.send("+61491570156", "   ", MessageFormat.SMS))
			.isInstanceOf(DomainValidationException.class);

		assertThat(messageRepository.getSaveCount()).isZero();
	}

	@Test
	void send_rejectsMissingFormatAndDoesNotSave() {
		assertThatThrownBy(() -> messageService.send("+61491570156", "hello", null))
			.isInstanceOf(DomainValidationException.class);

		assertThat(messageRepository.getSaveCount()).isZero();
	}

	@Test
	void send_rejectsNonSmsFormatAndDoesNotSave() {
		assertThatThrownBy(() -> messageService.send("+61491570156", "hello", MessageFormat.MMS))
			.isInstanceOf(DomainValidationException.class);

		assertThat(messageRepository.getSaveCount()).isZero();
	}

	private static final class RecordingMessageRepository implements MessageRepository {

		private final Map<UUID, Message> messages = new ConcurrentHashMap<>();
		private int saveCount;

		@Override
		public Message save(Message message) {
			saveCount++;
			messages.put(message.id(), message);
			return message;
		}

		@Override
		public java.util.Optional<Message> findById(UUID id) {
			return java.util.Optional.ofNullable(messages.get(id));
		}

		int getSaveCount() {
			return saveCount;
		}
	}
}
