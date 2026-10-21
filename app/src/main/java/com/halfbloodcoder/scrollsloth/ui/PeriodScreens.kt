package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ai.ModelState
import com.halfbloodcoder.scrollsloth.ai.Tone
import com.halfbloodcoder.scrollsloth.ai.Verdict
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import com.halfbloodcoder.scrollsloth.score.Scores
import com.halfbloodcoder.scrollsloth.stats.DayCount
import com.halfbloodcoder.scrollsloth.stats.MonthCell
import com.halfbloodcoder.scrollsloth.stats.MonthChart
import com.halfbloodcoder.scrollsloth.stats.Period
import com.halfbloodcoder.scrollsloth.stats.PeriodCharts
import com.halfbloodcoder.scrollsloth.stats.PeriodReport
import com.halfbloodcoder.scrollsloth.stats.WeekChart
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText
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
    contentPadding: PaddingValues = PaddingValues(),
) {
    ScreenColumn(modifier, contentPadding) {
        BrandHeader("A little check-in.")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        if (report == null) {
            LoadingIndicator()
            return@ScreenColumn
        }
        val stats = report.stats
        ConsumptionHero(
            HeroContent(
                heading = "Today, so far",
                count = stats.reels,
                caption = "${reelsWord(stats.reels)} watched",
                minutes = stats.minutes,
                footerLabel = "Yesterday",
                footerValue = state.previous?.stats?.let {
                    "${it.reels} ${reelsWord(it.reels)} · ${formatMinutes(it.minutes)}"
                } ?: "—",
            ),
            state.tracking,
        )
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
            state.scores?.let { ScoreSection(it, stats, state.config, Period.DAY) }
            AppBreakdown(stats.byPlatform, caption = "Today's ${stats.reels} ${reelsWord(stats.reels)}")
        }
        VerdictSection(Period.DAY, state.verdict, state.modelState, tone = state.tone, onToneChange = onToneChange)
    }
}

@Composable
fun WeekScreen(
    state: WeekUiState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    ScreenColumn(modifier, contentPadding) {
        BrandHeader("Your week.")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        val chart = state.chart
        if (report == null || chart == null) {
            LoadingIndicator()
            return@ScreenColumn
        }
        ConsumptionHero(
            HeroContent(
                heading = "This week, so far",
                count = chart.total,
                caption = "${reelsWord(chart.total)} this week",
                minutes = report.stats.minutes,
                footerLabel = "Daily average",
                footerValue = chart.averagePerDay?.let { average ->
                    val text = formatAverage(average)
                    "$text ${if (text == "1") "reel" else "reels"} a day"
                } ?: "—",
            ),
            state.tracking,
        )
        Section("Reels per day", caption = weekRange(chart)) {
            WeekBars(chart)
        }
        if (chart.total == 0) {
            EmptyState(
                title = "A spotless week so far",
                body = "No reels counted this week. The bars grow as soon as one sneaks in.",
            )
        } else {
            state.scores?.let { ScoreSection(it, report.stats, state.config, Period.WEEK) }
            AppBreakdown(report.stats.byPlatform, caption = "This week's ${chart.total} ${reelsWord(chart.total)}")
        }
        VerdictSection(Period.WEEK, state.verdict, state.modelState)
    }
}

