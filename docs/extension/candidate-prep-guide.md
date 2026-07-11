# Candidate Preparation Guide: Sinch SMS Router Live Coding

A practical guide to prepare for your 60–90 minute live coding interview on the Sinch SMS Router codebase. This guide covers what to expect, how to prepare, and strategies to succeed.

---

## Before the Interview

### 1. Understand the Project (30–45 minutes)

**Read these in order:**

1. [README.md](../README.md) — Overview, architecture, how to build and run tests.
2. [Live Coding Extension Directions](./sinch-live-coding-extension-directions-en.md) — The likely problem areas you'll face.

**Key takeaways to have in your head:**

- The system routes SMS to AU/NZ phone numbers via different carriers (Telstra, Optus, Spark).
- Current flow: validate → check opt-out → save message → select carrier → return 201.
- `MessageStatus` enum has states: `PENDING`, `SENT`, `DELIVERED`, `FAILED`, `BLOCKED`.
- Current implementation uses an in-memory repository (not a database).
- Tests use JUnit 5 with Spring Boot test utilities.

### 2. Set Up Your Environment (15–20 minutes)

**Clone and build:**

```bash
git clone <repo-url>
cd sinch-sms-router
./mvnw clean install
./mvnw test
```

**Verify everything works:**

```bash
./mvnw test -Dtest=MessageServiceTest
```

**Familiarize yourself with the test runner:**

- How to run a single test class.
- How to run a single test method.
- How to see test output.

If you use an IDE (IntelliJ, Eclipse, VS Code), learn the keyboard shortcut to run/debug tests there too.

### 3. Explore the Codebase (30–45 minutes)

**Know where these classes live:**

- `MessageService.java` — Core business logic: validation, opt-out check, carrier selection.
- `MessageController.java` — REST endpoints: `POST /messages`.
- `MessageRepository.java` (interface) — In-memory storage.
- `OptOutService.java` — Tracks unsubscribed numbers.
- `CarrierRouter.java` — Implements round-robin carrier selection.
- `PhoneNumberValidator.java` — Validates AU/NZ formats.

**Read one test class end-to-end:**

- [MessageServiceTest.java](../../src/test/java/com/sinch/sms/service/MessageServiceTest.java) or similar.
- Understand the structure: setup → action → assertion.
- Note the testing patterns used.

### 4. Prepare for Three Likely Scenarios (45–60 minutes)

