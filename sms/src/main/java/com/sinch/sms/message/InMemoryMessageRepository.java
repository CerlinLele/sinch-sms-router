package com.sinch.sms.message;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMessageRepository implements MessageRepository {

	private final ConcurrentHashMap<UUID, Message> messages = new ConcurrentHashMap<>();

	@Override
	public Message save(Message message) {
		messages.put(message.id(), message);
		return message;
	}

	@Override
	public Optional<Message> findById(UUID id) {
		return Optional.ofNullable(messages.get(id));
	}
}
