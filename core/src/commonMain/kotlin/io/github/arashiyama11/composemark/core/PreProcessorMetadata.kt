package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable


public val RenderContext.metadata: ImmutablePreProcessorMetadata?
    @Composable
    get() = LocalPreProcessorMetadata.current

public data class PreProcessorPipelineContent<T>(
    val data: T,
    val metadata: PreProcessorMetadata,
)

public class PreProcessorMetadataKey<T>(public val name: String)

public class PreProcessorMetadata(
    private val container: MutableMap<PreProcessorMetadataKey<*>, Any?> = mutableMapOf()
) {

    public operator fun <T> set(key: PreProcessorMetadataKey<T>, value: T) {
        container[key] = value
    }

    public operator fun <T> get(key: PreProcessorMetadataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return container[key] as? T
    }

    public fun snapshot(): ImmutablePreProcessorMetadata =
        ImmutablePreProcessorMetadata(container.toMap())
}

@Immutable
public class ImmutablePreProcessorMetadata internal constructor(
    internal val container: Map<PreProcessorMetadataKey<*>, Any?>
) {
    public operator fun <T> get(key: PreProcessorMetadataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return container[key] as? T
    }
}