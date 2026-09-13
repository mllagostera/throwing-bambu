package dev.bambu.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
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
    var angle by remember { mutableIntStateOf(45) }
    var power by remember { mutableIntStateOf(50) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NumericSlider(
            label = "Angle",
            value = angle,
            range = G.ANGLE_MIN..G.ANGLE_MAX,
            enabled = state.canAim,
            onValueChange = { angle = it },
        )
        NumericSlider(
            label = "Power",
            value = power,
            range = G.POWER_MIN..G.POWER_MAX,
            enabled = state.canAim,
            onValueChange = { power = it },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previous = state.lastShots[state.currentPlayer]
            TextButton(
                enabled = state.canAim && previous != null,
                onClick = {
                    previous?.let {
                        angle = it.angle
                        power = it.power
                    }
                },
            ) {
                Text("Repeat previous")
            }
            Box(modifier = Modifier.weight(1f))
            Button(
                enabled = state.canAim,
                onClick = { onThrow(angle, power) },
            ) {
                Text(if (state.canAim) "Throw!" else "…")
            }
        }
    }
}

/**
 * A slider with an editable numeric field beside it.
 *
 * The original was played by typing numbers, and keeping that route is what lets a
 * player adjust one degree at a time — which a slider on a phone cannot do (§14).
 */
@Composable
private fun NumericSlider(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color.White, modifier = Modifier.width(64.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.let { onValueChange(it.coerceIn(range)) }
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions =
                androidx.compose.foundation.text
                    .KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp),
        )
    }
}
