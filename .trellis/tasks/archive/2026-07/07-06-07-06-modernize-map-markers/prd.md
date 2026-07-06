# Modernize Map Location Markers

## Goal

将高德地图（AMap）中的“当前定位标志”与“目标位置标志”进行现代化、科技感视觉改造，采用动态 Canvas 渲染的方式替代系统默认的传统图标，提升产品视觉体验。

## Background & Target Files

- **探索界面 (ExploreRoute.kt)**：
  - `enableCurrentLocationCursor` (L563): 配置高德自带定位图层 `MyLocationStyle`。
  - `renderExploreOverlays` (L596): 渲染量子目标点 Marker。
- **历史界面 (HistoryRoute.kt)**：
  - `renderHistoryOverlays` (L373, L379): 渲染路线的起点 Marker 与终点 Marker。

## Requirements

1. **当前定位标志**：
   - 替换高德原生的“蓝色定位箭头点”。
   - 设计风格为**静态现代科技风**：柔和半透明蓝色渐变外圈（约 16dp 半径，具有微光雷达感）+ 亮蓝色核心实心圆点（约 6dp 半径）+ 白色细描边。
2. **目标点标志 (Quantum Target)**：
   - 替换现有的“红色大头针”。
   - 采用**静态科技量子准星风**：亮蓝色/青色外圆环 + 核心定位点 + 四角量子十字校准延伸线。
3. **起点标志**：
   - 替换现有的“浅蓝色大头针”。
   - 采用扁平化的圆环风格，与终点（量子目标点）样式统一。
4. **性能与兼容性**：
   - 在内存中单例缓存生成的 `BitmapDescriptor`，避免在界面刷新或滑动重绘时反复创建 Bitmap 导致垃圾回收 (GC) 抖动。
   - 正确适配各种屏幕密度（DPI），使用 `dp` 转换为 `px` 后再在 Canvas 上绘制。

## Acceptance Criteria

- [x] 当前定位标志采取静态雷达圆环样式（经用户决策确认）。
- [x] 探索界面中，高德默认定位点被替换为现代扁平雷达风标志。
- [x] 探索界面中的“量子目标点”被替换为现代化微光量子标志。
- [x] 历史记录界面中的起点和终点大头针替换为一致的现代扁平标志。
- [x] 标志尺寸在不同 DPI 设备上显示比例一致，无拉伸与模糊。
