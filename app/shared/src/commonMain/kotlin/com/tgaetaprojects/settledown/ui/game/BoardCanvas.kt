package com.tgaetaprojects.settledown.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.tgaetaprojects.settledown.game.Building
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PortType
import com.tgaetaprojects.settledown.game.Terrain
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.ui.theme.GameColors

/** Maps board units (see [BoardGeometry]) to pixels: centered, as large as fits. */
private class BoardTransform(size: Size) {
    val scale: Float = minOf(
        size.width / (2 * BoardGeometry.HALF_WIDTH),
        size.height / (2 * BoardGeometry.HALF_HEIGHT),
    )
    private val origin = Offset(size.width / 2, size.height / 2)

    fun toScreen(unit: Offset): Offset = origin + unit * scale
    fun toBoard(screen: Offset): Offset = (screen - origin) / scale
}

/**
 * The island drawn with Compose's [Canvas]: tiles, number discs, harbors, the robber and every piece, with the
 * spots in [targets] highlighted by a pulsing ring on a layer of their own. A tap near a highlighted spot calls [onVertex], [onEdge] or
 * [onHex].
 *
 * Sizes scale with the board, but text and tap areas have minimums so small phones stay readable and tappable.
 */
@Composable
fun BoardCanvas(
    view: PlayerView,
    targets: BoardTargets,
    dark: Boolean,
    onVertex: (Vertex) -> Unit,
    onEdge: (Edge) -> Unit,
    onHex: (Hex) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ~18 distinct labels are drawn; the default cache of 8 would lay them all out again on every draw
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val tapVertex by rememberUpdatedState(onVertex)
    val tapEdge by rememberUpdatedState(onEdge)
    val tapHex by rememberUpdatedState(onHex)
    Box(
        modifier
            .testTag("board")
            .semantics { contentDescription = "Game board" }
            .pointerInput(targets) {
                detectTapGestures { tap ->
                    val t = BoardTransform(size.toSize())
                    val at = t.toBoard(tap)
                    val reach = maxOf(SPOT_REACH, MIN_TAP_RADIUS.toPx() / t.scale)
                    val vertex = targets.vertices.nearest(at, reach) { BoardGeometry.position(it) }
                    val edge = targets.edges.nearest(at, reach) { BoardGeometry.midpoint(it) }
                    val hex = targets.hexes.nearest(at, maxOf(HEX_REACH, reach)) { BoardGeometry.center(it) }
                    when {
                        vertex != null -> tapVertex(vertex)
                        edge != null -> tapEdge(edge)
                        hex != null -> tapHex(hex)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = BoardTransform(size)
            drawSea(t, if (dark) GameColors.seaDark else GameColors.sea)
            for (hex in Hex.entries) drawTile(t, hex, view.layout.terrain.getValue(hex), dark)
            for ((edge, port) in view.layout.ports) drawHarbor(t, measurer, edge, port)
            for (hex in Hex.entries) {
                val total = view.layout.numbers.getValue(hex).value
                if (total != 7) drawToken(t, measurer, BoardGeometry.center(hex), total)
            }
            drawRobber(t, view.robber, hasToken = view.layout.terrain.getValue(view.robber) != Terrain.DESERT)
            for ((edge, color) in view.roads) drawRoad(t, edge, color)
            for ((vertex, building) in view.buildings) drawBuilding(t, vertex, building)
        }
        // Highlights get their own layer, so the pulse only redraws them and not the whole board.
        if (!targets.isEmpty) {
            val pulse = pulse()
            Canvas(Modifier.fillMaxSize()) { drawTargets(BoardTransform(size), targets, pulse.value) }
        }
    }
}

/** A 0.5–1 alpha that breathes, so highlighted spots catch the eye. */
@Composable
private fun pulse(): State<Float> = rememberInfiniteTransition().animateFloat(
    initialValue = 0.5f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
)

/** How close, in hex radii, a tap must land to a vertex or edge target. */
private const val SPOT_REACH = 0.4f
private const val HEX_REACH = 0.85f

/** The smallest tap radius on screen, whatever the board's size: a 48dp target. */
private val MIN_TAP_RADIUS = 24.dp

// Minimums in dp, not sp: these labels are part of the drawing, which must keep its proportions at any font scale.
private val TOKEN_MIN_TEXT = 11.dp
private val HARBOR_MIN_TEXT = 8.dp

private inline fun <T> Set<T>.nearest(at: Offset, reach: Float, position: (T) -> Offset): T? =
    map { it to (position(it) - at).getDistance() }.filter { it.second < reach }.minByOrNull { it.second }?.first

private fun DrawScope.polygon(t: BoardTransform, corners: List<Offset>): Path = Path().apply {
    corners.forEachIndexed { i, c ->
        val p = t.toScreen(c)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.drawSea(t: BoardTransform, color: Color) {
    val path = polygon(t, BoardGeometry.seaCorners)
    drawPath(path, color)
    drawPath(path, Color.White.copy(alpha = 0.25f), style = Stroke(0.08f * t.scale, join = StrokeJoin.Round))
}

private fun DrawScope.drawTile(t: BoardTransform, hex: Hex, terrain: Terrain, dark: Boolean) {
    val path = polygon(t, BoardGeometry.corners(hex))
    val fill = GameColors.terrain(terrain)
    // a little dimmer in dark mode, so the tiles don't glare against the night sea
    drawPath(path, if (dark) lerp(fill, Color.Black, 0.12f) else fill)
    drawPath(path, GameColors.tileBorder, style = Stroke(0.07f * t.scale, join = StrokeJoin.Round))
}

/** [fraction] of a hex radius in pixels, but never less than [min]. */
private fun atLeast(t: BoardTransform, fraction: Float, min: Float): Float = maxOf(fraction * t.scale, min)

private fun DrawScope.drawToken(t: BoardTransform, measurer: TextMeasurer, center: Offset, total: Int) {
    val c = t.toScreen(center)
    val fontPx = atLeast(t, 0.34f, TOKEN_MIN_TEXT.toPx())
    val radius = maxOf(0.36f * t.scale, fontPx * 1.05f)
    drawCircle(Color.Black.copy(alpha = 0.2f), radius, c + Offset(0f, radius * 0.08f))
    drawCircle(GameColors.token, radius, c)
    val hot = total == 6 || total == 8
    val ink = if (hot) GameColors.tokenHot else GameColors.tokenText
    drawLabel(
        measurer, total.toString(), c - Offset(0f, radius * 0.17f),
        TextStyle(color = ink, fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold),
    )
    val pips = pipCount(total)
    val pip = maxOf(radius * 0.1f, 1.2.dp.toPx())
    val gap = pip * 2.6f
    val y = c.y + radius * 0.55f
    for (i in 0 until pips) {
        drawCircle(ink, pip, Offset(c.x + (i - (pips - 1) / 2f) * gap, y))
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
    )
}

/**
 * A harbor: a disc out at sea joined by piers to its two coastal corners. 2:1 harbors name their resource by
 * initial as well as color.
 */
private fun DrawScope.drawHarbor(t: BoardTransform, measurer: TextMeasurer, edge: Edge, port: PortType) {
    val fontPx = atLeast(t, 0.2f, HARBOR_MIN_TEXT.toPx())
    val radius = maxOf(0.33f * t.scale, fontPx * 1.75f)
    val offset = maxOf(0.62f, radius / t.scale + 0.2f)
    val (a, b) = BoardGeometry.ends(edge)
    val at = BoardGeometry.midpoint(edge) + BoardGeometry.outward(edge) * offset
    val c = t.toScreen(at)
    for (end in listOf(a, b)) {
        drawLine(GameColors.pier, t.toScreen(end), c, 0.07f * t.scale, StrokeCap.Round)
    }
    val resource = port.accepted.singleOrNull().takeIf { port != PortType.GENERIC }
    val fill = resource?.let(GameColors::resource) ?: GameColors.token
    val ink = resource?.let(GameColors::onResource) ?: GameColors.tokenText
    // a light rim keeps dark discs (lumber, ore) visible on the dark sea
    drawCircle(GameColors.token, radius + 1.5.dp.toPx(), c)
    drawCircle(fill, radius, c)
    drawCircle(GameColors.pier, radius, c, style = Stroke(1.dp.toPx()))
    if (resource == null) {
        drawLabel(
            measurer, port.label, c,
            TextStyle(color = ink, fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold),
        )
    } else {
        drawLabel(
            measurer, resource.initial, c - Offset(0f, radius * 0.3f),
            TextStyle(color = ink, fontSize = (fontPx * 1.15f).toSp(), fontWeight = FontWeight.Black),
        )
        drawLabel(
            measurer, port.label, c + Offset(0f, radius * 0.4f),
            TextStyle(color = ink, fontSize = (fontPx * 0.85f).toSp(), fontWeight = FontWeight.Bold),
        )
    }
}

private fun DrawScope.drawRobber(t: BoardTransform, hex: Hex, hasToken: Boolean) {
    drawPath(polygon(t, BoardGeometry.corners(hex)), Color.Black.copy(alpha = 0.28f))
    val base = t.toScreen(BoardGeometry.center(hex) + if (hasToken) Offset(-0.6f, 0.05f) else Offset.Zero)
    val s = t.scale
    drawOval(
        Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(base.x - 0.18f * s, base.y + 0.2f * s),
        size = Size(0.36f * s, 0.1f * s),
    )
    val body = Path().apply {
        moveTo(base.x - 0.15f * s, base.y + 0.25f * s)
        lineTo(base.x - 0.09f * s, base.y - 0.1f * s)
        lineTo(base.x + 0.09f * s, base.y - 0.1f * s)
        lineTo(base.x + 0.15f * s, base.y + 0.25f * s)
        close()
    }
    // a light halo keeps the dark pawn visible on dark tiles such as forest
    val halo = Stroke(0.06f * s, join = StrokeJoin.Round)
    drawPath(body, GameColors.token.copy(alpha = 0.85f), style = halo)
    drawCircle(GameColors.token.copy(alpha = 0.85f), 0.12f * s, Offset(base.x, base.y - 0.2f * s), style = halo)
    drawPath(body, GameColors.robber)
    drawCircle(GameColors.robber, 0.12f * s, Offset(base.x, base.y - 0.2f * s))
}

private fun DrawScope.drawRoad(t: BoardTransform, edge: Edge, color: PlayerColor) {
    val (a, b) = BoardGeometry.ends(edge)
    val from = t.toScreen(a + (b - a) * 0.16f)
    val to = t.toScreen(b + (a - b) * 0.16f)
    drawLine(GameColors.pieceOutline, from, to, 0.17f * t.scale, StrokeCap.Round)
    drawLine(GameColors.player(color), from, to, 0.11f * t.scale, StrokeCap.Round)
}

private val SETTLEMENT_SHAPE = listOf(
    Offset(-1f, 0.6f), Offset(-1f, -0.2f), Offset(0f, -1f), Offset(1f, -0.2f), Offset(1f, 0.6f),
)
private val CITY_SHAPE = listOf(
    Offset(-1.2f, 0.7f), Offset(-1.2f, -0.4f), Offset(-0.6f, -1f), Offset(0f, -0.4f),
    Offset(0f, -0.1f), Offset(1.2f, -0.1f), Offset(1.2f, 0.7f),
)

private fun DrawScope.drawBuilding(t: BoardTransform, vertex: Vertex, building: Building) {
    val at = BoardGeometry.position(vertex)
    val size = if (building.isCity) 0.22f else 0.2f
    val shape = if (building.isCity) CITY_SHAPE else SETTLEMENT_SHAPE
    val path = polygon(t, shape.map { at + it * size })
    drawPath(path, GameColors.player(building.color))
    drawPath(path, GameColors.pieceOutline, style = Stroke(0.045f * t.scale, join = StrokeJoin.Round))
}

/**
 * Highlighted spots in amber with a dark rim and a white halo, a color no player uses, so a candidate road never
 * looks like someone's road. [pulse] fades the halo in and out.
 */
private fun DrawScope.drawTargets(t: BoardTransform, targets: BoardTargets, pulse: Float) {
    val s = t.scale
    val halo = Color.White.copy(alpha = 0.9f * pulse)
    for (hex in targets.hexes) {
        val c = t.toScreen(BoardGeometry.center(hex))
        drawCircle(halo, 0.52f * s, c, style = Stroke(0.14f * s))
        drawCircle(GameColors.highlightRim, 0.52f * s, c, style = Stroke(0.08f * s))
        drawCircle(GameColors.highlight, 0.52f * s, c, style = Stroke(0.05f * s))
    }
    for (edge in targets.edges) {
        val (a, b) = BoardGeometry.ends(edge)
        val from = t.toScreen(a + (b - a) * 0.22f)
        val to = t.toScreen(b + (a - b) * 0.22f)
        drawLine(halo, from, to, maxOf(0.22f * s, 7.dp.toPx()), StrokeCap.Round)
        drawLine(GameColors.highlightRim, from, to, maxOf(0.12f * s, 4.dp.toPx()), StrokeCap.Round)
        drawLine(GameColors.highlight, from, to, maxOf(0.08f * s, 2.5.dp.toPx()), StrokeCap.Round)
    }
    for (vertex in targets.vertices) {
        val c = t.toScreen(BoardGeometry.position(vertex))
        val r = maxOf(0.15f * s, 6.dp.toPx())
        drawCircle(halo, r * 1.6f, c)
        drawCircle(GameColors.highlightRim, r, c)
        drawCircle(GameColors.highlight, r * 0.75f, c)
    }
}
