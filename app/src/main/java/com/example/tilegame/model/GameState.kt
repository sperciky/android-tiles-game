package com.example.tilegame.model

/** Grid dimensions – 10 columns wide, 15 rows tall. */
const val GRID_COLS = 10
const val GRID_ROWS = 15

// ---------------------------------------------------------------------------
// Enumerations
// ---------------------------------------------------------------------------

enum class QuestionMode {
    /** "How many tiles cover THIS specific cell?" */
    MODE_A,
    /** "How many cells are covered by exactly N tiles?" */
    MODE_B
}

enum class GamePhase {
    ANIMATING,       // tiles are dropping onto the grid one by one
    QUESTION,        // player is answering the question
    ANSWER_REVEALED, // correct/wrong feedback shown; player can see explanation
    EXPLANATION,     // grid highlights relevant tiles/cells
    SIDE_VIEW,       // vertical stacking animation for a chosen cell
    LEVEL_COMPLETE   // between levels
}

enum class AnswerState { UNANSWERED, CORRECT, INCORRECT }

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

/**
 * Per-level difficulty descriptor.
 *
 * [tileCount]           – number of tiles generated for this level.
 * [questionMode]        – which question type is asked.
 * [minOverlapRequired]  – tile generator guarantees at least this many cells
 *                         have this overlap depth.
 */
data class LevelConfig(
    val level: Int,
    val tileCount: Int,
    val questionMode: QuestionMode,
    val minOverlapRequired: Int = 2
)

// ---------------------------------------------------------------------------
// Grid
// ---------------------------------------------------------------------------

/**
 * One cell of the 10×15 grid.
 * [coveringTileIds] tracks which tile IDs are stacked on this cell.
 */
data class GridCell(
    val col: Int,
    val row: Int,
    val coveringTileIds: Set<Int> = emptySet()
) {
    val overlapCount: Int get() = coveringTileIds.size
}

fun emptyGrid(): List<List<GridCell>> =
    List(GRID_ROWS) { row -> List(GRID_COLS) { col -> GridCell(col, row) } }

// ---------------------------------------------------------------------------
// Master game state
// ---------------------------------------------------------------------------

data class GameState(
    val level: Int = 1,
    val levelConfig: LevelConfig = LevelConfig(1, 3, QuestionMode.MODE_A),
    val tiles: List<Tile> = emptyList(),
    val grid: List<List<GridCell>> = emptyGrid(),
    val phase: GamePhase = GamePhase.ANIMATING,

    // How many tiles have been "dropped" so far (drives animation).
    val revealedTileCount: Int = 0,

    // ----- Question fields -----
    val questionMode: QuestionMode = QuestionMode.MODE_A,
    /** Mode A: the cell whose overlap count is being asked. (col, row) */
    val highlightedCell: Pair<Int, Int>? = null,
    /** Mode B: target overlap depth N in "how many cells have exactly N tiles?" */
    val targetOverlapLevel: Int = 1,
    val correctAnswer: Int = 0,
    val playerAnswer: String = "",
    val answerState: AnswerState = AnswerState.UNANSWERED,

    // ----- Explanation fields -----
    /** Tile IDs that should be rendered at reduced opacity. */
    val dimmedTileIds: Set<Int> = emptySet(),
    /** Cells that should be highlighted with a glow/outline. */
    val highlightedCells: Set<Pair<Int, Int>> = emptySet(),

    // ----- Side-view fields -----
    /** Cell being shown in the vertical stacking view. (col, row) */
    val sideViewCell: Pair<Int, Int>? = null,
    /** How many layers have animated into the side view so far. */
    val sideViewRevealedLayers: Int = 0,

    // ----- Score -----
    val score: Int = 0,
    val streak: Int = 0,
    val totalLevels: Int = 8
)
