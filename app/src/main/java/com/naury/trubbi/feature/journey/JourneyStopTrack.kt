package com.naury.trubbi.feature.journey

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.domain.model.StopKind
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.ui.designsystem.AppIcons
import com.naury.trubbi.ui.designsystem.pressable
import com.naury.trubbi.ui.theme.LocalTrubbiColors

/**
 * 정차지 진행 표시 — 가로 시퀀스와 세로 스테퍼 두 형태.
 *
 * 정차지가 적으면 한눈에 보이는 가로형을, 많으면 접을 수 있는 세로형을 쓴다.
 */
/**
 * 정차지 시퀀스 — 뱀(boustrophedon) 레이아웃: 한 줄에 들어갈 만큼 채우고 줄이 바뀌면 방향을 뒤집는다
 * (1줄 좌→우, 2줄 우→좌, 3줄 좌→우…). 같은 줄 사이는 직선, 윗줄 끝↔아랫줄 첫 노드는 곡선으로 잇는다.
 * 현재 이동 구간(movingSegment)에는 점선 흐름 + 트럭(이동중).
 */
@Composable
fun JourneySequence(
    stops: List<JourneyStopEntity>,
    currentIndex: Int,
    movingSegment: Int,
) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val separator = c.separator
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val density = LocalDensity.current
    val nodeW = with(density) { 64.dp.toPx() }
    val nodeH = with(density) { 82.dp.toPx() }
    val circleCY = with(density) { 22.dp.toPx() }
    val gap = with(density) { 34.dp.toPx() }
    val rowGap = with(density) { 30.dp.toPx() }
    val strokePx = with(density) { 3.dp.toPx() }
    val flow = rememberInfiniteTransition(label = "seqFlow")
    val dashPhase by flow.animateFloat(
        0f,
        20f,
        infiniteRepeatable(tween(700, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "seqDash",
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val items = stops.size + 1 // 마지막 = 여정 완료(깃발) 노드
        if (items == 0) return@BoxWithConstraints
        val maxW = constraints.maxWidth.toFloat().takeIf { it > 0f } ?: nodeW
        val reserve = with(density) { 44.dp.toPx() } // 곡선이 바깥으로 부풀 여유(양옆)
        val usableW = (maxW - 2 * reserve).coerceAtLeast(nodeW)
        val perRow = maxOf(1, kotlin.math.floor((usableW + gap) / (nodeW + gap)).toInt())
        val rows = kotlin.math.ceil(items / perRow.toFloat()).toInt().coerceAtLeast(1)
        val stride = nodeH + rowGap
        val totalH = nodeH + (rows - 1) * stride
        val nodeR = with(density) { 22.dp.toPx() } // 노드 원 반지름 — 선이 원 가장자리에 딱 닿게(틈 없이)
        val bulge = with(density) { 42.dp.toPx() } // 줄바꿈 U턴이 옆으로 부푸는 정도

        fun rowOf(i: Int) = i / perRow
        fun itemsInRow(r: Int) = if (r < rows - 1) perRow else items - r * perRow
        val blockW = perRow * nodeW + (perRow - 1) * gap
        val blockX = (maxW - blockW) / 2f // 전체 그리드를 가운데로
        fun cx(i: Int): Float {
            val r = rowOf(i)
            val col = i % perRow
            val n = itemsInRow(r)
            // 짝수줄 좌→우(왼쪽 정렬), 홀수줄 우→좌(오른쪽 정렬). 부분 줄도 연결되는 쪽으로 붙여
            // 윗줄 끝과 아랫줄 첫 노드의 x가 일치 → 곡선이 반듯한 ( ) 모양이 된다.
            val colInBlock = if (r % 2 == 0) col else (perRow - n) + (n - 1 - col)
            return blockX + colInBlock * (nodeW + gap) + nodeW / 2f
        }
        fun cy(i: Int): Float = rowOf(i) * stride + circleCY

        Box(Modifier.fillMaxWidth().height(with(density) { totalH.toDp() })) {
            // 커넥터 — 같은 줄은 직선(노드 가장자리까지만), 줄 바뀜은 바깥으로 부푸는 곡선.
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                for (i in 0 until items - 1) {
                    val active = i == movingSegment
                    val col = if (i < currentIndex || active) primary else separator
                    val effect = if (active) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), dashPhase) else null
                    val ax = cx(i)
                    val ay = cy(i)
                    val bx = cx(i + 1)
                    val by = cy(i + 1)
                    if (rowOf(i) == rowOf(i + 1)) {
                        // 같은 줄 — 두 원 가장자리(반지름)에 딱 맞닿는 직선(틈 없이).
                        val dir = if (bx >= ax) 1f else -1f
                        drawLine(
                            col,
                            androidx.compose.ui.geometry.Offset(ax + nodeR * dir, ay),
                            androidx.compose.ui.geometry.Offset(bx - nodeR * dir, by),
                            strokeWidth = strokePx,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            pathEffect = effect,
                        )
                    } else {
                        // 줄바꿈 — 같은 쪽 가장자리끼리 바깥으로 둥글게 도는 U턴(반원에 가까운 부드러운 곡선).
                        val side = if (ax > maxW / 2f) 1f else -1f
                        val sx = ax + nodeR * side
                        val sy = ay
                        val ex = bx + nodeR * side
                        val ey = by
                        // 제어점을 시작/끝 높이에 두면(평평한 S 대신) 둥근 반원형 고리가 된다.
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sx, sy)
                            cubicTo(sx + bulge * side, sy, ex + bulge * side, ey, ex, ey)
                        }
                        drawPath(
                            path,
                            col,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokePx,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                pathEffect = effect,
                            ),
                        )
                    }
                }
            }
            // 노드들 (계산된 위치에 배치)
            stops.forEachIndexed { i, stop ->
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((cx(i) - nodeW / 2f).toInt(), (rowOf(i) * stride).toInt()) }
                        .width(with(density) { nodeW.toDp() }),
                ) {
                    SeqNode(stop = stop, isCurrent = i == currentIndex, done = stop.completedAt != null)
                }
            }
            // 마지막 — 여정 완료(깃발). 모든 정차지를 마치면 current가 되어 강조된다.
            run {
                val i = stops.size
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((cx(i) - nodeW / 2f).toInt(), (rowOf(i) * stride).toInt()) }
                        .width(with(density) { nodeW.toDp() }),
                ) { SeqEndNode(isCurrent = currentIndex >= stops.size) }
            }
            // 이동중 트럭 — 활성 구간 중앙(곡선 구간이면 바깥쪽으로 살짝).
            if (movingSegment in 0 until items - 1) {
                val seg = movingSegment
                val sameRow = rowOf(seg) == rowOf(seg + 1)
                val side = if (cx(seg) > maxW / 2f) 1f else -1f
                val mx = (cx(seg) + cx(seg + 1)) / 2f + (if (sameRow) 0f else (nodeR + bulge * 0.62f) * side)
                val my = (cy(seg) + cy(seg + 1)) / 2f
                val half = with(density) { 11.dp.toPx() }
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((mx - half).toInt(), (my - half).toInt()) }
                        .size(22.dp).clip(RoundedCornerShape(percent = 50)).background(primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Truck, contentDescription = stringResource(StopPhase.MOVING.labelRes), tint = onPrimary, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun SeqNode(stop: JourneyStopEntity, isCurrent: Boolean, done: Boolean) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val halo = rememberInfiniteTransition(label = "seqHalo")
    val haloT by halo.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "seqHaloT")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 70.dp)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Box(
                    Modifier.size(44.dp)
                        .graphicsLayer {
                            val s = 1f + haloT * 0.6f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - haloT) * 0.5f
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .border(2.dp, primary, RoundedCornerShape(percent = 50)),
                )
            }
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        when {
                            done -> primary
                            isCurrent -> primary.copy(alpha = if (c.isDark) 0.30f else 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .then(if (isCurrent) Modifier.border(2.dp, primary, RoundedCornerShape(percent = 50)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // 출발지(ORIGIN) — 항상 출발점 아이콘으로 표시(완료 체크 대신).
                    stop.kindEnum == StopKind.ORIGIN -> Icon(
                        AppIcons.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                    done -> Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    isCurrent -> Icon(AppIcons.Truck, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    else -> Icon(
                        if (stop.kindEnum == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff,
                        contentDescription = null,
                        tint = c.secondaryLabel,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stop.kindEnum.label(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (done || isCurrent) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Text(
            stop.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = c.secondaryLabel,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

/** 시퀀스 맨 끝의 여정 완료 노드. 모든 정차지를 마치면 강조된다. */
@Composable
private fun SeqEndNode(isCurrent: Boolean) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val halo = rememberInfiniteTransition(label = "endHalo")
    val haloT by halo.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "endHaloT")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 70.dp)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Box(
                    Modifier.size(44.dp)
                        .graphicsLayer {
                            val s = 1f + haloT * 0.6f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - haloT) * 0.5f
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .border(2.dp, primary, RoundedCornerShape(percent = 50)),
                )
            }
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isCurrent) primary.copy(alpha = if (c.isDark) 0.30f else 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (isCurrent) Modifier.border(2.dp, primary, RoundedCornerShape(percent = 50)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Flag,
                    contentDescription = null,
                    tint = if (isCurrent) primary else c.secondaryLabel,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(StopPhase.DONE.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
        )
    }
}

// ── 단계 표시

/** 가로로 넘겨 보는 정차지 단계 표시. 지금 있는 자리에 트럭이 서 있다. */
@Composable
fun JourneyStepper(
    stops: List<JourneyStopEntity>,
    onAdd: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val currentIndex = stops.indexOfFirst { it.completedAt == null }.let { if (it < 0) stops.size else it }
    // 현재 단계가 화면 밖으로 밀려나지 않게 따라 스크롤한다.
    LaunchedEffect(currentIndex, stops.size) {
        runCatching { listState.animateScrollToItem(currentIndex.coerceAtMost((stops.size - 1).coerceAtLeast(0))) }
    }
    LazyRow(state = listState, verticalAlignment = Alignment.Top) {
        itemsIndexed(stops, key = { _, s -> s.stopId }) { i, stop ->
            Row(verticalAlignment = Alignment.Top) {
                StepNode(stop = stop, isCurrent = i == currentIndex)
                if (i < stops.size - 1 || onAdd != null) {
                    StepConnector(done = i < currentIndex)
                }
            }
        }
        if (onAdd != null) {
            item(key = "add") { AddStopNode(onAdd) }
        }
    }
}

@Composable
private fun StepNode(stop: JourneyStopEntity, isCurrent: Boolean) {
    val c = LocalTrubbiColors.current
    val primary = MaterialTheme.colorScheme.primary
    val done = stop.completedAt != null
    val pulse by animateFloatAsState(if (isCurrent) 1f else 0f, label = "stepPulse")
    // 현재 단계 강조용 후광(halo) — 바깥으로 퍼지며 사라지는 링.
    val halo = rememberInfiniteTransition(label = "stepHalo")
    val haloT by halo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "stepHaloT",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 76.dp)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Box(
                    Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            val s = 1f + haloT * 0.7f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - haloT) * 0.5f
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .border(2.dp, primary, RoundedCornerShape(percent = 50)),
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.08f * pulse
                        scaleY = 1f + 0.08f * pulse
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        when {
                            done -> primary
                            isCurrent -> primary.copy(alpha = if (c.isDark) 0.30f else 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .then(
                        if (isCurrent) Modifier.border(2.dp, primary, RoundedCornerShape(percent = 50)) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    done -> Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
                    isCurrent -> {
                        // 현재 노드의 트럭만 좌우로 미세하게 흔들어 '달리는 중'을 표시한다.
                        val drive = rememberInfiniteTransition(label = "stepTruck")
                        val bob by drive.animateFloat(
                            initialValue = 0f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = keyframes {
                                    durationMillis = 1800
                                    0f at 0
                                    -1.8f at 450 using FastOutSlowInEasing
                                    0f at 900 using FastOutSlowInEasing
                                    1.8f at 1350 using FastOutSlowInEasing
                                    0f at 1800
                                },
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "stepTruckBob",
                        )
                        Icon(
                            AppIcons.Truck,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier
                                .size(17.dp)
                                .graphicsLayer {
                                    translationX = bob * 2f
                                    translationY = bob * 0.6f
                                },
                        )
                    }
                    else -> Icon(
                        if (stop.kindEnum == StopKind.PICKUP) AppIcons.Pickup else AppIcons.Dropoff,
                        contentDescription = null,
                        tint = c.secondaryLabel,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            stop.kindEnum.label(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (done || isCurrent) MaterialTheme.colorScheme.onSurface else c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Text(
            stop.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = c.secondaryLabel,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun StepConnector(done: Boolean) {
    val c = LocalTrubbiColors.current
    Box(
        Modifier
            .padding(top = 17.dp)
            .width(18.dp)
            .height(2.dp)
            .background(if (done) MaterialTheme.colorScheme.primary else c.separator),
    )
}

@Composable
private fun AddStopNode(onClick: () -> Unit) {
    val c = LocalTrubbiColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(1.5.dp, c.separator, RoundedCornerShape(percent = 50))
                .pressable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Add, contentDescription = stringResource(R.string.action_add_stop), tint = c.secondaryLabel, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(stringResource(R.string.action_add), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
    }
}

// ── 여정 시작 카드와 시트
