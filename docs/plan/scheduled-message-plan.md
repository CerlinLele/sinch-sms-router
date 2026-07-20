# Scheduled Message Sending Implementation Plan

## Purpose

Add the ability to schedule SMS messages for future delivery by introducing a `scheduledAt` timestamp field to the Message domain model. Messages can be sent immediately (null timestamp) or scheduled for a specific future time.

## Overview

This feature enables messages to be queued for delivery at a specific time rather than being sent immediately. The system will require:

1. A timestamp field in the Message domain model
2. API request/response modifications to accept and return the schedule time
3. A background scheduler to process pending messages when their scheduled time arrives
4. Message status transitions: PENDING → SENT/BLOCKED
5. Updated validation logic and tests

## Design Decisions

### Timestamp Representation
- Use `Instant` (Java 8+ time API) for the `scheduledAt` field
- Store UTC timestamps to avoid timezone complexity
- Accept ISO-8601 formatted timestamps in the API (e.g., `"2026-07-20T14:30:00Z"`)
- `null` scheduledAt means "send immediately" (current behavior)

### Message Status Flow
- **Immediate send** (scheduledAt = null):
  - Validates → Routes → `SENT`/`BLOCKED` immediately
- **Scheduled send** (scheduledAt = future):
  - Validates → Saves with `PENDING` → Background scheduler picks up → `SENT`/`BLOCKED` at scheduled time
- **Past timestamp**: Treat as immediate send (or reject with validation error)

### Scheduling Mechanism
- **Option A (Recommended)**: Spring's `@Scheduled` with periodic polling
  - Poll the repository every 10-30 seconds
  - Find messages where `status = PENDING` and `scheduledAt <= now()`
  - Process each message through the existing send logic
  - Simple, thread-safe, no external dependencies
  
- **Option B**: Java ScheduledExecutorService with per-message tasks
  - Schedule individual tasks for each message
  - More precise timing but complex state management
  - Risk: scheduled tasks lost on restart

- **Option C**: External job scheduler (Quartz, etc.)
  - Overkill for this scale
  - Adds dependency complexity

**Recommendation**: Use Option A for simplicity and alignment with in-memory storage limitations.

## Implementation Stages

### Stage 1 - Domain Model Extension

#### Step 1.1 - Add scheduledAt field to Message record

**Goal**: Extend the Message record to include an optional scheduled timestamp.

**Test First**:
- Update `MessageServiceTest` to verify messages can be created with `scheduledAt = null` (immediate)
- Add test case for messages with future `scheduledAt` that remain `PENDING`
- Assert that scheduled messages are not immediately routed to a carrier

**Implementation**:
```java
// Message.java
public record Message(
    UUID id,
    String destinationNumber,
    String content,
    MessageFormat format,
    MessageStatus status,
    Carrier carrier,
    Instant scheduledAt  // null = immediate, non-null = scheduled
) {
}
```

**Verification**:
- Run `MessageServiceTest`
- Ensure existing tests still pass with `scheduledAt = null`

#### Step 1.2 - Update MessageService send logic

**Goal**: Split send behavior into immediate vs. scheduled paths.

**Test First**:
- Add `MessageServiceTest` cases:
  - `scheduledAt = null` → routes and returns `SENT`/`BLOCKED` immediately
  - `scheduledAt = future` → saves with `PENDING` status, no carrier assigned yet
  - `scheduledAt = past` → treat as immediate send (or reject)
  - Scheduled messages to opted-out numbers → save as `PENDING` initially

