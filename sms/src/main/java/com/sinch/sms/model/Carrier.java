package com.sinch.sms.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Carrier {
    TELSTRA("Telstra"),
    OPTUS("Optus"),
    SPARK("Spark"),
    GLOBAL("Global");

    private final String value;

    Carrier(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
