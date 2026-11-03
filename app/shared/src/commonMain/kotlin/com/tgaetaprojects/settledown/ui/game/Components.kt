package com.tgaetaprojects.settledown.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.ui.theme.GameColors

/** A player's color as a dot, outlined so white shows on light surfaces. */
@Composable
fun PlayerDot(color: PlayerColor, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(GameColors.player(color), CircleShape)
            .border(1.dp, GameColors.pieceOutline.copy(alpha = 0.6f), CircleShape)
    )
}

/** One resource in a hand: a colored card with its count. Faded when there are none. */
@Composable
fun ResourceCard(resource: Resource, count: Int, modifier: Modifier = Modifier) {
    val color = GameColors.resource(resource)
    Surface(
        modifier = modifier.width(58.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (count > 0) color else color.copy(alpha = 0.25f),
        contentColor = if (count > 0) GameColors.onResource(resource) else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(resource.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

/** A row of [ResourceCard]s, one per resource. */
@Composable
fun HandRow(hand: ResourceCounts, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in Resource.entries) ResourceCard(r, hand[r])
    }
}

/** A cost as small colored pips, one per card. */
@Composable
fun CostPips(cost: ResourceCounts, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in cost.types) repeat(cost[r]) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(GameColors.resource(r), RoundedCornerShape(2.dp))
                    .border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        }
    }
}

/** A labeled count with − and + buttons, kept within `0..max`. */
@Composable
fun ResourceStepper(resource: Resource, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(16.dp).background(GameColors.resource(resource), RoundedCornerShape(4.dp)))
        Text(resource.label, Modifier.width(64.dp), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Text("−") }
        Text(
            value.toString(), Modifier.width(28.dp),
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}
