package com.example.tilegame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.tilegame.model.*
import com.example.tilegame.ui.theme.GridBackground
import com.example.tilegame.ui.theme.GridLineColor
import com.example.tilegame.ui.theme.HighlightGold
import com.example.tilegame.ui.theme.HighlightOutline

/**
 * Renders the 10×15 game grid with animated tile placements.
 *
 * Each tile has its own [Animatable] tracking a Y-offset that starts at
 * -[DROP_DISTANCE_PX] (above the screen) and springs to 0 when the tile is
 * "revealed". Reading those animated values inside the composable body
 * subscribes Compose to recompose every frame the animation runs, so the
 * Canvas re-draws automatically.
 *
 * @param tiles            Full ordered list of tiles for this level.
 * @param revealedTileCount How many tiles have been dropped so far.
 * @param level            Current level number – used as a [remember] key so
 *                         animatables reset cleanly between levels.
 * @param dimmedTileIds    Tiles that should render at low opacity (explanation mode).
 * @param highlightedCells Cells that get a gold fill (Mode B explanation / Mode A).
 * @param highlightedCell  The single queried cell in Mode A (gets a pulsing outline).
 * @param onCellTap        Optional callback for learning-mode tap on a cell.
 */
@Composable
fun GridCanvas(
    modifier: Modifier = Modifier,
    tiles: List<Tile>,
    revealedTileCount: Int,
    level: Int,
    dimmedTileIds: Set<Int> = emptySet(),
    highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    highlightedCell: Pair<Int, Int>? = null,
    onCellTap: ((col: Int, row: Int) -> Unit)? = null
) {
    // One Animatable per tile; reset when the level changes.
    val animatables = remember(level, tiles.size) {
        List(tiles.size) { Animatable(DROP_DISTANCE_PX) }
    }

    // Trigger the drop animation for the latest revealed tile.
    LaunchedEffect(revealedTileCount) {
        val idx = revealedTileCount - 1
        if (idx in animatables.indices) {
            animatables[idx].animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Read all current offsets in composable scope so Compose tracks
    // the State reads and recomposes every animation frame.
    val offsets = animatables.map { it.value }

    // Grid aspect ratio: 10 cols × 15 rows → width : height = 2 : 3
    val tapModifier = if (onCellTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val cellW = size.width / GRID_COLS.toFloat()
                val cellH = size.height / GRID_ROWS.toFloat()
                val col = (offset.x / cellW).toInt().coerceIn(0, GRID_COLS - 1)
                val row = (offset.y / cellH).toInt().coerceIn(0, GRID_ROWS - 1)
                onCellTap(col, row)
            }
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS.toFloat())
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(GridBackground)
            .border(1.5.dp, Color(0xFFBBBBBB), RoundedCornerShape(8.dp))
            .then(tapModifier)
    ) {
        val cellW = size.width / GRID_COLS
        val cellH = size.height / GRID_ROWS

        // ── 1. Tile fills ──────────────────────────────────────────────────
        tiles.take(revealedTileCount).forEachIndexed { i, tile ->
            val yOff = offsets.getOrElse(i) { DROP_DISTANCE_PX }
            val alpha = if (tile.id in dimmedTileIds) DIMMED_ALPHA else 1f
            drawTileFill(tile, cellW, cellH, yOff, alpha)
        }

        // ── 2. Highlighted cells (gold tint) ──────────────────────────────
        highlightedCells.forEach { (col, row) ->
            drawRect(
                color = HighlightGold.copy(alpha = 0.55f),
                topLeft = Offset(col * cellW, row * cellH),
                size = Size(cellW, cellH)
            )
        }

        // ── 3. Queried cell outline (Mode A) ──────────────────────────────
        highlightedCell?.let { (col, row) ->
            // Bright outline so child can identify the cell easily
            drawRect(
                color = HighlightOutline,
                topLeft = Offset(col * cellW + 2f, row * cellH + 2f),
                size = Size(cellW - 4f, cellH - 4f),
                style = Stroke(width = 5f)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(col * cellW, row * cellH),
                size = Size(cellW, cellH)
            )
        }

        // ── 4. Tile borders (drawn on top of fills so they're always sharp) ─
        tiles.take(revealedTileCount).forEachIndexed { i, tile ->
            val yOff = offsets.getOrElse(i) { DROP_DISTANCE_PX }
            val alpha = if (tile.id in dimmedTileIds) DIMMED_ALPHA else 1f
            drawTileBorder(tile, cellW, cellH, yOff, alpha)
        }

        // ── 5. Grid lines (on top so they're always visible) ──────────────
        drawGridLines(cellW, cellH)
    }
}

// ---------------------------------------------------------------------------
// DrawScope helpers
// ---------------------------------------------------------------------------

private const val DROP_DISTANCE_PX = -1600f
private const val DIMMED_ALPHA = 0.18f

private fun DrawScope.drawTileFill(
    tile: Tile,
    cellW: Float,
    cellH: Float,
    yOff: Float,
    alpha: Float
) {
    drawRect(
        color = tile.color.copy(alpha = alpha),
        topLeft = Offset(tile.x * cellW, tile.y * cellH + yOff),
        size = Size(tile.width * cellW, tile.height * cellH)
    )
}

private fun DrawScope.drawTileBorder(
    tile: Tile,
    cellW: Float,
    cellH: Float,
    yOff: Float,
    alpha: Float
) {
    // Outer dark stroke
    drawRect(
        color = Color.Black.copy(alpha = alpha * 0.75f),
        topLeft = Offset(tile.x * cellW, tile.y * cellH + yOff),
        size = Size(tile.width * cellW, tile.height * cellH),
        style = Stroke(width = 3.5f)
    )
    // Inner white highlight for depth
    drawRect(
        color = Color.White.copy(alpha = alpha * 0.25f),
        topLeft = Offset(tile.x * cellW + 3f, tile.y * cellH + yOff + 3f),
        size = Size(tile.width * cellW - 6f, tile.height * cellH - 6f),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawGridLines(cellW: Float, cellH: Float) {
    for (col in 0..GRID_COLS) {
        val x = col * cellW
        drawLine(GridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
    for (row in 0..GRID_ROWS) {
        val y = row * cellH
        drawLine(GridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}
