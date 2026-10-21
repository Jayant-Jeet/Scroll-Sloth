package com.halfbloodcoder.scrollsloth.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ai.CommentarySource
import com.halfbloodcoder.scrollsloth.ai.ModelState
import com.halfbloodcoder.scrollsloth.ai.Tone
import com.halfbloodcoder.scrollsloth.ai.Verdict
import com.halfbloodcoder.scrollsloth.detect.Platform
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import com.halfbloodcoder.scrollsloth.score.ScoreExplainer
import com.halfbloodcoder.scrollsloth.score.ScoreKind
import com.halfbloodcoder.scrollsloth.score.ScoreResult
import com.halfbloodcoder.scrollsloth.score.Scores
import com.halfbloodcoder.scrollsloth.stats.Period
import com.halfbloodcoder.scrollsloth.stats.PeriodStats
import com.halfbloodcoder.scrollsloth.ui.theme.Inter
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText
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
    const val BOTTOM_NAV = "bottom_nav"
    const val SPLASH = "splash"
    fun score(kind: ScoreKind) = "score_${kind.name.lowercase()}"
}

internal val CardShape = RoundedCornerShape(24.dp)

/** "SCROLL SLOTH" eyebrow, the screen title, and the logo. */
@Composable
fun BrandHeader(title: String, modifier: Modifier = Modifier) {
    val colors = SlothTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("SCROLL SLOTH", style = slothText(12.sp, FontWeight.SemiBold), color = colors.lavenderText)
            Text(
                title,
                style = slothText(32.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Image(
            painter = painterResource(R.drawable.sloth_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 80.dp, height = 60.dp).clip(RoundedCornerShape(18.dp)),
        )
    }
}

/** 18sp section title with an optional muted caption or trailing element on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SlothTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = slothText(18.sp, FontWeight.SemiBold),
            color = colors.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (caption != null) Text(caption, style = slothText(11.sp), color = colors.muted)
        trailing?.invoke()
    }
}

/** A titled section: header, then its content 12dp below. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, caption = caption, trailing = trailing)
        content()
    }
}

/** The card every section sits on. */
@Composable
fun SlothCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SlothTheme.colors.card)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun SlothIcon(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** A rounded square holding an icon, as used by app rows and day highlights. */
@Composable
fun IconBox(@DrawableRes icon: Int, tint: Color, boxSize: Dp, iconSize: Dp, corner: Dp = 11.dp) {
    Box(
        modifier = Modifier.size(boxSize).clip(RoundedCornerShape(corner)).background(SlothTheme.colors.cardRaised),
        contentAlignment = Alignment.Center,
    ) {
        SlothIcon(icon, tint, iconSize)
    }
}

@Composable
fun SlothDivider(modifier: Modifier = Modifier, color: Color = SlothTheme.colors.divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * A tap target of at least 48dp around a smaller visual, so pills can look like the design and
 * still be easy to hit. [content] gets whether it is pressed, to dim itself.
 */
@Composable
fun PressableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) {
        content(pressed)
    }
}

/** "Open", "Check now", "Export CSV": a small raised pill with an icon. */
@Composable
fun ActionPill(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SlothTheme.colors.mintText,
    enabled: Boolean = true,
) {
    PressableBox(onClick = onClick, enabled = enabled, modifier = modifier) { pressed ->
        Row(
            modifier = Modifier
                .alpha(if (!enabled) 0.45f else if (pressed) 0.7f else 1f)
                .clip(CircleShape)
                .background(SlothTheme.colors.cardRaised)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) SlothIcon(icon, color, 14.dp)
            Text(text, style = slothText(12.sp, FontWeight.Medium), color = color)
        }
    }
}

/**
 * A pill segmented control. Each option is a 48dp-tall radio target; the track is drawn behind at
 * the design's size so the layout stays compact.
 */
