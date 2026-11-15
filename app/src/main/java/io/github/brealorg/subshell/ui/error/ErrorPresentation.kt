package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.toSafeTechnicalDetail
import io.github.brealorg.subshell.ui.UiText

// LOWUI4_NO_DIALOG_ERROR_KIND_V1 (OCB-133): no error was ever presented as a
// dialog; the DIALOG kind and ErrorPresentationDialog are gone, so every kind
// renders as a notice.
enum class ErrorPresentationKind {
    BLOCKING,
    DEGRADED,
    INLINE,
}

enum class ErrorActionKind {
    RETRY,
    SIGN_IN,
    OPEN_SETTINGS,
    DISMISS,
    OPEN_IN_BROWSER,
}

// TRANSLATE02_ERROR_TEXT_IS_UITEXT_V1 (#299): the words of an error are a UiText,
// resolved where the error is shown. The String constructors wrap ready text
// (Reddit's own wording, or text not moved to resources yet) as UiText.Plain.
data class ErrorAction(
    val kind: ErrorActionKind,
    val label: UiText,
) {
    constructor(kind: ErrorActionKind, label: String) : this(kind, UiText.Plain(label))

    init {
        require(label !is UiText.Plain || label.value.isNotBlank())
    }
}

data class ErrorPresentation(
    val kind: ErrorPresentationKind,
    val title: UiText,
    val message: UiText,
    val technicalDetail: String? = null,
    val primaryAction: ErrorAction? = null,
    val secondaryAction: ErrorAction? = null,
    val failureDetails: FailureDetails? = null,
) {
    constructor(
        kind: ErrorPresentationKind,
        title: String,
        message: String,
        technicalDetail: String? = null,
        primaryAction: ErrorAction? = null,
        secondaryAction: ErrorAction? = null,
        failureDetails: FailureDetails? = null,
    ) : this(
        kind,
        UiText.Plain(title),
        UiText.Plain(message),
        technicalDetail,
        primaryAction,
        secondaryAction,
        failureDetails,
    )

    init {
        require(title !is UiText.Plain || title.value.isNotBlank())
        require(message !is UiText.Plain || message.value.isNotBlank())
        require(technicalDetail == null || technicalDetail.isNotBlank())
        require(primaryAction == null || primaryAction != secondaryAction)
    }

    fun withFailureDetails(details: FailureDetails?): ErrorPresentation {
        details ?: return this
        return copy(
            technicalDetail = technicalDetail ?: details.toSafeTechnicalDetail(),
            failureDetails = details,
        )
    }
}
