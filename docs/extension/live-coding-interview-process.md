# Sinch SMS Router: Live Coding Interview Process

A structured guide for conducting 60–90 minute live coding interviews on the Sinch SMS Router codebase. This guide helps interviewers set clear expectations, evaluate technical depth, and provide consistent feedback.

## Interview Flow Overview

```
0–5 min:  Warm-up & context
5–10 min: Problem statement & clarification
10–60 min: Implementation (40–50 minutes)
60–75 min: Testing & edge cases (10–15 minutes)
75–90 min: Code review & discussion (10–15 minutes)
```

## Phase 1: Warm-up & Context (0–5 minutes)

**Interviewer says:**

> "Thanks for joining. This is a live coding session where you'll extend a real SMS routing system. We'll spend about 60–90 minutes on this. You're welcome to ask clarifying questions, explain your thinking, and use IDE features—treat it like a normal workday. At the end, we'll review the code together."

**What to cover:**

- Candidate's familiarity with the codebase (have they read the README?)
- Any setup questions (IDE, build tool, test runner)
- Confirm timezone/context (AU/NZ carriers, phone number formats)
- Reassure: "There's no single 'correct' answer—we're evaluating your problem-solving, testing mindset, and communication."

---

## Phase 2: Problem Statement & Clarification (5–10 minutes)

### Option A: Message Status Lifecycle (Recommended, 60% probability)

**Interviewer says:**

> "Currently, when we send a message, it goes straight to `SENT` or `BLOCKED`. In production, we want to model the real world: a message is first `PENDING`, then (if opt-out isn't triggered) we mark it `SENT` after the carrier accepts it, and later a webhook from the carrier updates it to `DELIVERED`. 
>
> Your task: Implement a state machine that validates legal status transitions. A message should move through `PENDING → SENT → DELIVERED`, or be blocked at `PENDING → BLOCKED`. Terminal states (`BLOCKED`, `DELIVERED`, `FAILED`) should not transition further.
>
> Implement a `PATCH /messages/{id}/status` endpoint that updates a message's status while enforcing the state machine. Write tests for valid transitions, invalid transitions, and edge cases."

**Clarification questions the candidate should ask (and your responses):**

| Candidate Question | Interviewer Response |
| --- | --- |
| Do I need to modify the database schema? | No, `MessageStatus` enum already has the states you need. Use in-memory repository for now. |
| What if someone tries to PATCH a non-existent message ID? | Return `404 Not Found`. That's a good test case. |
| Can I add a `lastUpdated` timestamp? | You can, but it's not required for the core functionality. Focus on the state machine first. |
| Should I validate the transition in service or controller? | Good question—put validation in service (single responsibility), controller just calls service. |
| How do I handle concurrent requests to the same message? | Good catch. Discuss briefly: single-instance we're safe with our current in-memory store, but flag multi-instance as a future consideration. |

**Expected candidate actions:**

- Asks for clarification on which states are terminal.
- Asks whether `FAILED` state is required or optional.
- Confirms the endpoint path and HTTP method.
- Clarifies if they need to add an audit log or just update status.

---

### Option B: Unsubscribe Re-subscription (Alternative, 30% probability)

**Interviewer says:**

> "We have an `OptOutService` that tracks unsubscribed phone numbers. Once opt-out, users stay opted out forever. But product wants users to re-subscribe.
>
> Implement a `DELETE /optout/{phoneNumber}` endpoint that removes a phone number from the opt-out list. Write tests for: re-subscribing a previously opted-out number, attempting to delete a never-opted-out number, and verifying that after deletion, new messages can be sent."

---

### Option C: Routing Extension (Alternative, 10% probability)

**Interviewer says:**

> "Currently we round-robin between Telstra and Optus for AU numbers. Product wants to add Vodafone as a third option, and eventually support weighted routing (e.g., 50% Telstra, 30% Optus, 20% Vodafone).
>
> First: add Vodafone to the round-robin. Then: add a `weight` field to the `Carrier` enum and implement weighted distribution. Write tests to verify the round-robin order respects weights."

