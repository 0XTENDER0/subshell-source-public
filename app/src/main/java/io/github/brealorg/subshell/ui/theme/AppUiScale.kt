package io.github.brealorg.subshell.ui.theme

// ISSUE278_UI_SIZE_SCALES_THE_WHOLE_APP_V1 (owner 2026-10-28, reader request):
// one size for the whole interface — bars, rows, buttons, padding and text —
// applied by SubshellTheme to LocalDensity.density. Text size stays relative on
// top. Portable through the settings map.
enum class AppUiScale(val factor: Float, @androidx.annotation.StringRes val labelRes: Int) {
    SMALLEST(0.85f, io.github.brealorg.subshell.R.string.app_ui_scale_smallest),
    SMALLER(0.90f, io.github.brealorg.subshell.R.string.app_ui_scale_smaller),
    DEFAULT(1.0f, io.github.brealorg.subshell.R.string.app_ui_scale_default),
    LARGER(1.10f, io.github.brealorg.subshell.R.string.app_ui_scale_larger),
}
