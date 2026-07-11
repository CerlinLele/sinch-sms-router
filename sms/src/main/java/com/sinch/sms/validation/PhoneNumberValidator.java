package com.sinch.sms.validation;

public class PhoneNumberValidator {

	public String validate(String value) {
		if (value == null || !value.matches("\\+[1-9][0-9]{7,14}")) {
			throw new DomainValidationException("Phone number must be in canonical E.164 form");
		}
		return value;
	}
}
