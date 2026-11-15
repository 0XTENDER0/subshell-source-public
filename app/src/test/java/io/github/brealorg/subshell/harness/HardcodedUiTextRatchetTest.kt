package io.github.brealorg.subshell.harness

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TRANSLATE13_HARDCODED_UI_TEXT_RATCHET_V1 (#299): prose written as a string literal
 * in the UI packages cannot be translated. This counts, per file, the literals that
 * look like text a reader sees (a capital letter and a lower-case letter first, no
 * code-like characters, outside comments, logs, comparisons and keys) and fails if a
 * file has more than `hardcoded-ui-text-baseline.txt` allows. The baseline can only
 * go down: move the text to `strings.xml` (`stringResource` in Compose, `UiText` or
 * `Context.getString` elsewhere) and lower the number.
 *
 * Update the baseline after a real reduction with:
 *   SUBSHELL_UPDATE_HARDCODED_BASELINE=1 ./gradlew --offline --no-daemon --no-configuration-cache \
 *     :app:testDebugUnitTest --tests '*HardcodedUiTextRatchetTest'
 */
class HardcodedUiTextRatchetTest {
    private val root: File by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java").isDirectory }
            ?.let { File(it, "app") }
            ?: File("app")
    }

    private val skipLine = Regex(
        """Log\.|const val|require\(|check\(|error\(|testTag|Regex|\.startsWith|\.endsWith|\.contains\(|==|!=|""" +
            """\.equals\(|\.removePrefix|\.removeSuffix|\.replace\(|\.split\(|\.indexOf|\.trim|import |@Suppress|""" +
            """@Deprecated|Exception\(|SharedPreferences|\bkey = |name = "|route|putString|getString\(|Intent|MIME|mime|assert""",
    )
    private val literal = Regex(""""([^"\\\n]*+(?:\\.[^"\\\n]*+)*+)"""")
    private val prose = Regex("""[A-Z][a-z].*""")
    private val codeLike = Regex("""[a-z]+[_/.:-][a-z]|^[A-Z]{2,}$""")
    private val excluded = listOf(
        "DiagnosticsScreen", "RedditApiValuesEditor", "SensitiveMediaLab", "Terminal",
        "/data/", "/ui/error/", "UiText.kt", "Symbols.kt", "SettingsSearchIndex.kt",
    )

    private fun countsByFile(): Map<String, Int> {
        val main = File(root, "src/main/java")
        val counts = sortedMapOf<String, Int>()
        main.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relative = file.relativeTo(main).path
            if (!relative.contains("/ui/") && !relative.contains("/inbox/") && !relative.contains("/widget/") &&
                !relative.contains("/update/") && !relative.contains("/applock/")
            ) {
                return@forEach
            }
            if (excluded.any { relative.contains(it) }) return@forEach
            var inBlock = false
            var count = 0
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("/*")) inBlock = !trimmed.contains("*/")
                if (inBlock) {
                    if (trimmed.contains("*/")) inBlock = false
                    return@forEach
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEach
                if (skipLine.containsMatchIn(line) || line.contains("R.string")) return@forEach
                literal.findAll(line).forEach { match ->
                    val text = match.groupValues[1]
                    if (text.length >= 4 && prose.matches(text) && !codeLike.containsMatchIn(text)) count++
                }
            }
            if (count > 0) counts[relative] = count
        }
        return counts
    }

    private val baselineFile: File get() = File(root, "src/test/resources/hardcoded-ui-text-baseline.txt")

    private fun baseline(): Map<String, Int> =
        if (!baselineFile.isFile) emptyMap()
        else baselineFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate {
            val (count, path) = it.split(" ", limit = 2)
            path to count.toInt()
        }

    @Test
    fun noFileHasMoreHardcodedUiTextThanTheBaselineAllows() {
        val counts = countsByFile()
        if (System.getenv("SUBSHELL_UPDATE_HARDCODED_BASELINE") == "1") {
            baselineFile.parentFile?.mkdirs()
            baselineFile.writeText(
                "# hardcoded UI text per file (HardcodedUiTextRatchetTest). Only ever lower these numbers.\n" +
                    counts.entries.joinToString("\n") { "${it.value} ${it.key}" } + "\n",
            )
            return
        }
        val allowed = baseline()
        val over = counts.filter { (file, count) -> count > (allowed[file] ?: 0) }
        assertTrue(
            "New hardcoded UI text (move it to strings.xml): " +
                over.entries.joinToString { "${it.key} has ${it.value}, allowed ${allowed[it.key] ?: 0}" },
            over.isEmpty(),
        )
    }
}
