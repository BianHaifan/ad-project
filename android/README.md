# AD Candidate Android

Kotlin + Jetpack Compose + Material 3 prototype for the candidate-side MVP. The app is intentionally backed by local mock data until the Spring Boot API contract is implemented.

## Included Figma flows

- Sign in (`2046:2`) and create account (`2051:2`)
- Job feed (`1:2`) and job detail (`2004:2`)
- Apply confirmation (`2035:3`) and submitted state (`2051:42`)
- My applications (`2044:150`)
- Learning unavailable (`1:235`), messages (`1:304`), profile/tools (`1:95`)
- Candidate chat detail (`2144:2`)
- Resume edit (`2046:34`)

Original SVG exports from Figma are committed under `app/src/main/res/raw` and rendered with Coil's SVG decoder.

## Build

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## API contract

- Human-readable contract: [`../docs/API_CATALOG.zh-CN.md`](../docs/API_CATALOG.zh-CN.md)
- Swagger/Postman-compatible OpenAPI 3.1 file: [`../docs/openapi-v1.yaml`](../docs/openapi-v1.yaml)
- Current screen-to-data map: [`DATA_API.md`](DATA_API.md)

`FakeCandidateApi` remains the active implementation until a Retrofit-backed implementation is connected to the v1 HTTP contract.
