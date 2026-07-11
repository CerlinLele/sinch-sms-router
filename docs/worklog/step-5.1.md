# Worklog

## 2026-07-11

- Action: executed Stage 5.1 from `docs/plan/implementation-plan.md` after approval.
- Files touched:
  - `sms/src/main/java/com/sinch/sms/SmsConfiguration.java`
  - `sms/src/main/java/com/sinch/sms/api/SendMessageRequest.java`
  - `sms/src/main/java/com/sinch/sms/api/MessageResponse.java`
  - `sms/src/main/java/com/sinch/sms/api/OptOutResponse.java`
  - `sms/src/main/java/com/sinch/sms/api/MessageController.java`
  - `sms/src/main/java/com/sinch/sms/api/OptOutController.java`
  - `sms/src/test/java/com/sinch/sms/api/MessageControllerTest.java`
- Action: verified the step with `cmd /c ".\\mvnw.cmd -Dtest=MessageControllerTest test"` from `sms/`.
- Result:
  - `MessageControllerTest` passed with `BUILD SUCCESS`.
  - AU sends return `201 Created`, `SENT`, and `Telstra`, with lookup returning the same response shape.
  - NZ sends return `SENT` and `Spark`.
  - Opt-out returns `{"phone_number":"+...","opted_out":true}`.
  - A subsequent opted-out send returns `BLOCKED` with `carrier: null`, and lookup returns the same blocked record.
