# Implementation Plan - Modernize Map Location Markers

## Ordered Checklist

- [ ] **Step 1: 创建 MapMarkerHelper**
  - 创建 `android/app/src/main/java/com/quorvia/app/feature/explore/MapMarkerHelper.kt`
  - 实现 `dp` 转 `px` 的工具函数。
  - 使用 `android.graphics.Canvas` 动态绘制 `UserLocation`、`QuantumTarget` 和 `Origin` 的科技感图标。
  - 加入内存缓存机制。

- [ ] **Step 2: 修改 ExploreRoute**
  - 修改 `ExploreRoute.kt` 的 `enableCurrentLocationCursor` 方法，将 `map.myLocationStyle.myLocationIcon(...)` 设为 `MapMarkerHelper.getUserLocationMarker(context)`。
  - 修改 `renderExploreOverlays` 中生成 `targetMarker` 的部分，将其 `.icon` 设为 `MapMarkerHelper.getQuantumTargetMarker(context)`。同时，将 `.anchor(0.5f, 0.5f)` 以便让量子准星的圆心正对坐标。

- [ ] **Step 3: 修改 HistoryRoute**
  - 修改 `HistoryRoute.kt` 中渲染 `originMarker` 与 `targetMarker` 的部分，分别替换为 `MapMarkerHelper.getOriginMarker(context)` 与 `MapMarkerHelper.getQuantumTargetMarker(context)`，并设置 `.anchor(0.5f, 0.5f)`。

- [ ] **Step 4: 编译与测试**
  - 运行 `scripts/verify.ps1` 校验编译。
  - 运行 `gradlew.bat compileReleaseKotlin` 以确保 release 版本的编译和混淆也全部通过。

## Validation Commands

- 验证本地编译：
  ```bash
  powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
  ```
- 检查 Release Kotlin 混淆与打包：
  ```bash
  cd android
  .\gradlew.bat compileReleaseKotlin
  ```
