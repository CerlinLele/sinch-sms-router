package com.sinch.sms.message;

import java.util.UUID;

import com.sinch.sms.routing.Carrier;

public record Message(
	UUID id,
	String destinationNumber,
	String content,
	MessageFormat format,
	MessageStatus status,
	Carrier carrier
) {
}
