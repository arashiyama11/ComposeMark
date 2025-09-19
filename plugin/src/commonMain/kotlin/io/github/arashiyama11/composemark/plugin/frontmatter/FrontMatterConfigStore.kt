package io.github.arashiyama11.composemark.plugin.frontmatter

import io.github.arashiyama11.composemark.core.ImmutablePreProcessorMetadata
import io.github.arashiyama11.composemark.core.PreProcessorMetadata
import io.github.arashiyama11.composemark.core.PreProcessorMetadataKey
import kotlin.reflect.KClass

internal class FrontMatterConfigStore(
    val section: ConfigSection,
    val defaultRegistry: FrontMatterConfigRegistry,
) {
    private val cache: MutableMap<CacheKey, ConfigCacheEntry> = mutableMapOf()

    fun <T : Any> decode(
        target: KClass<T>,
        registry: FrontMatterConfigRegistry,
    ): T? {
        val key = CacheKey(registry, target)
        val cached = cache[key]
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached.value as? T
        }

        val outcome = registry.decode(section, target)
        val entry = ConfigCacheEntry(value = outcome.value, errors = outcome.errors)
        cache[key] = entry

        if (outcome.errors.isNotEmpty()) {
            outcome.errors.forEach(registry.onError)
        }

        return outcome.value
    }

    fun errors(): List<ConfigError> = cache.values.flatMap { it.errors }

    fun raw(): String = section.rawText

    fun formatHint(): String? = section.formatHint
}

private data class CacheKey(
    val registry: FrontMatterConfigRegistry,
    val target: KClass<*>,
)

private data class ConfigCacheEntry(
    val value: Any?,
    val errors: List<ConfigError>,
)

@PublishedApi
internal val FrontMatterConfigStoreKey: PreProcessorMetadataKey<FrontMatterConfigStore> =
    PreProcessorMetadataKey("frontmatter.config.store")

internal fun PreProcessorMetadata.storeFrontMatterConfig(store: FrontMatterConfigStore) {
    this[FrontMatterConfigStoreKey] = store
}

@PublishedApi
internal fun ImmutablePreProcessorMetadata.findFrontMatterConfigStore(): FrontMatterConfigStore? =
    this[FrontMatterConfigStoreKey]

public fun ImmutablePreProcessorMetadata.frontMatterConfigRaw(): String? =
    findFrontMatterConfigStore()?.raw()

public fun ImmutablePreProcessorMetadata.frontMatterConfigErrors(): List<ConfigError> =
    findFrontMatterConfigStore()?.errors().orEmpty()

public fun <T : Any> ImmutablePreProcessorMetadata.configOrNull(
    target: KClass<T>,
    registryOverride: FrontMatterConfigRegistry? = null,
): T? {
    val store = findFrontMatterConfigStore() ?: return null
    val registry = registryOverride ?: store.defaultRegistry
    return store.decode(target, registry)
}

public inline fun <reified T : Any> ImmutablePreProcessorMetadata.configOrNull(
    registryOverride: FrontMatterConfigRegistry? = null,
): T? = configOrNull(T::class, registryOverride)

public fun <T : Any> ImmutablePreProcessorMetadata.requireConfig(
    target: KClass<T>,
    registryOverride: FrontMatterConfigRegistry? = null,
): T {
    return configOrNull(target, registryOverride)
        ?: error("Config for ${target.simpleName ?: target} was not available")
}

public inline fun <reified T : Any> ImmutablePreProcessorMetadata.requireConfig(
    registryOverride: FrontMatterConfigRegistry? = null,
): T = requireConfig(T::class, registryOverride)

public fun <T : Any> ImmutablePreProcessorMetadata.configOrElse(
    target: KClass<T>,
    registryOverride: FrontMatterConfigRegistry? = null,
    fallback: () -> T,
): T = configOrNull(target, registryOverride) ?: fallback()

public inline fun <reified T : Any> ImmutablePreProcessorMetadata.configOrElse(
    registryOverride: FrontMatterConfigRegistry? = null,
    noinline fallback: () -> T,
): T = configOrElse(T::class, registryOverride, fallback)