@Composable
fun MonthScreen(
    state: MonthUiState,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    ScreenColumn(modifier, contentPadding) {
        BrandHeader("Your month.")
        TrackingBanners(state.tracking, onOpenAccessibilitySettings)
        val report = state.report
        val chart = state.chart
        if (report == null || chart == null) {
            LoadingIndicator()
            return@ScreenColumn
        }
        val locale = Locale.getDefault()
        val today = chart.cells.firstOrNull { it.isToday }?.date ?: chart.cells.lastOrNull { it.reels != null }?.date
        ConsumptionHero(
            HeroContent(
                heading = "This month, so far",
                count = chart.total,
                caption = "${reelsWord(chart.total)} this month",
                minutes = report.stats.minutes,
                footerLabel = "Month to date",
                footerValue = today?.let {
                    "1–${it.dayOfMonth} ${DateTimeFormatter.ofPattern("MMM yyyy", locale).format(it)}"
                } ?: "—",
            ),
            state.tracking,
        )
        Section(
            "Daily rhythm",
            caption = chart.cells.firstOrNull()?.date?.let { DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(it) },
        ) {
            MonthHeatMap(chart, today)
        }
        BestWorst(chart)
        if (chart.total == 0) {
            EmptyState(
                title = "Nothing to heat up yet",
                body = "No reels this month. The squares darken as the counts climb.",
            )
        } else {
            state.scores?.let { ScoreSection(it, report.stats, state.config, Period.MONTH) }
            AppBreakdown(report.stats.byPlatform, caption = "This month's ${chart.total} ${reelsWord(chart.total)}")
        }
        VerdictSection(Period.MONTH, state.verdict, state.modelState)
    }
}

@Composable
private fun TrackingBanners(tracking: TrackingState, onOpenAccessibilitySettings: () -> Unit) =
    TrackingBanners(tracking.permissionGranted, tracking.paused, tracking.anyAppReady, onOpenAccessibilitySettings)

