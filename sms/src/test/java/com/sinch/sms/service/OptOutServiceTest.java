package com.sinch.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sinch.sms.validation.DomainValidationException;
import com.sinch.sms.validation.PhoneNumberValidator;

class OptOutServiceTest {

	private OptOutService optOutService;

	@BeforeEach
	void setUp() {
		optOutService = new OptOutService(new PhoneNumberValidator());
	}

	@Test
	void isOptedOut_returnsFalseByDefault() {
		assertThat(optOutService.isOptedOut("+61491570156")).isFalse();
	}

	@Test
	void optOut_marksNumberAsOptedOut() {
		optOutService.optOut("+61491570156");

		assertThat(optOutService.isOptedOut("+61491570156")).isTrue();
	}

	@Test
	void optOut_isIdempotentForRepeatedCalls() {
		optOutService.optOut("+61491570156");
		optOutService.optOut("+61491570156");

		assertThat(optOutService.isOptedOut("+61491570156")).isTrue();
	}

	@Test
	void invalidNumbersAreRejectedWithoutChangingState() {
		assertThatThrownBy(() -> optOutService.optOut("61491570156"))
			.isInstanceOf(DomainValidationException.class);

		assertThat(optOutService.isOptedOut("+61491570156")).isFalse();

		optOutService.optOut("+61491570156");

		assertThatThrownBy(() -> optOutService.isOptedOut("  +61491570156  "))
			.isInstanceOf(DomainValidationException.class);

		assertThat(optOutService.isOptedOut("+61491570156")).isTrue();
	}
}
