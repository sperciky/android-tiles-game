package com.example.tilegame.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tilegame.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Tile colour palette – bright, child-friendly
// ---------------------------------------------------------------------------
private val TILE_COLORS = listOf(
    Color(0xFFFF6B6B), // coral red
    Color(0xFF4ECDC4), // teal
    Color(0xFFFFE66D), // yellow
    Color(0xFF6C5CE7), // violet
    Color(0xFF00B894), // emerald
    Color(0xFFFF7675), // salmon
    Color(0xFF74B9FF), // sky blue
    Color(0xFFFD79A8), // rose
    Color(0xFFFDAA3E), // orange
    Color(0xFF55EFC4), // aquamarine
    Color(0xFFE17055), // terra cotta
    Color(0xFFA29BFE), // lavender
)

// Tile shapes (width x height in grid cells)
private val TILE_SHAPES = listOf(
    2 to 2, 2 to 3, 3 to 2,
    3 to 3, 4 to 2, 2 to 4,
    3 to 4, 4 to 3, 5 to 2,
    2 to 5, 5 to 3, 3 to 5,
)

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // Level configs: progressively harder
    private val levelConfigs = listOf(
        LevelConfig(1, 3, QuestionMode.MODE_A, 2),
        LevelConfig(2, 4, QuestionMode.MODE_A, 2),
        LevelConfig(3, 5, QuestionMode.MODE_A, 2),
        LevelConfig(4, 5, QuestionMode.MODE_B, 2),
        LevelConfig(5, 7, QuestionMode.MODE_B, 3),
        LevelConfig(6, 8, QuestionMode.MODE_B, 3),
        LevelConfig(7, 10, QuestionMode.MODE_B, 3),
        LevelConfig(8, 12, QuestionMode.MODE_B, 4),
    )

    init {
        startLevel(1)
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun startLevel(level: Int) {
        val config = levelConfigs.getOrElse((level - 1).coerceIn(0, levelConfigs.lastIndex)) { levelConfigs.last() }
            .copy(level = level)
        val tiles = generateTiles(config)
        val grid = buildGrid(tiles)

        _state.value = GameState(
            level = level,
            levelConfig = config,
            tiles = tiles,
            grid = grid,
            phase = GamePhase.ANIMATING,
            revealedTileCount = 0,
            totalLevels = levelConfigs.size
        )

        // Drive the tile-reveal animation from the ViewModel so timing is
        // controlled independently from the UI.
        viewModelScope.launch {
            delay(300L) // brief pause before first tile
            tiles.indices.forEach { i ->
                _state.value = _state.value.copy(revealedTileCount = i + 1)
                delay(450L) // gap between consecutive drops
            }
            delay(900L) // pause after all tiles are placed
            prepareQuestion()
        }
    }

    fun submitAnswer(answer: String) {
        val current = _state.value
        val intAnswer = answer.toIntOrNull() ?: return
        val correct = intAnswer == current.correctAnswer
        _state.value = current.copy(
            playerAnswer = answer,
            answerState = if (correct) AnswerState.CORRECT else AnswerState.INCORRECT,
            phase = GamePhase.ANSWER_REVEALED,
            score = if (correct) current.score + 100 + current.streak * 20 else current.score,
            streak = if (correct) current.streak + 1 else 0
        )
    }

    fun showExplanation() {
        val current = _state.value
        val (dimmed, highlighted) = when (current.questionMode) {
            QuestionMode.MODE_A -> computeModeAExplanation(current)
            QuestionMode.MODE_B -> computeModeBExplanation(current)
        }
        _state.value = current.copy(
            phase = GamePhase.EXPLANATION,
            dimmedTileIds = dimmed,
            highlightedCells = highlighted
        )
    }

    fun showSideView() {
        val current = _state.value

        // Pick the most representative cell: for Mode A it's the queried cell,
        // for Mode B it's whichever highlighted cell has the highest stack.
        val cell = when (current.questionMode) {
            QuestionMode.MODE_A -> current.highlightedCell
            QuestionMode.MODE_B -> current.highlightedCells.maxByOrNull { (col, row) ->
                current.grid[row][col].overlapCount
            }
        } ?: return

        _state.value = current.copy(
            phase = GamePhase.SIDE_VIEW,
            sideViewCell = cell,
            sideViewRevealedLayers = 0
        )

        // Animate layers appearing one by one
        val layerCount = current.grid[cell.second][cell.first].overlapCount
        viewModelScope.launch {
            delay(400L)
            repeat(layerCount) {
                _state.value = _state.value.copy(
                    sideViewRevealedLayers = _state.value.sideViewRevealedLayers + 1
                )
                delay(600L)
            }
        }
    }

    fun nextLevel() {
        startLevel(_state.value.level + 1)
    }

    fun replayLevel() {
        startLevel(_state.value.level)
    }

    // -----------------------------------------------------------------------
    // Tile generation
    // -----------------------------------------------------------------------

    private fun generateTiles(config: LevelConfig): List<Tile> {
        val rng = Random(System.currentTimeMillis())
        val tiles = mutableListOf<Tile>()

        // Place first tile anywhere that fits
        placeTile(tiles, config, rng, requireOverlap = false)

        // Subsequent tiles: first half must overlap to guarantee meaningful puzzles
        while (tiles.size < config.tileCount) {
            val needOverlap = tiles.size < (config.tileCount + 1) / 2
            val placed = placeTile(tiles, config, rng, requireOverlap = needOverlap)
            if (!placed) {
                // Fallback: place overlapping with a random existing tile
                forcePlaceTile(tiles, config, rng)
            }
        }

        // Verify minimum overlap guarantee; if insufficient, replace isolated tiles
        ensureMinOverlap(tiles, config, rng)

        return tiles
    }

    /** Attempts to place a new tile, returns true if successful within attempt limit. */
    private fun placeTile(
        existing: MutableList<Tile>,
        config: LevelConfig,
        rng: Random,
        requireOverlap: Boolean
    ): Boolean {
        repeat(400) {
            val (w, h) = TILE_SHAPES.random(rng)
            if (w > GRID_COLS || h > GRID_ROWS) return@repeat
            val x = rng.nextInt(GRID_COLS - w + 1)
            val y = rng.nextInt(GRID_ROWS - h + 1)
            val candidate = Tile(
                id = existing.size,
                x = x, y = y,
                width = w, height = h,
                color = TILE_COLORS[existing.size % TILE_COLORS.size]
            )
            val overlapsAny = existing.any { it.overlaps(candidate) }
            if (!requireOverlap || overlapsAny) {
                existing.add(candidate)
                return true
            }
        }
        return false
    }

    /** Force-places a tile by centering it on a randomly chosen existing tile. */
    private fun forcePlaceTile(
        existing: MutableList<Tile>,
        config: LevelConfig,
        rng: Random
    ) {
        val base = existing.random(rng)
        val (w, h) = TILE_SHAPES.random(rng)
        val x = (base.x + base.width / 2 - w / 2).coerceIn(0, (GRID_COLS - w).coerceAtLeast(0))
        val y = (base.y + base.height / 2 - h / 2).coerceIn(0, (GRID_ROWS - h).coerceAtLeast(0))
        existing.add(
            Tile(
                id = existing.size,
                x = x, y = y,
                width = w.coerceAtMost(GRID_COLS),
                height = h.coerceAtMost(GRID_ROWS),
                color = TILE_COLORS[existing.size % TILE_COLORS.size]
            )
        )
    }

    /**
     * Guarantees that at least [config.minOverlapRequired] cells have
     * overlapCount >= 2 (levels 1-3 guarantee 2, higher levels guarantee 3+).
     * If not, replaces up to 2 isolated tiles with forced overlaps.
     */
    private fun ensureMinOverlap(
        tiles: MutableList<Tile>,
        config: LevelConfig,
        rng: Random
    ) {
        val grid = buildGrid(tiles)
        val overlapCells = grid.flatten().count { it.overlapCount >= 2 }
        if (overlapCells >= config.minOverlapRequired) return

        // Find tiles that don't overlap anything and nudge them
        val isolatedIds = tiles
            .filter { t -> tiles.none { other -> other.id != t.id && t.overlaps(other) } }
            .map { it.id }
            .take(2)

        isolatedIds.forEach { id ->
            val base = tiles.filter { it.id != id }.randomOrNull(rng) ?: return@forEach
            val old = tiles[id]
            val x = (base.x + base.width / 2 - old.width / 2)
                .coerceIn(0, (GRID_COLS - old.width).coerceAtLeast(0))
            val y = (base.y + base.height / 2 - old.height / 2)
                .coerceIn(0, (GRID_ROWS - old.height).coerceAtLeast(0))
            tiles[id] = old.copy(x = x, y = y)
        }
    }

    // -----------------------------------------------------------------------
    // Grid builder
    // -----------------------------------------------------------------------

    private fun buildGrid(tiles: List<Tile>): List<List<GridCell>> {
        val grid = Array(GRID_ROWS) { row ->
            Array(GRID_COLS) { col -> GridCell(col, row) }
        }
        tiles.forEach { tile ->
            for (row in tile.y until (tile.y + tile.height).coerceAtMost(GRID_ROWS)) {
                for (col in tile.x until (tile.x + tile.width).coerceAtMost(GRID_COLS)) {
                    grid[row][col] = grid[row][col].copy(
                        coveringTileIds = grid[row][col].coveringTileIds + tile.id
                    )
                }
            }
        }
        return grid.map { it.toList() }
    }

    // -----------------------------------------------------------------------
    // Question preparation
    // -----------------------------------------------------------------------

    private fun prepareQuestion() {
        val current = _state.value
        when (current.levelConfig.questionMode) {
            QuestionMode.MODE_A -> prepareModeAQuestion(current)
            QuestionMode.MODE_B -> prepareModeBQuestion(current)
        }
    }

    private fun prepareModeAQuestion(state: GameState) {
        val covered = state.grid.flatten().filter { it.overlapCount > 0 }
        if (covered.isEmpty()) { _state.value = state.copy(phase = GamePhase.LEVEL_COMPLETE); return }

        // Prefer cells with ≥2 tiles for interesting questions
        val candidates = covered.filter { it.overlapCount >= 2 }.ifEmpty { covered }
        val chosen = candidates.random()

        _state.value = state.copy(
            phase = GamePhase.QUESTION,
            questionMode = QuestionMode.MODE_A,
            highlightedCell = chosen.col to chosen.row,
            correctAnswer = chosen.overlapCount
        )
    }

    private fun prepareModeBQuestion(state: GameState) {
        // Gather distinct overlap depths present on the grid
        val depthGroups = state.grid.flatten()
            .filter { it.overlapCount > 0 }
            .groupBy { it.overlapCount }

        if (depthGroups.isEmpty()) { _state.value = state.copy(phase = GamePhase.LEVEL_COMPLETE); return }

        // Prefer depth ≥2 so the answer isn't trivially "all covered tiles"
        val availableDepths = depthGroups.keys.sorted()
        val targetDepth = availableDepths.filter { it >= 2 }.ifEmpty { availableDepths }.random()
        val count = depthGroups[targetDepth]?.size ?: 0

        _state.value = state.copy(
            phase = GamePhase.QUESTION,
            questionMode = QuestionMode.MODE_B,
            targetOverlapLevel = targetDepth,
            correctAnswer = count,
            highlightedCell = null
        )
    }

    // -----------------------------------------------------------------------
    // Explanation computation
    // -----------------------------------------------------------------------

    private fun computeModeAExplanation(
        state: GameState
    ): Pair<Set<Int>, Set<Pair<Int, Int>>> {
        val (col, row) = state.highlightedCell ?: return emptySet<Int>() to emptySet()
        val cell = state.grid[row][col]
        // Dim tiles that don't touch the queried cell
        val dimmed = state.tiles.map { it.id }.toSet() - cell.coveringTileIds
        return dimmed to setOf(col to row)
    }

    private fun computeModeBExplanation(
        state: GameState
    ): Pair<Set<Int>, Set<Pair<Int, Int>>> {
        val target = state.targetOverlapLevel
        val highlighted = state.grid.flatten()
            .filter { it.overlapCount == target }
            .map { it.col to it.row }
            .toSet()
        return emptySet<Int>() to highlighted
    }
}
