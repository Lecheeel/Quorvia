package com.quorvia.app.feature.history

import com.quorvia.app.feature.explore.ExplorationTargetType
import com.quorvia.app.feature.explore.MapVisualMode
import com.quorvia.app.feature.explore.RouteMode
import com.quorvia.app.feature.explore.TargetGenerationMode
import com.quorvia.app.settings.RandomProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteHistoryTest {
    @Test
    fun oldHistoryRecordDefaultsGenerationFields() {
        val record = JSONObject(OLD_RECORD_JSON).toRouteHistoryRecord()

        assertEquals(TargetGenerationMode.Standard, record.generationMode)
        assertEquals(ExplorationTargetType.Attractor, record.targetType)
        assertEquals(1, record.samplePointCount)
        assertNull(record.intent)
        assertNull(record.densityScore)
    }

    @Test
    fun historyJsonRoundTripsGenerationFields() {
        val record = RouteHistoryRecord(
            id = "id",
            createdAtMillis = 123,
            randomProvider = RandomProvider.Debug,
            randomSource = "debug",
            randomType = "uint16",
            randomLength = 2048,
            randomValues = emptyList(),
            generationMode = TargetGenerationMode.Standard,
            targetType = ExplorationTargetType.Void,
            intent = "find quiet",
            samplePointCount = 1024,
            densityScore = 1.25,
            radiusMeters = 3000,
            routeMode = RouteMode.Drive,
            mapVisualMode = MapVisualMode.Satellite,
            originLatitude = 39.9,
            originLongitude = 116.4,
            targetLatitude = 39.91,
            targetLongitude = 116.41,
            routePointCount = 0,
            routePoints = emptyList(),
        )

        val restored = record.toRouteHistoryJson().toRouteHistoryRecord()

        assertEquals(TargetGenerationMode.Standard, restored.generationMode)
        assertEquals(ExplorationTargetType.Void, restored.targetType)
        assertEquals("find quiet", restored.intent)
        assertEquals(1024, restored.samplePointCount)
        assertEquals(1.25, restored.densityScore ?: 0.0, 0.001)
    }

    private companion object {
        const val OLD_RECORD_JSON = """
            {
              "id": "old",
              "createdAtMillis": 123,
              "randomProvider": "Quantum",
              "randomSource": "ANU Quantum Numbers",
              "randomType": "uint16",
              "randomLength": 2,
              "randomValues": [1, 2],
              "radiusMeters": 3000,
              "routeMode": "Drive",
              "mapVisualMode": "Satellite",
              "originLatitude": 39.9,
              "originLongitude": 116.4,
              "targetLatitude": 39.91,
              "targetLongitude": 116.41,
              "routePointCount": 0,
              "routePoints": []
            }
        """
    }
}
