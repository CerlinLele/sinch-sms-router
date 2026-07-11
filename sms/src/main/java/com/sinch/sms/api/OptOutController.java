package com.sinch.sms.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sinch.sms.service.OptOutService;

@RestController
public class OptOutController {

	private final OptOutService optOutService;

	public OptOutController(OptOutService optOutService) {
		this.optOutService = optOutService;
	}

	@PostMapping("/optout/{phoneNumber}")
	public OptOutResponse optOut(@PathVariable String phoneNumber) {
		optOutService.optOut(phoneNumber);
		return new OptOutResponse(phoneNumber, true);
	}
}
