package com.example.tilegame.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tilegame.model.*
import com.example.tilegame.ui.theme.*
import com.example.tilegame.viewmodel.GameViewModel

/**
 * Root game screen.
 *
 * Layout (portrait):
 *   ┌─────────────────────────┐
 *   │  TopBar (level, score)  │
 *   ├─────────────────────────┤
 *   │                         │
 *   │      GridCanvas         │  ← fills remaining width, aspect-locked 10:15
 *   │                         │
 *   ├─────────────────────────┤
 *   │  Phase-specific panel   │  ← QuestionPanel / ExplanationPanel / …
 *   └─────────────────────────┘
 *
 * The [SideViewPanel] renders as a Dialog (full-screen overlay).
 */
@Composable
fun GameScreen(
    vm: GameViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GameBackground)
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            GameTopBar(state = state, onReplay = vm::replayLevel)

            // ── Grid ──────────────────────────────────────────────────────
            GridCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                tiles = state.tiles,
                revealedTileCount = state.revealedTileCount,
                level = state.level,
                dimmedTileIds = state.dimmedTileIds,
                highlightedCells = state.highlightedCells,
                highlightedCell = when (state.phase) {
                    GamePhase.QUESTION,
                    GamePhase.ANSWER_REVEALED,
                    GamePhase.EXPLANATION -> state.highlightedCell
                    else -> null
                },
                // Learning mode: tap any cell to see its overlap count as a Snackbar
                onCellTap = null
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Phase-specific bottom panel ───────────────────────────────
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                },
                label = "phase_transition"
            ) { phase ->
                when (phase) {
                    GamePhase.ANIMATING -> AnimatingHint(state)
                    GamePhase.QUESTION -> QuestionPanel(
                        state = state,
                        onSubmit = vm::submitAnswer
                    )
                    GamePhase.ANSWER_REVEALED -> AnswerRevealedPanel(
                        state = state,
                        onShowExplanation = vm::showExplanation
                    )
                    GamePhase.EXPLANATION -> ExplanationPanel(
                        state = state,
                        onShowSideView = vm::showSideView
                    )
                    GamePhase.SIDE_VIEW -> {} // handled by dialog overlay below
                    GamePhase.LEVEL_COMPLETE -> LevelCompletePanel(
                        state = state,
                        onNext = vm::nextLevel,
                        onReplay = vm::replayLevel
                    )
                }
            }
        }

        // ── Side-view dialog overlay ───────────────────────────────────────
        if (state.phase == GamePhase.SIDE_VIEW) {
            SideViewPanel(
                state = state,
                onContinue = {
                    // Advance to level-complete (or trigger next level directly)
                    vm.nextLevel()
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun GameTopBar(state: GameState, onReplay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF6650A4), Color(0xFF9C27B0))
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level indicator
        Column {
            Text(
                text = "Level ${state.level}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${state.tiles.size} tiles",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Level progress dots
        LevelProgressDots(current = state.level, total = state.totalLevels)

        Spacer(modifier = Modifier.weight(1f))

        // Score
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${state.score} pts",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.streak > 1) {
                Text(
                    text = "🔥 ×${state.streak}",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LevelProgressDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val done = i < current
            val active = i == current - 1
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            done && !active -> Color.White.copy(alpha = 0.9f)
                            active -> Color(0xFFFFD700)
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Animating hint (shown while tiles are dropping)
// ---------------------------------------------------------------------------

@Composable
private fun AnimatingHint(state: GameState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
        ) {
            Text(
                text = "Watch the tiles fall  (${state.revealedTileCount} / ${state.tiles.size})",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Level-complete panel
// ---------------------------------------------------------------------------

@Composable
private fun LevelCompletePanel(
    state: GameState,
    onNext: () -> Unit,
    onReplay: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (state.level >= state.totalLevels) "🏆 All Levels Complete!" else "Level ${state.level} Complete!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Score: ${state.score}",
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReplay,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Replay", fontSize = 16.sp)
                }

                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(2f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = state.level < state.totalLevels
                ) {
                    Text(
                        if (state.level >= state.totalLevels) "Done!" else "Next Level →",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
