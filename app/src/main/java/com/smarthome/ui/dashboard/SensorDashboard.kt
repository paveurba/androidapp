package com.smarthome.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.smarthome.data.SensorTilePosition
import com.smarthome.data.TempSensor
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val tileTimeFormatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

// Gap between grid slots and the minimum width a slot is allowed to shrink
// to before another column is dropped. 160.dp keeps two columns comfortable
// on a ~360-412dp phone while giving a wide tablet several columns instead
// of a fixed-width master pane.
private val TILE_GAP = 8.dp
private val MIN_TILE_WIDTH = 160.dp

// The visible card fills its whole grid slot (1.0 = no inset). Was tried at
// 0.85 for a bit of breathing room around each tile, but reverted back to
// full size on request.
private const val TILE_FILL_RATIO = 1f

// How much a tile's bottom-right-most row of text needs to stay clear of
// the card's trailing edge so it doesn't run under the corner DragHandle
// DraggableTile overlays on top of the card content. Used by both
// SensorTile and AlarmSensorTile.
internal val HANDLE_CLEARANCE = 26.dp

internal fun computeColumns(canvasWidthDp: Dp): Int =
    max(2, (canvasWidthDp / MIN_TILE_WIDTH).toInt())

internal fun computeTileSize(canvasWidthDp: Dp, columns: Int): Dp =
    (canvasWidthDp - TILE_GAP * (columns + 1)) / columns

// Converts a tile's dense integer slot ([SensorTilePosition.order], or an
// auto-assigned stand-in - see resolveOrders) into its top-left pixel
// position. The *only* place a tile's on-screen position comes from - there
// is no separate freeform x/y a drag can end up at between cells, so a tile
// is always exactly on a grid cell, never partway between two.
internal fun slotPosition(order: Int, columns: Int, tileSize: Dp): Pair<Dp, Dp> {
    val col = order % columns
    val row = order / columns
    val xDp = TILE_GAP + (tileSize + TILE_GAP) * col
    val yDp = TILE_GAP + (tileSize + TILE_GAP) * row
    return xDp to yDp
}

// Assigns every item a unique, dense order in 0 until items.size: trust a
// saved order only if it's in range AND not already claimed by an
// earlier item in `itemIds` (guards against stale/duplicate store entries -
// e.g. two rapid drags racing each other - permanently overlapping two
// tiles forever; the loser here just falls back to auto-assignment instead).
// Items with no usable saved order fill whatever slots are left, in their
// original list order, so a brand-new item lands in the next open slot
// without disturbing anyone else's saved position.
internal fun resolveOrders(itemIds: List<String>, saved: Map<String, Int>): Map<String, Int> {
    val n = itemIds.size
    val result = LinkedHashMap<String, Int>()
    val usedOrders = HashSet<Int>()
    for (id in itemIds) {
        val order = saved[id]
        if (order != null && order in 0 until n && usedOrders.add(order)) {
            result[id] = order
        }
    }
    var next = 0
    for (id in itemIds) {
        if (id !in result) {
            while (next in usedOrders) next++
            usedOrders.add(next)
            result[id] = next
        }
    }
    return result
}

/**
 * Generic dashing.io-style dashboard: items sit in a responsive grid (more
 * columns as the canvas gets wider) and can be dragged (via each tile's
 * corner [DragHandle]) onto another tile to swap places with it - see
 * [resolveOrders]/[slotPosition] for why every tile is always on some exact
 * grid cell, never a freeform in-between position, and [onSwap] for why a
 * drag always exchanges two tiles rather than relocating just one. A tap
 * anywhere else on a tile instead fires [onItemClick] - see
 * [DraggableTile]'s doc comment for why dragging lives on a handle rather
 * than the whole tile.
 *
 * Shared by the temp-sensor dashboard ([SensorDashboardCanvas]) and the
 * alarm-sensor one ([AlarmSensorDashboardCanvas]): the grid math, drag
 * gesture handling, and persisted-position shape ([SensorTilePosition]) are
 * identical for both, only what a tile looks like differs, via [tileContent].
 */