@Composable
fun <T> PillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 10.sp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
    track: Color = SlothTheme.colors.inset,
) {
    val colors = SlothTheme.colors
    var pillHeight by remember { mutableIntStateOf(0) }
    Row(
        modifier = modifier
            .selectableGroup()
            .drawBehind {
                if (pillHeight > 0) {
                    val height = pillHeight + 6.dp.toPx()
                    drawRoundRect(
                        color = track,
                        topLeft = Offset(0f, (size.height - height) / 2),
                        size = Size(size.width, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                }
            }
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        interactionSource = null,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = slothText(textSize, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isSelected) colors.mintInk else colors.muted,
                    modifier = Modifier
                        .onSizeChanged { pillHeight = it.height }
                        .clip(CircleShape)
                        .background(if (isSelected) colors.mint else Color.Transparent)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                )
            }
        }
    }
}

/** The design's 42x24 switch. Visual only: the row around it is the toggle. */
@Composable
fun PillSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = SlothTheme.colors
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) colors.mint else colors.meterTrack)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val thumb = when {
            checked -> colors.mintInk
            colors.isDark -> colors.text
            else -> Color.White
        }
        Box(Modifier.size(18.dp).clip(CircleShape).background(thumb))
    }
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
    val colors = SlothTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, colors.lavenderText.copy(alpha = 0.5f), CardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = slothText(14.sp, FontWeight.SemiBold), color = colors.text)
        Text(body, style = slothText(13.sp, lineHeight = 1.6f), color = colors.muted)
        if (action != null) ActionPill(action, R.drawable.ic_external_link, onAction)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = SlothTheme.colors
    SlothCard(modifier = modifier.testTag(Tags.EMPTY_STATE), spacing = 6.dp) {
        Text(title, style = slothText(16.sp, FontWeight.SemiBold), color = colors.text)
        Text(body, style = slothText(13.sp, lineHeight = 1.6f), color = colors.muted)
    }
}

@Composable
fun LoadingIndicator() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = SlothTheme.colors.mintText,
            trackColor = SlothTheme.colors.cardRaised,
            modifier = Modifier.semantics { contentDescription = "Counting" },
        )
    }
}

/** What the lavender hero shows. */
data class HeroContent(
    val heading: String,
    val count: Int,
    val caption: String,
    val minutes: Double,
    val footerLabel: String,
    val footerValue: String,
)

/** The lavender card with the big count, time spent and a one-line comparison. */
@Composable
fun ConsumptionHero(content: HeroContent, tracking: TrackingState, modifier: Modifier = Modifier) {
    val colors = SlothTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.lavender)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                content.heading,
                style = slothText(14.sp, FontWeight.SemiBold),
                color = colors.lavenderInk,
                modifier = Modifier.weight(1f),
            )
            TrackingPill(tracking)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                BasicText(
                    content.count.toString(),
                    style = slothText(80.sp, lineHeight = 0.98f).copy(color = colors.lavenderInk),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 80.sp),
                    modifier = Modifier.testTag(Tags.COUNT),
                )
                Text(content.caption, style = slothText(14.sp), color = colors.lavenderMuted)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                SlothIcon(R.drawable.ic_clock, colors.lavenderMuted, 20.dp)
                Spacer(Modifier.height(4.dp))
                Text(timeText(content.minutes), color = colors.lavenderInk, maxLines = 1)
                Text("spent scrolling", style = slothText(12.sp), color = colors.lavenderMuted)
            }
        }
        Column {
            SlothDivider(color = colors.heroDivider)
            Row(
                modifier = Modifier.padding(top = 15.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    content.footerLabel,
                    style = slothText(12.sp),
                    color = colors.lavenderMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(content.footerValue, style = slothText(12.sp, FontWeight.SemiBold), color = colors.lavenderInk)
            }
        }
    }
}

