package com.reelsloth.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelsloth.ai.CommentarySource
import com.reelsloth.ai.ModelState
import com.reelsloth.ai.Tone
import com.reelsloth.ai.Verdict
import com.reelsloth.detect.Platform
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.ScoreExplainer
import com.reelsloth.score.ScoreKind
import com.reelsloth.score.ScoreResult
import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodStats
import kotlin.math.roundToInt

/** Test tags used by the Compose UI tests. */
object Tags {
    const val COUNT = "count"
    const val PERMISSION_BANNER = "permission_banner"
    const val PAUSED_BANNER = "paused_banner"
    const val CALIBRATION_BANNER = "calibration_banner"
    const val EMPTY_STATE = "empty_state"
    const val PLATFORM_SPLIT = "platform_split"
    const val COMMENTARY_BADGE = "commentary_badge"
    const val MODEL_PROGRESS = "model_progress"
    const val WEEK_CHART = "week_chart"
    const val MONTH_HEAT_MAP = "month_heat_map"
    fun score(kind: ScoreKind) = "score_${kind.name.lowercase()}"
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** Why nothing is being counted right now, most important first. Shows at most one banner. */
@Composable
fun TrackingBanners(
    permissionGranted: Boolean,
    paused: Boolean,
    anyAppReady: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    when {
        !permissionGranted -> Banner(
            tag = Tags.PERMISSION_BANNER,
            title = "Counting is off",
            body = "Scroll Sloth needs its accessibility service to notice reels. It only looks at Instagram, YouTube and Facebook.",
            action = "Turn it on",
            onAction = onOpenAccessibilitySettings,
        )
        paused -> Banner(
            tag = Tags.PAUSED_BANNER,
            title = "Counting is paused",
            body = "Nothing you watch is counted until you resume it in Settings.",
        )
        !anyAppReady -> Banner(
            tag = Tags.CALIBRATION_BANNER,
            title = "No app is calibrated yet",
            body = "Detection rules are still missing, so nothing is counted. Settings shows each app's status.",
        )
    }
}

@Composable
private fun Banner(tag: String, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(Tags.EMPTY_STATE),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A big number with a caption under it, read out as one phrase. */
@Composable
fun BigNumber(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.displayLarge, modifier = Modifier.testTag(Tags.COUNT))
        Text(caption, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ScoreCard(result: ScoreResult, stats: PeriodStats, config: ScoreConfig, period: Period, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(result.kind) { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().testTag(Tags.score(result.kind))) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.kind.title, style = MaterialTheme.typography.titleMedium)
                    Text(result.band.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    result.score.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { contentDescription = "${result.score} out of 100" },
                )
            }
            ScoreMeter(result.score)
            if (period != Period.DAY) {
                Text(
                    "Average of ${result.daysAveraged} daily ${if (result.daysAveraged == 1) "score" else "scores"}; quiet days count as 0.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide why" else "Why this score")
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (expanded) {
                HorizontalDivider()
                ScoreExplainer.explain(result, stats, config).forEach { line ->
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row {
                            Text(line.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (result.kind == ScoreKind.LAZINESS) "${line.value}/100, adds ${line.adds}" else "${line.value}/100",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(line.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(result.kind.formula, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A 0..100 bar. Decorative: the number next to it is what screen readers hear. */
@Composable
private fun ScoreMeter(score: Int) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {}) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * score.coerceIn(0, 100) / 100f
        if (width > 0f) drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
    }
}

/** Stacked bar of reels per app with a legend. */
@Composable
fun PlatformSplit(byPlatform: Map<Platform, Int>, modifier: Modifier = Modifier) {
    val total = byPlatform.values.sum()
    if (total == 0) return
    val entries = Platform.entries.mapNotNull { platform -> byPlatform[platform]?.takeIf { it > 0 }?.let { platform to it } }
    val colors = platformColors()
    val description = "Reels by app: " + entries.joinToString(", ") { (platform, count) -> "${platform.label} $count" }
    Card(modifier = modifier.fillMaxWidth().testTag(Tags.PLATFORM_SPLIT)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("By app")
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                var x = 0f
                val gap = if (entries.size > 1) 2.dp.toPx() else 0f
                entries.forEachIndexed { index, (platform, count) ->
                    val width = size.width * count / total - if (index < entries.lastIndex) gap else 0f
                    drawRoundRect(
                        colors.getValue(platform),
                        topLeft = Offset(x, 0f),
                        size = Size(width.coerceAtLeast(1f), size.height),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    x += width + gap
                }
            }
            entries.forEach { (platform, count) ->
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(12.dp).clearAndSetSemantics {}) {
                        Surface(shape = CircleShape, color = colors.getValue(platform), modifier = Modifier.size(12.dp)) {}
                    }
                    Text(platform.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "$count · ${(count * 100.0 / total).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun platformColors(): Map<Platform, Color> = mapOf(
    Platform.INSTAGRAM to MaterialTheme.colorScheme.primary,
    Platform.YOUTUBE to MaterialTheme.colorScheme.tertiary,
    Platform.FACEBOOK to MaterialTheme.colorScheme.secondary,
)

/**
 * The cached verdict for the last completed period, with a badge saying who wrote it. Never waits
 * for the model. [modelState] adds a line while Gemma is downloading; [onToneChange] adds the toggle.
 */
@Composable
fun CommentaryCard(
    period: Period,
    verdict: Verdict?,
    modelState: ModelState,
    tone: Tone? = null,
    onToneChange: ((Tone) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(period.verdictTitle, modifier = Modifier.weight(1f))
                if (verdict != null) SourceBadge(verdict.source)
            }
            if (verdict == null) {
                Text(period.noVerdictYet, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(verdict.text, style = MaterialTheme.typography.bodyLarge)
            }
            ModelProgressNote(modelState)
            if (tone != null && onToneChange != null) ToneToggle(tone, onToneChange)
        }
    }
}

@Composable
private fun SourceBadge(source: CommentarySource) {
    val gemma = source == CommentarySource.GEMMA
    Surface(
        shape = CircleShape,
        color = if (gemma) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (gemma) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .testTag(Tags.COMMENTARY_BADGE)
            .semantics {
                contentDescription = if (gemma) "Written by Gemma on your phone" else "Written from a template"
            },
    ) {
        Text(
            if (gemma) "Gemma" else "Template",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ModelProgressNote(state: ModelState) {
    val text = when (state) {
        is ModelState.Downloading -> if (state.totalBytes > 0) {
            "Gemma is downloading (${(state.bytesSoFar * 100 / state.totalBytes).coerceIn(0, 100)}%). Templates until it is ready."
        } else {
            "Gemma is downloading. Templates until it is ready."
        }
        ModelState.Verifying -> "Gemma has arrived and is being checked. Templates until it is ready."
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(Tags.MODEL_PROGRESS),
    )
}

private val Period.verdictTitle: String
    get() = when (this) {
        Period.DAY -> "Yesterday's verdict"
        Period.WEEK -> "Last week's verdict"
        Period.MONTH -> "Last month's verdict"
    }

private val Period.noVerdictYet: String
    get() = when (this) {
        Period.DAY -> "The first verdict arrives after your first full day of counting."
        Period.WEEK -> "The first verdict arrives after your first full week of counting."
        Period.MONTH -> "The first verdict arrives after your first full month of counting."
    }

private val ScoreKind.title: String
    get() = when (this) {
        ScoreKind.LAZINESS -> "Laziness"
        ScoreKind.UNPRODUCTIVITY -> "Unproductivity"
    }

private val ScoreKind.formula: String
    get() = when (this) {
        ScoreKind.LAZINESS -> "Laziness = 45% volume + 15% above usual + 20% late night + 20% longest session."
        ScoreKind.UNPRODUCTIVITY -> "Unproductivity = time spent against four times your daily budget."
    }

fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToInt()
    return when {
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}

fun reelsWord(count: Int) = if (count == 1) "reel" else "reels"
