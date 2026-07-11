package com.sinch.sms.message;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository {

	Message save(Message message);

	Optional<Message> findById(UUID id);
}
