package com.vansid.panda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vansid.panda.app.R
import com.vansid.panda.app.render.GameCanvas
import com.vansid.panda.app.render.GameFrame
import com.vansid.panda.app.render.rememberGameSprites
import com.vansid.panda.app.ui.bluetooth.BluetoothSession
import com.vansid.panda.app.ui.theme.playfieldScrim
import com.vansid.panda.core.AiLevel
import com.vansid.panda.core.G
import com.vansid.panda.core.logicalWidth

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
    aiLevel: AiLevel?,
    onFinished: (winner: Int) -> Unit,
    remote: Boolean = false,
    viewModel: GameViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val sprites = rememberGameSprites()

    // The system bars are hidden (see MainActivity), so what is normally left here is a
    // display cutout, plus whatever a swipe brings back transiently. Belt and braces: on
    // a device that refuses to hide them, the clock lands on the wind indicator.
    val horizontal = WindowInsetsSides.Horizontal
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + horizontal)
    val bottomInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + horizontal)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = with(density) { maxWidth.roundToPx() }
        val screenH = with(density) { maxHeight.roundToPx() }

        LaunchedEffect(seed, screenW, screenH, remote) {
            if (screenW <= 0 || screenH <= 0) return@LaunchedEffect
            val link = BluetoothSession.link
            val agreed = BluetoothSession.config
            if (remote && link != null && agreed != null) {
                // The width comes from the handshake, never from this screen: the host
                // decided it, and a device that recomputed its own would be playing a
                // differently shaped match from the one on the other side of the room.
                viewModel.startRemote(link, agreed, BluetoothSession.localPlayer)
            } else {
                viewModel.start(seed, logicalWidth(screenW, screenH), roundsToWin, aiLevel)
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

        // The insets go on the overlays and never on the canvas above. Padding the canvas
        // would change the logical width it derives from its own size (D-04) — and for a
        // networked match that width is negotiated, not measured locally.
        //
        // Each bar applies its own insets rather than receiving them applied. A padding
        // modifier shrinks what comes after it, so applied out here it would shrink the
        // bar before the bar painted its own background, and the scrim would stop at the
        // cutout instead of reaching the corner.
        Hud(
            state = state,
            insets = topInsets,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Controls(
            state = state,
            onThrow = viewModel::submit,
            insets = bottomInsets,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Hud(
    state: GameUiState,
    insets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.playfieldScrim)
                .windowInsetsPadding(insets)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    R.string.game_score,
                    state.scores.getOrElse(0) { 0 },
                    state.scores.getOrElse(1) { 0 },
                ),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                when {
                    state.waitingForAi -> stringResource(R.string.game_thinking)
                    state.humanPlayers.size == 1 -> stringResource(R.string.game_your_turn)
                    else -> stringResource(R.string.game_player, state.currentPlayer + 1)
                },
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WindArrow(wind = state.wind, color = colors.onSurface)
            Text(
                // The arrow already announces the value; repeating it would read it twice.
                text = state.wind.toString(),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun Controls(
    state: GameUiState,
    onThrow: (Int, Int) -> Unit,
    insets: WindowInsets,
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
                .background(MaterialTheme.colorScheme.playfieldScrim)
                .windowInsetsPadding(insets)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompactSlider(
            label = stringResource(R.string.game_angle),
            text = angleText,
            value = angle,
            range = G.ANGLE_MIN..G.ANGLE_MAX,
            enabled = state.canAim,
            onTextChange = { angleText = it },
            modifier = Modifier.weight(1f),
        )
        CompactSlider(
            label = stringResource(R.string.game_power),
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
            Text(stringResource(R.string.game_repeat), style = MaterialTheme.typography.labelLarge)
        }
        Button(
            enabled = state.canAim && angle != null && power != null,
            contentPadding = PaddingValues(horizontal = 16.dp),
            onClick = {
                if (angle != null && power != null) onThrow(angle, power)
            },
        ) {
            Text(
                text = stringResource(if (state.canAim) R.string.game_throw else R.string.game_waiting),
                style = MaterialTheme.typography.labelLarge,
            )
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
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
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
                    color = if (value == null) colors.error else colors.onSurface,
                    textAlign = TextAlign.Center,
                ),
            cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                Modifier
                    .width(44.dp)
                    // Opaque, not a tint of the scrim. The field used to be white at 12 %,
                    // which left its real colour to whatever the playfield happened to be
                    // showing underneath: an invalid value came out at 1.2:1 over a white
                    // panda. A surface of its own is also what an input should look like.
                    .background(colors.surfaceContainerHigh, shape)
                    .border(1.dp, colors.outline, shape)
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
