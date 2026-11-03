package com.tgaetaprojects.settledown.ui.theme

import androidx.compose.ui.graphics.Color
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Terrain

/** Colors for game pieces, which stay the same in light and dark themes so the board always reads the same. */
object GameColors {
    val sea = Color(0xFF3A8DB5)
    val seaDark = Color(0xFF1D4E66)
    val tileBorder = Color(0xFFF2E6C9)
    val token = Color(0xFFFFF6E0)
    val tokenText = Color(0xFF2B2B2B)
    val tokenHot = Color(0xFFC62828)
    val pier = Color(0xFF8D6E4F)
    val robber = Color(0xFF2E2E33)
    val pieceOutline = Color(0xFF1F1F1F)

    fun terrain(terrain: Terrain): Color = when (terrain) {
        Terrain.HILL -> Color(0xFFC8693F)
        Terrain.FOREST -> Color(0xFF3C7D3E)
        Terrain.MOUNTAIN -> Color(0xFF8A96A3)
        Terrain.FIELDS -> Color(0xFFEBC55A)
        Terrain.PASTURE -> Color(0xFFA5D46A)
        Terrain.DESERT -> Color(0xFFE6D3A3)
    }

    fun resource(resource: Resource): Color = when (resource) {
        Resource.BRICK -> Color(0xFFB5522B)
        Resource.LUMBER -> Color(0xFF2E6B34)
        Resource.ORE -> Color(0xFF6E7B8B)
        Resource.GRAIN -> Color(0xFFE3B53C)
        Resource.WOOL -> Color(0xFF8BBF55)
    }

    /** Text drawn on a [resource]-colored background. */
    fun onResource(resource: Resource): Color = when (resource) {
        Resource.GRAIN, Resource.WOOL -> Color(0xFF1F1F1F)
        else -> Color.White
    }

    fun player(color: PlayerColor): Color = when (color) {
        PlayerColor.RED -> Color(0xFFD32F2F)
        PlayerColor.WHITE -> Color(0xFFF5F5F5)
        PlayerColor.BLUE -> Color(0xFF1E63C6)
        PlayerColor.ORANGE -> Color(0xFFF57C00)
    }

    fun onPlayer(color: PlayerColor): Color = if (color == PlayerColor.WHITE) Color(0xFF1F1F1F) else Color.White
}
