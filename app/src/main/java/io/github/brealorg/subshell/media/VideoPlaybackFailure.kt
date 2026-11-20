package io.github.brealorg.subshell.media

import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.ui.UiText
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Bounded, structured metadata only: no URL, response body, headers or Throwable. */
data class VideoSourceFailure(
    val mediaId: String,
    val attemptId: Long,
    val sourceIndex: Int,
    val media3ErrorCode: Int,
    val httpStatusCode: Int? = null,
    val transportKind: VideoFailureTransportKind = VideoFailureTransportKind.OTHER,
)

enum class VideoFailureTransportKind {
    TIMEOUT,
    DNS,
    TLS,
    CONNECTION,
    // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: what RedGIFs answered for the clip.
    CLIP_UNAVAILABLE,
    PROVIDER_OFF,
    OTHER,
}

/** One playback attempt, including the source failures preceding its terminal failure. */
data class VideoPlaybackFailure(
    val mediaId: String,
    val attemptId: Long,
    val sources: List<VideoSourceFailure>,
) {
    init {
        require(sources.isNotEmpty())
        require(sources.all { it.mediaId == mediaId && it.attemptId == attemptId })
    }

    val finalSource: VideoSourceFailure
        get() = sources.last()
}

internal fun videoFailureBelongsToCurrentPlayback(
    activeMediaId: String?,
    playerMediaId: String?,
    hasOwner: Boolean,
    playRequested: Boolean,
    isCurrentPlayerError: Boolean,
): Boolean = activeMediaId != null && activeMediaId == playerMediaId &&
    hasOwner && playRequested && isCurrentPlayerError

internal fun currentVideoPlaybackFailure(
    failure: VideoPlaybackFailure?,
    mediaId: String?,
    attemptId: Long,
): VideoPlaybackFailure? = failure?.takeIf {
    it.mediaId == mediaId && it.attemptId == attemptId
}

/**
 * Read structured causes; never parse an exception message for a guessed status.
 * The caller supplies Media3's HTTP-exception adapter so this policy stays JVM-testable.
 */
internal fun videoSourceFailure(
    mediaId: String,
    attemptId: Long,
    sourceIndex: Int,
    media3ErrorCode: Int,
    cause: Throwable?,
    httpStatusCodeOf: (Throwable) -> Int?,
): VideoSourceFailure? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var next = cause
    while (next != null && causes.size < 32 && seen.add(next)) {
        causes += next
        next = next.cause
    }
    if (causes.any { it is CancellationException || it is InterruptedException }) return null
    val httpStatus = causes.asSequence()
        .mapNotNull(httpStatusCodeOf)
        .firstOrNull { it in 100..599 }
    val transportKind = when {
        causes.any { it is VideoClipUnavailableException } -> VideoFailureTransportKind.CLIP_UNAVAILABLE
        causes.any { it is VideoProviderOffException } -> VideoFailureTransportKind.PROVIDER_OFF
        causes.any { it is SocketTimeoutException } || media3ErrorCode == 2002 ->
            VideoFailureTransportKind.TIMEOUT
        causes.any { it is UnknownHostException } -> VideoFailureTransportKind.DNS
        causes.any { it is SSLException } -> VideoFailureTransportKind.TLS
        causes.any { it is ConnectException } -> VideoFailureTransportKind.CONNECTION
        else -> VideoFailureTransportKind.OTHER
    }
    return VideoSourceFailure(
        mediaId = mediaId,
        attemptId = attemptId,
        sourceIndex = sourceIndex,
        media3ErrorCode = media3ErrorCode,
        httpStatusCode = httpStatus,
        transportKind = transportKind,
    )
}

internal data class VideoPlaybackErrorCopy(val title: UiText, val message: UiText)

internal fun videoPlaybackErrorCopy(
    errorCode: Int?,
    failure: VideoPlaybackFailure?,
): VideoPlaybackErrorCopy {
    val finalSource = failure?.finalSource
    val result = videoSourceErrorCopy(finalSource?.media3ErrorCode ?: errorCode, finalSource)
    // Do not collapse unlike errors into a claim that every source returned the last status.
    val earlierTitles = failure?.sources.orEmpty().dropLast(1)
        .map { videoSourceErrorCopy(it.media3ErrorCode, it).title }
        .distinct()
        .filter { it != result.title }
    return if (earlierTitles.isEmpty()) result else result.copy(
        message = otherSourceAttempts(result.message, earlierTitles),
    )
}

// TRANSLATE14: up to three earlier titles, then "…" when there were more.
private fun otherSourceAttempts(message: UiText, titles: List<UiText>): UiText {
    val shown = titles.take(3)
    val res = when (shown.size) {
        1 -> R.string.video_err_other_source_attempts_1
        2 -> R.string.video_err_other_source_attempts_2
        else -> if (titles.size > 3) {
            R.string.video_err_other_source_attempts_more
        } else {
            R.string.video_err_other_source_attempts_3
        }
    }
    return UiText.Resource(res, message, *shown.toTypedArray())
}

