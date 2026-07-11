package com.sinch.sms.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinch.sms.message.MessageFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
	@JsonProperty("destination_number")
	@NotBlank String destinationNumber,
	@NotBlank String content,
	@NotNull MessageFormat format
) {
}
