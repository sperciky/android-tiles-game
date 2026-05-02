package com.example.tilegame.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tilegame.model.*

/**
 * Bottom panel displayed during [GamePhase.QUESTION].
 *
 * Shows the question text, a numeric input field, and a Submit button.
 * Designed to be large enough for small fingers.
 */
@Composable
fun QuestionPanel(
    state: GameState,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember(state.level, state.phase) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFCCCCCC))
                )

                // Question label badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.questionMode == QuestionMode.MODE_A) "STACK COUNT" else "AREA COUNT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Question text
                val questionText = when (state.questionMode) {
                    QuestionMode.MODE_A ->
                        "How many tiles cover the highlighted square?"
                    QuestionMode.MODE_B ->
                        "How many squares are covered by exactly ${state.targetOverlapLevel} tile${
                            if (state.targetOverlapLevel == 1) "" else "s"
                        }?"
                }
                Text(
                    text = questionText,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Numeric input
                OutlinedTextField(
                    value = input,
                    onValueChange = { new ->
                        // Allow only non-negative integers up to 3 digits
                        if (new.isEmpty() || (new.all { it.isDigit() } && new.length <= 3)) {
                            input = new
                        }
                    },
                    label = { Text("Your answer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        if (input.isNotEmpty()) onSubmit(input)
                    }),
                    modifier = Modifier.width(160.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )

                // Quick-pick number buttons for child-friendliness
                NumberPad(
                    onNumber = { n ->
                        val candidate = input + n.toString()
                        if (candidate.length <= 3) input = candidate
                    },
                    onClear = { input = "" },
                    onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) }
                )

                // Submit
                Button(
                    onClick = {
                        keyboard?.hide()
                        if (input.isNotEmpty()) onSubmit(input)
                    },
                    enabled = input.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Submit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** 3×4 number pad (1-9, clear, 0, backspace). */
@Composable
private fun NumberPad(
    onNumber: (Int) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit = onClear
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { n ->
                    OutlinedButton(
                        onClick = { onNumber(n) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(n.toString(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("C", fontSize = 18.sp) }
            OutlinedButton(
                onClick = { onNumber(0) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("0", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("⌫", fontSize = 18.sp) }
        }
    }
}