**Implementation**:
```java
// MessageService.java
public Message send(String destinationNumber, String content, 
                    MessageFormat format, Instant scheduledAt) {
    String validatedNumber = phoneNumberValidator.validate(destinationNumber);
    validateContent(content);
    validateFormat(format);
    validateScheduledAt(scheduledAt); // reject if past by > threshold
    
    UUID id = UUID.randomUUID();
    
    // Immediate send path
    if (scheduledAt == null || scheduledAt.isBefore(Instant.now().plusSeconds(5))) {
        boolean optedOut = optOutService.isOptedOut(validatedNumber);
        Carrier carrier = optedOut ? null : carrierRouter.route(validatedNumber);
        MessageStatus status = optedOut ? MessageStatus.BLOCKED : MessageStatus.SENT;
        
        Message message = new Message(id, validatedNumber, content, format, 
                                      status, carrier, null);
        return messageRepository.save(message);
    }
    
    // Scheduled send path
    Message pendingMessage = new Message(id, validatedNumber, content, format,
                                         MessageStatus.PENDING, null, scheduledAt);
    return messageRepository.save(pendingMessage);
}

private void validateScheduledAt(Instant scheduledAt) {
    if (scheduledAt != null && scheduledAt.isBefore(Instant.now().minusSeconds(60))) {
        throw new DomainValidationException(
            "Scheduled time cannot be more than 60 seconds in the past");
    }
}
```

**Verification**:
- Run `MessageServiceTest`
- Confirm immediate and scheduled paths work independently

---

### Stage 2 - API Layer Updates

#### Step 2.1 - Update SendMessageRequest and MessageResponse

**Goal**: Accept and return `scheduledAt` in the API.

**Test First**:
- Extend `MessageControllerTest`:
  - POST with `scheduled_at = null` → `201`, `SENT`, carrier assigned
  - POST with future `scheduled_at` → `201`, `PENDING`, `carrier: null`, returns `scheduled_at`
  - GET scheduled message → returns `PENDING` status with `scheduled_at`
  - POST with invalid `scheduled_at` (past) → `400` with `VALIDATION_ERROR`

**Implementation**:
```java
// SendMessageRequest.java
public record SendMessageRequest(
    @JsonProperty("destination_number") @NotBlank String destinationNumber,
    @NotBlank String content,
    @NotNull MessageFormat format,
    @JsonProperty("scheduled_at") Instant scheduledAt  // optional, null = immediate
) {
}

// MessageResponse.java
public record MessageResponse(
    UUID id,
    MessageStatus status,
    Carrier carrier,
    @JsonProperty("scheduled_at") Instant scheduledAt
) {
}

// MessageController.java
@PostMapping("/messages")
public ResponseEntity<MessageResponse> send(@Valid @RequestBody SendMessageRequest request) {
    Message message = messageService.send(
        request.destinationNumber(),
        request.content(),
        request.format(),
        request.scheduledAt()  // pass through
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(message));
}

private MessageResponse toResponse(Message message) {
    return new MessageResponse(
        message.id(),
        message.status(),
        message.carrier(),
        message.scheduledAt()
    );
}
```

**Verification**:
- Run `MessageControllerTest`
- Test JSON serialization/deserialization of ISO-8601 timestamps

---

### Stage 3 - Background Scheduler

#### Step 3.1 - Implement scheduled message processor

**Goal**: Periodically find and process pending scheduled messages.

**Test First**:
- Add `ScheduledMessageProcessorTest`:
  - Create scheduled messages with past `scheduledAt` and `PENDING` status
  - Invoke processor
  - Assert messages transition to `SENT` with carrier assigned
  - Assert opted-out scheduled messages transition to `BLOCKED`
  - Assert future scheduled messages remain `PENDING`

