package cy.rfc6570

import cy.github.encodeURLComponent

private interface Value {
    val key: String
}
private class SingleValue(override val key: String, val value: String): Value
private class ListValue(override val key: String, val expand: Boolean, val list: List<String>): Value
private class MapValue(override val key: String, val expand: Boolean, val pairs: List<Pair<String, String>>): Value

fun String.expandURLFormat(keys: Map<String, *>): String = replace("""\{[^\}]+\}""".toRegex()) { mr ->
    val template = mr.value.removeSurrounding("{", "}")

    when {
        template.isEmpty() -> ""
        template.startsWith("?") -> template.removePrefix("?").mapList(keys).flatMap { it.mapify { it.encodeURLComponent() }.map { "${it.first}=${it.second}" } }.join("&", "?").toEmptyIf("?")
        template.startsWith("&") -> template.removePrefix("?").mapList(keys).flatMap { it.mapify { it.encodeURLComponent() }.map { "${it.first}=${it.second}" } }.join("&", "&").toEmptyIf("&")
        template.startsWith("+") -> template.removePrefix("+").mapList(keys).flatMap { it.flatten { it } }.join(",")  // TODO escape except slashes
        template.startsWith("#") -> template.removePrefix("#").mapList(keys).flatMap { it.flatten { it } }.join(",", "#").toEmptyIf("#")  // TODO escape except slashes
        template.startsWith(".") -> template.removePrefix(".").mapList(keys).flatMap { it.flatten { it.encodeURLComponent() } }.join(".", ".").toEmptyIf(".")
        template.startsWith(";") -> template.removePrefix(";").mapList(keys).flatMap { it.mapify { it.encodeURLComponent() }.map { "${it.first}=${it.second}" } }.join(";", ";").toEmptyIf(";")
        template.startsWith("/") -> template.removePrefix("/").mapList(keys).flatMap { it.flatten { it } }.join("/", "/").toEmptyIf("/")
        else -> template.mapList(keys).flatMap { it.flatten { it.encodeURLComponent() } }.join(",")
    }
}

fun main(args: Array<String>) {
    println("https://api.github.com/repos/{owner}/{repo}".expandURLFormat(mapOf(
            "owner" to "cy6erGn0m",
            "repo" to "dummy"
    )))

    println("https://api.github.com/user/repos{?type,page,per_page,sort}".expandURLFormat(mapOf(
            "page" to 7,
            "type" to "x"
    )))
}

private fun Value.flatten(encoder: (String) -> String): List<String> = when (this) {
    is SingleValue -> listOf(encoder(this.value))
    is ListValue -> when {
        this.expand -> this.list.map(encoder)
        this.list.isEmpty() -> emptyList()
        else -> listOf(this.list.map { encoder(it) }.join(","))
    }
    is MapValue -> when {
        this.expand -> this.pairs.map { "${encoder(it.first)}=${encoder(it.second)}" }
        this.pairs.isEmpty() -> emptyList()
        else -> listOf(this.pairs.map { "${encoder(it.first)}=${encoder(it.second)}" }.join(","))
    }
    else -> throw UnsupportedOperationException(this.javaClass.getName())
}

private fun Value.mapify(encoder: (String) -> String): List<Pair<String, String>> = when (this) {
    is SingleValue -> listOf(encoder(this.key) to encoder(this.value))
    is ListValue -> when {
        this.expand -> this.list.map { encoder(this.key) to encoder(it) }
        else -> listOf(encoder(this.key) to this.list.map { encoder(it) }.join(","))
    }
    is MapValue -> when {
        this.expand -> this.pairs.map { encoder(it.first) to encoder(it.second) }
        else -> listOf(encoder(this.key) to this.pairs.map { "${encoder(it.first)},${encoder(it.second)}" }.join(","))
    }
    else -> throw UnsupportedOperationException(this.javaClass.getName())
}

private fun String.mapList(keys: Map<String, *>): List<Value> = trim().split("\\s*,\\s*".toRegex()).flatMap { key ->
    val expand = key.endsWith("*")
    val slice = ":\\d+$".toRegex().match(key)
    val sliceIndex = slice?.value?.removePrefix(":")?.toInt()
    val clearedKey = key.removeSuffix("*").removeSuffix(slice?.value ?: "")

    val value = keys[clearedKey]

    when (value) {
        null -> emptyList<Value>()
        is Map<*, *> -> listOf(MapValue(clearedKey, expand, value.entrySet().filter { it.key != null && it.value != null}.map { it.key.toString() to it.value.toString()} ))
        is Iterable<*> -> listOf(ListValue(clearedKey, expand, value.filterNotNull().map { it.toString() }))
        else -> listOf(SingleValue(clearedKey, value.toString().subStringIfIndexNotNull(sliceIndex)))
    }
}
private fun String.toEmptyIf(value: String) = if (this == value) "" else this
private fun String.subStringIfIndexNotNull(index: Int?) = if (index == null) this else take(index)
