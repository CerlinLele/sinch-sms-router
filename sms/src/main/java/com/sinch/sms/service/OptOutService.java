package com.sinch.sms.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sinch.sms.validation.PhoneNumberValidator;

public class OptOutService {

	private final PhoneNumberValidator phoneNumberValidator;
	private final Set<String> optedOutNumbers = ConcurrentHashMap.newKeySet();

	public OptOutService(PhoneNumberValidator phoneNumberValidator) {
		this.phoneNumberValidator = phoneNumberValidator;
	}

	public void optOut(String phoneNumber) {
		optedOutNumbers.add(phoneNumberValidator.validate(phoneNumber));
	}

	public boolean isOptedOut(String phoneNumber) {
		return optedOutNumbers.contains(phoneNumberValidator.validate(phoneNumber));
	}
}
