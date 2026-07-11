# Test Generation Plan

## Purpose

This plan defines the test suite to generate for the SMS Message Router described in `PRD.md`. The goal is to cover the take-home exercise requirements with a small, readable, interview-friendly test set that validates routing, opt-out behavior, message status lookup, validation, and API error handling.

## Product Requirements Covered

- `POST /messages` sends an SMS message request.
- `GET /messages/{id}` returns stored message status.
- `POST /optout/{phoneNumber}` opts a destination number out.
- AU numbers (`+61`) route to Telstra or Optus, alternating between the two.
- NZ numbers (`+64`) route to Spark.
- Other valid numbers route to Global.
- Opted-out numbers are blocked.
- Messages are stored in memory.
- Status values include `PENDING`, `SENT`, `DELIVERED`, and `BLOCKED`.
- Phone numbers are validated with reasonable AU/NZ assumptions.

## Current Project Context

- The project is a Spring Boot Maven application under `sms/`.
- The existing test suite contains only `SmsApplicationTests.contextLoads()`.
- The current `pom.xml` includes `spring-boot-starter` and `spring-boot-starter-test`.
- API-level tests will need Spring MVC support in the application implementation, typically via `spring-boot-starter-web`.

## Testing Strategy

Use a layered test suite:

1. Domain/service unit tests for routing, opt-out state, message creation, and status lookup.
2. API tests for request/response behavior, JSON shape, HTTP status codes, and validation errors.
3. A minimal Spring context test to ensure the application wiring loads.

Prefer deterministic tests. Any alternating AU carrier behavior should be tested with controlled ordering and isolated service state.

## Proposed Test Files

### `CarrierRouterTest`

Purpose: validate carrier selection independently from HTTP and message persistence.

Scenarios:

- Routes first valid AU number to Telstra.
- Routes second valid AU number to Optus.
- Continues alternating AU routing across repeated AU sends.
- Routes valid NZ number to Spark.
- Routes valid non-AU/NZ number to Global.
- Rejects invalid phone number inputs if validation belongs inside the router.

### `PhoneNumberValidatorTest`

Purpose: document the project's phone number assumptions and reject malformed inputs.

Scenarios:

- Accepts valid AU mobile-style numbers such as `+61491570156`.
- Accepts valid NZ-style numbers such as `+64211234567`.
- Accepts valid international fallback numbers if the implementation supports Global routing.
- Rejects blank values.
- Rejects missing `+`.
- Rejects alphabetic characters.
- Rejects numbers that are too short.
- Rejects numbers that are too long.
- Rejects malformed AU/NZ numbers based on the agreed assumptions.

### `OptOutServiceTest`

Purpose: verify opt-out state is stored and checked consistently.

Scenarios:

- A number is not opted out by default.
- Posting an opt-out records the number.
- Repeated opt-out calls are idempotent.
- Phone number normalization is applied consistently if implemented.
- Invalid phone numbers cannot be opted out.

### `MessageServiceTest`

Purpose: verify the main business workflow without HTTP.

Scenarios:

- Sending to a valid AU number creates a message ID and assigns Telstra or Optus.
- Sending to a valid NZ number creates a message ID and assigns Spark.
- Sending to a valid other-country number creates a message ID and assigns Global.
- Sending to an opted-out number creates or stores a blocked message with status `BLOCKED`.
- Blocked messages can be retrieved through status lookup.
- Successful messages can be retrieved through status lookup.
- Looking up an unknown message ID returns a not-found result.
- Invalid message requests are rejected before storage.
- Empty content is rejected.
- Unsupported message format is rejected if only `SMS` is allowed.

### `MessageControllerTest`

Purpose: verify API contract behavior using `MockMvc` or equivalent Spring test support.

Scenarios:

