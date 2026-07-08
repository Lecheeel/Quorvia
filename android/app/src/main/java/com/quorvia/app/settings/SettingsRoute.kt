package com.quorvia.app.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quorvia.app.BuildConfig
import com.quorvia.app.feature.explore.ExplorePreferences
import com.quorvia.app.feature.explore.TargetGenerationMode
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    settings: DeveloperSettings,
    explorePreferences: ExplorePreferences,
    onSettingsChange: (DeveloperSettings) -> Unit,
    onExplorePreferencesChange: (ExplorePreferences) -> Unit,
    onBack: () -> Unit,
) {
    var aboutTapCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            aboutTapCount += 1
                            if (aboutTapCount >= DEVELOPER_TAP_COUNT) {
                                onSettingsChange(settings.copy(developerModeEnabled = true))
                            }
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("About Quorvia", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (settings.developerModeEnabled) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Developer mode", style = MaterialTheme.typography.titleSmall)
                        Text("Random source", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.randomProvider == RandomProvider.Quantum,
                                onClick = {
                                    onSettingsChange(settings.copy(randomProvider = RandomProvider.Quantum))
                                },
                                label = { Text("Quantum") },
                            )
                            FilterChip(
                                selected = settings.randomProvider == RandomProvider.Debug,
                                onClick = {
                                    onSettingsChange(settings.copy(randomProvider = RandomProvider.Debug))
                                },
                                label = { Text("Debug") },
                            )
                        }
                        Text("Heatmap tuning", style = MaterialTheme.typography.bodyMedium)
                        HeatmapPreview(settings.heatmap)
                        Button(
                            onClick = {
                                onSettingsChange(settings.copy(heatmap = HeatmapDeveloperSettings()))
                            },
                        ) {
                            Text("Reset heatmap defaults")
                        }
                        HeatmapSlider(
                            label = "Map visibility",
                            value = settings.heatmap.overlayTransparency,
                            range = 0.0f..0.6f,
                            onValueChange = {
                                onSettingsChange(
                                    settings.copy(heatmap = settings.heatmap.copy(overlayTransparency = it)),
                                )
                            },
                        )
                        HeatmapSlider(
                            label = "Hot contrast",
                            value = settings.heatmap.hotGamma,
                            range = 0.5f..3.0f,
                            onValueChange = {
                                onSettingsChange(settings.copy(heatmap = settings.heatmap.copy(hotGamma = it)))
                            },
                        )
                        HeatmapSlider(
                            label = "Hot transparent area",
                            value = settings.heatmap.hotAlphaCutoff,
                            range = 0.0f..0.3f,
                            onValueChange = {
                                onSettingsChange(
                                    settings.copy(heatmap = settings.heatmap.copy(hotAlphaCutoff = it)),
                                )
                            },
                        )
                        HeatmapSlider(
                            label = "Hot fade",
                            value = settings.heatmap.hotAlphaGamma,
                            range = 0.5f..3.0f,
                            onValueChange = {
                                onSettingsChange(
                                    settings.copy(heatmap = settings.heatmap.copy(hotAlphaGamma = it)),
                                )
                            },
                        )
                        HeatmapSlider(
                            label = "Void strength",
                            value = settings.heatmap.voidGamma,
                            range = 0.3f..2.0f,
                            onValueChange = {
                                onSettingsChange(settings.copy(heatmap = settings.heatmap.copy(voidGamma = it)))
                            },
                        )
                        HeatmapSlider(
                            label = "Void edge fade",
                            value = settings.heatmap.voidEdgeFadeStart,
                            range = 0.5f..0.98f,
                            onValueChange = {
                                onSettingsChange(
                                    settings.copy(heatmap = settings.heatmap.copy(voidEdgeFadeStart = it)),
                                )
                            },
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Generation quality", style = MaterialTheme.typography.titleSmall)
                    Text("Standard uses 1024 sample points. Fine uses 2048 sample points.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = explorePreferences.generationMode == TargetGenerationMode.Standard,
                            onClick = {
                                onExplorePreferencesChange(
                                    explorePreferences.copy(generationMode = TargetGenerationMode.Standard),
                                )
                            },
                            label = { Text("Standard") },
                        )
                        FilterChip(
                            selected = explorePreferences.generationMode == TargetGenerationMode.Fine,
                            onClick = {
                                onExplorePreferencesChange(
                                    explorePreferences.copy(generationMode = TargetGenerationMode.Fine),
                                )
                            },
                            label = { Text("Fine") },
                        )
                    }
                }
            }

            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun HeatmapPreview(settings: HeatmapDeveloperSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            val columns = HEATMAP_PREVIEW_COLUMNS
            val rows = HEATMAP_PREVIEW_ROWS
            val cellWidth = size.width / columns
            val cellHeight = size.height / rows
            for (row in 0 until rows) {
                val y = row.toFloat() / (rows - 1).coerceAtLeast(1)
                for (column in 0 until columns) {
                    val x = column.toFloat() / (columns - 1).coerceAtLeast(1)
                    val isVoidPreview = column >= columns / 2
                    val localX = if (isVoidPreview) {
                        (column - columns / 2).toFloat() / ((columns / 2) - 1).coerceAtLeast(1)
                    } else {
                        column.toFloat() / ((columns / 2) - 1).coerceAtLeast(1)
                    }
                    val color = if (isVoidPreview) {
                        previewVoidColor(localX, y, settings)
                    } else {
                        previewHotColor(localX, y, settings)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(column * cellWidth, row * cellHeight),
                        size = Size(cellWidth + 1f, cellHeight + 1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Attractor / Anomaly", style = MaterialTheme.typography.bodySmall)
            Text("Void", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HeatmapSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(formatSliderValue(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

private fun formatSliderValue(value: Float): String =
    (value * 100).roundToInt().let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}" }

private fun previewHotColor(x: Float, y: Float, settings: HeatmapDeveloperSettings): Color {
    val density = (
        gaussian(x, y, 0.72f, 0.42f, 0.18f) * 1.05f +
            gaussian(x, y, 0.42f, 0.64f, 0.22f) * 0.72f +
            gaussian(x, y, 0.22f, 0.24f, 0.28f) * 0.34f
        ).coerceIn(0f, 1f)
    val intensity = density.toDouble().pow(settings.hotGamma.toDouble()).coerceIn(0.0, 1.0)
    return previewHeatColor(
        intensity = intensity,
        alphaCutoff = settings.hotAlphaCutoff.toDouble(),
        alphaGamma = settings.hotAlphaGamma.toDouble(),
        colors = HOT_PREVIEW_COLORS,
        stops = HOT_PREVIEW_STOPS,
        overlayTransparency = settings.overlayTransparency,
    )
}

private fun previewVoidColor(x: Float, y: Float, settings: HeatmapDeveloperSettings): Color {
    val density = (
        gaussian(x, y, 0.72f, 0.36f, 0.16f) * 0.95f +
            gaussian(x, y, 0.38f, 0.68f, 0.22f) * 0.62f +
            gaussian(x, y, 0.18f, 0.18f, 0.24f) * 0.28f
        ).coerceIn(0f, 1f)
    val centeredX = x * 2f - 1f
    val centeredY = y * 2f - 1f
    val radialDistance = sqrt(centeredX * centeredX + centeredY * centeredY).toDouble()
    val voidValue = 1.0 - density
    val edgeFade = 1.0 - smoothstep(settings.voidEdgeFadeStart.toDouble(), 1.0, radialDistance)
    val intensity = smoothstep(VOID_PREVIEW_CONTRAST_LOW, VOID_PREVIEW_CONTRAST_HIGH, voidValue)
        .pow(settings.voidGamma.toDouble())
        .times(edgeFade)
        .coerceIn(0.0, 1.0)
    return previewHeatColor(
        intensity = intensity,
        alphaCutoff = VOID_PREVIEW_ALPHA_CUTOFF,
        alphaGamma = VOID_PREVIEW_ALPHA_GAMMA,
        colors = VOID_PREVIEW_COLORS,
        stops = VOID_PREVIEW_STOPS,
        overlayTransparency = settings.overlayTransparency,
    )
}

private fun previewHeatColor(
    intensity: Double,
    alphaCutoff: Double,
    alphaGamma: Double,
    colors: List<Color>,
    stops: FloatArray,
    overlayTransparency: Float,
): Color {
    if (intensity <= alphaCutoff) {
        return PREVIEW_MAP_COLOR
    }
    val heatColor = interpolatePreviewColor(colors, stops, intensity)
    val alpha = (HEATMAP_PREVIEW_MIN_ALPHA + intensity.pow(alphaGamma) * (HEATMAP_PREVIEW_MAX_ALPHA - HEATMAP_PREVIEW_MIN_ALPHA))
        .coerceIn(0.0, 1.0) * (1f - overlayTransparency.coerceIn(0f, 1f))
    return blendPreviewColor(PREVIEW_MAP_COLOR, heatColor, alpha.toFloat())
}

private fun interpolatePreviewColor(colors: List<Color>, stops: FloatArray, value: Double): Color {
    val clamped = value.coerceIn(0.0, 1.0).toFloat()
    val upperIndex = stops.indexOfFirst { clamped <= it }.takeIf { it >= 0 } ?: stops.lastIndex
    if (upperIndex == 0) {
        return colors.first()
    }
    val lowerIndex = upperIndex - 1
    val lowerStop = stops[lowerIndex]
    val upperStop = stops[upperIndex]
    val fraction = if (upperStop == lowerStop) 0f else (clamped - lowerStop) / (upperStop - lowerStop)
    return Color(
        red = lerp(colors[lowerIndex].red, colors[upperIndex].red, fraction),
        green = lerp(colors[lowerIndex].green, colors[upperIndex].green, fraction),
        blue = lerp(colors[lowerIndex].blue, colors[upperIndex].blue, fraction),
        alpha = 1f,
    )
}

private fun blendPreviewColor(base: Color, overlay: Color, alpha: Float): Color =
    Color(
        red = lerp(base.red, overlay.red, alpha),
        green = lerp(base.green, overlay.green, alpha),
        blue = lerp(base.blue, overlay.blue, alpha),
        alpha = 1f,
    )

private fun gaussian(x: Float, y: Float, centerX: Float, centerY: Float, sigma: Float): Float {
    val dx = x - centerX
    val dy = y - centerY
    return exp(-((dx * dx + dy * dy) / (2f * sigma * sigma)))
}

private fun smoothstep(edge0: Double, edge1: Double, value: Double): Double {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private const val DEVELOPER_TAP_COUNT = 7
private const val HEATMAP_PREVIEW_COLUMNS = 72
private const val HEATMAP_PREVIEW_ROWS = 36
private const val HEATMAP_PREVIEW_MIN_ALPHA = 10.0 / 255.0
private const val HEATMAP_PREVIEW_MAX_ALPHA = 190.0 / 255.0
private const val VOID_PREVIEW_CONTRAST_LOW = 0.04
private const val VOID_PREVIEW_CONTRAST_HIGH = 0.78
private const val VOID_PREVIEW_ALPHA_CUTOFF = 0.005
private const val VOID_PREVIEW_ALPHA_GAMMA = 0.85
private val PREVIEW_MAP_COLOR = Color(0xFF20343B)
private val HOT_PREVIEW_COLORS = listOf(
    Color(0xFF007AFF),
    Color(0xFF00D2B4),
    Color(0xFFFFD60A),
    Color(0xFFFF851B),
    Color(0xFFFF3B30),
)
private val HOT_PREVIEW_STOPS = floatArrayOf(0.08f, 0.50f, 0.78f, 0.94f, 1.0f)
private val VOID_PREVIEW_COLORS = listOf(
    Color(0xFF234EFF),
    Color(0xFF744DFF),
    Color(0xFF00E5FF),
    Color(0xFFA0F5FF),
    Color(0xFFF5FAFF),
)
private val VOID_PREVIEW_STOPS = floatArrayOf(0.02f, 0.36f, 0.66f, 0.90f, 1.0f)
