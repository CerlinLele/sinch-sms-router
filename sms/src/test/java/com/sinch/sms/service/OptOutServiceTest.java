package com.sinch.sms.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptOutServiceTest {

    private final PhoneNumberValidator phoneNumberValidator = new PhoneNumberValidator();
    private final OptOutService service = new OptOutService(phoneNumberValidator);

    @Test
    void marksNumbersAsOptedOut() {
        String phoneNumber = "+61491570156";

        String normalized = service.optOut(phoneNumber);

        assertThat(normalized).isEqualTo(phoneNumber);
        assertThat(service.isOptedOut(phoneNumber)).isTrue();
    }

    @Test
    void keepsOptOutIdempotent() {
        String phoneNumber = "+64211234567";

        service.optOut(phoneNumber);
        service.optOut(phoneNumber);

        assertThat(service.isOptedOut(phoneNumber)).isTrue();
    }
}