@Composable
private fun TrackingPill(tracking: TrackingState) {
    val colors = SlothTheme.colors
    val label = when {
        tracking.canCount -> "Tracking quietly"
        tracking.paused -> "Paused"
        else -> "Not counting"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.trackingPill)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (tracking.canCount) colors.mintInk else colors.lavenderMuted),
        )
        Text(label, style = slothText(10.sp, FontWeight.SemiBold), color = colors.lavenderInk)
    }
}

/** "1 h 21 min" with big numbers and small units, kept as one string for screen readers and tests. */
private fun timeText(minutes: Double) = buildAnnotatedString {
    val big = SpanStyle(fontFamily = Inter, fontSize = 27.sp)
    val small = SpanStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    formatMinutes(minutes).split(' ').forEachIndexed { index, part ->
        val separator = if (index == 0) "" else " "
        if (part.all { it.isDigit() }) {
            withStyle(small) { append(separator) }
            withStyle(big) { append(part) }
        } else {
            withStyle(small) { append(separator + part) }
        }
    }
}

/** "Your scroll state": the two score cards side by side, with their explanations below when opened. */
@Composable
fun ScoreSection(scores: Scores, stats: PeriodStats, config: ScoreConfig, period: Period) {
    var lazinessOpen by rememberSaveable { mutableStateOf(false) }
    var unproductivityOpen by rememberSaveable { mutableStateOf(false) }
    Section("Your scroll state", caption = "A nudge, not a label") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard(scores.laziness, lazinessOpen, { lazinessOpen = !lazinessOpen }, Modifier.weight(1f))
            ScoreCard(
                scores.unproductivity,
                unproductivityOpen,
                { unproductivityOpen = !unproductivityOpen },
                Modifier.weight(1f),
            )
        }
        if (period != Period.DAY) {
            val days = scores.laziness.daysAveraged
            Text(
                "Average of $days daily ${if (days == 1) "score" else "scores"}; quiet days count as 0.",
                style = slothText(11.sp, lineHeight = 1.5f),
                color = SlothTheme.colors.muted,
            )
        }
        if (lazinessOpen) ScoreExplanation(scores.laziness, stats, config)
        if (unproductivityOpen) ScoreExplanation(scores.unproductivity, stats, config)
    }
}

@Composable
private fun ScoreCard(result: ScoreResult, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = SlothTheme.colors
    SlothCard(
        modifier = modifier.testTag(Tags.score(result.kind)),
        padding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 2.dp),
        spacing = 12.dp,
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlothIcon(result.kind.icon, colors.lavenderText, 16.dp)
                Text(result.kind.title, style = slothText(12.sp), color = colors.text)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = colors.text)) {
                        append(result.score.toString())
                    }
                    withStyle(SpanStyle(fontSize = 12.sp, color = colors.muted)) { append(" / 100") }
                },
                style = slothText(42.sp, lineHeight = 1.08f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(result.band.label, style = slothText(11.sp), color = colors.lavenderText)
                Meter(result.score)
            }
        }
        PressableBox(
            onClick = onToggle,
            onClickLabel = if (expanded) "Hide why" else "Show why",
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) { pressed ->
            Row(
                modifier = Modifier.alpha(if (pressed) 0.7f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (expanded) "Hide why" else "Why this score",
                    style = slothText(11.sp, FontWeight.Medium),
                    color = colors.mintText,
                )
                SlothIcon(
                    R.drawable.ic_chevron_down,
                    colors.mintText,
                    13.dp,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

/** A 0..100 bar. Decorative: the number above it is what screen readers hear. */
@Composable
private fun Meter(score: Int) {
    val colors = SlothTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.meterTrack)
            .clearAndSetSemantics {},
    ) {
        val fraction = score.coerceIn(0, 100) / 100f
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(colors.lavenderText))
        }
    }
}

