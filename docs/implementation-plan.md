# SMS Router TDD Implementation Plan

## Purpose

Implement the SMS Router as a small Spring Boot application using a strict test-driven workflow. For every implementation step, add or update the relevant failing test cases first, implement the smallest production change that makes them pass, then run the relevant test class before continuing.

## Fixed API and Behaviour Contract

### Phone numbers

- Accept only canonical E.164-like values: `+` followed by 8 to 15 ASCII digits, with a non-zero first digit after `+`.
- Do not trim, normalize, or accept whitespace, separators, local formats, or alphabetic characters.
- Valid examples: `+61491570156`, `+64211234567`, and `+15551234567`.

### Endpoints and responses

| Endpoint | Successful response |
| --- | --- |
| `POST /messages` | `201 Created` with response fields `id`, `status` (`SENT` or `BLOCKED`), and `carrier` (`Telstra`, `Optus`, `Spark`, `Global`, or `null`) |
| `GET /messages/{id}` | `200 OK` with the same `{id,status,carrier}` response shape |
| `POST /optout/{phoneNumber}` | `200 OK` with `{ "phone_number": "+...", "opted_out": true }` |

- Repeated opt-out calls are idempotent and return the same successful response.
- Unknown, syntactically valid message IDs return `404 Not Found`.
- Invalid request values and malformed message IDs return `400 Bad Request`.
- Errors use `{ "code": "...", "message": "..." }` with these stable codes: `VALIDATION_ERROR`, `MALFORMED_JSON`, and `MESSAGE_NOT_FOUND`.

### Message processing rules

- AU numbers (`+61`) route successful sends alternately to `Telstra`, then `Optus`, beginning with `Telstra`.
- NZ numbers (`+64`) route to `Spark`.
- Other valid numbers route to `Global`.
- Check opt-out status before routing. An opted-out send still creates and stores a message with status `BLOCKED` and `carrier: null`; it does not advance the AU alternation.
- A successful synchronous send stores and returns `SENT`.
- Keep `PENDING`, `SENT`, `DELIVERED`, and `BLOCKED` in the status enum, but do not add delivery simulation or a status-transition endpoint in v1.
- Store messages and opt-out state in memory only; application restart clears both states and the AU routing sequence.

## Stage 0 - Web Test Foundation

### Step 0.1 - Enable MVC test support

1. Test first: extend `SmsApplicationTests` with a failing assertion that a full Spring test context can inject `MockMvc`.
2. Implement: add Spring Web and Bean Validation starters to `sms/pom.xml`, keeping dependency versions managed by Spring Boot.
3. Verify: run `SmsApplicationTests` and confirm the Web context loads.

## Stage 1 - Phone Number Validation

### Step 1.1 - Define canonical number validation

1. Test first: add `PhoneNumberValidatorTest` covering valid AU, NZ, and Global examples; reject null, blank, missing `+`, letters, whitespace, separators, fewer than 8 digits, more than 15 digits, and a zero-leading country code.
2. Implement: add `PhoneNumberValidator#validate(String)` that enforces the fixed canonical-number rule and returns the validated value or raises a domain validation error.
3. Verify: run `PhoneNumberValidatorTest`.

## Stage 2 - Carrier Routing

### Step 2.1 - Implement deterministic routing

1. Test first: add `CarrierRouterTest` that asserts consecutive valid AU sends route to `Telstra`, `Optus`, then `Telstra`; NZ routes to `Spark`; Global numbers route to `Global`; and non-AU sends do not affect the AU sequence.
2. Implement: add the `Carrier` enum and a thread-safe `CarrierRouter#route(String)` for already validated, non-opted-out numbers. Initialize the AU sequence with `Telstra`.
3. Verify: run `CarrierRouterTest`. Construct a fresh router per test so alternation state cannot leak.

## Stage 3 - Opt-out State

### Step 3.1 - Implement idempotent opt-out behaviour