Spend 15 minutes on each scenario; think through (don't code yet):

#### Scenario A: Message Status Transitions (Highest Probability)

**The Problem:**
- Implement a state machine where messages flow: `PENDING → SENT → DELIVERED`.
- Terminal states (`BLOCKED`, `DELIVERED`, `FAILED`) can't transition further.
- Add a `PATCH /messages/{id}/status` endpoint.
- Validate transitions; reject invalid ones.

**Preparation:**

1. What tests would you write? (List 5–7.)
   - Valid transitions: `PENDING → SENT`, `SENT → DELIVERED`.
   - Invalid transitions: `DELIVERED → PENDING`, `BLOCKED → SENT`.
   - Edge cases: non-existent ID (404), same transition twice (idempotency).

2. Where would the validation logic live? (Service layer.)

3. What's the minimal code change?
   - Add a method to `MessageService`: `updateMessageStatus(id, newStatus)`.
   - Check if transition is valid.
   - Persist and return.
   - Add controller endpoint that calls it.

4. What would you ask the interviewer?
   - "Is the request body just `{"status": "SENT"}`?"
   - "Should I return the updated message or 204?"
   - "Do I need to persist this to a database, or is in-memory OK?"

#### Scenario B: Unsubscribe Re-subscription (30% Probability)

**The Problem:**
- Implement `DELETE /optout/{phoneNumber}` to allow re-subscription.
- After delete, the phone number can receive messages again.
- Write tests for happy path, 404, idempotency.

**Preparation:**

1. What tests would you write?
   - Delete a phone number that's opted out → succeeds.
   - Delete a phone number that's never opted out → 404 or 204? (Clarify.)
   - Send a message to a re-subscribed number → succeeds.
   - Delete the same number twice → idempotent.

2. Minimal implementation:
   - Add `remove(phoneNumber)` to `OptOutService`.
   - Add `DELETE /optout/{phoneNumber}` endpoint in controller.
   - Call the service method.

3. Questions to ask:
   - "Should deleting a non-existent number return 404 or 204?"
   - "Do I need to log or audit who deleted the opt-out?"

#### Scenario C: Routing Extension (10% Probability)

**The Problem:**
- Add Vodafone to the round-robin (currently Telstra & Optus).
- Later: implement weighted round-robin (50% / 30% / 20%).

**Preparation:**

1. What tests would you write?
   - Send 3 AU messages → verify they go to Telstra, Optus, Vodafone in order.
   - Weighted distribution: 100 sends should split ~50%, ~30%, ~20%.
   - NZ routing unaffected.

2. Where's the logic?
   - `CarrierRouter.selectCarrier(phoneNumber)`.
   - Increment a counter; mod by carrier count.

3. What's the change?
   - Add `VODAFONE` to `Carrier` enum.
   - Update the loop in `CarrierRouter` to include it.
   - For weights, add a `weight` field to enum or config.

---

## During the Interview

### Opening (First 5 minutes)

**You say:**

> "Thanks for walking me through the interview. I've reviewed the README and the extension directions. I'm ready to code. Where do we start?"

**Listen carefully to the problem statement. Then ask clarifying questions:**

- "Just to confirm, I need to implement [restate the problem]?"
- "Are there any constraints I should know? (Database, time limit, etc.)"
- "What's the highest priority: happy path first, or should I handle edge cases upfront?"

**Interviewer is evaluating:** Can you listen, ask good questions, and avoid false starts?

---

### Planning Phase (First 10 minutes)

**Before you write any code:**

1. **State what you'll do:**

   > "I'll start by writing 4–5 test cases covering the happy path and key edge cases. Then I'll implement the minimal logic to make them pass. Then we'll review."

2. **List the test cases:**

   ```
   ✓ Valid transition succeeds
   ✓ Invalid transition fails
   ✓ Non-existent ID returns 404
   ✓ Same request twice is idempotent
   ✓ [one more context-specific case]
   ```

3. **Sketch the files you'll touch:**

   > "I'll add a test in `MessageServiceTest`, a method in `MessageService`, and an endpoint in `MessageController`."

**Interviewer is evaluating:** Do you think before you code? Are you methodical?

---

### Implementation Phase (Next 40–50 minutes)

#### Step 1: Write the Tests (10–15 minutes)

**Start with a test class. Don't worry about making it compile yet—just think out loud:**

```java
@SpringBootTest
class MessageStatusTransitionTest {
    
    @Test
    void testValidTransitionFromPendingToSent() {
        // Arrange: create a message in PENDING state
        // Act: transition to SENT
        // Assert: status is SENT
    }
    
    @Test
    void testInvalidTransitionFromDeliveredToPending() {
        // Arrange: message in DELIVERED state
        // Act: attempt transition to PENDING
        // Assert: throws InvalidStateTransitionException or returns 400
    }
    
    @Test
    void testNonExistentMessageReturns404() {
        // Attempt to PATCH non-existent ID
        // Expect 404
    }
}
```

**At this stage, say:** "These are my test scenarios. Let me implement the service and controller to make them pass."

**Interviewer is evaluating:** Do you know what behavior to test?

---

#### Step 2: Implement Service Logic (15–20 minutes)

**Add a method to `MessageService`:**

```java
public Message updateMessageStatus(UUID messageId, MessageStatus newStatus) {
    // 1. Find the message
    Message message = messageRepository.findById(messageId)
        .orElseThrow(() -> new NotFoundException("Message not found"));
    
    // 2. Validate the transition
    if (!isValidTransition(message.getStatus(), newStatus)) {
        throw new InvalidStateTransitionException(
            String.format("Cannot transition from %s to %s", 
                message.getStatus(), newStatus)
        );
    }
    
    // 3. Update and save
    message.setStatus(newStatus);
    message.setUpdatedAt(Instant.now());
    return messageRepository.save(message);
}

private boolean isValidTransition(MessageStatus from, MessageStatus to) {
    // Define your state machine here
    return /* your logic */;
}
```

**As you code, narrate your thinking:**

> "I'm putting validation in the service because that's where business logic belongs. The controller will just call this and handle exceptions."

**Interviewer is evaluating:** Is your logic sound? Do you know the framework? Can you explain decisions?

---

#### Step 3: Add the Controller Endpoint (5–10 minutes)

```java
@PatchMapping("/{id}/status")
public ResponseEntity<MessageResponse> updateMessageStatus(
    @PathVariable UUID id,
    @RequestBody UpdateStatusRequest request) {
    
    Message updated = messageService.updateMessageStatus(id, request.getStatus());
    return ResponseEntity.ok(toResponse(updated));
}
```

**At this point, say:** "Now let's run the tests and see what happens."

**Interviewer is evaluating:** Do you follow REST conventions? Can you wire it up?

---

#### Step 4: Run Tests and Debug (5–10 minutes)

**Run your test class:**

```bash
./mvnw test -Dtest=MessageStatusTransitionTest
```

**If tests fail:**

- Read the error message carefully.
- Ask yourself: "Is it a compile error, an assertion error, or a runtime error?"
- Debug systematically: print statements, breakpoints, or add `System.out.println()` temporarily.
- Say aloud what you're thinking: "The error says `newStatus` is null—let me check the request parsing..."

**If tests pass:**

> "Good! My tests pass. Let me run the full suite to make sure I didn't break anything else."

```bash
./mvnw test
```

**Interviewer is evaluating:** Can you debug? Are you methodical? Do you check for regressions?

---

### Testing & Edge Cases Phase (10–15 minutes)

**Interviewer may ask:** "What edge cases are you concerned about?"

**You should mention:**

1. **Null/missing inputs** — Non-existent ID, null status.
2. **Boundary conditions** — Transitioning from/to each state.
3. **Concurrency** — Two requests updating the same message at once.
4. **Idempotency** — Same request sent twice.

**If time, add one more test:**

```java
@Test
void testIdempotency() {
    Message msg = createPendingMessage();
    Message result1 = messageService.updateMessageStatus(msg.getId(), SENT);
    Message result2 = messageService.updateMessageStatus(msg.getId(), SENT);
    
    assertEquals(result1.getId(), result2.getId());
    assertEquals(SENT, result2.getStatus());
}
```

**Interviewer is evaluating:** Do you think like a QA? Can you spot edge cases?

---

### Code Review & Discussion Phase (10–15 minutes)

**Interviewer will ask open-ended questions:**

#### Q1: "Walk me through what happens when this endpoint is called."

**Your answer (narrate like you're explaining to a junior):**

> "A client sends a PATCH request to `/messages/{id}/status` with `{"status": "SENT"}`. The controller receives it, extracts the ID and new status, and calls `messageService.updateMessageStatus()`. The service looks up the message by ID. If it doesn't exist, we throw a 404. If it exists, we check: is this a valid transition? We check a state machine: PENDING can go to SENT, SENT can go to DELIVERED, but DELIVERED is terminal. If valid, we update the status, save it, and return the updated message. If invalid, we throw an exception, which the exception handler catches and returns a 409 or 400."

**Interviewer is evaluating:** Can you explain architecture clearly?

---

#### Q2: "What would you change for multiple instances?"

**Your thoughtful answer:**

> "Right now we're in-memory, so each instance has its own state. If two instances are running and both try to update the same message, they'd have stale copies. In production, we'd need: (a) a shared database with optimistic locking, or (b) a distributed lock (Redis). The state machine logic doesn't change—just the persistence layer. I'd keep the `MessageRepository` interface abstraction so we can swap the impl."

**Interviewer is evaluating:** Do you understand distributed systems? Can you design for scale?

---

#### Q3: "If this were production, what else would you add?"

**Your answer (prioritize):**

1. **Logging** — Log transitions, especially invalid ones.
2. **Audit trail** — Track who changed the status and when.
3. **Metrics** — Count transitions by type; alert if many failures.
4. **Rollback strategy** — What if we transition to a state and then need to undo?

**Interviewer is evaluating:** Do you think beyond the code?

---

## Dos and Don'ts

### ✅ Do

- **Ask clarifying questions** — Better to ask upfront than assume wrong.
- **Write tests before implementation** — Shows discipline.
- **Think out loud** — Explain your reasoning as you code.
- **Run the test suite** — Catch regressions early.
- **Admit when you don't know** — "I'm not sure—let me look that up" is better than guessing.
- **Reference the codebase** — "I see the pattern used in `OptOutService`; I'll follow the same..."
- **Discuss tradeoffs** — "I could do X or Y; I chose Y because..."

### ❌ Don't

- **Jump to coding without planning** — You'll waste time.
- **Write brittle tests** — Tests should be stable, not flaky.
- **Add unnecessary abstractions** — Keep it simple; YAGNI (You Aren't Gonna Need It).
- **Ignore errors** — Debug them; don't move on and hope.
- **Claim certainty you don't have** — "I'm pretty sure this is thread-safe" (when you're not) is a red flag.
- **Spend too long on one edge case** — If you're stuck, move on and come back.
- **Ignore the interviewer's hints** — If they say "you might want to consider concurrency," they mean it.

---

## Time Management

| Phase | Time | What to Do |
| --- | --- | --- |
| Warm-up | 5 min | Listen, nod, ask one clarifying question. |
| Problem | 5 min | Repeat back the requirement; ask 2–3 clarifying questions. |
| Planning | 5 min | List tests, sketch files, outline approach. |
| Tests | 10 min | Write test class with 4–5 test cases. |
| Implementation | 25 min | Service logic, controller, wire it up. |
| Debug | 10 min | Run tests, fix failures, check for regressions. |
| Review | 15 min | Walk through code, discuss production, answer questions. |

**If you fall behind:**

- At 30 minutes: You should have tests written and starting implementation.
- At 45 minutes: Core logic should be implemented; tests are running.
- At 60 minutes: Tests pass; you're reviewing code.

**If you're ahead:**

- Add another test for a hairy edge case.
- Refactor for clarity (small variable rename, extract a method).
- Discuss production hardening.

---

## Mock Interview Checklist

Before your real interview, do a **dry run** in your own environment:

- [ ] Clone the repo; build and run tests.
- [ ] Pick one scenario (state transitions, opt-out delete, or routing).
- [ ] Set a timer for 60 minutes.
- [ ] Write the test class (don't implement yet).
- [ ] Implement the service and controller.
- [ ] Run tests; debug any failures.
- [ ] Review your code; explain it aloud.
- [ ] Note what took longer than expected.
- [ ] Repeat with a different scenario.

**After each mock run, ask yourself:**

- Did I communicate clearly?
- Were my tests good?
- Was my implementation minimal?
- Did I handle edge cases?
- Could I explain my code to someone else?

---

## FAQs

### Q: Do I need to memorize the codebase?

**A:** No. But know where to find things. You should be able to open a file and understand it in 30 seconds.

### Q: Can I use Google/Stack Overflow during the interview?

**A:** Yes, absolutely. Just don't spend 10 minutes on it. Say: "Let me check the Spring docs for the right annotation..." and move on.

### Q: What if I don't finish?

**A:** That's okay. Interviewers care about *how* you work, not just the final output. If you're at 50% with 5 minutes left, walk the interviewer through what you'd do next: "The next step would be to add these two tests..." That shows intent.

### Q: What if my tests fail and I can't debug?

**A:** Stay calm. Say: "Hmm, the test is failing because [reason]. I think the issue is [hypothesis]. Let me add a debug statement to check." Most times, talking through it helps. The interviewer may also help: "Have you checked if the message is actually being saved?"

### Q: What if the interviewer asks about something I don't know?

**A:** Honest answer: "I haven't worked with that before, but here's how I'd approach it..." Then think out loud. Bonus if you can relate it to something you *do* know: "It's like caching in Redis—you need to think about staleness and invalidation."

### Q: Can I ask the interviewer for help?

**A:** Yes, but use it sparingly. "Can you show me how to run this test?" is good. "Can you write the code for me?" is not.

---

## Quick Reference: State Machines

**Option A: Message Status Transitions**

```
PENDING ──SENT──> SENT ──DELIVERED──> DELIVERED (terminal)
    └─BLOCKED─> BLOCKED (terminal)

Rules:
- PENDING → {SENT, BLOCKED}
- SENT → DELIVERED
- DELIVERED → (none; terminal)
- BLOCKED → (none; terminal)
- FAILED → (none; terminal, if added)
```

**Option B: Opt-out Re-subscription**

```
Delete removes number from opt-out list.
After delete, number can receive messages.
Non-existent delete is idempotent (succeeds silently or returns 204).
```

**Option C: Carrier Routing**

```
AU: Telstra → Optus → Vodafone → Telstra → ...
NZ: Spark (unchanged)

Weighted (future):
AU: Telstra (50%) → Optus (30%) → Vodafone (20%)
```

---

## Resources

- [Project README](../README.md)
- [Live Coding Extension Directions](./sinch-live-coding-extension-directions-en.md)
- [Live Coding Interview Process](./live-coding-interview-process.md) (interviewer guide; useful to understand expectations)
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [JUnit 5 Documentation](https://junit.org/junit5/)

---

## Final Thoughts

Remember: **The interviewer wants you to succeed.** They're not trying to trick you. They're evaluating:

1. Can you break down a problem?
2. Can you write clean, testable code?
3. Can you communicate your thinking?
4. Can you recover from mistakes?
5. Do you care about quality?

If you do these five things, you'll do great. Good luck!
