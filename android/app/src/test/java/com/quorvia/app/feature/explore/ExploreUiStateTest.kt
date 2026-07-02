package com.quorvia.app.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreUiStateTest {
    @Test
    fun defaultStateIsWalkWithinGenerationBounds() {
        val state = ExploreUiState()

        assertEquals(3, state.radiusKm)
        assertEquals(RouteMode.Walk, state.routeMode)
        assertFalse(state.canGenerate)
    }

    @Test
    fun stateCanGenerateWhenLocationIsReady() {
        val state = ExploreUiState(currentPoint = ExplorePoint(39.9, 116.4))

        assertTrue(state.canGenerate)
    }

    @Test
    fun radiusIsClampedToSupportedBounds() {
        assertEquals(MIN_RADIUS_KM, ExploreUiState().withRadius(-10).radiusKm)
        assertEquals(MAX_RADIUS_KM, ExploreUiState().withRadius(99).radiusKm)
    }

    @Test
    fun routeModeCanSwitchToDrive() {
        val state = ExploreUiState().withRouteMode(RouteMode.Drive)

        assertEquals(RouteMode.Drive, state.routeMode)
    }

    @Test
    fun invalidRadiusDisablesGenerationWhenStateIsConstructedExternally() {
        assertFalse(ExploreUiState(radiusKm = 0).canGenerate)
        assertFalse(ExploreUiState(radiusKm = 11).canGenerate)
    }
}