---

## Phase 3: Implementation (10–60 minutes)

### Structured Approach (What You're Evaluating)

**Tier 1: Excellent** (candidate demonstrates all of the below)

1. **Confirms understanding** — Repeats the problem back; asks about edge cases before coding.
2. **Writes tests first** — Sketches out test cases (valid transition, invalid transition, 404, idempotency).
3. **Minimal implementation** — Adds only what's needed: state transition validation, no extra abstractions.
4. **Tests pass** — Runs tests and verifies they pass (or debugs failures in real time).
5. **Handles concurrency thoughtfully** — Acknowledges single vs. multi-instance; no false confidence.

**Tier 2: Solid** (candidate demonstrates most)

- Writes some tests but maybe not before implementation.
- Implementation works but includes defensive code that's not required.
- Tests pass but only after minor debugging.
- Briefly discusses concurrency concerns.

**Tier 3: Needs Improvement**

- Jumps to implementation without tests.
- Tests fail and candidate can't debug.
- Doesn't consider edge cases (404, invalid transitions).
- Dismisses concurrency entirely ("not relevant right now").

### Key Checkpoints for the Interviewer

**At ~15 minutes:**
- Has the candidate written at least a test class sketch? (Good signal.)
- Are they working in the right files (`MessageService`, `MessageController`, `MessageRepository`)?
- Prompt if stuck: "What's the first test case you'd write?"

**At ~30 minutes:**
- Is the core logic implemented? (State machine validation.)
- Are tests running or close to running?
- If they're stuck on something, help unblock (e.g., "Let me show you how to run tests in this project").

**At ~45 minutes:**
- Are tests passing?
- Have they handled the main edge cases (404, invalid transition, idempotency)?
- Are they on track to finish in the next 10–15 minutes?

---

## Phase 4: Testing & Edge Cases (60–75 minutes)

**Interviewer guidance:**

"Let's step back and make sure we've covered edge cases. Run the test suite and show me what we've validated so far."

### For Message Status Transitions:

**Candidate should verify:**

- ✅ `PENDING → SENT` succeeds.
- ✅ `SENT → DELIVERED` succeeds.
- ✅ `PENDING → BLOCKED` succeeds.
- ✅ `DELIVERED → PENDING` fails (invalid).
- ✅ `BLOCKED → SENT` fails (terminal state).
- ✅ Non-existent message ID returns 404.
- ✅ Sending same request twice is idempotent (returns the same result).

**Edge case prompts (if time allows):**

- "What if we try to PATCH a message that doesn't exist?"
- "What if two requests arrive for the same message simultaneously?"
- "Should the endpoint return the updated message or just a 204 No Content?"

### For Unsubscribe Re-subscription:

**Candidate should verify:**

- ✅ `DELETE /optout/+61412345678` succeeds.
- ✅ Deleting a never-opted-out number returns 204 or 404 (clarify which).
- ✅ After deletion, new messages are NOT blocked.
- ✅ Deleting the same number twice is safe (idempotent).

### For Routing:

**Candidate should verify:**

- ✅ Three consecutive AU sends alternate: Telstra → Optus → Vodafone → Telstra.
- ✅ With weights, distribution matches (e.g., 100 sends: ~50 Telstra, ~30 Optus, ~20 Vodafone).
- ✅ NZ routing unaffected by AU changes.
- ✅ Non-AU numbers bypass the router.

---

## Phase 5: Code Review & Discussion (75–90 minutes)

**Interviewer walks through the code together:**

### What to Praise

- "I like how you put the validation in the service layer—keeps the controller clean."
- "Good catch: you're checking for null before transition. That's defensive."
- "Your test names are clear: `testCannotTransitionFromTerminalState`. Nice."

### What to Probe

**Ask open-ended questions:**

- "Walk me through what happens when this endpoint is called."
- "What would you change if we needed to support multiple instances?"
- "How would you test this against a real database?"
- "If this were production, what else would you add?" (Logging, audit trail, retry logic, etc.)

