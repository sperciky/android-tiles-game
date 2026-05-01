package com.example.tilegame.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tilegame.model.*
import com.example.tilegame.ui.theme.CorrectGreen
import com.example.tilegame.ui.theme.HighlightGold
import com.example.tilegame.ui.theme.WrongRed

/**
 * Panel shown after the player answers, both for the immediate feedback phase
 * ([GamePhase.ANSWER_REVEALED]) and the explanation phase ([GamePhase.EXPLANATION]).
 *
 * ANSWER_REVEALED – shows correct / wrong badge and the correct answer.
 * EXPLANATION     – adds explanatory text about which cells are highlighted.
 */
@Composable
fun AnswerRevealedPanel(
    state: GameState,
    onShowExplanation: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        modifier = modifier
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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DragHandle()

                // Correct / Incorrect badge
                val isCorrect = state.answerState == AnswerState.CORRECT
                val badgeColor = if (isCorrect) CorrectGreen else WrongRed
                val badgeText = if (isCorrect) "✓  Correct!" else "✗  Not quite"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isCorrect) {
                    Text(
                        text = "The correct answer is ${state.correctAnswer}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                if (isCorrect && state.streak > 1) {
                    Text(
                        text = "🔥 ${state.streak} streak!",
                        fontSize = 18.sp,
                        color = Color(0xFFFF6B00),
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // Score summary
                Text(
                    text = "Score: ${state.score}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = onShowExplanation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("See Explanation", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Bottom panel shown during [GamePhase.EXPLANATION].
 * Describes what the grid highlights mean and offers a "Side View" button.
 */
@Composable
fun ExplanationPanel(
    state: GameState,
    onShowSideView: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        modifier = modifier
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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DragHandle()

                Text(
                    text = "Explanation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Context-specific explanation text
                val explanationText = when (state.questionMode) {
                    QuestionMode.MODE_A -> {
                        val (col, row) = state.highlightedCell ?: (0 to 0)
                        val tileCount = state.correctAnswer
                        val dimmedCount = state.dimmedTileIds.size
                        buildString {
                            append("The outlined cell at column ${col + 1}, row ${row + 1} ")
                            append("is covered by $tileCount tile${if (tileCount == 1) "" else "s"}.\n\n")
                            if (dimmedCount > 0) {
                                append("$dimmedCount tile${if (dimmedCount == 1) " is" else "s are"} ")
                                append("faded out because they don't touch this cell.")
                            }
                        }
                    }
                    QuestionMode.MODE_B -> {
                        val n = state.targetOverlapLevel
                        val count = state.correctAnswer
                        "The ${count} highlighted square${if (count == 1) "" else "s"} " +
                        "${if (count == 1) "is" else "are"} each covered by exactly $n " +
                        "tile${if (n == 1) "" else "s"}."
                    }
                }

                // Highlighted explanation box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HighlightGold.copy(alpha = 0.15f))
                        .border(1.dp, HighlightGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = explanationText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start
                    )
                }

                Button(
                    onClick = onShowSideView,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("See 3D Stack View", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFFCCCCCC))
    )
}
