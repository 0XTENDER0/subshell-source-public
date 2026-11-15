package io.github.brealorg.subshell.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// TRANSLATE02_UITEXT_V1
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class UiText02ResolveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun plainIsReturnedAsWritten() {
        assertEquals("u/example", UiText.Plain("u/example").resolve(context))
    }

    @Test
    fun resourceFillsFormatArguments() {
        assertEquals(
            "u/example needs sign-in again",
            UiText.Resource(R.string.account_switcher_needs_sign_in, "example").resolve(context),
        )
    }

    @Test
    fun nestedUiTextArgumentsAreResolvedFirst() {
        assertEquals(
            "u/u/example needs sign-in again",
            UiText.Resource(
                R.string.account_switcher_needs_sign_in,
                UiText.Resource(R.string.account_switcher_user, "example"),
            ).resolve(context),
        )
    }

    @Test
    fun norwegianIsUsedWhenTheLocaleIsNorwegian() {
        val nb = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>().let { base ->
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(java.util.Locale.forLanguageTag("nb"))
            base.createConfigurationContext(config)
        }
        assertEquals("Kontoer", UiText.Resource(R.string.account_switcher_title).resolve(nb))
    }

    @Test
    fun pluralPicksTheFormForTheCount() {
        assertEquals("1 visit", UiText.Plural(R.plurals.reading_visits, 1, 1).resolve(context))
        assertEquals("2 visits", UiText.Plural(R.plurals.reading_visits, 2, 2).resolve(context))
        assertEquals("0 visits", UiText.Plural(R.plurals.reading_visits, 0, 0).resolve(context))
    }

    @Test
    fun equalValuesAreEqual() {
        assertEquals(
            UiText.Resource(R.string.selection_count, 3),
            UiText.Resource(R.string.selection_count, 3),
        )
        assertNotEquals(
            UiText.Resource(R.string.selection_count, 3),
            UiText.Resource(R.string.selection_count, 4),
        )
    }
}
