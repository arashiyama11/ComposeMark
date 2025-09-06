package io.github.arashiyama11.composemark.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineControlTest {

    @Test
    fun `interceptors run in registration order and propagate subject`() {
        val p = Pipeline<String>()
        p.intercept { s -> proceedWith(s + "-A") }
        p.intercept { s -> proceedWith(s + "-B") }
        p.intercept { s -> proceedWith(s + "-C") }

        val result = p.execute("S")
        assertEquals("S-A-B-C", result)
    }

    @Test
    fun `finish stops subsequent interceptors`() {
        val p = Pipeline<String>()
        p.intercept { s -> proceedWith(s + "-1") }
        p.intercept { s ->
            // mutate and stop the chain without proceeding further
            subject = s + "-2"
            finish()
        }
        p.intercept { s -> proceedWith(s + "-3") }

        val result = p.execute("S")
        // third interceptor should not run
        assertEquals("S-1-2", result)
    }
}
