package com.sinch.sms.controller;

import com.sinch.sms.dto.OptOutResponse;
import com.sinch.sms.service.OptOutService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/optout")
public class OptOutController {

    private final OptOutService optOutService;

    public OptOutController(OptOutService optOutService) {
        this.optOutService = optOutService;
    }

    @PostMapping("/{phoneNumber}")
    public OptOutResponse optOut(@PathVariable String phoneNumber) {
        String normalized = optOutService.optOut(phoneNumber);
        return new OptOutResponse(normalized, true);
    }
}
