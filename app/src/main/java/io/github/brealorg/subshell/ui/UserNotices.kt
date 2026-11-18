package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.error.presentNotice

// ISSUE222_NO_TOASTS_V1: one-off feedback goes to the viewport tray (the
// app's one message surface), not to Toasts. A failure keeps the error tone;
// a confirmation uses the neutral tone (owner decision).
internal const val USER_NOTICE_SCOPE = "user-notice"

internal fun ViewportErrorHostState?.noticeFailure(title: String, message: String) =
    noticeFailure(UiText.Plain(title), UiText.Plain(message))

internal fun ViewportErrorHostState?.noticeFailure(title: UiText, message: UiText) {
    this?.presentNotice(USER_NOTICE_SCOPE, title, message, TrayTone.ERROR)
}

// ISSUE238_CONFIRMATIONS_DISMISS_THEMSELVES_V1 (#238, owner): a confirmation
// ("Muted r/x") leaves the tray by itself; a failure stays until closed.
internal fun ViewportErrorHostState?.noticeInfo(title: String, message: String) =
    noticeInfo(UiText.Plain(title), UiText.Plain(message))

internal fun ViewportErrorHostState?.noticeInfo(title: UiText, message: UiText) {
    this?.presentNotice(USER_NOTICE_SCOPE, title, message, TrayTone.INFO, dismissesItself = true)
}

/**
 * Android 13 and newer confirm a copy themselves (Google: do not add your
 * own confirmation there); older versions get a neutral tray notice.
 */
internal fun ViewportErrorHostState?.noticeCopied(what: String, sdkInt: Int = Build.VERSION.SDK_INT) =
    noticeCopied(UiText.Plain(what), sdkInt)

/** TRANSLATE14: what was copied is a word that is translated too ("Link", "Code"). */
internal fun ViewportErrorHostState?.noticeCopied(what: UiText, sdkInt: Int = Build.VERSION.SDK_INT) {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        noticeInfo(UiText.Resource(R.string.nt_copied, what), UiText.Resource(R.string.nt_it_s_on_the_clipboard))
    }
}
