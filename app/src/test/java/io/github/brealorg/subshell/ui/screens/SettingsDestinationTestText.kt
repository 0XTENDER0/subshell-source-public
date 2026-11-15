package io.github.brealorg.subshell.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider

// TRANSLATE05: SettingsDestination holds string-resource ids; tests that ask for
// its English title or description read them here (Robolectric tests only).
private fun resolved(id: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id)

internal val SettingsDestination.title: String get() = resolved(titleRes)

internal val SettingsDestination.description: String get() = descriptionRes?.let(::resolved) ?: ""
