package com.sinch.sms.controller;

import com.sinch.sms.exception.ApiExceptionHandler;
import com.sinch.sms.service.OptOutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OptOutControllerTest {

    @Mock
    private OptOutService optOutService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new OptOutController(optOutService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void marksNumberAsOptedOut() throws Exception {
        when(optOutService.optOut(anyString())).thenReturn("+61491570156");

        mockMvc.perform(post("/optout/%2B61491570156")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone_number").value("+61491570156"))
                .andExpect(jsonPath("$.optedOut").value(true));
    }
}
