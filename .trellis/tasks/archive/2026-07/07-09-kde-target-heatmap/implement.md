# KDE Target Generation and Heatmap Exploration Implementation Plan

## Checklist

1. Server models and generation logic
   - Add target generation request/response schemas.
   - Add batch QRNG fetch helper using existing `fetchQuantumNumbers`.
   - Add sample-point generation from `uint16` pairs.
   - Add density grid calculation and target selection.
   - Add `POST /v1/targets/generate`.

2. Server tests
   - Verify invalid generation requests return `400`.
   - Verify standard mode uses two `1024` batches.
   - Verify fine mode uses four `1024` batches.
   - Verify attractor/void/anomaly target selection returns expected roles using deterministic debug/fake data.
   - Verify debug-disabled behavior remains clear.

3. Android models/client
   - Add generation mode and target type enums.
   - Add generation response models and JSON parser.
   - Add `QrngClient` or dedicated client method for `POST /v1/targets/generate`.

4. Android state/preferences/history
   - Persist generation quality preference.
   - Add target type/intent/visualization payload to UI state.
   - Extend history record with default-compatible fields.

5. Android UI and map rendering
   - Add generate dialog for target type and optional intent.
   - Add settings control for fine mode.
   - Render point cloud, heat grid, density marker, target marker, radius, and route.
   - Keep debug source badge behavior.

6. Android tests
   - Update existing target/history/preference tests.
   - Add parser tests for generation response.
   - Add defaults/backward compatibility tests.

7. Validation
   - Run server tests.
   - Run Android unit tests.
   - Run Android build or the narrow Gradle task available in the repo.

## Validation Commands

```powershell
Set-Location server; npm test
Set-Location android; .\gradlew test
```

If the full Android test suite is too slow, run the relevant module unit tests first and report any skipped checks.

## Risky Files

- `server/src/app.ts`
- `server/src/qrng.ts`
- new server target-generation module
- `android/app/src/main/java/com/quorvia/app/feature/explore/ExploreRoute.kt`
- `android/app/src/main/java/com/quorvia/app/feature/explore/ExploreUiState.kt`
- `android/app/src/main/java/com/quorvia/app/feature/explore/ExplorePreferences.kt`
- `android/app/src/main/java/com/quorvia/app/feature/history/RouteHistory.kt`
- `android/app/src/main/java/com/quorvia/app/settings/SettingsRoute.kt`

## Review Gates

- Do not remove the low-level `/v1/qrng` endpoint.
- Do not add local random fallback for production target generation.
- Do not make intent affect randomness or target selection.
- Keep visualization payload bounded.
- Preserve loading of existing route-history records.
