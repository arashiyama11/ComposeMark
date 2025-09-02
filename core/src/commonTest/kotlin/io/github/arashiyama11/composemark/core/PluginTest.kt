package io.github.arashiyama11.composemark.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PluginTest {

    @Test
    fun test() = runTest {
        val composeMark = MyComposeMark()
        composeMark.setup()
        val result = composeMark.pipeline.execute("Initial Content")
        println("!!!!")
        println(result)
    }

}

private class SomePlugin : ComposeMarkPlugin<SomePlugin.Config> {

    class Config {
        var value: String = "Default"
    }

    override fun install(scope: ComposeMark, block: Config.() -> Unit) {
        val config = Config().apply(block)
        scope.pipeline.intercept {
            proceedWith(
                subject + " [Processed by SomePlugin with value: ${config.value}]"
            )
        }
    }
}

public class MyComposeMark : ComposeMark() {
    override fun setup() {
        install(SomePlugin()) {
            value = "Custom"
        }
    }
}