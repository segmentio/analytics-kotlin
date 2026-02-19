# analytics-kotlin e2e-cli

E2E test CLI for the [analytics-kotlin](https://github.com/segmentio/analytics-kotlin) SDK. Accepts a JSON input describing events and SDK configuration, sends them through the real SDK, and outputs results as JSON.

Built with Kotlin and run via Gradle.

## Usage

```bash
./gradlew :e2e-cli:run --args '--input {"writeKey":"...", ...}'
```

Or build a fat jar:

```bash
./gradlew :e2e-cli:shadowJar
java -jar e2e-cli/build/libs/e2e-cli-*-SNAPSHOT.jar --input '{"writeKey":"...", ...}'
```

## Input Format

```jsonc
{
  "writeKey": "your-write-key",       // required
  "apiHost": "https://...",           // optional — SDK default if omitted
  "cdnHost": "https://...",           // optional — SDK default if omitted
  "sequences": [                      // required — event sequences to send
    {
      "delayMs": 0,                   // delay before sending this batch
      "events": [
        { "type": "track", "event": "Test", "userId": "user-1" }
      ]
    }
  ],
  "config": {                         // optional
    "flushAt": 20,                    // batch size threshold
    "flushInterval": 30,              // seconds between flushes
    "maxRetries": 10,
    "timeout": 15
  }
}
```

## Output Format

```json
{ "success": true, "sentBatches": 1 }
```

On failure:

```json
{ "success": false, "error": "description", "sentBatches": 0 }
```
