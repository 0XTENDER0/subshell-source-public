package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TRANSLATE14_ACCOUNT_AGE_IS_SHOWN_THROUGH_PLURALS_V1: the stored compact age
 * ("9y", "5mo", "3d") is shown as a translated plural; nb read "Redditor i 9 y".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Translate14AccountAgeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shown(stored: String): String {
        var text = ""
        composeRule.setContent { text = accountAgeText(stored) }
        composeRule.waitForIdle()
        return text
    }

    @Test
    @Config(sdk = [36], qualifiers = "en")
    fun theStoredCodeReadsAsWords() {
        assertEquals("9 years", shown("9y"))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en")
    fun oneMonthIsSingular() {
        assertEquals("1 month", shown("1mo"))
    }

    @Test
    @Config(sdk = [36], qualifiers = "en")
    fun anythingElseIsShownAsStored() {
        assertEquals("—", shown("—"))
    }

    @Test
    @Config(sdk = [36], qualifiers = "nb")
    fun norwegianSaysAar() {
        assertEquals("9 år", shown("9y"))
    }
}
