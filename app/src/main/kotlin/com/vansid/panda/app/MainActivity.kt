package com.vansid.panda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vansid.panda.app.ui.BambuApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent { BambuApp() }
    }

    /**
     * The bars come back on their own and have to be sent away again.
     *
     * A permission dialog, the recents screen or a notification shade all restore them,
     * and Android does not undo that when focus returns. Without this the clock reappears
     * over the wind indicator after the first trip out of the app.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Hides the status and navigation bars (§13, §14).
     *
     * This game is landscape-only and draws a fixed logical canvas, so the ~28 dp the
     * status bar takes is both an obstruction — the clock and the battery sit exactly
     * where the wind indicator and the sun are — and vertical space the HUD needs. A
     * swipe brings the bars back transiently, which is the behaviour a player expects
     * from a game rather than from a form.
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
