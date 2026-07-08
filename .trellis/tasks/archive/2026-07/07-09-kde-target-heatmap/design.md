# KDE Target Generation and Heatmap Exploration Design

## Architecture

The server becomes the owner of exploration target generation:

- Fetch QRNG/debug values in fixed `1024`-value batches.
- Convert random value pairs into uniformly distributed sample points within the requested radius.
- Compute a bounded KDE-like density grid.
- Select Attractor, Void, or Anomaly.
- Return a mobile-friendly visualization payload.

Android remains responsible for:

- Collecting current location, target type, generation mode preference, and optional intent.
- Calling the server generation endpoint.
- Rendering sample points, heat grid, selected target, search radius, route, and history.
- Fetching AMap route geometry after the target is selected.

`/v1/qrng` remains as the low-level QRNG proxy. A new target endpoint handles multi-batch generation so Android does not orchestrate 2-4 QRNG calls.

## Server Contract

Add `POST /v1/targets/generate`.

Request fields:

- `origin`: `{ latitude: number, longitude: number }`
- `radiusMeters`: supported positive integer range, aligned with Android supported radii where practical.
- `mode`: `standard | fine`
- `targetType`: `attractor | void | anomaly`
- `provider`: `aqn | debug`
- `heatGridSize`: optional bounded integer, default around `32`
- `pointSampleLimit`: optional bounded integer for visualization, default around `512`

Response fields:

- `source`: `ANU Quantum Numbers | debug`
- `provider`
- `mode`
- `targetType`
- `randomType`: `uint16`
- `batchCount`
- `randomValueCount`
- `samplePointCount`
- `origin`
- `radiusMeters`
- `target`: `{ latitude, longitude, role, density, score }`
- `attractor`: highest-density point metadata
- `void`: lowest-density point metadata
- `heatGrid`: bounded grid with geographic bounds and normalized values
- `samplePoints`: bounded display subset of generated points

The response should avoid returning all raw random values by default. Counts and source metadata are enough for history. If tests need deterministic inspection, inject a fake fetcher.

## KDE Approach

Use a deterministic, dependency-light density grid instead of adding SciPy-equivalent dependencies:

1. Generate sample points from `uint16` pairs with polar sampling using `sqrt(random)` for uniform disk distribution.
2. Build a square grid over the origin radius bounding box.
3. Include only cells inside the radius for target selection.
4. Estimate density at each grid cell using a Gaussian kernel over generated sample points.
5. Normalize density values for heat rendering.
6. Select:
   - Attractor: max density valid grid cell.
   - Void: min density valid grid cell, excluding edge cells or using an inner valid mask to avoid edge-only artifacts.
   - Anomaly: stronger absolute normalized deviation from mean between Attractor and Void.

For performance, grid size should default to a modest value such as `32x32`, and fine mode should still complete within server timeout. If naive KDE is too slow, use a histogram plus separable blur approximation while keeping the same response contract.

## Android Data Flow

`ExploreRoute.kt` generation flow changes from direct QRNG fetch to target-generation fetch:

1. User taps Generate.
2. App shows a generation dialog with target type and optional intent.
3. App calls server generation endpoint with current location, radius, mode preference, target type, and provider.
4. App receives target + visualization payload.
5. App fetches AMap route from origin to target.
6. App stores history with intent and generation metadata.
7. App renders overlays.

Add or update Android models near `feature/explore` for target generation request/response, avoiding anonymous JSON parsing inside composables.

## Settings

Add a user-facing generation quality preference:

- Default: Standard.
- Fine mode: enabled in settings as "Fine mode" or "High-density mode".

Keep debug random source under developer mode. Debug provider selection must still be visible in the exploration panel to avoid confusing debug output with ANU/AQN.

## History Compatibility

Add nullable/defaulted fields to `RouteHistoryRecord`:

- `intent`
- `targetType`
- `generationMode`
- `randomValueCount`
- `samplePointCount`
- `densityScore`

When loading older records, use:

- `targetType = Attractor`
- `generationMode = Standard`
- `randomValueCount = randomLength`
- `samplePointCount = randomLength / 2`
- `intent = null`

## Trade-Offs

- Server-side target generation centralizes algorithm work and avoids Android math dependencies.
- Returning bounded visualization data keeps mobile rendering predictable.
- A grid-based KDE approximation is acceptable for product behavior, but docs/UI should avoid overstating scientific precision.
- Fine mode increases ANU/AQN usage and latency, so it is a setting rather than the default.

## Rollback

Keep `/v1/qrng` and the existing single-point generator tests until the new flow is verified. If server target generation fails, Android should show an unavailable error rather than falling back to local random generation.
