package org.example.consumer.test

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "consumer-test",
    ) {
        App()
    }
}