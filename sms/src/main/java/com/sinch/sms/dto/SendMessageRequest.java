package com.sinch.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinch.sms.model.MessageFormat;

public record SendMessageRequest(
        @JsonProperty("destination_number") String destinationNumber,
        String content,
        MessageFormat format) {
}
