package com.reelsloth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelsloth.ai.ModelState
import com.reelsloth.ai.Tone
import com.reelsloth.ai.Verdict
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.Scores
import com.reelsloth.stats.MonthCell
import com.reelsloth.stats.MonthChart
import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodCharts
import com.reelsloth.stats.PeriodReport
import com.reelsloth.stats.WeekChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Whether anything can be counted right now. Shared by the three period screens. */
data class TrackingState(
    val permissionGranted: Boolean,
    val paused: Boolean,
    val anyAppReady: Boolean,
)

/** Everything the Today screen draws. A null [report] means the database has not answered yet. */
data class TodayUiState(
    val tracking: TrackingState,
    val report: PeriodReport?,
    val previous: PeriodReport?,
    val scores: Scores?,
    val config: ScoreConfig,
    val verdict: Verdict?,
    val tone: Tone,
    val modelState: ModelState,
)

data class WeekUiState(
    val tracking: TrackingState,
    val report: PeriodReport?,
    val chart: WeekChart?,
    val scores: Scores?,
    val config: ScoreConfig,
    val verdict: Verdict?,
    val modelState: ModelState,
)

data class MonthUiState(
    val tracking: TrackingState,
    val report: PeriodReport?,
    val chart: MonthChart?,
    val scores: Scores?,
    val config: ScoreConfig,
    val verdict: Verdict?,
    val modelState: ModelState,
)

@Composable
fun TodayScreen(
    state: TodayUiState,
    onToneChange: (Tone) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        ScreenTitle("Today")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        if (report == null) {
            Loading()
            return@ScreenColumn
        }
        val stats = report.stats
        BigNumber(
            value = stats.reels.toString(),
            caption = "${reelsWord(stats.reels)} today, ${formatMinutes(stats.minutes)} of scrolling",
        )
        state.previous?.let {
            Text(
                "Yesterday: ${it.stats.reels} ${reelsWord(it.stats.reels)}, ${formatMinutes(it.stats.minutes)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (stats.reels == 0) {
            EmptyState(
                title = "Not a single reel yet",
                body = if (state.tracking.canCount) {
                    "The sloth is awake and counting. Enjoy the quiet while it lasts."
                } else {
                    "Once counting is on, today's reels, minutes and scores show up here."
                },
            )
        } else {
            state.scores?.let { scores ->
                ScoreCard(scores.laziness, stats, state.config, Period.DAY)
                ScoreCard(scores.unproductivity, stats, state.config, Period.DAY)
            }
            PlatformSplit(stats.byPlatform)
        }
        CommentaryCard(Period.DAY, state.verdict, state.modelState, state.tone, onToneChange)
    }
}

@Composable
fun WeekScreen(state: WeekUiState, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        ScreenTitle("This week")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        val chart = state.chart
        if (report == null || chart == null) {
            Loading()
            return@ScreenColumn
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BigNumber(chart.total.toString(), "${reelsWord(chart.total)} this week", Modifier.weight(1f))
            Stat(
                value = chart.averagePerDay?.let { formatAverage(it) } ?: "–",
                caption = "a day on average",
                modifier = Modifier.weight(1f),
            )
        }
        Text("${formatMinutes(report.stats.minutes)} of scrolling.", style = MaterialTheme.typography.bodyMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Reels per day")
                WeekBars(chart)
            }
        }
        if (chart.total == 0) {
            EmptyState(
                title = "A spotless week so far",
                body = "No reels counted this week. The bars grow as soon as one sneaks in.",
            )
        } else {
            state.scores?.let { scores ->
                ScoreCard(scores.laziness, report.stats, state.config, Period.WEEK)
                ScoreCard(scores.unproductivity, report.stats, state.config, Period.WEEK)
            }
            PlatformSplit(report.stats.byPlatform)
        }
        CommentaryCard(Period.WEEK, state.verdict, state.modelState)
    }
}

