package com.sinch.sms.model;

import java.util.Objects;

public class Message {

    private final String id;
    private final String destinationNumber;
    private final String content;
    private final MessageFormat format;
    private final Carrier carrier;
    private final MessageStatus status;

    public Message(
            String id,
            String destinationNumber,
            String content,
            MessageFormat format,
            Carrier carrier,
            MessageStatus status) {
        this.id = id;
        this.destinationNumber = destinationNumber;
        this.content = content;
        this.format = format;
        this.carrier = carrier;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getDestinationNumber() {
        return destinationNumber;
    }

    public String getContent() {
        return content;
    }

    public MessageFormat getFormat() {
        return format;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public MessageStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(id, message.id)
                && Objects.equals(destinationNumber, message.destinationNumber)
                && Objects.equals(content, message.content)
                && format == message.format
                && carrier == message.carrier
                && status == message.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, destinationNumber, content, format, carrier, status);
    }
}
