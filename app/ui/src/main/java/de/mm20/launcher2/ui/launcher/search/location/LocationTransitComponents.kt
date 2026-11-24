package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import blend.Blend.harmonize
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LineNameComparator
import de.mm20.launcher2.search.location.LineType
import de.mm20.launcher2.ui.component.MarqueeText
import de.mm20.launcher2.ui.theme.LocalDarkTheme
import de.mm20.launcher2.ui.theme.atTone
import de.mm20.launcher2.ui.theme.toComposeColor
import de.mm20.launcher2.ui.utils.LocalTime
import de.mm20.launcher2.ui.utils.LocalTimeFormat
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun Departures(
    modifier: Modifier = Modifier,
    departures: List<Departure>,
) {
    val context = LocalContext.current

    val nextDeparture = key(LocalTime.current) {
        departures.firstOrNull {
            it.time.plus(it.delay ?: Duration.ZERO).isAfter(ZonedDateTime.now())
        }
    }
    var animateFilterChipsOnce by remember { mutableStateOf(true) }
    if (nextDeparture != null) {
        var showDepartureList by remember { mutableStateOf(false) }
        OutlinedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            onClick = { showDepartureList = true }
        ) {
            AnimatedContent(showDepartureList) { showList ->
                if (!showList) {
                    Row(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineIcon(
                            departure = nextDeparture,
                            Modifier.padding(end = 8.dp)
                        )
                        val lastStop = nextDeparture.lastStop
                        if (lastStop != null) {
                            MarqueeText(
                                modifier = Modifier.weight(1f),
                                text = lastStop,
                                style = MaterialTheme.typography.labelMedium,
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 0,
                                velocity = 20.dp,
                                fadeLeft = 5.dp,
                                fadeRight = 5.dp,
                            )
                        }

                        Text(
                            text = key(LocalTime.current) {
                                departureInMinutes(context, nextDeparture)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Icon(painterResource(BaseR.drawable.chevron_forward_24px), null)
                    }
                } else {
                    val (lines, groupedDepartures) = remember(departures) {
                        val dict = departures.groupBy { it.line to it.type }
                        dict.keys.toList().sortedWith { (line1, type1), (line2, type2) ->
                            if (type1 != type2) compareValues(type1?.ordinal, type2?.ordinal)
                            else LineNameComparator.compare(line1, line2)
                        } to dict
                    }

                    var showMinutes by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { showMinutes = !showMinutes },
                                onLongClick = { showDepartureList = false },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            val filterChipListState = rememberLazyListState()
                            var selectedLine by remember {
                                mutableStateOf(nextDeparture.line to nextDeparture.type)
                            }
                            LaunchedEffect(Unit) {
                                val itemIdx = lines.indexOf(selectedLine)
                                if (itemIdx != -1) {
                                    if (animateFilterChipsOnce) {
                                        kotlinx.coroutines.delay(500)
                                        filterChipListState.animateScrollToItem(itemIdx)
                                        animateFilterChipsOnce = false
                                    } else {
                                        filterChipListState.scrollToItem(itemIdx)
                                    }
                                }
                            }
                            LazyRow(
                                state = filterChipListState,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                itemsIndexed(
                                    lines,
                                    key = { idx, _ -> idx }
                                ) { idx, it ->
                                    val (lineName, _) = it
                                    val firstDeparture = groupedDepartures[it]?.first()
                                    if (firstDeparture != null) {
                                        LineFilterChip(
                                            lineName = lineName,
                                            lineColor = firstDeparture.lineColor?.toComposeColor(),
                                            lineType = firstDeparture.type,
                                            selected = selectedLine == it,
                                            onClick = { selectedLine = it },
                                            modifier = Modifier
                                                .padding(
                                                    top = 12.dp,
                                                    bottom = 12.dp,
                                                    start = 4.dp,
                                                    end = 4.dp,
                                                )
                                                .graphicsLayer {
                                                    alpha = filterChipListState.layoutInfo.blendIntoViewScale(idx, 0.5f)
                                                }
                                        )
                                    }
                                }
                            }
                            AnimatedContent(
                                selectedLine,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220, delayMillis = 90))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                }
                            ) { line ->
                                val selectedDepartures = groupedDepartures[line]
                                if (selectedDepartures != null) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                        for (i in 0..<selectedDepartures.size.coerceAtMost(8)) {
                                            if (i > 0) {
                                                HorizontalDivider()
                                            }
                                            val dep = selectedDepartures[i]
                                            DepartureRow(
                                                departure = dep,
                                                lineWidth = remember(selectedDepartures) {
                                                    selectedDepartures.maxOfOrNull { it.line.length }
                                                },
                                                withIcon = false,
                                                minutesInsteadOfTime = showMinutes,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LineMarqueeText(
    lineName: String,
    lineForeground: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) = MarqueeText(
    text = lineName,
    style = style,
    color = lineForeground,
    textAlign = TextAlign.Center,
    fadeLeft = 2.5.dp,
    fadeRight = 2.5.dp,
    iterations = Int.MAX_VALUE,
    repeatDelayMillis = 0,
    spacing = MarqueeSpacing(10.dp),
    velocity = 20.dp,
    modifier = modifier
)

@Composable
internal fun LineTypeIcon(
    lineType: LineType?,
    tint: Color,
    modifier: Modifier = Modifier
) = Icon(
    painter = painterResource(
        when (lineType) {
            LineType.Bus -> BaseR.drawable.directions_bus_20px
            LineType.Tram -> BaseR.drawable.tram_20px
            LineType.Subway -> BaseR.drawable.subway_20px
            LineType.Monorail -> BaseR.drawable.monorail_20px
            LineType.CommuterTrain -> BaseR.drawable.directions_railway_20px
            LineType.Train, LineType.RegionalTrain, LineType.HighSpeedTrain -> BaseR.drawable.train_20px
            LineType.Boat -> BaseR.drawable.directions_boat_20px
            LineType.CableCar -> BaseR.drawable.cable_car_20px
            LineType.AerialTramway -> BaseR.drawable.gondola_lift_20px
            LineType.Airplane -> BaseR.drawable.flight_20px
            null -> BaseR.drawable.commute_20px
        }
    ),
    contentDescription = null,
    modifier = modifier,
    tint = tint
)

@Composable
internal fun LineFilterChip(
    lineName: String,
    lineColor: Color?,
    lineType: LineType?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = 0.875f

    val dark = LocalDarkTheme.current
    val color =
        if (lineColor == null) MaterialTheme.colorScheme.primary
        else Color(harmonize(lineColor.toArgb(), MaterialTheme.colorScheme.primary.toArgb()))

    InputChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(lineName, style = MaterialTheme.typography.labelMedium)
        },
        avatar = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color.atTone(if (dark) 80 else 40))
                    .requiredSize(
                        InputChipDefaults.AvatarSize * scale
                    )
            ) {
                LineTypeIcon(
                    lineType = lineType,
                    tint = color.atTone(if (dark) 20 else 100),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedLabelColor = color.atTone(if (dark) 90 else 30),
            selectedContainerColor = color.atTone(if (dark) 30 else 90),
        ),
        modifier = modifier.height(FilterChipDefaults.Height * scale)
    )
}

@Composable
internal fun LineIcon(
    lineName: String,
    lineType: LineType?,
    lineColor: android.graphics.Color?,
    hasDeparted: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = LocalDarkTheme.current
    val color =
        if (lineColor == null) MaterialTheme.colorScheme.primary
        else Color(harmonize(lineColor.toArgb(), MaterialTheme.colorScheme.primary.toArgb()))

    Row(
        modifier = modifier
            .wrapContentWidth(Alignment.Start)
            .background(
                color.atTone(if (dark) 80 else 40),
                MaterialTheme.shapes.small
            )
            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val foregroundColor = color.atTone(if (dark) 20 else 100)
        LineTypeIcon(
            lineType,
            foregroundColor,
            Modifier
                .padding(end = 2.dp)
                .size(16.dp)
        )
        LineMarqueeText(
            lineName,
            foregroundColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .wrapContentSize()
                .widthIn(max = 34.dp)
        )
    }
}

@Composable
internal fun LineIcon(
    departure: Departure,
    modifier: Modifier = Modifier,
) = LineIcon(
    lineName = departure.line,
    lineType = departure.type,
    lineColor = departure.lineColor,
    hasDeparted = ZonedDateTime.now().isAfter(departure.time + (departure.delay ?: Duration.ZERO)),
    modifier = modifier
)

@Composable
internal fun DepartureRow(
    departure: Departure,
    lineWidth: Int?,
    withIcon: Boolean,
    minutesInsteadOfTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val twentyFourHours = LocalTimeFormat.current.isTwentyFourHours(context)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            if (withIcon) {
                LineIcon(
                    departure = departure,
                    Modifier
                        .padding(end = 8.dp)
                        .widthIn(
                            min = if (lineWidth == null) 0.dp
                            else max(64.dp, lineWidth * 8.dp)
                        )
                )
            }
            if (departure.lastStop != null) {
                MarqueeText(
                    text = departure.lastStop!!,
                    style = MaterialTheme.typography.labelMedium,
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 0,
                    velocity = 20.dp,
                    fadeLeft = 5.dp,
                    fadeRight = 5.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (minutesInsteadOfTime) {
                    key(LocalTime.current) { departureInMinutes(context, departure) }
                } else {
                    departure.time.format(
                        DateTimeFormatter.ofPattern(
                            if (twentyFourHours) "HH:mm" else "hh:mm a",
                            Locale.getDefault()
                        )
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 2.dp)
            )
            if (!minutesInsteadOfTime) {
                val delayMinutes = departure.delay?.toMinutes()
                if (null != delayMinutes && 0L < delayMinutes) {
                    Text(
                        text = "+$delayMinutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

internal fun departureInMinutes(context: Context, departure: Departure): String {
    val delayedDepartureTime =
        departure.time + (departure.delay ?: Duration.ZERO)
    val now = ZonedDateTime.now()

    if (delayedDepartureTime < now)
        return context.getString(I18nR.string.departure_time_departed)

    val timeLeft =
        Duration.between(now, delayedDepartureTime)
            .toMinutes().toInt()
    return if (timeLeft < 1) {
        context.getString(I18nR.string.departure_time_now)
    } else {
        context.resources.getQuantityString(
            I18nR.plurals.departure_time_in,
            timeLeft,
            timeLeft
        )
    }
}
