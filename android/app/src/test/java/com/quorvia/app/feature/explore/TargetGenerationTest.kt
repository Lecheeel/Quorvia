package com.quorvia.app.feature.explore

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetGenerationTest {
    @Test
    fun parsesTargetGenerationResponse() {
        val result = targetGenerationResultFromJson(JSONObject(RESPONSE_JSON))

        assertEquals("debug", result.source)
        assertEquals(TargetGenerationMode.Standard, result.mode)
        assertEquals(ExplorationTargetType.Attractor, result.targetType)
        assertEquals(2, result.batchCount)
        assertEquals(2048, result.randomValueCount)
        assertEquals(1024, result.samplePointCount)
        assertEquals("attractor", result.target.role)
        assertEquals(1, result.samplePoints.size)
        assertEquals(1, result.heatGrid.cells.size)
    }

    private companion object {
        const val RESPONSE_JSON = """
            {
              "source": "debug",
              "provider": "debug",
              "mode": "standard",
              "targetType": "attractor",
              "randomType": "uint16",
              "batchCount": 2,
              "randomValueCount": 2048,
              "samplePointCount": 1024,
              "origin": { "latitude": 39.9, "longitude": 116.4 },
              "radiusMeters": 3000,
              "target": { "latitude": 39.91, "longitude": 116.41, "role": "attractor", "density": 0.8, "score": 2.1 },
              "attractor": { "latitude": 39.91, "longitude": 116.41, "role": "attractor", "density": 0.8, "score": 2.1 },
              "void": { "latitude": 39.92, "longitude": 116.42, "role": "void", "density": 0.1, "score": 1.5 },
              "heatGrid": {
                "size": 16,
                "minLatitude": 39.8,
                "maxLatitude": 40.0,
                "minLongitude": 116.3,
                "maxLongitude": 116.5,
                "cells": [
                  { "latitude": 39.9, "longitude": 116.4, "value": 0.5 }
                ]
              },
              "samplePoints": [
                { "latitude": 39.9, "longitude": 116.4 }
              ]
            }
        """
    }
}
