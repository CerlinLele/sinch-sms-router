package com.sinch.sms.service;

import com.sinch.sms.exception.InvalidPhoneNumberException;
import org.springframework.stereotype.Service;

@Service
public class PhoneNumberValidator {

    public String validate(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPhoneNumberException("Phone number is required");
        }

        String normalized = phoneNumber.trim();

        if (normalized.startsWith("+61")) {
            if (!normalized.matches("\\+61\\d{9}")) {
                throw new InvalidPhoneNumberException("AU numbers must match +61 followed by 9 digits");
            }
            return normalized;
        }

        if (normalized.startsWith("+64")) {
            if (!normalized.matches("\\+64\\d{8,9}")) {
                throw new InvalidPhoneNumberException("NZ numbers must match +64 followed by 8 or 9 digits");
            }
            return normalized;
        }

        if (!normalized.matches("\\+\\d{8,15}")) {
            throw new InvalidPhoneNumberException("Phone number must start with + and contain 8 to 15 digits");
        }

        return normalized;
    }

    public boolean isAustralia(String phoneNumber) {
        return phoneNumber.startsWith("+61");
    }

    public boolean isNewZealand(String phoneNumber) {
        return phoneNumber.startsWith("+64");
    }
}
