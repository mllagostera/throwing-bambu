package dev.bambu.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bambu.app.render.GameCanvas
import dev.bambu.app.render.GameFrame
import dev.bambu.app.render.rememberGameSprites
import dev.bambu.core.G
import dev.bambu.core.logicalWidth

/**
 * The game screen (§14).
 *
 * The UI does **not** live inside the logical canvas: it sits on top in native dp, so
 * the controls stay readable whatever integer scale the playfield ends up using.
 */
@Composable
fun GameScreen(
    seed: Long,
    roundsToWin: Int,
    onFinished: (winner: Int) -> Unit,
    viewModel: GameViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val sprites = rememberGameSprites()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = with(density) { maxWidth.roundToPx() }
        val screenH = with(density) { maxHeight.roundToPx() }

        LaunchedEffect(seed, screenW, screenH) {
            if (screenW > 0 && screenH > 0) {
                viewModel.start(seed, logicalWidth(screenW, screenH), roundsToWin)
            }
        }

        LaunchedEffect(state.matchWinner) {
            state.matchWinner?.let(onFinished)
        }

        val scenario = state.scenario
        val terrain = state.terrain
        if (scenario != null && terrain != null) {
            GameCanvas(
                frame =
                    GameFrame(
                        scenario = scenario,
                        terrain = terrain,
                        terrainVersion = state.terrainVersion,
                        canePoint = state.canePoint,
                        caneFrame = state.caneFrame,
                        trail = state.trail,
                        boom = state.boom,
                        sunOuch = state.sunOuch,
                        pandaPoses = state.pandaPoses,
                    ),
                sprites = sprites,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Hud(state = state, modifier = Modifier.align(Alignment.TopCenter))

        Controls(
            state = state,
            onThrow = viewModel::submit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Hud(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "P1 ${state.scores.getOrElse(0) { 0 }} — ${state.scores.getOrElse(1) { 0 }} P2",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Player ${state.currentPlayer + 1}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        WindIndicator(state.wind)
    }
}

/** An arrow whose length is proportional to |wind|, with the value beside it (§14). */
@Composable
private fun WindIndicator(wind: Int) {
    val arrow =
        when {
            wind > 0 -> "→".repeat(windBars(wind))
            wind < 0 -> "←".repeat(windBars(wind))
            else -> "·"
        }
    Text(
        text = "$arrow $wind",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
    )
}

private fun windBars(wind: Int): Int = (kotlin.math.abs(wind) + 2) / 3

@Composable
private fun Controls(
    state: GameUiState,
    onThrow: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The text is the single source of truth, so a half-typed value is never fought over
    // by the slider. The slider writes into it just like the keyboard does.
    var angleText by remember { mutableStateOf("45") }
    var powerText by remember { mutableStateOf("50") }
    val angle = angleText.toIntOrNull()?.coerceIn(G.ANGLE_MIN, G.ANGLE_MAX)
    val power = powerText.toIntOrNull()?.coerceIn(G.POWER_MIN, G.POWER_MAX)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompactSlider(
            label = "ANG",
            text = angleText,
            value = angle,
            range = G.ANGLE_MIN..G.ANGLE_MAX,
            enabled = state.canAim,
            onTextChange = { angleText = it },
            modifier = Modifier.weight(1f),
        )
        CompactSlider(
            label = "PWR",
            text = powerText,
            value = power,
            range = G.POWER_MIN..G.POWER_MAX,
            enabled = state.canAim,
            onTextChange = { powerText = it },
            modifier = Modifier.weight(1f),
        )

        val previous = state.lastShots[state.currentPlayer]
        TextButton(
            enabled = state.canAim && previous != null,
            contentPadding = PaddingValues(horizontal = 8.dp),
            onClick = {
                previous?.let {
                    angleText = it.angle.toString()
                    powerText = it.power.toString()
                }
            },
        ) {
            Text("Repeat", style = MaterialTheme.typography.labelLarge)
        }
        Button(
            enabled = state.canAim && angle != null && power != null,
            contentPadding = PaddingValues(horizontal = 16.dp),
            onClick = {
                if (angle != null && power != null) onThrow(angle, power)
            },
        ) {
            Text("Throw!", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Label, editable number and slider on a single line.
 *
 * The panel used to stack two of these plus a button row and swallowed a third of the
 * screen. The playfield is the point of the screen, so the controls get one line: the
 * number stays big enough to read at a glance and typeable for one-unit adjustments,
 * which is the route the original was played with (§14).
 */
@Composable
private fun CompactSlider(
    label: String,
    text: String,
    value: Int?,
    range: IntRange,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
        )
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                // Digits only, and never longer than the widest value in range.
                val digits = typed.filter { it.isDigit() }.take(range.last.toString().length)
                onTextChange(digits)
            },
            enabled = enabled,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.titleMedium.copy(
                    color = if (value == null) Color(0xFFFF8A80) else Color.White,
                    textAlign = TextAlign.Center,
                ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                Modifier
                    .width(44.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
        )
        Slider(
            value = (value ?: range.first).toFloat(),
            onValueChange = { onTextChange(it.toInt().coerceIn(range).toString()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}
