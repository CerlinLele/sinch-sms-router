# Sinch SMS Router Live Coding Extension Directions

Based on the take-home PRD and current implementation, live coding is likely not a greenfield build but rather adding a business rule, state transition, or interface to the existing structure.

## Tier 1: Top Priorities to Prepare

| Extension Direction | How Interview May Ask | Code Entry Points | Key Tests |
| --- | --- | --- | --- |
| Message Status Transitions | After sending, status goes from `PENDING` to `SENT` / `DELIVERED` | `MessageService`, `MessageRepository`, `MessageController` | Valid/invalid transitions, unknown ID, immutable terminal states |
| Delivery Callback | Carrier notifies delivery result via webhook | New `POST /messages/{id}/delivery` endpoint | `SENT -> DELIVERED`, idempotent retries |
| Opt-in / Unsubscribe | Can users re-subscribe? | `OptOutService`, `OptOutController` | Repeated opt-in, unsubscribed numbers, can send after re-subscribe |
| Extended Routing Rules | Add Vodafone, or weighted load balancing | `CarrierRouter`, `Carrier` | Order, weight boundaries, AU-specific rules don't affect NZ |
| Carrier Failover | Fallback to Optus if Telstra unavailable | `CarrierRouter` or new carrier availability/gateway abstraction | Primary failure, fallback, complete outage |
| AU/NZ Precise Phone Validation | AU and NZ formats require true differences | `PhoneNumberValidator` | Country code, length, mobile prefixes, invalid local formats |

Message status transitions are the most obvious extension point: `MessageStatus` already defines `PENDING` and `DELIVERED`, but `MessageService` currently sends directly to `SENT` or `BLOCKED`. The README explicitly mentions delivery simulation as future work, so this is likely a live coding candidate.

Recommended state machine:

```text
PENDING -> SENT -> DELIVERED
    \----------> FAILED (if interviewer requires)

PENDING -> BLOCKED
BLOCKED, DELIVERED, FAILED are terminal states
```

Do not allow arbitrary status overwrites—the interviewer will likely ask why `DELIVERED` can revert to `PENDING`.

## Tier 2: Senior Design Assessment

### 1. Introduce Real Carrier Gateway

May require separating "carrier selection" from "carrier invocation":

```java
interface SmsGateway {
    SendResult send(Message message);
}
```

Example: `TelstraGateway`, `OptusGateway`, `SparkGateway`. Flow becomes:

```text
Validate -> Check opt-out -> Save PENDING -> Select carrier
-> Invoke gateway -> Update SENT / FAILED
```

Tests dependency inversion, exception handling, and test doubles. Live coding typically uses a fake gateway rather than real external APIs.

### 2. Retry and Fallback

Common requirements:

- Carrier timeout triggers two retries.
- Telstra failure falls back to Optus.
- Business rejections do not retry; network errors do.
- Never resend already-successful messages.

Requires distinguishing:

- **Transient failure**: retry eligible.
- **Permanent failure**: no retry.
- **Provider received but client timeout**: risk of duplicate sending; need idempotency key.

### 3. Idempotent Sends

May add `Idempotency-Key` header to prevent duplicate messages from client retries.

Minimal implementation maintains:

```text
idempotency key -> message ID
```

Same key and request return original message; same key, different request returns `409 Conflict`. Atomic check-and-create is essential.

### 4. Concurrency Safety

Current implementation already uses `ConcurrentHashMap` and `AtomicReference`. Interviewer may ask:

- Does 100 concurrent AU requests strictly alternate?
- What semantics when opt-out and send happen simultaneously?
- Is `AtomicReference` valid across multiple instances?
- Are routing order and opt-outs lost on service restart?

`CarrierRouter` is safe for single-instance concurrency but does not guarantee global round-robin across instances. Multi-instance typically requires database, Redis, or explicit per-instance semantics.

### 5. Persistence

Replace in-memory repository with JPA/H2/PostgreSQL. Current `MessageRepository` interface already defines clear boundaries.

May require:

- Messages survive service restart.
- Opt-outs persist.
- Optimistic locking prevents concurrent status overwrites.
- Database unique constraints ensure idempotency.

If time is short, don't proactively refactor to JPA—only if explicitly required.

## Tier 3: API and Product Features

