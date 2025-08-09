package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.processing.KSPLogger
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DefaultMarkdownLoaderTest {

    private val mockKspLogger = mockk<KSPLogger>(relaxed = true)

    @Test
    fun `load should read file from rootPath`() {
        // Arrange
        val rootPath = "src/test/resources/root_path_test"
        val relativePath = "docs/test.md"
        val loader = DefaultMarkdownLoader(rootPath)

        // Act

        val content =
            with(mockKspLogger) { loader.load(relativePath) }


        // Assert
        assertEquals("This is a test file for root_path.", content.trim())
    }

    @Test
    fun `load should read file from module root when rootPath is null`() {
        // Arrange
        val tempFile = File.createTempFile("temp_test", ".md").apply {
            writeText("content from module root")
            deleteOnExit()
        }
        val loader = DefaultMarkdownLoader(null)

        // Act
        val content = with(mockKspLogger) { loader.load(tempFile.path) }


        // Assert
        assertEquals("content from module root", content.trim())
    }

    @Test
    fun `load should throw exception for non-existent file`() {
        // Arrange
        val loader = DefaultMarkdownLoader("src/test/resources")

        // Act & Assert
        assertFails {
            with(mockk<KSPLogger>(relaxed = true)) {
                loader.load("non_existent_file.md")
            }
        }
    }
}
