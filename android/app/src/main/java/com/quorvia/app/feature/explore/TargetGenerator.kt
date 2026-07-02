package com.quorvia.app.feature.explore

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val UINT16_MAX_EXCLUSIVE = 65_536.0
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

fun generateTargetPoint(
    origin: ExplorePoint,
    radiusMetersLimit: Int,
    randomValues: List<Int>,
): ExplorePoint {
    require(radiusMetersLimit in SUPPORTED_RADIUS_METERS)
    require(randomValues.size >= 2)

    val angle = (randomValues[0].coerceIn(0, 65_535) / UINT16_MAX_EXCLUSIVE) * 2.0 * PI
    val radiusMeters = sqrt(randomValues[1].coerceIn(0, 65_535) / UINT16_MAX_EXCLUSIVE) * radiusMetersLimit

    val latitudeOffset = (radiusMeters * cos(angle)) / METERS_PER_LATITUDE_DEGREE
    val longitudeScale = METERS_PER_LATITUDE_DEGREE * cos(origin.latitude * PI / 180.0)
    val longitudeOffset = if (longitudeScale == 0.0) {
        0.0
    } else {
        (radiusMeters * sin(angle)) / longitudeScale
    }

    return ExplorePoint(
        latitude = origin.latitude + latitudeOffset,
        longitude = origin.longitude + longitudeOffset,
    )
}
