package com.tgaetaprojects.settledown.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
 * spots in [targets] highlighted. A tap near a highlighted spot calls [onVertex], [onEdge] or [onHex].
 */
@Composable
fun BoardCanvas(
    view: PlayerView,
    targets: BoardTargets,
    highlight: Color,
    darkSea: Boolean,
    onVertex: (Vertex) -> Unit,
    onEdge: (Edge) -> Unit,
    onHex: (Hex) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .testTag("board")
            .semantics { contentDescription = "Game board" }
            .pointerInput(targets) {
                detectTapGestures { tap ->
                    val at = BoardTransform(size.toSize()).toBoard(tap)
                    val vertex = targets.vertices.nearest(at, VERTEX_REACH) { BoardGeometry.position(it) }
                    val edge = targets.edges.nearest(at, EDGE_REACH) { BoardGeometry.midpoint(it) }
                    val hex = targets.hexes.nearest(at, HEX_REACH) { BoardGeometry.center(it) }
                    when {
                        vertex != null -> onVertex(vertex)
                        edge != null -> onEdge(edge)
                        hex != null -> onHex(hex)
                    }
                }
            }
    ) {
        val t = BoardTransform(size)
        drawSea(t, if (darkSea) GameColors.seaDark else GameColors.sea)
        for (hex in Hex.entries) drawTile(t, hex, view.layout.terrain.getValue(hex))
        for ((edge, port) in view.layout.ports) drawHarbor(t, measurer, edge, port)
        for (hex in Hex.entries) {
            val total = view.layout.numbers.getValue(hex).value
            if (total != 7) drawToken(t, measurer, BoardGeometry.center(hex), total)
        }
        drawRobber(t, view.robber, hasToken = view.layout.terrain.getValue(view.robber) != Terrain.DESERT)
        for ((edge, color) in view.roads) drawRoad(t, edge, color)
        for ((vertex, building) in view.buildings) drawBuilding(t, vertex, building)
        drawTargets(t, targets, highlight)
    }
}

private const val VERTEX_REACH = 0.4f
private const val EDGE_REACH = 0.4f
private const val HEX_REACH = 0.85f

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

private fun DrawScope.drawTile(t: BoardTransform, hex: Hex, terrain: Terrain) {
    val path = polygon(t, BoardGeometry.corners(hex))
    drawPath(path, GameColors.terrain(terrain))
    drawPath(path, GameColors.tileBorder, style = Stroke(0.07f * t.scale, join = StrokeJoin.Round))
}

private fun DrawScope.drawToken(t: BoardTransform, measurer: TextMeasurer, center: Offset, total: Int) {
    val c = t.toScreen(center)
    val radius = 0.36f * t.scale
    drawCircle(Color.Black.copy(alpha = 0.2f), radius, c + Offset(0f, 0.03f * t.scale))
    drawCircle(GameColors.token, radius, c)
    val hot = total == 6 || total == 8
    val ink = if (hot) GameColors.tokenHot else GameColors.tokenText
    drawLabel(
        measurer, total.toString(), c - Offset(0f, 0.06f * t.scale),
        TextStyle(color = ink, fontSize = (0.34f * t.scale).toSp(), fontWeight = FontWeight.Bold),
    )
    val pips = pipCount(total)
    val pip = 0.035f * t.scale
    val gap = 0.09f * t.scale
    val y = c.y + 0.2f * t.scale
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

private fun DrawScope.drawHarbor(t: BoardTransform, measurer: TextMeasurer, edge: Edge, port: PortType) {
    val (a, b) = BoardGeometry.ends(edge)
    val at = BoardGeometry.midpoint(edge) + BoardGeometry.outward(edge) * 0.62f
    val pier = Stroke(0.07f * t.scale, cap = StrokeCap.Round)
    for (end in listOf(a, b)) {
        drawLine(GameColors.pier, t.toScreen(end), t.toScreen(at), pier.width, pier.cap)
    }
    val resource = port.accepted.singleOrNull().takeIf { port != PortType.GENERIC }
    val fill = resource?.let(GameColors::resource) ?: GameColors.token
    val ink = resource?.let(GameColors::onResource) ?: GameColors.tokenText
    val c = t.toScreen(at)
    drawCircle(fill, 0.3f * t.scale, c)
    drawCircle(GameColors.pier, 0.3f * t.scale, c, style = Stroke(0.04f * t.scale))
    drawLabel(
        measurer, port.label, c,
        TextStyle(color = ink, fontSize = (0.2f * t.scale).toSp(), fontWeight = FontWeight.Bold),
    )
}

private fun DrawScope.drawRobber(t: BoardTransform, hex: Hex, hasToken: Boolean) {
    drawPath(polygon(t, BoardGeometry.corners(hex)), Color.Black.copy(alpha = 0.28f))
    val base = t.toScreen(BoardGeometry.center(hex) + if (hasToken) Offset(-0.58f, 0.05f) else Offset.Zero)
    val s = t.scale
    val body = Path().apply {
        moveTo(base.x - 0.15f * s, base.y + 0.25f * s)
        lineTo(base.x - 0.09f * s, base.y - 0.1f * s)
        lineTo(base.x + 0.09f * s, base.y - 0.1f * s)
        lineTo(base.x + 0.15f * s, base.y + 0.25f * s)
        close()
    }
    drawPath(body, GameColors.robber)
    drawCircle(GameColors.robber, 0.12f * s, Offset(base.x, base.y - 0.2f * s))
    drawPath(body, Color.White.copy(alpha = 0.5f), style = Stroke(0.02f * s))
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

private fun DrawScope.drawTargets(t: BoardTransform, targets: BoardTargets, highlight: Color) {
    val s = t.scale
    for (hex in targets.hexes) {
        val c = t.toScreen(BoardGeometry.center(hex))
        drawCircle(Color.White.copy(alpha = 0.85f), 0.5f * s, c, style = Stroke(0.1f * s))
        drawCircle(highlight, 0.5f * s, c, style = Stroke(0.05f * s))
    }
    for (edge in targets.edges) {
        val (a, b) = BoardGeometry.ends(edge)
        val from = t.toScreen(a + (b - a) * 0.22f)
        val to = t.toScreen(b + (a - b) * 0.22f)
        drawLine(Color.White.copy(alpha = 0.85f), from, to, 0.16f * s, StrokeCap.Round)
        drawLine(highlight, from, to, 0.08f * s, StrokeCap.Round)
    }
    for (vertex in targets.vertices) {
        val c = t.toScreen(BoardGeometry.position(vertex))
        drawCircle(Color.White.copy(alpha = 0.9f), 0.15f * s, c)
        drawCircle(highlight, 0.15f * s, c, style = Stroke(0.05f * s))
    }
}