@Composable
private fun ScoreExplanation(result: ScoreResult, stats: PeriodStats, config: ScoreConfig) {
    val colors = SlothTheme.colors
    SlothCard(modifier = Modifier.animateContentSize(), spacing = 12.dp) {
        Text(
            "Why ${result.kind.title.lowercase()} is ${result.score}",
            style = slothText(14.sp, FontWeight.SemiBold),
            color = colors.text,
        )
        ScoreExplainer.explain(result, stats, config).forEach { line ->
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row {
                    Text(
                        line.label,
                        style = slothText(13.sp, FontWeight.Medium),
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (result.kind == ScoreKind.LAZINESS) "${line.value}/100, adds ${line.adds}" else "${line.value}/100",
                        style = slothText(13.sp, FontWeight.SemiBold),
                        color = colors.lavenderText,
                    )
                }
                Text(line.detail, style = slothText(12.sp, lineHeight = 1.5f), color = colors.muted)
            }
        }
        SlothDivider()
        Text(result.kind.formula, style = slothText(11.sp, lineHeight = 1.5f), color = colors.muted)
    }
}

/** "By app": a share bar and one row per app with reels. Hidden when nothing was counted. */
@Composable
fun AppBreakdown(byPlatform: Map<Platform, Int>, caption: String, modifier: Modifier = Modifier) {
    val total = byPlatform.values.sum()
    if (total == 0) return
    val colors = SlothTheme.colors
    val entries = Platform.entries.mapNotNull { platform ->
        byPlatform[platform]?.takeIf { it > 0 }?.let { platform to it }
    }
    val description = "Reels by app: " + entries.joinToString(", ") { (platform, count) -> "${platform.label} $count" }
    Section("By app", modifier = modifier, caption = caption) {
        SlothCard(modifier = Modifier.testTag(Tags.PLATFORM_SPLIT)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { (platform, count) ->
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(platform.color()),
                    )
                }
            }
            entries.forEach { (platform, count) ->
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBox(platform.icon, platform.color(), boxSize = 34.dp, iconSize = 19.dp)
                    Text(
                        platform.label,
                        style = slothText(13.sp, FontWeight.Medium),
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$count ${reelsWord(count)}", style = slothText(13.sp, FontWeight.SemiBold), color = colors.text)
                    Text(
                        "${(count * 100.0 / total).roundToInt()}%",
                        style = slothText(12.sp),
                        color = platform.color(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(35.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun Platform.color(): Color = when (this) {
    Platform.INSTAGRAM -> SlothTheme.colors.instagram
    Platform.YOUTUBE -> SlothTheme.colors.youtube
    Platform.FACEBOOK -> SlothTheme.colors.facebook
}

internal val Platform.icon: Int
    get() = when (this) {
        Platform.INSTAGRAM -> R.drawable.ic_instagram
        Platform.YOUTUBE -> R.drawable.ic_youtube
        Platform.FACEBOOK -> R.drawable.ic_facebook
    }

/**
 * The cached verdict for the last completed period, with a badge saying who wrote it. Never waits
 * for the model. [modelState] adds a line while Gemma is downloading; [onToneChange] adds the toggle.
 */
@Composable
fun VerdictSection(
    period: Period,
    verdict: Verdict?,
    modelState: ModelState,
    modifier: Modifier = Modifier,
    tone: Tone? = null,
    onToneChange: ((Tone) -> Unit)? = null,
) {
    val colors = SlothTheme.colors
    Section(
        period.verdictTitle,
        modifier = modifier,
        trailing = { if (verdict != null) SourceBadge(verdict.source) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(colors.reflection)
                .border(1.dp, colors.reflectionBorder, CardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (verdict == null) SlothIcon(R.drawable.ic_clock, colors.mintText, 14.dp)
                    Text(
                        if (verdict == null) "A LITTLE MORE TIME" else tone.eyebrow,
                        style = slothText(10.sp, FontWeight.SemiBold).copy(letterSpacing = 0.6.sp),
                        color = colors.mintText,
                    )
                }
                if (tone != null && onToneChange != null) ToneToggle(tone, onToneChange)
            }
            if (verdict == null) {
                Text("Still getting to know you.", style = slothText(22.sp, lineHeight = 1.2f), color = colors.text)
                Text(period.noVerdictYet, style = slothText(13.sp, lineHeight = 1.6f), color = colors.reflectionBody)
            } else {
                val (headline, body) = splitVerdict(verdict.text)
                Text(headline, style = slothText(22.sp, lineHeight = 1.2f), color = colors.text)
                if (body.isNotEmpty()) {
                    Text(body, style = slothText(13.sp, lineHeight = 1.6f), color = colors.reflectionBody)
                }
            }
            ModelProgressNote(modelState)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlothIcon(R.drawable.ic_shield_check, colors.mintText, 12.dp)
                Text("Written on your phone. Just for you.", style = slothText(10.sp), color = colors.mintText)
            }
        }
    }
}

/** Gentle / Roast. */
@Composable
fun ToneToggle(
    tone: Tone,
    onToneChange: (Tone) -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 10.sp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
) {
    PillToggle(
        options = Tone.entries,
        selected = tone,
        label = { if (it == Tone.GENTLE) "Gentle" else "Roast" },
        onSelect = onToneChange,
        modifier = modifier.semantics { contentDescription = "Commentary tone" },
        textSize = textSize,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
    )
}

@Composable
private fun SourceBadge(source: CommentarySource) {
    val colors = SlothTheme.colors
    val gemma = source == CommentarySource.GEMMA
    Row(
        modifier = Modifier
            .testTag(Tags.COMMENTARY_BADGE)
            .semantics {
                contentDescription = if (gemma) "Written by Gemma on your phone" else "Written from a template"
            }
            .clip(CircleShape)
            .background(colors.cardRaised)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SlothIcon(R.drawable.ic_sparkles, if (gemma) colors.mintText else colors.muted, 11.dp)
        Text(
            if (gemma) "Gemma" else "Template",
            style = slothText(10.sp, FontWeight.SemiBold),
            color = if (gemma) colors.mintText else colors.muted,
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
        style = slothText(12.sp, lineHeight = 1.5f),
        color = SlothTheme.colors.reflectionBody,
        modifier = Modifier.testTag(Tags.MODEL_PROGRESS),
    )
}

/**
 * Splits a verdict into a headline (its first sentence) and the rest, without changing any
 * character, so NumberGuard-checked text is shown exactly as it was written.
 */
internal fun splitVerdict(text: String): Pair<String, String> {
    val trimmed = text.trim()
    val match = Regex("""[.!?]["')\]]*\s+""").find(trimmed) ?: return trimmed to ""
    val headline = trimmed.substring(0, match.range.first + match.value.trimEnd().length)
    val body = trimmed.substring(match.range.last + 1).trim()
    return if (body.isEmpty()) trimmed to "" else headline to body
}

private val Tone?.eyebrow: String
    get() = when (this) {
        Tone.GENTLE -> "A KIND PERSPECTIVE"
        Tone.ROAST -> "A CHEEKY PERSPECTIVE"
        null -> "A FRIENDLY PERSPECTIVE"
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

private val ScoreKind.icon: Int
    get() = when (this) {
        ScoreKind.LAZINESS -> R.drawable.ic_moon
        ScoreKind.UNPRODUCTIVITY -> R.drawable.ic_battery_low
    }

private val ScoreKind.formula: String
    get() = when (this) {
        ScoreKind.LAZINESS -> "Laziness = 45% volume + 15% above usual + 20% late night + 20% longest session."
        ScoreKind.UNPRODUCTIVITY -> "Unproductivity = time spent against four times your daily budget."
    }

internal val TrackingState.canCount: Boolean get() = permissionGranted && !paused && anyAppReady

fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToInt()
    return when {
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}

fun reelsWord(count: Int) = if (count == 1) "reel" else "reels"
