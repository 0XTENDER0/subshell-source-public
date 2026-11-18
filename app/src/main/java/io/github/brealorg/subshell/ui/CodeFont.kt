package io.github.brealorg.subshell.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.brealorg.subshell.R

enum class CodeFont(
    @androidx.annotation.StringRes val labelRes: Int,
    val typefaceName: String,
) {
    JETBRAINS_MONO(R.string.code_font_balanced, "JetBrains Mono"),
    IOSEVKA(R.string.code_font_narrow, "Iosevka"),
    RECURSIVE_MONO_CASUAL(R.string.code_font_casual, "Recursive Mono Casual"),
}

val CodeFont.fontFamily: FontFamily
    get() = when (this) {
        CodeFont.JETBRAINS_MONO -> FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        )
        CodeFont.IOSEVKA -> FontFamily(
            Font(R.font.iosevka_compact_regular, FontWeight.Normal),
        )
        CodeFont.RECURSIVE_MONO_CASUAL -> FontFamily(
            Font(R.font.recursive_mono_casual_regular, FontWeight.Normal),
        )
    }

val LocalCodeFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