- `POST /messages` with a valid AU payload returns a success status, message ID, message status, and carrier.
- `POST /messages` with a valid NZ payload returns carrier `Spark`.
- `POST /optout/{phoneNumber}` followed by `POST /messages` for the same number returns or stores status `BLOCKED`.
- `GET /messages/{id}` returns the stored status for a previously sent message.
- `GET /messages/{id}` for an unknown ID returns `404 Not Found`.
- `POST /messages` with malformed JSON returns `400 Bad Request`.
- `POST /messages` with invalid destination number returns `400 Bad Request`.
- `POST /messages` with missing content returns `400 Bad Request`.
- `POST /messages` with unsupported format returns `400 Bad Request`.
- `POST /optout/{phoneNumber}` with invalid phone number returns `400 Bad Request`.

### `SmsApplicationTests`

Purpose: keep a single smoke test that confirms Spring context startup.

Scenarios:

- Application context loads.

## Primary Acceptance Test Matrix

| Requirement | Test Layer | Expected Result |
| --- | --- | --- |
| Send to valid AU number | API + service + router | Message is accepted and routed to Telstra or Optus |
| AU carrier alternation | Router/service | Consecutive AU sends alternate between Telstra and Optus |
| Send to opted-out number | API + service | Message is blocked and retrievable with status `BLOCKED` |
| Get blocked message status | API + service | `GET /messages/{id}` returns `BLOCKED` |
| Send to valid NZ number | API + service + router | Message is accepted and routed to Spark |
| Send to other valid number | Service + router | Message is accepted and routed to Global |
| Invalid phone number | API + validator | Request fails with `400 Bad Request` |
| Unknown message ID | API + service | Request fails with `404 Not Found` |

## Test Data

Use fixed test data to keep generated tests readable:

| Case | Value |
| --- | --- |
| AU valid number A | `+61491570156` |
| AU valid number B | `+61411111111` |
| NZ valid number | `+64211234567` |
| Global valid number | `+15551234567` |
| Invalid missing plus | `61491570156` |
| Invalid letters | `+61ABC570156` |
| Invalid empty content | empty string |
| Valid content | `Hello world` |
| Valid format | `SMS` |
| Unsupported format | `MMS` |

## Status Assumptions To Fix Before Writing Tests

The PRD says status should track `PENDING -> SENT -> DELIVERED/BLOCKED`, but it does not define asynchronous delivery simulation. Use these implementation assumptions unless the application chooses a different explicit model:

- A successful synchronous send returns and stores `SENT`.
- An opted-out send returns and stores `BLOCKED`.
- `PENDING` is allowed internally during message creation, but tests should not depend on a transient state unless the implementation exposes it.
- `DELIVERED` should only be tested if a delivery simulation, callback, or manual status transition endpoint is implemented.

## Generation Order

1. Add or confirm domain model names for message request, message response, message status, and carrier.
2. Generate `PhoneNumberValidatorTest` to lock validation assumptions first.
3. Generate `CarrierRouterTest` to lock routing and AU alternation.
4. Generate `OptOutServiceTest` to lock opt-out behavior and idempotency.
5. Generate `MessageServiceTest` to cover the business workflow and in-memory storage.
6. Generate `MessageControllerTest` after controller contracts and response status codes are stable.
7. Keep `SmsApplicationTests.contextLoads()` as a smoke test.
8. Run `mvn test` from `sms/` and adjust only tests or production behavior that conflicts with the PRD.

## Test Implementation Notes

- Use JUnit 5 assertions.
- Use AssertJ if available through Spring Boot test dependencies.
- Use `@WebMvcTest` for controller tests when controllers can be isolated.
- Use `@SpringBootTest` plus `@AutoConfigureMockMvc` only when full wiring is needed.
- Reset or recreate in-memory services between tests to avoid carrier alternation and opt-out state leaking across test cases.
- Prefer explicit JSON assertions for API responses instead of snapshot-style tests.
- Keep tests small and named after behavior, for example `sendAuMessageRoutesToAlternatingCarrier()`.

## Completion Criteria

The generated test suite is complete when:

- The three PRD test cases are covered by executable tests.
- Validation and error handling have at least one positive and one negative test per endpoint.
- Message status lookup is tested for both existing and missing IDs.
- Opt-out behavior is tested through both service and API paths.
- Carrier routing is deterministic and isolated between tests.
- `mvn test` passes from the `sms/` directory.
