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

    @Test
    fun `interceptors respect priority and order`() {
        val pipeline = Pipeline<String>()

        pipeline.intercept(priority = PipelinePriority.Low) { s -> proceedWith("$s-low") }
        pipeline.intercept(priority = PipelinePriority.High) { s -> proceedWith("$s-high0") }
        pipeline.intercept(priority = PipelinePriority.High, order = 1) { s -> proceedWith("$s-high1") }
        pipeline.intercept(priority = PipelinePriority.Normal, order = 5) { s -> proceedWith("$s-normalA") }
        pipeline.intercept(priority = PipelinePriority.Normal, order = 5) { s -> proceedWith("$s-normalB") }
        pipeline.intercept(priority = PipelinePriority.Normal, order = -1) { s -> proceedWith("$s-normalC") }

        val result = pipeline.execute("S")

        assertEquals("S-high1-high0-normalA-normalB-normalC-low", result)
    }
}
