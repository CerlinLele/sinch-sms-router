package com.sinch.sms.api;

import java.util.UUID;

import com.sinch.sms.message.MessageStatus;
import com.sinch.sms.routing.Carrier;

public record MessageResponse(UUID id, MessageStatus status, Carrier carrier) {
}
