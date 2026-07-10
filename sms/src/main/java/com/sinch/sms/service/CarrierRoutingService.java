package com.sinch.sms.service;

import com.sinch.sms.model.Carrier;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CarrierRoutingService {

    private final AtomicInteger auRoutingCounter = new AtomicInteger();

    public Carrier route(String phoneNumber) {
        if (phoneNumber.startsWith("+61")) {
            return auRoutingCounter.getAndIncrement() % 2 == 0 ? Carrier.TELSTRA : Carrier.OPTUS;
        }

        if (phoneNumber.startsWith("+64")) {
            return Carrier.SPARK;
        }

        return Carrier.GLOBAL;
    }
}
