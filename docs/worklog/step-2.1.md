# Worklog

## 2026-07-11

- Action: executed Stage 2.1 from `docs/plan/implementation-plan.md` after approval.
- Files touched:
  - `sms/src/main/java/com/sinch/sms/routing/Carrier.java`
  - `sms/src/main/java/com/sinch/sms/routing/CarrierRouter.java`
  - `sms/src/test/java/com/sinch/sms/routing/CarrierRouterTest.java`
- Action: verified the step with `cmd /c ".\\mvnw.cmd -Dtest=CarrierRouterTest test"` from `sms/`.
- Result:
  - `CarrierRouterTest` passed with `BUILD SUCCESS`.
  - AU numbers alternate `Telstra` and `Optus`, starting with `Telstra`.
  - NZ numbers route to `Spark`, global numbers route to `Global`, and non-AU routes do not change the AU alternation state.
