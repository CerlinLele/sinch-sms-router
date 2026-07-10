package com.sinch.sms.controller;

import com.sinch.sms.exception.ApiExceptionHandler;
import com.sinch.sms.model.Carrier;
import com.sinch.sms.model.Message;
import com.sinch.sms.model.MessageFormat;
import com.sinch.sms.model.MessageStatus;
import com.sinch.sms.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new MessageController(messageService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsMessageAndReturnsItsStatus() throws Exception {
        Message saved = new Message(
                "msg-1",
                "+61491570156",
                "Hello world",
                MessageFormat.SMS,
                Carrier.TELSTRA,
                MessageStatus.SENT);
        when(messageService.sendMessage(any())).thenReturn(saved);

        mockMvc.perform(post("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination_number": "+61491570156",
                                  "content": "Hello world",
                                  "format": "SMS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("msg-1"))
                .andExpect(jsonPath("$.destination_number").value("+61491570156"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.carrier").value("Telstra"));
    }

    @Test
    void returnsMessageById() throws Exception {
        Message saved = new Message(
                "msg-2",
                "+64211234567",
                "Kia ora",
                MessageFormat.SMS,
                null,
                MessageStatus.BLOCKED);
        when(messageService.getMessage("msg-2")).thenReturn(saved);

        mockMvc.perform(get("/messages/msg-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("msg-2"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.carrier").value(nullValue()));
    }
}
