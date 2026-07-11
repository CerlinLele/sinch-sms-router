# Worklog

## 2026-07-11

- Action: executed Stage 4.2 from `docs/plan/implementation-plan.md` after approval.
- Files touched:
  - `sms/src/main/java/com/sinch/sms/message/MessageFormat.java`
  - `sms/src/main/java/com/sinch/sms/service/MessageService.java`
  - `sms/src/test/java/com/sinch/sms/service/MessageServiceTest.java`
- Action: verified the step with `cmd /c ".\\mvnw.cmd -Dtest=MessageServiceTest test"` from `sms/`.
- Result:
  - `MessageServiceTest` passed with `BUILD SUCCESS`.
  - Opted-out sends persist as `BLOCKED` with `carrier: null`.
  - Blocked sends do not advance the AU carrier rotation.
  - Invalid destination numbers, blank content, missing format, and non-`SMS` format are rejected before save.
  - Unknown UUID lookup raises the dedicated not-found exception.
