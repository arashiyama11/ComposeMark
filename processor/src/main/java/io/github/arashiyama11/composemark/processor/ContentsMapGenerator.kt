package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration


internal fun findContentsMapDeclaration(
    classDecl: KSClassDeclaration
): KSPropertyDeclaration? {
    return classDecl.getAllProperties().firstOrNull { prop ->
        val mapType = prop.type.resolve()
        if (mapType.declaration.qualifiedName?.asString() != "kotlin.collections.Map") return@firstOrNull false
        val args = mapType.arguments
        if (args.size != 2) return@firstOrNull false

        val keyType = args[0].type?.resolve()
        if (keyType?.declaration?.qualifiedName?.asString() != "kotlin.String") return@firstOrNull false

        val valueTypeRef = args[1].type ?: return@firstOrNull false
        val valueType = valueTypeRef.resolve()

        val isComposable = valueTypeRef.annotations.any {
            it.shortName.asString() == "Composable"
        }
        if (!isComposable) return@firstOrNull false

        val declName =
            valueType.declaration.qualifiedName?.asString() ?: return@firstOrNull false
        if (!declName.matches(Regex("""kotlin\.Function\d+"""))) return@firstOrNull false

        val paramAndReturn =
            valueType.arguments.mapNotNull { it.type?.resolve()?.declaration?.qualifiedName?.asString() }
        if (paramAndReturn.size != 2) return@firstOrNull false
        val (param, ret) = paramAndReturn
        if (param != "androidx.compose.ui.Modifier" || ret != "kotlin.Unit") return@firstOrNull false
        true
    }
}

// Map<String,@Composable (Modifier) -> Unit>
internal fun generateContentsMap(
    isOverride: Boolean,
    propertyName: String = "contents",
    declaredFns: Sequence<KSFunctionDeclaration>
): Sequence<String> = sequence {
    yield("    ${if (isOverride) "override" else ""} val $propertyName: Map<String, @Composable (Modifier) -> Unit> = mapOf(")
    declaredFns.forEach { fnDecl ->

        val fnName = fnDecl.simpleName.asString()
        val acceptModifier =
            fnDecl.parameters.size == 1 && fnDecl.parameters.first().type.resolve().declaration.qualifiedName?.asString() == "androidx.compose.ui.Modifier"
        if (acceptModifier) {
            yield("        \"${fnName}\" to { modifier -> $fnName(modifier) },")
        } else {
            yield("        \"${fnName}\" to { _ -> $fnName() },")
        }
    }
    yield("    )")
}