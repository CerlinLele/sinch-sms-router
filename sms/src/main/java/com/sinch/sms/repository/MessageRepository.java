package com.sinch.sms.repository;

import com.sinch.sms.model.Message;

import java.util.Optional;

public interface MessageRepository {

    Message save(Message message);

    Optional<Message> findById(String id);
}
