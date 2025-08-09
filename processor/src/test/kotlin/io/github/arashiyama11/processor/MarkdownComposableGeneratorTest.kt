//package io.github.arashiyama11.processor
//
//import com.google.devtools.ksp.symbol.KSFunctionDeclaration
//import io.github.arashiyama11.composemark.processor.MarkdownLoader
//import io.mockk.every
//import io.mockk.mockk
//import kotlin.test.Test
//import kotlin.test.assertEquals
//
//class MarkdownComposableGeneratorTest {
//
//    @Test
//    fun `generateRenderCall should call renderer Render if annotated by GenerateMarkdownFromSource`() {
//        val mockKSFunctionDeclaration: KSFunctionDeclaration = mockk {
//            every { annotations } returns sequenceOf(
//                mockk {
//                    every { shortName.asString() } returns "GenerateMarkdownFromSource"
//                    every { arguments } returns listOf(
//                        mockk {
//                            every { value } returns "mocked markdown content"
//                        }
//                    )
//                }
//            )
//        }
//
//        val result = generateRenderCall(mockKSFunctionDeclaration, mockk())
//        assertEquals(
//            result.joinToString("\n"),
//            "        renderer.Render(Modifier, null,  \"mocked markdown content\")"
//        )
//    }
//
//    @Test
//    fun `generateRenderCall should read markdown file if annotated by GenerateMarkdownFromPath`() {
//        val mockKSFunctionDeclaration: KSFunctionDeclaration = mockk {
//            every { annotations } returns sequenceOf(
//                mockk {
//                    every { shortName.asString() } returns "GenerateMarkdownFromPath"
//                    every { arguments } returns listOf(
//                        mockk {
//                            every { value } returns "path/to/markdown.md"
//                        }
//                    )
//                }
//            )
//        }
//
//        val mockMarkdownLoader = mockk<MarkdownLoader> {
//            every { load("path/to/markdown.md") } returns "mocked markdown content"
//        }
//
//
//        val result = generateRenderCall(mockKSFunctionDeclaration, mockMarkdownLoader)
//        assertEquals(
//            result.joinToString("\n"),
//            "        renderer.Render(Modifier, \"path/to/markdown.md\",  \"mocked markdown content\")"
//        )
//    }
//}