package io.github.arashiyama11.composemark.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PathResolutionTest {
    @Test
    fun `explicit path has priority over inherited`() {
        assertEquals("explicit.md", resolvePath("explicit.md", "inherited.md"))
    }

    @Test
    fun `falls back to inherited path when explicit is null`() {
        assertEquals("inherited.md", resolvePath(null, "inherited.md"))
    }

    @Test
    fun `returns null when both are null`() {
        assertEquals(null, resolvePath(null, null))
    }
}

