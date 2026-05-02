package com.example.tilegame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tilegame.model.GameState
import com.example.tilegame.model.GRID_COLS
import com.example.tilegame.model.GRID_ROWS
import com.example.tilegame.model.QuestionMode
import com.example.tilegame.ui.theme.SideViewBackground
import com.example.tilegame.ui.theme.SideViewLabel

/**
 * Full-screen dialog that shows a vertical cross-section ("side view") of
 * a single grid cell, illustrating why it has the given overlap count.
 *
 * Tiles are shown as coloured horizontal strips stacking from the bottom up.
 * Each strip slides in from below using an [Animatable], driven by the
 * [GameState.sideViewRevealedLayers] counter that the ViewModel increments.
 *
 * @param state      Current game state (must be in SIDE_VIEW phase).
 * @param onContinue Callback when the player taps "Continue to next level".
 */
@Composable
fun SideViewPanel(
    state: GameState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (col, row) = state.sideViewCell ?: return
    val cell = state.grid[row][col]
    val coveringTiles = state.tiles.filter { it.id in cell.coveringTileIds }
    val totalLayers = coveringTiles.size

    // One Animatable per layer, tracking the vertical slide-in offset.
    // Starts at +1 (below its slot) and animates to 0 (in place).
    val layerOffsets = remember(col, row, totalLayers) {
        List(totalLayers) { Animatable(1f) }
    }

    // Trigger each layer's animation as sideViewRevealedLayers increments.
    LaunchedEffect(state.sideViewRevealedLayers) {
        val idx = state.sideViewRevealedLayers - 1
        if (idx in layerOffsets.indices) {
            layerOffsets[idx].animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400)
            )
        }
    }

    val layerProgress = layerOffsets.map { it.value }

    Dialog(
        onDismissRequest = { /* block back-press during animation */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SideViewBackground)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Stack View",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SideViewLabel
                )

                val cellDesc = "Column ${col + 1}, Row ${row + 1}"
                Text(
                    text = "$totalLayers tile${if (totalLayers == 1) "" else "s"} at $cellDesc",
                    fontSize = 15.sp,
                    color = SideViewLabel.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )

                // Stack visualisation
                StackCanvas(
                    coveringTiles = coveringTiles,
                    revealedLayers = state.sideViewRevealedLayers,
                    layerProgress = layerProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181825))
                )

                // Layer legend
                if (state.sideViewRevealedLayers > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        coveringTiles.take(state.sideViewRevealedLayers)
                            .forEachIndexed { i, tile ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(tile.color)
                                    )
                                    Text(
                                        text = "Layer ${i + 1}  — Tile #${tile.id + 1}",
                                        fontSize = 13.sp,
                                        color = SideViewLabel.copy(alpha = 0.85f)
                                    )
                                }
                            }
                    }
                }

                // Continue button (only after all layers revealed)
                if (state.sideViewRevealedLayers >= totalLayers) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Next Level →", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Canvas that draws a vertical stack of coloured tile strips.
 *
 * The bottom strip is the first tile placed (index 0 in [coveringTiles]).
 * Each strip slides in from below using the corresponding value in [layerProgress]
 * (0 = in final position, 1 = one strip-height below its slot).
 */
@Composable
private fun StackCanvas(
    coveringTiles: List<com.example.tilegame.model.Tile>,
    revealedLayers: Int,
    layerProgress: List<Float>,
    modifier: Modifier = Modifier
) {
    val totalLayers = coveringTiles.size

    Canvas(modifier = modifier) {
        if (totalLayers == 0) return@Canvas

        val stripHeight = (size.height * 0.75f) / totalLayers
        val stripWidth = size.width * 0.7f
        val startX = (size.width - stripWidth) / 2f
        // Bottom of the stack area
        val stackBottom = size.height * 0.88f

        // Ground line
        drawLine(
            color = Color(0xFF585868),
            start = Offset(startX * 0.5f, stackBottom),
            end = Offset(size.width - startX * 0.5f, stackBottom),
            strokeWidth = 2f
        )

        // Draw each revealed strip (bottom first = index 0)
        for (i in 0 until revealedLayers.coerceAtMost(totalLayers)) {
            val tile = coveringTiles[i]
            val progress = layerProgress.getOrElse(i) { 0f }

            // Final top-Y for this strip (bottom strip is at stackBottom - stripHeight)
            val finalTop = stackBottom - (i + 1) * stripHeight
            // Slide in from one strip-height below
            val currentTop = finalTop + progress * stripHeight

            // Shadow / depth effect behind the strip
            drawRect(
                color = Color.Black.copy(alpha = 0.3f),
                topLeft = Offset(startX + 6f, currentTop + 6f),
                size = Size(stripWidth, stripHeight - 4f)
            )

            // Strip fill
            drawRect(
                color = tile.color,
                topLeft = Offset(startX, currentTop),
                size = Size(stripWidth, stripHeight - 4f)
            )

            // Top highlight
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(startX, currentTop),
                size = Size(stripWidth, 4f)
            )

            // Border
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(startX, currentTop),
                size = Size(stripWidth, stripHeight - 4f),
                style = Stroke(width = 2f)
            )

            // Layer index label (drawn as a small number indicator – via offset)
            // We can't draw text easily in DrawScope without TextMeasurer, so we
            // rely on the legend below the canvas instead.
        }
    }
}
