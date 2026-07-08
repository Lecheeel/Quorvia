package com.quorvia.app.feature.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreUiStateTest {
    @Test
    fun defaultStateIsDriveWithinGenerationBounds() {
        val state = ExploreUiState()

        assertEquals(3_000, state.radiusMeters)
        assertEquals(MapVisualMode.Satellite, state.mapVisualMode)
        assertEquals(TargetGenerationMode.Standard, state.generationMode)
        assertEquals(RouteMode.Drive, state.routeMode)
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
    fun routeModeIsDerivedFromRadius() {
        assertEquals(RouteMode.Walk, ExploreUiState().withRadius(300).routeMode)
        assertEquals(RouteMode.Ride, ExploreUiState().withRadius(1_000).routeMode)
        assertEquals(RouteMode.Ride, ExploreUiState().withRadius(2_000).routeMode)
        assertEquals(RouteMode.Drive, ExploreUiState().withRadius(3_000).routeMode)
    }

    @Test
    fun invalidRadiusDisablesGenerationWhenStateIsConstructedExternally() {
        assertFalse(ExploreUiState(radiusMeters = 0).canGenerate)
        assertFalse(ExploreUiState(radiusMeters = 11_000).canGenerate)
    }
}