**Implementation**:
```java
// ScheduledMessageProcessor.java
@Component
public class ScheduledMessageProcessor {
    
    private final MessageRepository messageRepository;
    private final CarrierRouter carrierRouter;
    private final OptOutService optOutService;
    
    public ScheduledMessageProcessor(MessageRepository messageRepository,
                                     CarrierRouter carrierRouter,
                                     OptOutService optOutService) {
        this.messageRepository = messageRepository;
        this.carrierRouter = carrierRouter;
        this.optOutService = optOutService;
    }
    
    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    public void processScheduledMessages() {
        Instant now = Instant.now();
        
        // Find all PENDING messages due for delivery
        List<Message> dueMessages = messageRepository.findAll().stream()
            .filter(m -> m.status() == MessageStatus.PENDING)
            .filter(m -> m.scheduledAt() != null)
            .filter(m -> !m.scheduledAt().isAfter(now))
            .toList();
        
        for (Message message : dueMessages) {
            processScheduledMessage(message);
        }
    }
    
    private void processScheduledMessage(Message message) {
        boolean optedOut = optOutService.isOptedOut(message.destinationNumber());
        Carrier carrier = optedOut ? null : carrierRouter.route(message.destinationNumber());
        MessageStatus newStatus = optedOut ? MessageStatus.BLOCKED : MessageStatus.SENT;
        
        Message updatedMessage = new Message(
            message.id(),
            message.destinationNumber(),
            message.content(),
            message.format(),
            newStatus,
            carrier,
            message.scheduledAt()  // preserve original scheduled time
        );
        
        messageRepository.save(updatedMessage);
    }
}
```

**Configuration**:
```java
// SmsConfiguration.java or SmsApplication.java
@EnableScheduling  // Add this annotation to enable Spring scheduling
```

**Verification**:
- Run `ScheduledMessageProcessorTest` with manual invocation
- Verify scheduled messages transition correctly

#### Step 3.2 - Add repository query support

**Goal**: Enable efficient lookup of pending scheduled messages.

**Test First**:
- Extend `InMemoryMessageRepository` tests to cover:
  - `findByStatusAndScheduledAtBefore(PENDING, now)` returns only due messages
  - Query excludes future scheduled messages
  - Query excludes already-sent messages

**Implementation**:
```java
// MessageRepository.java
public interface MessageRepository {
    Message save(Message message);
    Optional<Message> findById(UUID id);
    List<Message> findAll();
    
    // New method for scheduler
    default List<Message> findPendingScheduledBefore(Instant cutoff) {
        return findAll().stream()
            .filter(m -> m.status() == MessageStatus.PENDING)
            .filter(m -> m.scheduledAt() != null)
            .filter(m -> !m.scheduledAt().isAfter(cutoff))
            .toList();
    }
}
```

Update `ScheduledMessageProcessor` to use the new method:
```java
List<Message> dueMessages = messageRepository.findPendingScheduledBefore(now);
```

**Verification**:
- Run repository tests
- Confirm query correctness

---

### Stage 4 - Integration and Error Handling

#### Step 4.1 - Add error handling for edge cases

**Goal**: Handle scheduler failures and concurrent access gracefully.

**Test First**:
- Add integration tests:
  - Scheduler processes messages only once (no duplicates)
  - Concurrent sends don't cause race conditions
  - Repository save failures don't crash scheduler
  - Opted-out status checked at scheduled time, not submission time

**Implementation**:
- Add optimistic locking or idempotency checks in message updates
- Wrap scheduler processing in try-catch to prevent one failure from blocking others
- Log scheduler actions for debugging

```java
private void processScheduledMessage(Message message) {
    try {
        // Check if still PENDING (might have been processed already)
        Optional<Message> current = messageRepository.findById(message.id());
        if (current.isEmpty() || current.get().status() != MessageStatus.PENDING) {
            return; // Already processed
        }
        
        boolean optedOut = optOutService.isOptedOut(message.destinationNumber());
        Carrier carrier = optedOut ? null : carrierRouter.route(message.destinationNumber());
        MessageStatus newStatus = optedOut ? MessageStatus.BLOCKED : MessageStatus.SENT;
        
        Message updatedMessage = new Message(
            message.id(),
            message.destinationNumber(),
            message.content(),
            message.format(),
            newStatus,
            carrier,
            message.scheduledAt()
        );
        
        messageRepository.save(updatedMessage);
        
    } catch (Exception e) {
        // Log error but don't crash scheduler
        System.err.println("Failed to process scheduled message " + message.id() + ": " + e.getMessage());
    }
}
```

**Verification**:
- Run integration tests with concurrent scenarios
- Verify scheduler resilience

#### Step 4.2 - Update API documentation

**Goal**: Document the new scheduling capability.

