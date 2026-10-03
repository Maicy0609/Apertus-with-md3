package app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Apertus",
        state = rememberWindowState(width = androidx.compose.ui.unit.dp(420f), height = androidx.compose.ui.unit.dp(720f))
    ) {
        App()
    }
}
