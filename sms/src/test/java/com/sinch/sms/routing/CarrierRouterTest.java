package com.sinch.sms.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarrierRouterTest {

	private CarrierRouter router;

	@BeforeEach
	void setUp() {
		router = new CarrierRouter();
	}

	@Test
	void route_alternatesForConsecutiveAuNumbers() {
		assertThat(router.route("+61491570156")).isEqualTo(Carrier.Telstra);
		assertThat(router.route("+61491570157")).isEqualTo(Carrier.Optus);
		assertThat(router.route("+61491570158")).isEqualTo(Carrier.Telstra);
	}

	@Test
	void route_routesNzNumbersToSpark() {
		assertThat(router.route("+64211234567")).isEqualTo(Carrier.Spark);
	}

	@Test
	void route_routesGlobalNumbersToGlobal() {
		assertThat(router.route("+15551234567")).isEqualTo(Carrier.Global);
	}

	@Test
	void route_nonAuNumbersDoNotAffectAuSequence() {
		assertThat(router.route("+64211234567")).isEqualTo(Carrier.Spark);
		assertThat(router.route("+15551234567")).isEqualTo(Carrier.Global);
		assertThat(router.route("+61491570156")).isEqualTo(Carrier.Telstra);
		assertThat(router.route("+61491570157")).isEqualTo(Carrier.Optus);
	}
}
