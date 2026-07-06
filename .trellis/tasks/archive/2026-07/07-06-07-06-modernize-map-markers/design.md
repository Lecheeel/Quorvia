# Technical Design - Modernize Map Location Markers

## Architecture & Boundaries

为了保持地图路由逻辑代码的整洁，我们将动态绘制逻辑收拢到一个独立的辅助类 `MapMarkerHelper` 中，并采用单例/缓存模式防止在渲染帧中重复分配 `Bitmap` 资源。

```
+------------------+      +------------------+
|  ExploreRoute    |      |   HistoryRoute   |
+--------+---------+      +--------+---------+
         |                         |
         +------------+------------+
                      |
                      v (Request Bitmaps)
             +------------------+
             | MapMarkerHelper  |
             +--------+---------+
                      |
                      v (Draw with Canvas)
             +------------------+
             |  Android Canvas  |
             +------------------+
```

## Data Flow & Contracts

```kotlin
object MapMarkerHelper {
    // 缓存生成的图标 Descriptor，避免每次重绘都重新绘制并分配内存
    private var cachedUserLocation: BitmapDescriptor? = null
    private var cachedQuantumTarget: BitmapDescriptor? = null
    private var cachedOrigin: BitmapDescriptor? = null

    fun getUserLocationMarker(context: Context): BitmapDescriptor
    fun getQuantumTargetMarker(context: Context): BitmapDescriptor
    fun getOriginMarker(context: Context): BitmapDescriptor
}
```

### 绘制样式设计：
1. **User Location (用户位置)**:
   - 整体尺寸：`36dp * 36dp`
   - 外圆：半径 `14dp`，填充色 `#332367F4`（20% 透明度蓝色），用于模拟雷达波动外圈。
   - 内圆：半径 `6dp`，填充色 `#FF2367F4`（亮蓝色）+ `#FFFFFF`（白色描边，宽度 `2dp`）。
2. **Quantum Target (量子目标点)**:
   - 整体尺寸：`36dp * 36dp`
   - 外圆环：半径 `10dp`，描边宽度 `2dp`，填充色透明，描边色 `#FF2367F4`（亮蓝色）。
   - 中心圆点：半径 `3dp`，填充色 `#FF2367F4`。
   - 准星延伸线：由外圆向四周（上下左右）延伸 `4dp`，线宽 `2dp`，颜色 `#FF2367F4`。
3. **Origin (起点)**:
   - 与 Target 风格对应，但颜色为青色/淡蓝色（例如 `#FF00B0FF`）以作区分。

## Compatibility & Migration

- 原有的 `BitmapDescriptorFactory.defaultMarker(...)` 移除。
- 通过引入 `MapMarkerHelper` 的 `BitmapDescriptor`，不会改变任何地图 Overlay 的数据存储或网络交互，无破坏性变更。
