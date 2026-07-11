package com.sinch.sms.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sendLookupAndOptOutFlowWorksForSuccessfulRequests() throws Exception {
		String auMessageId = mockMvc.perform(post("/messages")
				.contentType("application/json")
				.content("""
					{"destination_number":"+61491570156","content":"hello","format":"SMS"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").exists())
			.andExpect(jsonPath("$.status").value("SENT"))
			.andExpect(jsonPath("$.carrier").value("Telstra"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String auId = extractJsonValue(auMessageId, "id");

		mockMvc.perform(get("/messages/{id}", auId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(auId))
			.andExpect(jsonPath("$.status").value("SENT"))
			.andExpect(jsonPath("$.carrier").value("Telstra"));

		mockMvc.perform(post("/messages")
				.contentType("application/json")
				.content("""
					{"destination_number":"+64211234567","content":"kia ora","format":"SMS"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SENT"))
			.andExpect(jsonPath("$.carrier").value("Spark"));
	}

	@Test
	void optOutThenSendReturnsBlockedAndLookupWorks() throws Exception {
		mockMvc.perform(post("/optout/{phoneNumber}", "+61491570156"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone_number").value("+61491570156"))
			.andExpect(jsonPath("$.opted_out").value(true));

		String blockedResponse = mockMvc.perform(post("/messages")
				.contentType("application/json")
				.content("""
					{"destination_number":"+61491570156","content":"hello","format":"SMS"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("BLOCKED"))
			.andExpect(jsonPath("$.carrier").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String blockedId = extractJsonValue(blockedResponse, "id");

		mockMvc.perform(get("/messages/{id}", blockedId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(blockedId))
			.andExpect(jsonPath("$.status").value("BLOCKED"))
			.andExpect(jsonPath("$.carrier").value(nullValue()));
	}

	private String extractJsonValue(String json, String fieldName) {
		int fieldIndex = json.indexOf('"' + fieldName + '"');
		int colonIndex = json.indexOf(':', fieldIndex);
		int startIndex = json.indexOf('"', colonIndex + 1) + 1;
		int endIndex = json.indexOf('"', startIndex);
		return json.substring(startIndex, endIndex);
	}
}
