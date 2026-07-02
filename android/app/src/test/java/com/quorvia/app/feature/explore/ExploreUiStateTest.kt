package com.quorvia.app.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreUiStateTest {
    @Test
    fun defaultStateIsWalkWithinGenerationBounds() {
        val state = ExploreUiState()

        assertEquals(3_000, state.radiusMeters)
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
        assertEquals(300, ExploreUiState().withRadius(-10).radiusMeters)
        assertEquals(10_000, ExploreUiState().withRadius(99_000).radiusMeters)
        assertEquals(500, ExploreUiState().withRadius(450).radiusMeters)
    }

    @Test
    fun routeModeCanSwitchToDrive() {
        val state = ExploreUiState().withRouteMode(RouteMode.Drive)

        assertEquals(RouteMode.Drive, state.routeMode)
    }

    @Test
    fun invalidRadiusDisablesGenerationWhenStateIsConstructedExternally() {
        assertFalse(ExploreUiState(radiusMeters = 0).canGenerate)
        assertFalse(ExploreUiState(radiusMeters = 11_000).canGenerate)
    }
}