1. Test first: add `OptOutServiceTest` for the default non-opted-out state, successful opt-out, idempotent repeated opt-out, and rejection of invalid numbers without state changes.
2. Implement: add `OptOutService#optOut(String)` and `isOptedOut(String)` backed by a concurrent in-memory set and the shared phone-number validator.
3. Verify: run `OptOutServiceTest`.

## Stage 4 - Message Workflow and Storage

### Step 4.1 - Implement successful sending and lookup

1. Test first: add `MessageServiceTest` for AU, NZ, and Global sends. Assert a generated UUID, status `SENT`, the expected carrier, persistence, and successful lookup by ID.
2. Implement: add the message domain model, `MessageStatus`, `MessageFormat` (only `SMS`), a repository abstraction, a `ConcurrentHashMap` repository implementation, and `MessageService#send(...)` / `get(UUID)`.
3. Verify: run `MessageServiceTest`.

### Step 4.2 - Implement blocked messages and business validation

1. Test first: extend `MessageServiceTest` to assert that an opted-out send persists a retrievable `BLOCKED` message with `carrier: null`, does not consume the next AU carrier, and that unknown IDs, invalid numbers, blank content, missing format, and non-`SMS` format fail correctly. Assert invalid requests are not saved.
2. Implement: enforce this order in `MessageService`: validate number, content, and format; check opt-out; route only successful sends; set `SENT` or `BLOCKED`; save the message. Raise a dedicated not-found exception for missing IDs.
3. Verify: run the full `MessageServiceTest` suite.

## Stage 5 - HTTP API and Error Mapping

### Step 5.1 - Implement successful endpoint flows

1. Test first: add a full-context `MessageControllerTest` using real `MockMvc`. Cover AU send returning `201`, `SENT`, and `Telstra`; NZ send returning `Spark`; lookup returning the same response shape; opt-out returning its confirmation JSON; and a subsequent send returning `201`, `BLOCKED`, and JSON `carrier: null`, followed by successful lookup.
2. Implement: add request/response DTOs, message and opt-out controllers, and JSON mappings. The request must accept the PRD fields `destination_number`, `content`, and `format`; response values must preserve the fixed names and casing.
3. Verify: run `MessageControllerTest`. Recreate the application context for each integration test method so in-memory state and carrier alternation do not leak.

### Step 5.2 - Implement stable API errors

1. Test first: extend `MessageControllerTest` for malformed JSON, invalid destination number, missing or blank content, missing format, `MMS`, invalid opt-out number, malformed message ID, and unknown valid UUID. Assert status codes, stable error codes, and a non-empty `message`.
2. Implement: add a `@RestControllerAdvice` that maps JSON parsing failures, Bean Validation failures, domain validation failures, invalid path IDs, and missing messages to the fixed error response contract.
3. Verify: run the full `MessageControllerTest` suite.

## Stage 6 - Delivery Verification and Documentation

### Step 6.1 - Run the complete test suite

1. Test first: retain the application context smoke test as part of the suite.
2. Implement: only correct behaviour revealed by failing tests; do not add untested delivery simulation or endpoints.
3. Verify: run `sms\\mvnw.cmd clean test` from `sms/` to avoid stale build artifacts affecting the result.

### Step 6.2 - Document the application

1. Test first: manually review that README examples match the controller tests and the fixed response contract.
2. Implement: add a root README with startup instructions, cURL examples for all endpoints, guidance to URL-encode `+` in opt-out paths, response and error examples, validation rules, routing rules, in-memory limitations, and the v1 status assumption.
3. Verify: execute the documented startup and representative cURL examples against the application, then rerun `sms\\mvnw.cmd clean test`.

## Test Isolation and Completion Criteria

- Unit tests must construct fresh stateful services and routers for each test.
- API tests must isolate the Spring context per test method or reset all in-memory state before each test.
- Use JUnit Jupiter and AssertJ assertions already available through the Spring Boot test dependency.
- The suite is complete when it covers the three PRD acceptance scenarios, every endpoint has at least one positive and one negative test, message lookup covers both found and not-found cases, opt-out is covered at service and API layers, and `sms\\mvnw.cmd clean test` passes.