### Opt-out Management

Beyond unsubscribe, may add:

- `GET /optout/{phoneNumber}`: query status.
- `DELETE /optout/{phoneNumber}`: re-subscribe.
- Persist unsubscribe timestamp, source, and reason.
- Global vs. sender-specific unsubscribe.
- `SENT` messages unaffected by later opt-outs.
- Clarify if `PENDING` messages should be blocked post-unsubscribe.

Current `OptOutService` needs only `remove` / `isOptedOut` API additions for a solid 15–20 minute live coding problem.

### Query and Pagination

May add:

```http
GET /messages?status=BLOCKED&carrier=Telstra&page=0&size=20
```

Consider:

- Repository query capability.
- Stable sort, e.g., creation time descending.
- Invalid status/carrier returns `400`.
- Empty result returns `200 []`.
- Do not return message content or other sensitive data unless explicitly needed.

### Batch Send

Example:

```http
POST /messages/batch
```

Key design questions:

- Does one failure fail the entire batch?
- Return `207 Multi-Status` or independent per-item results?
- AU carrier alternation counted on valid, unsubscribed messages?
- Batch size limit.
- Async or sync processing?

### Scheduled Messages

Add `send_at`:

- Future timestamps save as `PENDING`.
- Check opt-out at send time or creation time?
- Always use UTC for time zones.
- Inject `Clock` in tests to avoid real-time dependency.

### SMS Length and Segmentation

May require:

- GSM-7: 160 characters.
- Unicode/UCS-2: 70 characters.
- Long content splits into segments.
- Limit max segments or compute fees.

Focus is not memorizing GSM character tables but clarifying whether the problem expects "simple character length" or "true SMS encoding rules."

## Routing Extensions

Current AU uses simple global alternation. Possible variations:

1. Add carrier: Telstra → Optus → Vodafone.
2. Weighted round-robin: e.g., 50% / 30% / 20%.
3. Route by phone number prefix.
4. Route by lowest cost carrier.
5. Exclude failed carriers by health status.
6. Route by message type or customer tier.
7. Configuration-driven rules instead of hardcoded Java.
8. Per-country independent round-robin state.
9. Sticky routing: same phone number always uses same carrier.
10. Capacity limits: move to next carrier if rate limit reached.

In live coding, avoid complex rule engines from the start. Extract the minimal `RoutingStrategy` or configuration mapping first.

## Error Handling Extensions

Existing `ApiExceptionHandler` already standardizes error structure. May require:

- `409 INVALID_STATUS_TRANSITION`.
- Carrier timeout maps to `503`.
- Add `timestamp`, `path`, `trace_id`.
- Return all validation errors in one response.
- Do not leak internal exception messages to clients.
- Distinguish invalid enum from malformed JSON with different error codes.

One subtle point: unsubscribed numbers currently return `201 Created` and save as `BLOCKED`. This is reasonable—the system recorded a send attempt. Changing to `403` might prevent querying this blocked attempt via status API. Be ready to explain the tradeoff.

## Test-Focused Live Coding

Interviewer may not request new features but instead ask to:

- Add concurrent AU routing tests.
- Add opt-out idempotency tests.
- Add illegal state transition tests.
- Fix a failing test.
- Refactor controller tests from manual string parsing to `ObjectMapper`.
- Write a repository contract test.
- Mock gateway, verify message status on failure.
- Parameterize tests for various phone numbers.

This style assesses whether you define behavior first, then minimize implementation—not a wholesale project refactor in one go.

## Recommended Hands-On Preparation Order

If time is limited, prioritize these four scenarios:

1. Add `PATCH /messages/{id}/status` with valid state machine.
2. Add `DELETE /optout/{phoneNumber}` to allow re-subscription.
3. Add Vodafone to routing or implement weighted round-robin.
4. Introduce fake `SmsGateway`, handle success, failure, and fallback.

Each follows the same rhythm:

```text
Clarify business semantics
-> Add service tests first
-> Minimal implementation
-> Add controller tests
-> Run related test suite
-> Explain concurrency, persistence, production constraints
```

## Summary

Message state lifecycle, extensible routing, opt-in, and carrier failure/fallback are the four most likely live coding directions for this project, with message state lifecycle being the highest probability.
