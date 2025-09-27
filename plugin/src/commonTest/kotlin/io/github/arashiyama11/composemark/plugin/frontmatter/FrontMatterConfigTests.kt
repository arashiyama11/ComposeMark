package io.github.arashiyama11.composemark.plugin.frontmatter

import io.github.arashiyama11.composemark.core.Block
import io.github.arashiyama11.composemark.core.PreProcessorMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontMatterConfigTests {

    @Test
    fun parseFrontMatterExtractsSectionAndBody() {
        val source = """
            ---
            title = "Hello"
            count = 3
            ---
            # Heading
            body
        """.trimIndent()

        val result = parseFrontMatter(source)
        assertTrue(result != null)
        result ?: return

        assertEquals("title = \"Hello\"\ncount = 3\n", result.section.rawText)
        assertEquals("toml", result.section.formatHint)
        assertEquals("# Heading\nbody", result.body.trimEnd())
    }

    @Test
    fun registryFallsBackToLaterDecoderAndCachesResult() {
        var firstDecoderCount = 0
        var secondDecoderCount = 0
        val recordedErrors = mutableListOf<ConfigError>()

        val registry = FrontMatterConfigRegistry.build {
            decoder<String>(id = "fail", priority = 10) { _ ->
                firstDecoderCount += 1
                ConfigDecodeResult.Failure(message = "invalid", abort = false)
            }

            decoder<String>(id = "success", priority = 5) { ctx ->
                secondDecoderCount += 1
                ConfigDecodeResult.Success(ctx.rawText.trim())
            }

            onError { recordedErrors += it }
        }

        val section = ConfigSection(
            rawText = " value ",
            formatHint = null,
            contentStartLine = 5,
        )
        val store = FrontMatterConfigStore(section, registry)

        val metadata = PreProcessorMetadata().also { it.storeFrontMatterConfig(store) }
        val snapshot = metadata.snapshot()

        val first = snapshot.configOrNull<String>()
        val second = snapshot.configOrNull<String>()

        assertEquals("value", first)
        assertEquals("value", second)
        assertEquals(1, firstDecoderCount, "failure decoder should run once")
        assertEquals(1, secondDecoderCount, "success decoder should run once")
        assertEquals(1, recordedErrors.size)
        assertEquals("fail", recordedErrors.first().decoderId)
    }

    @Test
    fun stripFrontMatterRemovesConsumedPrefix() {
        val consumed = """
            ---
            foo: bar
            ---

        """.trimIndent() + "\n"
        val blocks = listOf(
            Block.markdown(source = consumed + "# heading\n", path = null),
            Block.markdown(source = "second", path = null),
        )

        val stripped = stripFrontMatter(blocks, consumed)
        assertEquals(2, stripped.size)
        assertEquals("# heading\n", stripped.first().source)
        assertEquals("second", stripped[1].source)
    }

    @Test
    fun configOrNullReturnsNullWithoutStore() {
        val metadata = PreProcessorMetadata().snapshot()
        val result = metadata.configOrNull<String>()
        assertNull(result)
    }
}