@Composable
internal fun ScreenColumn(
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

/** Seven bars, tallest = busiest day. Past days mint, today lavender, future days an outlined stub. */
@Composable
private fun WeekBars(chart: WeekChart) {
    val colors = SlothTheme.colors
    val locale = Locale.getDefault()
    val description = PeriodCharts.describe(chart, locale)
    val max = chart.bars.maxOf { it.reels ?: 0 }.coerceAtLeast(1)
    val future = chart.bars.filter { it.reels == null }
    SlothCard {
        Column(
            modifier = Modifier
                .testTag(Tags.WEEK_CHART)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chart.bars.forEach { bar ->
                    Box(
                        modifier = Modifier.weight(1f).height(PLOT_HEIGHT),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val reels = bar.reels
                            Text(
                                reels?.toString() ?: "—",
                                style = slothText(12.sp, if (reels == null) FontWeight.Normal else FontWeight.SemiBold),
                                color = if (reels == null) colors.muted else colors.text,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(6.dp))
                            val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            if (reels == null) {
                                Box(
                                    Modifier
                                        .size(width = BAR_WIDTH, height = 3.dp)
                                        .border(1.dp, colors.divider, RoundedCornerShape(2.dp)),
                                )
                            } else {
                                val height = (BAR_MAX_HEIGHT * (reels.toFloat() / max)).coerceAtLeast(3.dp)
                                Box(
                                    Modifier
                                        .size(width = BAR_WIDTH, height = height)
                                        .clip(barShape)
                                        .background(if (bar.isToday) colors.lavender else colors.mint),
                                )
                            }
                        }
                    }
                }
            }
            SlothDivider(color = colors.meterTrack)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chart.bars.forEach { bar ->
                    Text(
                        bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        style = slothText(11.sp, if (bar.isToday) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (bar.isToday) colors.mintText else colors.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (future.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(width = 12.dp, height = 3.dp).border(1.dp, colors.muted, RoundedCornerShape(2.dp)))
                val first = future.first().date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                val last = future.last().date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                Text(
                    if (future.size == 1) "$first hasn't happened yet." else "$first–$last haven't happened yet.",
                    style = slothText(11.sp),
                    color = colors.muted,
                )
            }
        }
    }
}

/** Calendar of the month, one cell per day, deeper colour for more reels. */
@Composable
private fun MonthHeatMap(chart: MonthChart, today: LocalDate?) {
    val colors = SlothTheme.colors
    val locale = Locale.getDefault()
    val description = PeriodCharts.describe(chart, locale)
    val weekdays = (0 until 7).map { chart.weekStart.plus(it.toLong()) }
    val slots: List<MonthCell?> = List(chart.leadingBlanks) { null } + chart.cells
    SlothCard(spacing = 14.dp) {
        Column(
            modifier = Modifier
                .testTag(Tags.MONTH_HEAT_MAP)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                weekdays.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale).take(2),
                        style = slothText(10.sp),
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            slots.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { cell -> HeatCell(cell, Modifier.weight(1f)) }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Text("Less", style = slothText(10.sp), color = colors.muted)
            (0..PeriodCharts.MAX_LEVEL).forEach { level ->
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(colors.heat[level]))
            }
            Text("More", style = slothText(10.sp), color = colors.muted)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.futureCell)
                    .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
            )
            Text("Future", style = slothText(10.sp), color = colors.muted)
        }
        if (today != null) {
            val day = DateTimeFormatter.ofPattern("d MMM", locale).format(today)
            Text(
                "Through today, $day. Future dates aren't counted.",
                style = slothText(11.sp, lineHeight = 1.5f),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun HeatCell(cell: MonthCell?, modifier: Modifier) {
    val colors = SlothTheme.colors
    val shape = RoundedCornerShape(9.dp)
    if (cell == null) {
        Spacer(modifier.height(CELL_HEIGHT))
        return
    }
    val future = cell.reels == null
    val level = cell.level.coerceIn(0, PeriodCharts.MAX_LEVEL)
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .clip(shape)
            .background(if (future) colors.futureCell else colors.heat[level])
            .let {
                when {
                    cell.isToday -> it.border(1.dp, colors.mintText, shape)
                    future -> it.border(1.dp, colors.divider, shape)
                    else -> it
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            style = slothText(12.sp, if (cell.isToday) FontWeight.SemiBold else FontWeight.Normal),
            color = if (future) colors.muted else colors.onHeat[level],
        )
    }
}

@Composable
private fun BestWorst(chart: MonthChart) {
    val colors = SlothTheme.colors
    Section("Best and worst day") {
        SlothCard(spacing = 14.dp) {
            val best = chart.best
            if (best == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBox(R.drawable.ic_calendar, colors.muted, boxSize = 38.dp, iconSize = 19.dp)
                    Text(
                        "These appear once you have ${PeriodCharts.MIN_DAYS_FOR_BEST_WORST} full days counted this month.",
                        style = slothText(13.sp, lineHeight = 1.5f),
                        color = colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                DayLine("Best day", best, colors.mintText)
                val worst = chart.worst
                SlothDivider()
                if (worst == null) {
                    Text(
                        "No worst day: every finished day was reel-free.",
                        style = slothText(13.sp, lineHeight = 1.5f),
                        color = colors.muted,
                    )
                } else {
                    DayLine("Worst day", worst, colors.lavenderText)
                }
            }
        }
    }
}

@Composable
private fun DayLine(label: String, day: DayCount, tint: androidx.compose.ui.graphics.Color) {
    val colors = SlothTheme.colors
    val date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(day.date)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(R.drawable.ic_calendar, tint, boxSize = 38.dp, iconSize = 19.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = slothText(11.sp), color = colors.muted)
            Text(date, style = slothText(13.sp, FontWeight.Medium), color = colors.text)
        }
        Text("${day.reels} ${reelsWord(day.reels)}", style = slothText(13.sp, FontWeight.SemiBold), color = colors.text)
    }
}

private fun weekRange(chart: WeekChart): String? {
    val first = chart.bars.firstOrNull()?.date ?: return null
    val last = chart.bars.last().date
    val locale = Locale.getDefault()
    val month = DateTimeFormatter.ofPattern("MMM", locale)
    return if (first.month == last.month) {
        "${first.dayOfMonth}–${last.dayOfMonth} ${month.format(last)}"
    } else {
        "${first.dayOfMonth} ${month.format(first)} – ${last.dayOfMonth} ${month.format(last)}"
    }
}

internal fun formatAverage(value: Double): String =
    if (value >= 10) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** The last day in a current-period report is today. */
fun PeriodReport.lastDay(zone: java.time.ZoneId): LocalDate? =
    days.lastOrNull()?.let { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }

private val PLOT_HEIGHT: Dp = 150.dp
private val BAR_MAX_HEIGHT: Dp = 124.dp
private val BAR_WIDTH: Dp = 26.dp
private val CELL_HEIGHT: Dp = 38.dp
private val CELL_GAP: Dp = 6.dp
