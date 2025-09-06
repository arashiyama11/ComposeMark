package io.github.arashiyama11.composemark.core

public typealias PipelineInterceptor<TSubject> = PipelineContext<TSubject>.(TSubject) -> Unit

public class Pipeline<T>() {

    private val interceptors = mutableListOf<PipelineInterceptor<T>>()

    public fun intercept(block: PipelineInterceptor<T>) {
        interceptors += block
    }

    public fun execute(
        subject: T,
    ): T {
        return SimplePipelineContext(interceptors, subject).execute(subject)
    }
}


public abstract class PipelineContext<T>() {
    internal abstract var subject: T
    public abstract fun finish()
    public abstract fun proceedWith(subject: T): T
    public abstract fun proceed(): T
    internal abstract fun execute(initial: T): T
}


internal class SimplePipelineContext<T>(
    private val interceptors: List<PipelineInterceptor<T>>,
    /**
     * Subject of this pipeline execution
     */
    override var subject: T,
) : PipelineContext<T>() {

    private var index = 0

    /**
     * Finishes current pipeline execution
     */
    override fun finish() {
        index = -1
    }

    /**
     * Continues execution of the pipeline with the given subject
     */
    override fun proceedWith(subject: T): T {
        this.subject = subject
        return proceed()
    }

    /**
     * Continues execution of the pipeline with the same subject
     */
    override fun proceed(): T {
        val index = index
        if (index < 0) return subject

        if (index >= interceptors.size) {
            finish()
            return subject
        }

        return proceedLoop()
    }

    override fun execute(initial: T): T {
        index = 0
        subject = initial
        return proceed()
    }

    private fun proceedLoop(): T {
        do {
            val index = index
            if (index == -1) {
                break
            }
            val interceptors = interceptors
            if (index >= interceptors.size) {
                finish()
                break
            }
            val executeInterceptor = interceptors[index]
            this.index = index + 1
            executeInterceptor.invoke(this, subject)
        } while (true)

        return subject
    }
}
