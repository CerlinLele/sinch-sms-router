package com.sinch.sms.service;

import com.sinch.sms.exception.InvalidPhoneNumberException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = new PhoneNumberValidator();

    @Test
    void validatesAustralianNumbers() {
        assertThat(validator.validate("+61491570156")).isEqualTo("+61491570156");
    }

    @Test
    void validatesNewZealandNumbers() {
        assertThat(validator.validate("+64211234567")).isEqualTo("+64211234567");
    }

    @Test
    void validatesOtherInternationalNumbers() {
        assertThat(validator.validate("+12025550123")).isEqualTo("+12025550123");
    }

    @Test
    void rejectsNumbersWithoutPlusPrefix() {
        assertThatThrownBy(() -> validator.validate("61491570156"))
                .isInstanceOf(InvalidPhoneNumberException.class);
    }
}
