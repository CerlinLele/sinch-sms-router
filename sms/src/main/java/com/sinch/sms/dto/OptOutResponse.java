package com.sinch.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptOutResponse(
        @JsonProperty("phone_number") String phoneNumber,
        boolean optedOut) {
}
