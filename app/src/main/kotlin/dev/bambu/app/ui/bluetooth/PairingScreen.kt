package dev.bambu.app.ui.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bambu.app.R
import dev.bambu.app.ui.ScreenScaffold
import dev.bambu.core.logicalWidth
import dev.bambu.core.net.LinkState
import dev.bambu.core.net.Peer

/**
 * Pairing: host or join, pick a panda from the list, watch the link come up (§12).
 *
 * The screen shows the link's real state rather than a spinner that means nothing.
 * "Waiting for a player" and "connecting" and "agreeing on the match" are three
 * different things to be stuck on, and a player who can see which one it is knows
 * whether to wait or to press Cancel.
 */
@Composable
fun PairingScreen(
    nick: String,
    onReady: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val width = logicalWidth(configuration.screenWidthDp, configuration.screenHeightDp)

    LaunchedEffect(state.ready) {
        if (state.ready) onReady()
    }

    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.pair_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            state.error?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            when (state.role) {
                null ->
                    RoleChoice(
                        onHost = { viewModel.host(context, nick, width) },
                        onJoin = { viewModel.joinAs(context, nick) },
                    )

                PairingRole.HOST -> Status(stringResource(R.string.pair_hosting), state.link)

                PairingRole.GUEST ->
                    PeerList(
                        peers = state.peers,
                        link = state.link,
                        onPick = { viewModel.connectTo(it, nick, width) },
                    )
            }

            OutlinedButton(
                onClick = {
                    viewModel.cancel()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pair_cancel))
            }
        }
    }
}

@Composable
private fun RoleChoice(
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    Text(
        text = stringResource(R.string.pair_choose_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pair_host))
    }
    Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pair_join))
    }
}

@Composable
private fun PeerList(
    peers: List<Peer>,
    link: LinkState,
    onPick: (Peer) -> Unit,
) {
    if (peers.isEmpty()) {
        Status(stringResource(R.string.pair_searching), link)
        return
    }
    // A plain Column, not a LazyColumn: this list is the handful of devices in one room,
    // and it already sits inside the scaffold's scroll. Nesting a lazy list in a
    // scrolling parent is an unbounded-height crash waiting to happen.
    for (peer in peers) {
        Button(onClick = { onPick(peer) }, modifier = Modifier.fillMaxWidth()) {
            Text(peer.nick.ifBlank { stringResource(R.string.pair_unnamed) })
        }
    }
}

@Composable
private fun Status(
    label: String,
    link: LinkState,
) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Text(
        text = stringResource(linkLabel(link)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (link != LinkState.LOST) CircularProgressIndicator()
}

private fun linkLabel(link: LinkState): Int =
    when (link) {
        LinkState.IDLE -> R.string.link_idle
        LinkState.ADVERTISING -> R.string.link_advertising
        LinkState.DISCOVERING -> R.string.link_discovering
        LinkState.CONNECTING -> R.string.link_connecting
        LinkState.CONNECTED -> R.string.link_connected
        LinkState.LOST -> R.string.link_lost
    }
