package com.sinch.sms.message;

public class MessageNotFoundException extends RuntimeException {

	public MessageNotFoundException(String message) {
		super(message);
	}
}
