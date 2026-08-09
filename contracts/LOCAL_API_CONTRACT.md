# ApplyMate Local API Contract 0.1

## General rules

- Base URL: `http://127.0.0.1:<configured-port>/api/applymate/v1`
- The desktop service must reject non-loopback binding.
- Every extension request uses `Authorization: Bearer <pairing-token>`.
- The service checks the requesting extension Origin against a locally paired extension ID.
- Responses use the existing JobTracker `Result<T>` envelope.
- No endpoint returns identity, highly sensitive values, passwords, captcha values, or files by default.
- Provider API keys are local desktop secrets. They are never returned to the extension or persisted in Profile, snapshot or fill-session data.

## Pairing flow

```text
Extension → GET /health
Extension → POST /pairing/requests (extensionId, public metadata)
Desktop UI → user approves or rejects
Extension ← one-time pairing token
Extension → authenticated minimal-data endpoints
```

The initial implementation may use an approval code shown in the desktop UI. The approval code is short-lived, single-use and never written to logs.

## Endpoints

### `GET /health`

Unauthenticated liveness check. It returns version and a boolean indicating whether pairing is required; it never returns profile data.

### `POST /pairing/requests`

Creates a pending pairing request. Request body:

```json
{
  "extensionId": "chrome-extension-id",
  "extensionVersion": "0.1.0",
  "displayName": "ApplyMate"
}
```

The desktop UI must explicitly approve the request before a token is issued.

### `GET /profile/values?keys=education.school,education.degree`

Returns only confirmed, non-denied values requested by the extension. Values for repeated records include their stable record ID and metadata needed by `RecordResolver`; they do not return the complete Profile document.

### `PUT /profile`

Desktop frontend only in the first iteration. Saves a user-confirmed Candidate Profile and creates a snapshot atomically.

### `POST /resumes/{resumeId}/parse`

Creates a parsing draft, not a direct Profile update. Request options:

```json
{
  "allowCloudAi": false,
  "allowVisionFallback": false
}
```

If extraction quality is insufficient and vision is not permitted, the response is `VISION_RECOMMENDED` with no file content sent to a provider.

The request resolves the local YAML task route. Provider names, API keys and models are not part of this public endpoint contract; the service validates that the selected candidate supports structured output and, when requested, vision input. A recoverable provider outage may move to the next configured candidate; credential and validation errors are returned directly.

### `POST /profile/diff`

Compares an accepted parsing draft with the current Profile. It returns additions, updates and conflicts. A separate confirmed action is required to apply the result.

### `POST /fill-sessions`

Stores a non-sensitive summary only: host, adapter, field key, match method, confidence and result code. Never store values such as identity-card number, address, password or captcha.

## Extension offline behavior

When `/health` fails, the extension may scan DOM and show field counts, but must disable profile lookup, Fill Plan creation and autofill. It must not fall back to a persisted copy of the full Candidate Profile.
