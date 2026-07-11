package com.sinch.sms.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PhoneNumberValidatorTest {

	private final PhoneNumberValidator validator = new PhoneNumberValidator();

	@ParameterizedTest
	@MethodSource("validNumbers")
	void validate_returnsCanonicalNumber(String input) {
		assertThat(validator.validate(input)).isEqualTo(input);
	}

	@ParameterizedTest
	@MethodSource("invalidNumbers")
	void validate_rejectsInvalidNumber(String input) {
		assertThatThrownBy(() -> validator.validate(input))
			.isInstanceOf(DomainValidationException.class);
	}

	static Stream<Arguments> validNumbers() {
		return Stream.of(
			Arguments.of("+61491570156"),
			Arguments.of("+64211234567"),
			Arguments.of("+15551234567")
		);
	}

	static Stream<Arguments> invalidNumbers() {
		return Stream.of(
			Arguments.of((String) null),
			Arguments.of(""),
			Arguments.of("   "),
			Arguments.of("61491570156"),
			Arguments.of("+61a91570156"),
			Arguments.of("+61 491570156"),
			Arguments.of("+61-491-570-156"),
			Arguments.of("+6149157"),
			Arguments.of("+6149157015600000"),
			Arguments.of("+012345678")
		);
	}
}
