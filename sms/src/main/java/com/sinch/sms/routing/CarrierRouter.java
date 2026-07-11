package com.sinch.sms.routing;

import java.util.concurrent.atomic.AtomicReference;

public class CarrierRouter {

	private final AtomicReference<Carrier> nextAuCarrier = new AtomicReference<>(Carrier.Telstra);

	public Carrier route(String phoneNumber) {
		if (phoneNumber.startsWith("+61")) {
			return nextAuCarrier.getAndUpdate(current -> current == Carrier.Telstra ? Carrier.Optus : Carrier.Telstra);
		}
		if (phoneNumber.startsWith("+64")) {
			return Carrier.Spark;
		}
		return Carrier.Global;
	}
}
