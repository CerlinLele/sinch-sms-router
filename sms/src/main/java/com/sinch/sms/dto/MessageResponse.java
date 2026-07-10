package com.sinch.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinch.sms.model.Carrier;
import com.sinch.sms.model.MessageFormat;
import com.sinch.sms.model.MessageStatus;

public record MessageResponse(
        String id,
        @JsonProperty("destination_number") String destinationNumber,
        String content,
        MessageFormat format,
        MessageStatus status,
        Carrier carrier) {
}