**Test First**:
- Review README examples match the new API contract
- Ensure error response codes are documented

**Implementation**:
Update README.md with:
- New optional `scheduled_at` field in request examples
- Response examples showing `PENDING` status for scheduled messages
- Explanation of immediate vs. scheduled send behavior
- Validation rules for `scheduled_at` (cannot be far in the past)
- Note about scheduler polling interval (messages processed within ~10-30 seconds of scheduled time)

Example:
```json
// Immediate send (existing behavior)
POST /messages
{
  "destination_number": "+61491570156",
  "content": "Hello world",
  "format": "SMS"
}
→ 201 Created, status: "SENT"

// Scheduled send (new)
POST /messages
{
  "destination_number": "+61491570156",
  "content": "Happy Birthday!",
  "format": "SMS",
  "scheduled_at": "2026-07-21T09:00:00Z"
}
→ 201 Created, status: "PENDING", scheduled_at: "2026-07-21T09:00:00Z"

// Later: GET /messages/{id}
→ 200 OK, status: "SENT", carrier: "Telstra", scheduled_at: "2026-07-21T09:00:00Z"
```

**Verification**:
- Execute documented examples against running application
- Verify scheduled messages are processed correctly

---

## Testing Strategy

### Unit Tests
- `MessageServiceTest`: immediate vs. scheduled send logic
- `ScheduledMessageProcessorTest`: message processing logic
- `MessageRepositoryTest`: query methods for pending messages

### Integration Tests
- `MessageControllerTest`: end-to-end API with scheduled messages
- Verify JSON serialization of `Instant` timestamps
- Test scheduler behavior with multiple pending messages

### Manual Testing
- Start application
- Send scheduled message 30 seconds in the future
- Poll GET endpoint to observe `PENDING` → `SENT` transition
- Verify carrier assignment happens at scheduled time
- Test opt-out before scheduled time takes effect

---

## Rollout Considerations

### Backward Compatibility
- ✅ `scheduled_at` is optional; existing clients continue to work
- ✅ Null `scheduled_at` preserves immediate send behavior
- ✅ No breaking changes to existing endpoints or response structure

### Performance
- Scheduler polls every 10 seconds (configurable via `fixedDelay`)
- In-memory iteration acceptable for prototype scale
- For production: consider indexed queries, batch processing, or dedicated job queue

### Limitations
- **In-memory storage**: Scheduled messages lost on application restart
- **Polling delay**: Messages sent within ~10-30 seconds of scheduled time, not precisely
- **No distributed scheduling**: Single instance only; multiple instances would duplicate sends
- **No cancellation API**: Once scheduled, messages cannot be canceled (could be added later)

### Future Enhancements
- Add `DELETE /messages/{id}` to cancel pending scheduled messages
- Add pagination to `GET /messages` with filtering by status
- Persist to database for durability
- Use distributed task scheduler (e.g., Quartz) for multi-instance deployments
- Add webhook notifications when scheduled messages are sent
- Support recurring scheduled messages

---

## Acceptance Criteria

The implementation is complete when:

1. ✅ Message domain model includes `scheduledAt` field
2. ✅ API accepts optional `scheduled_at` in ISO-8601 format
3. ✅ Immediate sends (null timestamp) work as before
4. ✅ Scheduled sends create `PENDING` messages with no carrier
5. ✅ Background scheduler processes due messages every 10 seconds
6. ✅ Scheduled messages transition to `SENT`/`BLOCKED` at scheduled time
7. ✅ Opt-out status checked at scheduled time, not submission time
8. ✅ All unit and integration tests pass
9. ✅ README documents the scheduling feature
10. ✅ Manual testing confirms end-to-end scheduled send workflow

---

## Implementation Order Summary

1. **Stage 1**: Extend Message model and service logic (split send paths)
2. **Stage 2**: Update API request/response DTOs and controller
3. **Stage 3**: Implement background scheduler with repository query support
4. **Stage 4**: Add error handling, integration tests, and documentation

Each stage builds on the previous and maintains passing tests throughout.