private fun videoSourceErrorCopy(
    errorCode: Int?,
    failure: VideoSourceFailure?,
): VideoPlaybackErrorCopy {
    failure?.httpStatusCode?.let { status ->
        val reason = when (status) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            402 -> "Payment Required"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            409 -> "Conflict"
            410 -> "Gone"
            413 -> "Content Too Large"
            414 -> "URI Too Long"
            415 -> "Unsupported Media Type"
            416 -> "Range Not Satisfiable"
            422 -> "Unprocessable Content"
            425 -> "Too Early"
            426 -> "Upgrade Required"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            451 -> "Unavailable For Legal Reasons"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            505 -> "HTTP Version Not Supported"
            else -> null
        }
        val message = when (status) {
            401 -> UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_server)
            403 -> UiText.Resource(R.string.video_err_msg_access_denied_by_server)
            404 -> UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_request)
            408 -> UiText.Resource(R.string.video_err_msg_this_video_could_not_be_loaded_nthe_serv)
            410 -> UiText.Resource(R.string.video_err_msg_this_video_is_no_longer_available_from_t)
            416 -> UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_server_2)
            429 -> UiText.Resource(R.string.video_err_msg_this_video_could_not_be_loaded_right_now)
            451 -> UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_server_3)
            500 -> UiText.Resource(R.string.video_err_msg_this_video_could_not_be_loaded_nthe_serv_2)
            502 -> UiText.Resource(R.string.video_err_msg_this_video_could_not_be_loaded_na_server)
            503 -> UiText.Resource(R.string.video_err_msg_the_video_service_is_temporarily_unavail)
            504 -> UiText.Resource(R.string.video_err_msg_this_video_could_not_be_loaded_na_server_2)
            else -> UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_video_r)
        }
        return VideoPlaybackErrorCopy(
            // HTTP reason phrases are technical labels and stay English.
            title = UiText.Plain("HTTP $status" + (reason?.let { " · $it" } ?: "")),
            message = message,
        )
    }
    val kind = failure?.transportKind
    return when {
        kind == VideoFailureTransportKind.CLIP_UNAVAILABLE ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_clip_not_available), UiText.Resource(R.string.video_err_msg_this_clip_was_removed_or_is_no_longer_av))
        kind == VideoFailureTransportKind.PROVIDER_OFF ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_redgifs_playback_is_off), UiText.Resource(R.string.video_err_msg_turn_on_show_nsfw_posts_to_play_redgifs))
        kind == VideoFailureTransportKind.TIMEOUT || errorCode == 2002 ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_connection_timed_out), UiText.Resource(R.string.video_err_msg_the_video_server_did_not_respond_in_time))
        kind == VideoFailureTransportKind.DNS ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_dns_lookup_failed), UiText.Resource(R.string.video_err_msg_the_video_server_s_address_could_not_be))
        kind == VideoFailureTransportKind.TLS ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_tls_connection_failed), UiText.Resource(R.string.video_err_msg_a_secure_connection_to_the_video_server))
        kind == VideoFailureTransportKind.CONNECTION ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_connection_failed), UiText.Resource(R.string.video_err_msg_the_video_server_could_not_be_reached_ch))
        errorCode == 2004 -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_2004_http_request_failed), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_http_re))
        errorCode == 2005 -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_2005_file_not_found), UiText.Resource(R.string.video_err_msg_the_requested_video_file_could_not_be_fo))
        errorCode == 2006 -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_2006_no_permission), UiText.Resource(R.string.video_err_msg_permission_to_read_this_video_resource_w))
        errorCode == 2007 -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_2007_cleartext_not_permitted), UiText.Resource(R.string.video_err_msg_this_video_uses_an_insecure_connection_t))
        errorCode == 2008 -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_2008_read_position_out_of_range), UiText.Resource(R.string.video_err_msg_the_requested_position_is_outside_the_av))
        errorCode != null && errorCode in 3000..3999 ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_n_source_parsing_failed, errorCode), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_video_s))
        errorCode != null && errorCode in 4000..4999 ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_n_decoding_failed, errorCode), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_device))
        errorCode != null && errorCode in 5000..5999 ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_n_audio_output_failed, errorCode), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nplayback_fa))
        errorCode != null && errorCode in 6000..6999 ->
            VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_n_protected_playback_failed, errorCode), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_protect))
        errorCode != null -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_media3_n_playback_error, errorCode), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nthe_player))
        else -> VideoPlaybackErrorCopy(UiText.Resource(R.string.video_err_title_playback_error), UiText.Resource(R.string.video_err_msg_this_video_cannot_be_played_nno_further))
    }
}
