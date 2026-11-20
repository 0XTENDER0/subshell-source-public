package io.github.brealorg.subshell.harness

import java.io.File

// TRANSLATE04_SOURCE_TEXT_V1 (#299): many tests read a source file as text and
// look for the words the reader sees. When those words moved to string
// resources, the source holds `stringResource(R.string.name, args)` (or
// `UiText.Resource(R.string.name, args)`) instead of the literal. This reads a
// source file with every such reference expanded back into the English literal
// from `strings.xml` ("u/%1$s" with arg `x` becomes "u/${x}"), so a test keeps
// asking for the words, not for how the code spells them.
internal fun File.readSourceText(): String = expandStringResources(readText())

private val stringsFile: File by lazy {
    generateSequence(File(System.getProperty("user.dir") ?: ".").canonicalFile) { it.parentFile }
        .map { File(it, "app/src/main/res/values/strings.xml") }
        .firstOrNull { it.isFile }
        ?: File("src/main/res/values/strings.xml")
}

private val englishStrings: Map<String, String> by lazy {
    Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(stringsFile.readText())
        .associate { match ->
            match.groupValues[1] to match.groupValues[2]
                .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
                .replace("&lt;", "<").replace("&amp;", "&").replace("%%", "%")
        }
}

private val referenceStart = Regex("""(?:stringResource|UiText\.Resource)\(R\.string\.(\w+)""")

internal fun expandStringResources(source: String): String {
    val out = StringBuilder()
    var position = 0
    while (true) {
        val match = referenceStart.find(source, position) ?: break
        val open = source.indexOf('(', match.range.first)
        var depth = 0
        var end = open
        while (end < source.length) {
            when (source[end]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) break }
            }
            end++
        }
        val text = englishStrings[match.groupValues[1]]
        out.append(source, position, match.range.first)
        if (text == null || end >= source.length) {
            out.append(source, match.range.first, minOf(end + 1, source.length))
        } else {
            val argText = source.substring(match.range.last + 1, end).trimStart(',')
            val args = splitTopLevel(argText)
            var literal: String = text
            args.forEachIndexed { index, arg -> literal = literal.replace("%${index + 1}\$s", "\${${arg.trim()}}") }
            out.append('"').append(literal.replace("\"", "\\\"")).append('"')
        }
        position = minOf(end + 1, source.length)
    }
    out.append(source, position, source.length)
    return out.toString()
}

private fun splitTopLevel(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val parts = mutableListOf<String>()
    var depth = 0
    val current = StringBuilder()
    for (c in text) {
        when {
            c == ',' && depth == 0 -> { parts += current.toString(); current.clear() }
            else -> { if (c == '(' || c == '{' || c == '[') depth++ else if (c == ')' || c == '}' || c == ']') depth--; current.append(c) }
        }
    }
    parts += current.toString()
    return parts
}
