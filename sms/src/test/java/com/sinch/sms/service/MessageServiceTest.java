package com.sinch.sms.service;

import com.sinch.sms.dto.SendMessageRequest;
import com.sinch.sms.exception.InvalidMessageException;
import com.sinch.sms.exception.MessageNotFoundException;
import com.sinch.sms.model.Carrier;
import com.sinch.sms.model.Message;
import com.sinch.sms.model.MessageFormat;
import com.sinch.sms.model.MessageStatus;
import com.sinch.sms.repository.InMemoryMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageServiceTest {

    private InMemoryMessageRepository repository;
    private CarrierRoutingService carrierRoutingService;
    private OptOutService optOutService;
    private PhoneNumberValidator phoneNumberValidator;
    private MessageService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMessageRepository();
        carrierRoutingService = new CarrierRoutingService();
        phoneNumberValidator = new PhoneNumberValidator();
        optOutService = new OptOutService(phoneNumberValidator);
        service = new MessageService(repository, carrierRoutingService, optOutService, phoneNumberValidator);
    }

    @Test
    void sendsValidAustralianMessageAndRoutesIt() {
        Message message = service.sendMessage(new SendMessageRequest("+61491570156", "Hello world", MessageFormat.SMS));

        assertThat(message.getId()).isNotBlank();
        assertThat(message.getDestinationNumber()).isEqualTo("+61491570156");
        assertThat(message.getContent()).isEqualTo("Hello world");
        assertThat(message.getFormat()).isEqualTo(MessageFormat.SMS);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getCarrier()).isEqualTo(Carrier.TELSTRA);
        assertThat(service.getMessage(message.getId())).isEqualTo(message);
    }

    @Test
    void blocksMessagesToOptedOutNumbers() {
        optOutService.optOut("+61491570156");

        Message message = service.sendMessage(new SendMessageRequest("+61491570156", "Hello world", MessageFormat.SMS));

        assertThat(message.getStatus()).isEqualTo(MessageStatus.BLOCKED);
        assertThat(message.getCarrier()).isNull();
        assertThat(service.getMessage(message.getId()).getStatus()).isEqualTo(MessageStatus.BLOCKED);
    }

    @Test
    void sendsNewZealandMessageToSpark() {
        Message message = service.sendMessage(new SendMessageRequest("+64211234567", "Kia ora", MessageFormat.SMS));

        assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(message.getCarrier()).isEqualTo(Carrier.SPARK);
    }

    @Test
    void rejectsInvalidPhoneNumbers() {
        assertThatThrownBy(() -> service.sendMessage(new SendMessageRequest("61491570156", "Hello world", MessageFormat.SMS)))
                .isInstanceOf(com.sinch.sms.exception.InvalidPhoneNumberException.class);
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> service.sendMessage(new SendMessageRequest("+61491570156", "   ", MessageFormat.SMS)))
                .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void throwsWhenMessageIdDoesNotExist() {
        assertThatThrownBy(() -> service.getMessage("missing-id"))
                .isInstanceOf(MessageNotFoundException.class);
    }
}
