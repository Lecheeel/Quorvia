# KDE target generation and heatmap exploration

## Goal

Upgrade Quorvia from single-point random target generation to server-computed KDE exploration targets with map visualization. Users can choose Attractor, Void, or Anomaly before generation, optionally enter an intent, and view the generated sample point cloud, heat region, and density peak/selected target on the Android map.

The implementation must keep ANU/AQN as the production random source while supporting the existing debug source for high-volume testing.

## Confirmed Facts

- Current Android generation requests `2` `uint16` values through `QrngClient.fetchUInt16()` and generates one point in `TargetGenerator.kt`.
- Current service `/v1/qrng` bounds `length` to `1..1024`, matching the ANU/AQN single-request limit.
- Current Android developer settings already support `Quantum` and `Debug` providers.
- Current route history records random source, random values, radius, map mode, route mode, origin, target, and route points.
- Target generation and route planning are currently coupled in `ExploreRoute.kt`: generate point, fetch AMap route, save history, render target/route.
- The user approved these sample sizes:
  - Standard: `2048` random values / `1024` sample points.
  - Fine: `4096` random values / `2048` sample points.

## Requirements

1. Target generation modes
   - Standard mode must be the default and must use two batched QRNG requests of `1024` `uint16` values each.
   - Fine mode must be enabled from settings and must use four batched QRNG requests of `1024` `uint16` values each.
   - The app must distinguish random value count from sample point count in labels, history, and tests.

2. Target types
   - Users must choose one target type before generation.
   - Default target type must be `Attractor`.
   - `Attractor` means the highest-density KDE location.
   - `Void` means the lowest-density KDE location inside the search area, avoiding invalid/out-of-radius and edge-only artifacts.
   - `Anomaly` means whichever of Attractor or Void has the stronger normalized density deviation.

3. Server-computed KDE
   - KDE target calculation must run on the server, not in Android UI code.
   - The server must expose a target-generation endpoint that accepts origin, radius, generation mode, target type, and provider.
   - The server must continue to support `/v1/qrng` for direct QRNG access.
   - The server must support `debug` provider when `ALLOW_DEBUG_RANDOM=true`; production must fail clearly when debug is disabled.

4. Visualization payload
   - The server response must include the selected target, attractor/void density extrema, sampled point-cloud data, heat grid data, source/provider metadata, and sample counts.
   - Android must render the random point cloud, heat region/grid, density peak/selected target, search radius, and route.
   - Visualization payloads should be bounded for mobile rendering; Android should not need to run KDE.

5. Intent input
   - Intent is an optional input shown during generation.
   - Intent must not influence random values or KDE selection in this version.
   - Intent must be saved with history records when provided.

6. History and metadata
   - History must persist target type, generation mode, random value count, sample point count, random source/provider, optional intent, and density/score metadata.
   - Existing history records must continue to load with sensible defaults.

7. Quality
   - Server tests must cover query validation, standard/fine batch counts, target type selection, debug-provider behavior, and response shape.
   - Android tests must cover model defaults, preference/history compatibility, and client parsing of generation responses.

## Acceptance Criteria

- [ ] Standard generation requests exactly two `1024`-length `uint16` batches and returns `1024` sample points worth of target metadata.
- [ ] Fine generation requests exactly four `1024`-length `uint16` batches and returns `2048` sample points worth of target metadata.
- [ ] The generation request supports `Attractor`, `Void`, and `Anomaly`; Android defaults to `Attractor`.
- [ ] KDE/heat-grid computation is implemented server-side and Android does not calculate density fields.
- [ ] Android can generate a target through the server endpoint, fetch an AMap route to it, render point cloud/heat/target overlays, and save history.
- [ ] Optional intent can be entered during generation and appears in saved history.
- [ ] Debug provider remains available for development when server config allows it and fails with a clear message when disabled.
- [ ] Existing unit tests plus new server/Android tests pass.

## Out Of Scope

- Camera RNG or device-local physical entropy.
- User accounts, cloud sync, or public trip reports.
- Making intent influence the random/KDE algorithm.
- Full scientific validation of Randonautica-style anomaly claims.
