package io.github.arashiyama11.composemark.plugin.frontmatter

import kotlin.reflect.KClass

public typealias ConfigDecoder<T> = (FrontMatterContext) -> ConfigDecodeResult<T>

public interface FrontMatterContext {
    public val rawText: String
    public val formatHint: String?
    public val contentStartLine: Int
}

internal class FrontMatterContextImpl(
    private val section: ConfigSection,
) : FrontMatterContext {
    override val rawText: String get() = section.rawText
    override val formatHint: String? get() = section.formatHint
    override val contentStartLine: Int get() = section.contentStartLine
}

public sealed class ConfigDecodeResult<out T> {
    public data class Success<T>(val value: T) : ConfigDecodeResult<T>()
    public data class Failure(
        val message: String,
        val line: Int? = null,
        val column: Int? = null,
        val cause: Throwable? = null,
        val abort: Boolean = false,
    ) : ConfigDecodeResult<Nothing>()

    public object Skip : ConfigDecodeResult<Nothing>()
}

public data class ConfigError(
    val decoderId: String,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val cause: Throwable? = null,
)

public class FrontMatterConfigRegistry internal constructor(
    internal val entries: List<ConfigDecoderEntry<*>>,
    internal val onError: (ConfigError) -> Unit,
) {
    public companion object {
        public val Empty: FrontMatterConfigRegistry = Builder().build()

        public inline fun build(block: Builder.() -> Unit): FrontMatterConfigRegistry =
            Builder().apply(block).build()
    }

    public class Builder {
        @PublishedApi
        internal val entries: MutableList<ConfigDecoderEntry<*>> = mutableListOf()
        @PublishedApi
        internal var onError: (ConfigError) -> Unit = {}

        public inline fun <reified T : Any> decoder(
            id: String,
            priority: Int = 0,
            noinline block: ConfigDecoder<T>
        ) {
            entries += ConfigDecoderEntry(
                id = id,
                priority = priority,
                targetClass = T::class,
                decoder = block,
            )
        }

        public fun decoderAny(
            id: String,
            priority: Int = 0,
            block: ConfigDecoder<Any>
        ) {
            entries += ConfigDecoderEntry(
                id = id,
                priority = priority,
                targetClass = Any::class,
                decoder = block,
            )
        }

        public fun onError(block: (ConfigError) -> Unit) {
            onError = block
        }

        public fun build(): FrontMatterConfigRegistry {
            val ordered = entries.sortedWith(
                compareByDescending<ConfigDecoderEntry<*>> { it.priority }
                    .thenBy { it.id }
            )
            return FrontMatterConfigRegistry(ordered, onError)
        }
    }
}

@PublishedApi
internal data class ConfigDecoderEntry<T : Any>(
    val id: String,
    val priority: Int,
    val targetClass: KClass<T>,
    val decoder: ConfigDecoder<T>,
) {
    fun accepts(request: KClass<*>): Boolean {
        if (targetClass == Any::class) return true
        return targetClass == request
    }
}

internal data class RegistryDecodeOutcome<T : Any>(
    val value: T?,
    val errors: List<ConfigError>,
)

internal fun <T : Any> FrontMatterConfigRegistry.decode(
    section: ConfigSection,
    request: KClass<T>,
): RegistryDecodeOutcome<T> {
    val context = FrontMatterContextImpl(section)
    val errors = mutableListOf<ConfigError>()
    val candidates = entries.filter { it.accepts(request) }
    if (candidates.isEmpty()) {
        return RegistryDecodeOutcome(null, emptyList())
    }

    for (entry in candidates) {
        val result = try {
            @Suppress("UNCHECKED_CAST")
            (entry.decoder as ConfigDecoder<T>)(context)
        } catch (t: Throwable) {
            ConfigDecodeResult.Failure(
                message = t.message ?: "Decoder threw exception",
                cause = t,
                abort = true,
            )
        }

        when (result) {
            is ConfigDecodeResult.Success -> {
                val value = result.value
                if (request.isInstance(value)) {
                    return RegistryDecodeOutcome(value, errors)
                }
                errors += ConfigError(
                    decoderId = entry.id,
                    message = "Decoder returned incompatible type ${value::class.simpleName ?: value::class.toString()}",
                )
            }

            is ConfigDecodeResult.Failure -> {
                errors += ConfigError(
                    decoderId = entry.id,
                    message = result.message,
                    line = result.line,
                    column = result.column,
                    cause = result.cause,
                )
                if (result.abort) {
                    return RegistryDecodeOutcome(null, errors)
                }
            }

            ConfigDecodeResult.Skip -> {
                // continue to next decoder
            }
        }
    }

    return RegistryDecodeOutcome(null, errors)
}
