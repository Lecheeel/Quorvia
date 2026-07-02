package com.quorvia.app.feature.explore

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class TargetGeneratorTest {
    @Test
    fun generatedTargetStaysInsideRadius() {
        val origin = ExplorePoint(latitude = 39.9087, longitude = 116.3975)
        val target = generateTargetPoint(
            origin = origin,
            radiusMetersLimit = 3_000,
            randomValues = listOf(12_345, 54_321),
        )

        assertTrue(distanceKm(origin, target) <= 3.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generatorRejectsMissingEntropy() {
        generateTargetPoint(
            origin = ExplorePoint(latitude = 39.9087, longitude = 116.3975),
            radiusMetersLimit = 3_000,
            randomValues = listOf(1),
        )
    }

    private fun distanceKm(a: ExplorePoint, b: ExplorePoint): Double {
        val latKm = (a.latitude - b.latitude) * 111.32
        val lngKm = (a.longitude - b.longitude) * 111.32 * kotlin.math.cos(a.latitude * Math.PI / 180.0)
        return sqrt(latKm.pow(2) + lngKm.pow(2))
    }
}
