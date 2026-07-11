# Worklog

## 2026-07-11

- Action: executed Stage 6.2 from `docs/plan/implementation-plan.md` after approval.
- Files touched:
  - `README.md`
- Action: verified the documented startup and representative cURL flows against the running app:
  - `POST /messages` returned `201 Created` with `SENT` and `Telstra`.
  - `GET /messages/{id}` returned the same message shape.
  - `POST /optout/%2B61491570156` returned the opt-out confirmation JSON.
  - A subsequent send for the opted-out number returned `201 Created` with `BLOCKED` and `carrier: null`.
  - Lookup of the blocked message returned the same blocked record.
- Action: verified the final build with `cmd /c ".\\mvnw.cmd clean test"` from `sms/`.
- Result:
  - `README.md` now documents startup, endpoints, cURL examples, validation rules, routing rules, stable error responses, and in-memory limitations.
  - The documented flows matched the running app.
  - The full suite passed with `BUILD SUCCESS` and 41 tests.
