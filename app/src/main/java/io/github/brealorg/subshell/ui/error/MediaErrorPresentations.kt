package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.media.isRemoteMediaGone

internal enum class RemoteMediaKind {
    IMAGE,
    GIF,
}

internal fun remoteMediaErrorPresentation(
    kind: RemoteMediaKind,
    details: FailureDetails,
    sourceUri: String? = null,
): ErrorPresentation {
    val noun = when (kind) {
        RemoteMediaKind.IMAGE -> UiText.Resource(R.string.error_media_noun_image)
        RemoteMediaKind.GIF -> UiText.Resource(R.string.error_media_noun_gif)
    }
    // MEDIAGONE01_A_GONE_SOURCE_READS_REMOVED_V1 (owner 2026-10-18): a 404/410
    // from the source means the media was removed; it is shown as such, without
    // an action, and is not recorded as an error.
    if (details.isRemoteMediaGone()) {
        return ErrorPresentation(
            kind = ErrorPresentationKind.INLINE,
            title = if (kind == RemoteMediaKind.IMAGE) UiText.Resource(R.string.error_media_image_removed) else UiText.Resource(R.string.error_media_gif_removed),
            message = UiText.Resource(R.string.error_media_it_is_no_longer_available_where_it),
        ).withFailureDetails(details)
    }
    val message = when {
        details.transportKind != null -> UiText.Resource(R.string.error_media_subshell_couldn_t_reach_the_source, noun)
        details.httpStatusCode != null &&
            details.httpStatusCode in 300..399 &&
            details.localKind == LocalFailureKind.SECURITY ->
            UiText.Resource(R.string.error_media_the_source_redirected_to_a_location_blocked, noun)
        details.httpStatusCode != null && details.httpStatusCode in 300..399 ->
            UiText.Resource(R.string.error_media_the_source_redirected_the_request, noun)
        details.httpStatusCode != null -> UiText.Resource(R.string.error_media_the_source_rejected_the_request, noun)
        details.protocolKind != null -> UiText.Resource(R.string.error_media_the_source_returned_data_subshell_couldn_t, noun)
        details.localKind == LocalFailureKind.DECODING -> UiText.Resource(R.string.error_media_subshell_couldn_t_decode_this, noun)
        details.localKind == LocalFailureKind.SECURITY -> UiText.Resource(R.string.error_media_this_source_isn_t_allowed_by_subshell, noun)
        details.localKind == LocalFailureKind.VALIDATION -> UiText.Resource(R.string.error_media_this_has_an_invalid_source_address, noun)
        else -> UiText.Resource(R.string.error_media_subshell_couldn_t_load_this, noun)
    }
    // ISSUE228_RETRY_ONLY_WHEN_IT_CAN_HELP_V1 (owner, #228): Retry only for a
    // failure that can pass (connection, a server that may answer later). A
    // blocked, invalid or undecodable source offers the browser instead
    // (ISSUE228_BLOCKED_MEDIA_OPENS_IN_THE_BROWSER_V1).
    val action = when {
        details.retryable != false -> ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_media_retry))
        mediaSourceHost(sourceUri) != null -> ErrorAction(ErrorActionKind.OPEN_IN_BROWSER, UiText.Resource(R.string.error_media_open_in_browser))
        else -> null
    }
    return ErrorPresentation(
        kind = ErrorPresentationKind.INLINE,
        title = UiText.Resource(R.string.error_media_couldn_t_load, noun),
        message = message,
        primaryAction = action,
    ).withFailureDetails(details)
}

/** The host a blocked media link points at, shown next to "Open in browser". */
internal fun mediaSourceHost(sourceUri: String?): String? =
    sourceUri
        ?.let { runCatching { java.net.URI(it.trim()) }.getOrNull() }
        ?.takeIf { it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true) }
        ?.host
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)

internal fun videoPlaybackFailureDetails(errorCode: Int?): FailureDetails {
    val localKind = when {
        errorCode != null && errorCode in 3000..3999 -> LocalFailureKind.VALIDATION
        errorCode != null && errorCode in 4000..4999 -> LocalFailureKind.DECODING
        errorCode != null && errorCode in 5000..5999 -> LocalFailureKind.IO
        errorCode != null && errorCode in 6000..6999 -> LocalFailureKind.SECURITY
        else -> null
    }
    return FailureDetails(
        retryable = errorCode == null || errorCode !in 4000..6999,
        localKind = localKind,
    ).withMediaCode(errorCode?.let(::videoPlaybackCodeLabel))
}

private fun videoPlaybackCodeLabel(errorCode: Int): String = when (errorCode) {
    1002 -> "Behind live window"
    1003 -> "Playback timeout"
    2001 -> "Network connection failed"
    2002 -> "Network connection timeout"
    2003 -> "Invalid HTTP content type"
    2004 -> "Bad HTTP status"
    2005 -> "Media file not found"
    2006 -> "Media permission denied"
    2007 -> "Cleartext media blocked"
    2008 -> "Read position out of range"
    3001 -> "Malformed media container"
    3002 -> "Unsupported media container"
    4001 -> "Decoder initialization failed"
    4002 -> "Decoder query failed"
    4003 -> "Decoding failed"
    4004 -> "Decoding format exceeded capabilities"
    4005 -> "Decoding format unsupported"
    6002 -> "DRM license acquisition failed"
    6003 -> "DRM content error"
    6004 -> "DRM system error"
    6006 -> "DRM license expired"
    else -> "Playback error $errorCode"
}

// ISSUE11A3_ACTUAL_ERROR_THEN_EXPLANATION_V1
internal fun videoPlaybackErrorPresentation(
    state: io.github.brealorg.subshell.media.VideoPlaybackUiState,
): ErrorPresentation {
    val failure = state.failure?.takeIf {
        state.phase == io.github.brealorg.subshell.media.VideoPlaybackPhase.ERROR &&
            it.mediaId == state.mediaId
    }
    val copy = io.github.brealorg.subshell.media.videoPlaybackErrorCopy(state.errorCode, failure)
    // LOWUI4_VIDEO_FAILURE_KEEPS_ITS_DETAILS_V1 (OCB-169): the diagnostics get
    // the media code and HTTP status; the visible text stays the copy above.
    val details = videoPlaybackFailureDetails(failure?.finalSource?.media3ErrorCode ?: state.errorCode)
        .copy(httpStatusCode = failure?.finalSource?.httpStatusCode)
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = copy.title,
        message = copy.message,
        technicalDetail = null,
        primaryAction = ErrorAction(ErrorActionKind.RETRY, UiText.Resource(R.string.error_media_retry)),
        failureDetails = details,
    )
}
