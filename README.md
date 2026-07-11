# SMS Router

Small Spring Boot SMS router for the Sinch take-home. The app keeps all state in memory and follows a strict TDD-built contract.

## Prerequisites

- JDK 21
- Maven Wrapper (`sms/mvnw.cmd`) provided in the repo
- A shell that can run `java`, `javac`, and `./mvnw.cmd`

To provide the JDK on Windows:

- Install a JDK 21 distribution, such as Temurin 21 or Oracle JDK 21.
- Set `JAVA_HOME` to the JDK installation directory.
- Add `%JAVA_HOME%\bin` to `PATH`.
- In your IDE, point the project SDK or Java runtime to the same JDK 21 install.

Quick checks:

```powershell
java -version
echo $env:JAVA_HOME
where java
```

If `java -version` does not report Java 21, fix `JAVA_HOME` and `PATH` before running the app or tests.

To provide the JDK on Linux or macOS:

- Install a JDK 21 distribution, such as Temurin 21 or Oracle JDK 21.
- Set `JAVA_HOME` to the JDK installation directory.
- Add `$JAVA_HOME/bin` to `PATH`.
- In your IDE, point the project SDK or Java runtime to the same JDK 21 install.

Quick checks:

```bash
java -version
echo "$JAVA_HOME"
which java
```

Temporary shell setup examples:

```bash
export JAVA_HOME="/path/to/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
```

On macOS, you can also discover the installed JDK path with:

```bash
/usr/libexec/java_home -v 21
```

## Run

From the `sms/` directory:

```bash
./mvnw.cmd spring-boot:run
```

The app starts on `http://localhost:8080` by default.

## Testing

Run the full test suite from `sms/`:

```bash
./mvnw.cmd clean test
```

Run a focused unit or integration test when iterating on one layer:

```bash
./mvnw.cmd -Dtest=PhoneNumberValidatorTest test
./mvnw.cmd -Dtest=CarrierRouterTest test
./mvnw.cmd -Dtest=OptOutServiceTest test
./mvnw.cmd -Dtest=MessageServiceTest test
./mvnw.cmd -Dtest=MessageControllerTest test
```

The `clean test` command is the best way to confirm the application still passes after a full rebuild, and it is the command used for the final verification step.

## API

### `POST /messages`

Send a message using the PRD request shape:

```json
{
  "destination_number": "+61491570156",
  "content": "hello",
  "format": "SMS"
}
```

Successful response:

```json
{
  "id": "7c1b7a6f-2c7d-4ec5-8a67-0e8d6a1e4a1d",
  "status": "SENT",
  "carrier": "Telstra"
}
```

If the destination number is opted out, the response is still `201 Created`, but the message is stored as:

```json
{
  "id": "7c1b7a6f-2c7d-4ec5-8a67-0e8d6a1e4a1d",
  "status": "BLOCKED",
  "carrier": null
}
```

### `GET /messages/{id}`

Returns the same response shape as `POST /messages`:

```json
{
  "id": "7c1b7a6f-2c7d-4ec5-8a67-0e8d6a1e4a1d",
  "status": "SENT",
  "carrier": "Telstra"
}
```

Unknown valid UUIDs return `404 Not Found`.

### `POST /optout/{phoneNumber}`

Opt out a canonical phone number. Remember to URL-encode `+` in the path as `%2B`.

Example:

```bash
curl -X POST "http://localhost:8080/optout/%2B61491570156"
```

Successful response:

```json
{
  "phone_number": "+61491570156",
  "opted_out": true
}
```

Repeated opt-out calls are idempotent and return the same success response.

## cURL Examples

Send an AU message:

```bash
curl -X POST "http://localhost:8080/messages" \
  -H "Content-Type: application/json" \
  -d "{\"destination_number\":\"+61491570156\",\"content\":\"hello\",\"format\":\"SMS\"}"
```

Send an NZ message:

```bash
curl -X POST "http://localhost:8080/messages" \
  -H "Content-Type: application/json" \
  -d "{\"destination_number\":\"+64211234567\",\"content\":\"kia ora\",\"format\":\"SMS\"}"
```

Look up a message:

```bash
curl "http://localhost:8080/messages/7c1b7a6f-2c7d-4ec5-8a67-0e8d6a1e4a1d"
```

Opt out a number:

```bash
curl -X POST "http://localhost:8080/optout/%2B61491570156"
```

## Validation Rules

- Phone numbers must be canonical E.164-like values: `+` followed by 8 to 15 ASCII digits.
- The first digit after `+` must be non-zero.
- The app does not trim or normalize input.
- Whitespace, separators, local formats, letters, and malformed JSON are rejected.
- `format` must be present and must be `SMS`.
- `content` must be non-blank.

## Routing Rules

- AU numbers (`+61`) alternate between `Telstra` and `Optus`, starting with `Telstra`.
- NZ numbers (`+64`) route to `Spark`.
- All other valid numbers route to `Global`.
- Opted-out sends are stored as `BLOCKED` with `carrier: null`.
- Blocked sends do not advance the AU alternation sequence.

## Error Responses

All API errors use this shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "..."
}
```

Stable error codes:

- `MALFORMED_JSON`
- `VALIDATION_ERROR`
- `MESSAGE_NOT_FOUND`

Examples:

```json
{
  "code": "MALFORMED_JSON",
  "message": "Malformed JSON request"
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Only SMS format is supported"
}
```

```json
{
  "code": "MESSAGE_NOT_FOUND",
  "message": "Message not found: 7c1b7a6f-2c7d-4ec5-8a67-0e8d6a1e4a1d"
}
```

## Limitations

- State lives only in memory.
- Restarting the app clears messages, opt-outs, and AU routing state.
- v1 does not include delivery simulation or status transition endpoints.
- `PENDING` and `DELIVERED` stay in the status enum for future expansion, but only `SENT` and `BLOCKED` are used in v1.
