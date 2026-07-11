package com.sinch.sms.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptOutResponse(
	@JsonProperty("phone_number") String phoneNumber,
	@JsonProperty("opted_out") boolean optedOut
) {
}