**Look for:**

- Can they explain their design decisions?
- Do they think about production constraints unprompted?
- Can they identify what's missing (persistence, observability)?

### Production Discussion (Tier 2+ candidates)

Prompt with:

> "Imagine this goes to production tomorrow. What concerns do you have?"

**Expected answers (in priority order):**

1. **Concurrency**: Single instance is safe, but multi-instance needs a database or lock.
2. **Persistence**: Restart loses all state. Need JPA/PostgreSQL.
3. **Observability**: No logging—hard to debug production issues.
4. **Idempotency**: What if the same PATCH arrives twice before we respond?
5. **Rate limiting**: Should we throttle status updates?

**If candidate nails this:** "That's excellent foresight. You'd be ready to take this to production with a few hours of hardening."

---

## Scoring Rubric

| Category | Excellent | Good | Acceptable | Needs Work |
| --- | --- | --- | --- | --- |
| **Problem Understanding** | Asks clarifying questions, repeats back, identifies edge cases | Understands core requirement, asks 1–2 questions | Understands requirement but misses nuance | Misunderstands or starts coding before clarifying |
| **Test-Driven Approach** | Tests written before implementation; covers happy path + 3+ edge cases | Tests written alongside code; covers happy path + 1–2 edge cases | Some tests written; mostly cover happy path | No tests or tests written after code |
| **Implementation Correctness** | All tests pass; logic is minimal and clear | Tests pass with minor fixes; logic is sound but verbose | Tests mostly pass; logic has minor bugs | Tests fail; significant logic errors |
| **Code Quality** | Reuses existing patterns; no unnecessary abstractions | Mostly follows project conventions; some cleanup needed | Follows conventions inconsistently | Code is hard to follow or violates patterns |
| **Edge Case Handling** | Handles 4+ edge cases (404, invalid transition, null, concurrency) | Handles 2–3 edge cases | Handles 1 edge case | No deliberate edge case handling |
| **Communication** | Explains reasoning; asks for feedback; thinks aloud | Generally clear; explains major decisions | Explains mostly in retrospect | Quiet; little explanation |
| **Production Thinking** | Discusses persistence, concurrency, logging, observability unprompted | Mentions 1–2 production concerns when prompted | Vague about production; acknowledges gaps | No awareness of production constraints |

---

## Sample Scoring

**Candidate A (Senior Engineer Expected):**

- Problem Understanding: Excellent ✅
- Test-Driven: Excellent ✅
- Correctness: Excellent ✅
- Code Quality: Good ✅
- Edge Cases: Excellent ✅
- Communication: Excellent ✅
- Production: Excellent ✅

**Decision:** Strong hire. Ready for a complex feature independently.

---

**Candidate B (Mid-Level):**

- Problem Understanding: Good ✅
- Test-Driven: Good ✅
- Correctness: Good ✅
- Code Quality: Good ✅
- Edge Cases: Good ✅
- Communication: Good ✅
- Production: Acceptable (prompts needed) ⚠️

**Decision:** Hire. Solid engineer; could use mentoring on production readiness.

---

**Candidate C (Junior):**

- Problem Understanding: Acceptable ⚠️
- Test-Driven: Acceptable ⚠️
- Correctness: Acceptable ⚠️
- Code Quality: Needs Work ❌
- Edge Cases: Needs Work ❌
- Communication: Acceptable ⚠️
- Production: Needs Work ❌

**Decision:** Pass for now. Come back after 6–12 months of experience. Offer feedback on testing and edge-case thinking.

---

## Common Pit Falls & Recovery

### Pit Fall 1: Candidate Jumps to Coding Without Tests

**You see:** Candidate opens IDE and starts implementing immediately.

**What to do:** Gently interrupt.

> "I notice you're diving straight into implementation. Walk me through what tests you'd write first—what should we validate?"

**Expected recovery:** Candidate sketches 2–3 test cases, then implements to make them pass.

---

### Pit Fall 2: Candidate Forgets Edge Cases

