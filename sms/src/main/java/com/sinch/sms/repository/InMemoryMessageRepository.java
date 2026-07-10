package com.sinch.sms.repository;

import com.sinch.sms.model.Message;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryMessageRepository implements MessageRepository {

    private final ConcurrentMap<String, Message> storage = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        storage.put(message.getId(), message);
        return message;
    }

    @Override
    public Optional<Message> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }
}
