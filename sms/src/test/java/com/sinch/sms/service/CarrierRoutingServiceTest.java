package com.sinch.sms.service;

import com.sinch.sms.model.Carrier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierRoutingServiceTest {

    private final CarrierRoutingService service = new CarrierRoutingService();

    @Test
    void routesAustralianNumbersAlternatingBetweenTelstraAndOptus() {
        Carrier first = service.route("+61491570156");
        Carrier second = service.route("+61491570157");
        Carrier third = service.route("+61491570158");

        assertThat(first).isEqualTo(Carrier.TELSTRA);
        assertThat(second).isEqualTo(Carrier.OPTUS);
        assertThat(third).isEqualTo(Carrier.TELSTRA);
    }

    @Test
    void routesNewZealandNumbersToSpark() {
        Carrier carrier = service.route("+64211234567");

        assertThat(carrier).isEqualTo(Carrier.SPARK);
    }

    @Test
    void routesOtherNumbersToGlobal() {
        Carrier carrier = service.route("+12025550123");

        assertThat(carrier).isEqualTo(Carrier.GLOBAL);
    }
}
