# Project-specific ProGuard / R8 rules

# =====================================================================
# 高德地图 (AMap) SDK 混淆保留规则
# =====================================================================

-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**
-dontwarn com.aps.**
-dontwarn com.amap.ams.gnss.GnssSoftLocator

# 3D 地图 / 检索 / 定位 / 导航
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.services.** { *; }
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.fence.** { *; }
-keep class com.amap.api.trace.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keep class com.aps.** { *; }
