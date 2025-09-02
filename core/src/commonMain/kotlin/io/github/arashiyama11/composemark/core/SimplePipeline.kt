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
        return DebugPipelineContext(interceptors, subject).execute(subject)
    }
}


public abstract class PipelineContext<TSubject>() {
    public abstract var subject: TSubject
    public abstract fun finish()
    public abstract fun proceedWith(subject: TSubject): TSubject
    public abstract fun proceed(): TSubject
    internal abstract fun execute(initial: TSubject): TSubject
}


internal class DebugPipelineContext<TSubject>(
    private val interceptors: List<PipelineInterceptor<TSubject>>,
    subject: TSubject,
) : PipelineContext<TSubject>() {
    /**
     * Subject of this pipeline execution
     */
    override var subject: TSubject = subject

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
    override fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    /**
     * Continues execution of the pipeline with the same subject
     */
    override fun proceed(): TSubject {
        val index = index
        if (index < 0) return subject

        if (index >= interceptors.size) {
            finish()
            return subject
        }

        return proceedLoop()
    }

    override fun execute(initial: TSubject): TSubject {
        index = 0
        subject = initial
        return proceed()
    }

    private fun proceedLoop(): TSubject {
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