@Composable
fun <T> DashboardCanvas(
    items: List<T>,
    itemId: (T) -> String,
    positions: List<SensorTilePosition>,
    onSwap: (movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: (T) -> Unit = {},
    tileContent: @Composable (item: T, sizeDp: Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val canvasWidthDp = maxWidth
        val columns = computeColumns(canvasWidthDp)
        val tileSize = computeTileSize(canvasWidthDp, columns)

        val itemIds = items.map(itemId)
        val savedOrders = remember(positions) { positions.associate { it.sensorId to it.order } }
        val orders = remember(itemIds, savedOrders) { resolveOrders(itemIds, savedOrders) }

        val rows = if (columns > 0) (items.size + columns - 1) / columns else 0
        val contentHeightDp = max(
            maxHeight.value,
            TILE_GAP.value + (tileSize.value + TILE_GAP.value) * rows
        ).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeightDp)
            ) {
                items.forEach { item ->
                    val id = itemId(item)
                    val order = orders.getValue(id)
                    val (xDp, yDp) = slotPosition(order, columns, tileSize)
                    // Keyed so Compose tracks each tile by id across
                    // recompositions (not by loop position) - matters here
                    // because this is a plain forEach, not a LazyColumn
                    // items() call, so without an explicit key a reordering
                    // of `items` could otherwise reuse one tile's remembered
                    // drag state for a different item.
                    key(id) {
                        DraggableTile(
                            id = id,
                            slotSize = tileSize,
                            xDp = xDp,
                            yDp = yDp,
                            onMoved = { finalXDp, finalYDp ->
                                val cell = tileSize + TILE_GAP
                                val centerX = finalXDp + tileSize / 2
                                val centerY = finalYDp + tileSize / 2
                                val col = ((centerX - TILE_GAP) / cell).toInt().coerceIn(0, columns - 1)
                                val row = ((centerY - TILE_GAP) / cell).toInt().coerceAtLeast(0)
                                val targetOrder = (row * columns + col).coerceIn(0, items.size - 1)
                                if (targetOrder != order) {
                                    val displacedId = orders.entries.find { it.value == targetOrder }?.key
                                    if (displacedId != null && displacedId != id) {
                                        onSwap(id, targetOrder, displacedId, order)
                                    }
                                }
                            },
                            onClick = { onItemClick(item) }
                        ) {
                            tileContent(item, tileSize * TILE_FILL_RATIO)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One positioned, draggable grid slot. The tile itself only ever handles
 * [onClick] (tap anywhere opens detail); repositioning is only ever
 * triggered from the small [DragHandle] in the corner, via a plain (not
 * long-press-gated) [detectDragGestures].
 *
 * Two things drove putting drag on a dedicated handle instead of the whole
 * tile:
 *  - A long-press-then-drag detector needs the pointer to stay within touch
 *    slop for the whole press duration before a drag can start. That's
 *    fussy with mouse input (an emulator, a Chromebook, a debug build on a
 *    desktop) - the smallest cursor jitter while "holding still" cancels
 *    the long-press wait, so dragging can end up simply not triggering.
 *    Requiring the initial press to land on the handle removes the need for
 *    that wait entirely: drag starts as soon as slop is exceeded, no timer.
 *  - Without a dedicated handle, an immediate (non-long-press) drag detector
 *    covering the whole tile would compete with the dashboard's vertical
 *    scroll: the first tile under a scrolling swipe would claim the gesture
 *    the moment it exceeds touch slop, in any direction, breaking scroll.
 *    Scoping the drag detector to a small corner region means a scroll
 *    swipe starting anywhere else on the tile is untouched.
 *
 * Two more things make this feel like a live drag rather than a static
 * grid, both driven by [isDragging]:
 *  - The tile itself scales up slightly and gains a shadow while held
 *    (a "lift off the board" cue), and jumps to the front (`zIndex`) so it
 *    visibly floats over neighboring tiles instead of sliding underneath
 *    them - the loop below draws tiles in list order with no explicit
 *    z-index otherwise, so without this a tile dragged down/right would be
 *    covered by tiles drawn after it.
 *  - Position is a spring-driven [Animatable], not a value applied
 *    directly: while dragging, the live pointer delta ([dragOffsetPx]) is
 *    added on top with no spring lag, so holding and moving a tile tracks
 *    the finger 1:1; once released, [basePosition] eases toward the tile's
 *    real slot instead of teleporting there. The same [LaunchedEffect] also
 *    covers a tile that *wasn't* dragged but got displaced by someone
 *    else's drop - its (xDp, yDp) target changes too, so it glides out of
 *    the way rather than jumping.
 *
 * The drag itself reports the raw final drop position to [onMoved] exactly
 * once, in onDragEnd. Turning that into an actual grid cell (and swapping
 * with whoever's there) is [DashboardCanvas]'s job, not this composable's -
 * it needs the full set of other tiles' orders to find a swap partner,
 * which this one doesn't have.
 */
@Composable
private fun DraggableTile(
    id: String,
    slotSize: Dp,
    xDp: Dp,
    yDp: Dp,
    onMoved: (finalXDp: Dp, finalYDp: Dp) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var isDragging by remember(id) { mutableStateOf(false) }
    var dragOffsetPx by remember(id) { mutableStateOf(Offset.Zero) }

    // Keyed only on id, so this doesn't restart on every recomposition
    // (that's the point - restarting mid-gesture would cancel an
    // in-progress drag). But that means the gesture-detector coroutine,
    // once launched, would otherwise keep calling whatever onClick closure
    // was current the moment it started - permanently, even after the
    // underlying item refreshes on the next poll. rememberUpdatedState
    // keeps onTap calling into the latest onClick without needing to
    // restart the detector.
    val currentOnClick by rememberUpdatedState(onClick)

    val basePosition = remember(id) {
        Animatable(Offset(with(density) { xDp.toPx() }, with(density) { yDp.toPx() }), Offset.VectorConverter)
    }

    // Fires whenever this tile's resolved slot actually changes - whether
    // because it was just dropped there, or because some other drag
    // displaced it into a different slot. Folds the live drag offset into
    // basePosition first (a snapTo, so this is visually a no-op - it just
    // re-bases the coordinate system) so the spring animates from wherever
    // the tile visually is right now, not from a stale pre-drag position.
    LaunchedEffect(xDp, yDp) {
        basePosition.snapTo(basePosition.value + dragOffsetPx)
        dragOffsetPx = Offset.Zero
        basePosition.animateTo(
            Offset(with(density) { xDp.toPx() }, with(density) { yDp.toPx() }),
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "tileScale")
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "tileElevation")

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .offset {
                IntOffset(
                    x = (basePosition.value.x + dragOffsetPx.x).roundToInt(),
                    y = (basePosition.value.y + dragOffsetPx.y).roundToInt()
                )
            }
            .size(slotSize)
            .scale(scale)
            .shadow(elevation, shape = MaterialTheme.shapes.medium)
            .pointerInput(id) {
                detectTapGestures(onTap = { currentOnClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        content()

        DragHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .pointerInput(id) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            // Grabbing a tile that's still mid-glide from a
                            // recent swap should feel immediate, not wait
                            // for that animation to finish first.
                            scope.launch { basePosition.stop() }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount
                        },
                        onDragEnd = {
                            isDragging = false
                            val finalXDp = with(density) { (basePosition.value.x + dragOffsetPx.x).toDp() }
                            val finalYDp = with(density) { (basePosition.value.y + dragOffsetPx.y).toDp() }
                            scope.launch {
                                basePosition.snapTo(basePosition.value + dragOffsetPx)
                                dragOffsetPx = Offset.Zero
                                basePosition.animateTo(
                                    Offset(with(density) { xDp.toPx() }, with(density) { yDp.toPx() }),
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                            onMoved(finalXDp, finalYDp)
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                basePosition.snapTo(basePosition.value + dragOffsetPx)
                                dragOffsetPx = Offset.Zero
                                basePosition.animateTo(
                                    Offset(with(density) { xDp.toPx() }, with(density) { yDp.toPx() }),
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    )
                }
        )
    }
}

// A small grip glyph (2x3 dots, like a bottom-sheet/list-reorder handle)
// marking the drag target in a tile's corner - see DraggableTile's doc
// comment for why dragging is scoped to this instead of the whole tile.
// 32dp touch target (generous relative to the 12dp visible glyph) so it's
// easy to grab without needing to be pixel-precise, especially with a mouse.
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thin [DashboardCanvas] wrapper for temperature sensors - see
 * [AlarmSensorDashboardCanvas] for the alarm-sensor counterpart.
 */
@Composable
fun SensorDashboardCanvas(
    sensors: List<TempSensor>,
    positions: List<SensorTilePosition>,
    unit: TempUnit,
    onSwap: (movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) -> Unit,
    onTileClick: (TempSensor) -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCanvas(
        items = sensors,
        itemId = { it.id },
        positions = positions,
        onSwap = onSwap,
        onItemClick = onTileClick,
        modifier = modifier
    ) { sensor, sizeDp ->
        SensorTile(sensor = sensor, unit = unit, sizeDp = sizeDp)
    }
}

// Dashing.io's signature Number-widget effect: eases toward `target` from
// wherever it currently sits (0 on first composition, since Animatable
// starts there) rather than jumping straight to it. Restarting the
// animateTo on every `target` change (LaunchedEffect's key) is what makes
// each new reading glide in instead of only animating once on first load.
@Composable
private fun animatedCountUp(target: Float): Float {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(target, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    return animatable.value
}

/**
 * Compact square dashboard tile - the "dashing.io cube" version of the old
 * full-width [SensorCard]. Keeps the same status rules (heating-needed tint,
 * low-battery red, stale-reading warning), the same opportunistic PM2.5/VOC
 * row, and the same "Last updated: HH:mm:ss" line (orange when stale), all
 * just compacted to fit a square tile.
 */
@Composable
fun SensorTile(sensor: TempSensor, unit: TempUnit, sizeDp: Dp, modifier: Modifier = Modifier) {
    val isHeatingNeeded = sensor.currentTemp < sensor.setTemp
    val isBatteryLow = sensor.batteryLevel < 20
    val isStale = System.currentTimeMillis() - sensor.lastUpdated > 3600000 // 1 hour

    // Dashing.io-style count-up: the headline number animates up from 0 the
    // first time a tile appears, and glides from its old reading to its new
    // one on every poll after that, instead of snapping straight to the
    // number. Keyed on the raw (unit-independent) Celsius value, not the
    // already-converted display value, so toggling °C/°F just re-renders
    // the same animated number in the other unit instantly rather than
    // replaying a bogus "count" between two unrelated-looking numbers.
    val animatedCurrentTemp = animatedCountUp(sensor.currentTemp)

    Card(
        modifier = modifier.size(sizeDp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHeatingNeeded)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${"%.1f".format(animatedCurrentTemp.toUnit(unit))}${unit.symbol()}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isHeatingNeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Set ${"%.1f".format(sensor.setTemp.toUnit(unit))}${unit.symbol()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // End-padded (rather than plain fillMaxWidth) on whichever row
            // ends up bottom-most, so its trailing text doesn't run under
            // the corner DragHandle DraggableTile overlays on top of this
            // card - see DraggableTile's doc comment for why the handle
            // lives there. Both this row and the PM2.5/VOC row below get
            // the same inset since either can be the actual last row,
            // depending on whether pm25/vocIndex are present.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = HANDLE_CLEARANCE),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Batt ${sensor.batteryLevel}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBatteryLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Hum ${sensor.humidity.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // pm25/vocIndex are opportunistic (only an air-quality-capable
            // sensor, e.g. an IKEA VINDSTYRKA, reports them - see
            // TempSensor's doc comment) - same conditional row SensorCard
            // used to show, just compacted to fit the square tile.
            if (sensor.pm25 > 0f || sensor.vocIndex > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = HANDLE_CLEARANCE),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (sensor.pm25 > 0f) "PM2.5 ${sensor.pm25.toInt()}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (sensor.vocIndex > 0f) "VOC ${sensor.vocIndex.toInt()}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Restored per request - was in the original full-width
            // SensorCard, dropped to a plain stale-dot when this was first
            // compacted into a square tile. Orange (not just an icon) is
            // what actually says "stale" here, same as before.
            Text(
                text = tileTimeFormatter.format(java.util.Date(sensor.lastUpdated)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isStale) Color(0xFFF57C00) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = HANDLE_CLEARANCE),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}
