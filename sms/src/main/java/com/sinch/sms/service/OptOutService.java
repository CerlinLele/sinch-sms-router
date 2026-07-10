package com.sinch.sms.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OptOutService {

    private final Set<String> optedOutNumbers = ConcurrentHashMap.newKeySet();
    private final PhoneNumberValidator phoneNumberValidator;

    public OptOutService(PhoneNumberValidator phoneNumberValidator) {
        this.phoneNumberValidator = phoneNumberValidator;
    }

    public String optOut(String phoneNumber) {
        String normalized = phoneNumberValidator.validate(phoneNumber);
        optedOutNumbers.add(normalized);
        return normalized;
    }

    public boolean isOptedOut(String phoneNumber) {
        String normalized = phoneNumberValidator.validate(phoneNumber);
        return optedOutNumbers.contains(normalized);
    }
}