**You see:** Tests pass for happy path; candidate says "done."

**What to do:** Ask directly.

> "What about the case where the message doesn't exist? Or an invalid transition?"

**Expected recovery:** Candidate adds tests, discovers missing logic, fixes it.

---

### Pit Fall 3: Candidate Gets Stuck on Syntax/Build

**You see:** Tests won't compile; candidate is frustrated.

**What to do:** Unblock quickly (don't let 20 minutes disappear).

> "Let me show you how to run tests in this repo. [Run together.] Now, what should the test do?"

**Expected recovery:** Candidate focuses back on logic, not tooling.

---

### Pit Fall 4: Candidate Overthinks Concurrency

**You see:** Candidate spends 15 minutes on thread safety for a 60-minute interview.

**What to do:** Refocus.

> "Good instinct. For now, let's assume single-instance. We can note 'multi-instance needs a lock' for production. Move forward?"

**Expected recovery:** Candidate simplifies and finishes on time.

---

## Feedback Template

**After the interview, send:**

---

**Hi [Candidate],**

**Thank you for a great session on the Sinch SMS Router! Here's our feedback:**

**Strengths:**

- [1–2 specific things they did well: e.g., "You wrote tests before implementing—shows maturity." or "Clear communication about trade-offs."]

**Areas for Growth:**

- [1–2 specific gaps: e.g., "Could have explored more edge cases." or "Production constraints came up only when prompted."]

**Next Steps:**

- [Decision: hire / pass / revisit in 6 months]
- [If hire: "We'd love to have you on the team."]
- [If pass: "You're close—focus on X and Y, and you'll be a strong fit."]

**Resources:**

- [Link to codebase README if they want to practice more]
- [Link to live coding extension directions]

---

## Interview Variants

### Shorter Interview (45 minutes)

**Time Allocation:**

- 0–3 min: Warm-up
- 3–8 min: Problem statement
- 8–40 min: Implementation
- 40–45 min: Quick review

**Recommendation:** Use the **unsubscribe re-subscription** problem (Option B)—smaller scope, faster to implement.

---

### Longer Interview (120 minutes)

**Time Allocation:**

- 0–5 min: Warm-up
- 5–10 min: Problem statement
- 10–70 min: Implementation
- 70–90 min: Testing & refinement
- 90–120 min: Follow-up problem or production deep-dive

**Follow-up problem:** "Now add a `GET /messages?status=BLOCKED&carrier=Telstra&page=0&size=20` query endpoint. What changes?"

---

### Remote Interview (Async via Screen Share)

Same structure, but:

- Share your screen; let candidate control via remote session.
- Use a shared GitHub branch or live collaborative editor (VSCode Live Share).
- Record for async feedback if candidate allows.

---

## Debrief Checklist (Interviewer)

After the candidate leaves:

- [ ] Candidate understood the problem (or asked clarifying questions).
- [ ] Candidate wrote tests (before or alongside implementation).
- [ ] All required tests pass.
- [ ] Code follows project conventions.
- [ ] Edge cases are handled.
- [ ] Candidate can explain their code.
- [ ] Candidate thinks about production.
- [ ] I'd be comfortable having them review code in a PR.

**Score 6+ checks:** Likely hire. **Score 4–5:** Likely pass, revisit. **Score <4:** Probably no.

---

## What Success Looks Like

A candidate succeeds when they:

1. ✅ Clarify ambiguity before coding.
2. ✅ Write 3–5 test cases covering happy path + edge cases.
3. ✅ Implement a minimal solution that passes all tests.
4. ✅ Run the full test suite and explain results.
5. ✅ Can walk you through the code and defend design choices.
6. ✅ Identify production concerns (persistence, concurrency, observability).
7. ✅ Communicate clearly and ask for help when stuck.

---

## References

- [Sinch SMS Router README](../README.md)
- [Live Coding Extension Directions](./sinch-live-coding-extension-directions-en.md)
- [Project Architecture](../ARCHITECTURE.md) (if available)
