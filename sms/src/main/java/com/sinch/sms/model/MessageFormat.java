package com.sinch.sms.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageFormat {
    SMS;

    @JsonValue
    public String getValue() {
        return name();
    }
}
