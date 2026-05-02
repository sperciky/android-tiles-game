package com.example.tilegame.model

import androidx.compose.ui.graphics.Color

/**
 * Represents one rectangular carpet/tile placed on the grid.
 *
 * [x]/[y] is the top-left corner in grid coordinates (column, row).
 * [width]/[height] are measured in grid cells.
 */
data class Tile(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: Color
) {
    /** True if this tile covers the given grid cell. */
    fun covers(col: Int, row: Int): Boolean =
        col in x until x + width && row in y until y + height

    /** Returns every (col, row) pair this tile covers. */
    fun coveredCells(): List<Pair<Int, Int>> =
        (y until y + height).flatMap { row ->
            (x until x + width).map { col -> col to row }
        }

    /** Axis-aligned rectangle overlap check. */
    fun overlaps(other: Tile): Boolean =
        x < other.x + other.width &&
        x + width > other.x &&
        y < other.y + other.height &&
        y + height > other.y
}