@Composable
fun MonthScreen(state: MonthUiState, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        ScreenTitle("This month")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        val chart = state.chart
        if (report == null || chart == null) {
            Loading()
            return@ScreenColumn
        }
        BigNumber(chart.total.toString(), "${reelsWord(chart.total)} this month, ${formatMinutes(report.stats.minutes)}")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Daily heat map")
                MonthHeatMap(chart)
            }
        }
        BestWorstCard(chart)
        if (chart.total == 0) {
            EmptyState(
                title = "Nothing to heat up yet",
                body = "No reels this month. The squares darken as the counts climb.",
            )
        } else {
            state.scores?.let { scores ->
                ScoreCard(scores.laziness, report.stats, state.config, Period.MONTH)
                ScoreCard(scores.unproductivity, report.stats, state.config, Period.MONTH)
            }
            PlatformSplit(report.stats.byPlatform)
        }
        CommentaryCard(Period.MONTH, state.verdict, state.modelState)
    }
}

private val TrackingState.canCount: Boolean get() = permissionGranted && !paused && anyAppReady

@Composable
private fun TrackingBanners(tracking: TrackingState, onOpenAccessibilitySettings: () -> Unit) =
    TrackingBanners(tracking.permissionGranted, tracking.paused, tracking.anyAppReady, onOpenAccessibilitySettings)

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Counting" })
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.displaySmall)
        Text(caption, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Seven bars, tallest = busiest day. Today is solid, other days faded, future days a stub. */
@Composable
private fun WeekBars(chart: WeekChart) {
    val locale = Locale.getDefault()
    val description = PeriodCharts.describe(chart, locale)
    val max = chart.bars.maxOf { it.reels ?: 0 }.coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val faded = primary.copy(alpha = 0.45f)
    val stub = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(Tags.WEEK_CHART)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        chart.bars.forEach { bar ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    bar.reels?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Canvas(modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
                    val reels = bar.reels
                    val minHeight = 3.dp.toPx()
                    val height = if (reels == null || reels == 0) minHeight else maxOf(minHeight, size.height * reels / max)
                    drawRoundRect(
                        color = when {
                            reels == null -> stub
                            bar.isToday -> primary
                            else -> faded
                        },
                        topLeft = Offset(0f, size.height - height),
                        size = Size(size.width, height),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = if (bar.isToday) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = if (bar.isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Calendar of the month, one square per day, darker for more reels. */
@Composable
private fun MonthHeatMap(chart: MonthChart) {
    val locale = Locale.getDefault()
    val description = PeriodCharts.describe(chart, locale)
    val weekdays = (0 until 7).map { chart.weekStart.plus(it.toLong()) }
    val slots: List<MonthCell?> = List(chart.leadingBlanks) { null } + chart.cells
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(Tags.MONTH_HEAT_MAP)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        slots.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell -> HeatCell(cell, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall)
            (0..PeriodCharts.MAX_LEVEL).forEach { level ->
                Box(Modifier.size(12.dp).background(heatColor(level), RoundedCornerShape(3.dp)))
            }
            Text("More", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HeatCell(cell: MonthCell?, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    if (cell == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val future = cell.reels == null
    val base = modifier
        .aspectRatio(1f)
        .background(if (future) MaterialTheme.colorScheme.surface else heatColor(cell.level), shape)
        .let { if (future) it.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape) else it }
        .let { if (cell.isToday) it.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else it }
    Box(base, contentAlignment = Alignment.Center) {
        Text(
            cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (cell.level >= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun heatColor(level: Int) = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.primary.copy(alpha = HEAT_ALPHAS[(level - 1).coerceIn(0, HEAT_ALPHAS.lastIndex)])
}

@Composable
private fun BestWorstCard(chart: MonthChart) {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Best and worst day")
            val best = chart.best
            if (best == null) {
                Text(
                    "These appear once you have ${PeriodCharts.MIN_DAYS_FOR_BEST_WORST} full days counted this month.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                DayLine("Best", formatter.format(best.date), best.reels)
                val worst = chart.worst
                if (worst == null) {
                    Text("No worst day: every finished day was reel-free.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    DayLine("Worst", formatter.format(worst.date), worst.reels)
                }
            }
        }
    }
}

@Composable
private fun DayLine(label: String, date: String, reels: Int) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("$date, $reels ${reelsWord(reels)}", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatAverage(value: Double): String =
    if (value >= 10) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** The last day in a current-period report is today. */
fun PeriodReport.lastDay(zone: java.time.ZoneId): LocalDate? =
    days.lastOrNull()?.let { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }

private val BAR_HEIGHT = 120.dp
private val HEAT_ALPHAS = floatArrayOf(0.25f, 0.45f, 0.7f, 1f)
