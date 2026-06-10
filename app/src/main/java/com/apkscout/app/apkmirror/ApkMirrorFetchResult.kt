package com.apkscout.app.apkmirror

sealed interface ApkMirrorFetchResult {
    data class Success(
        val url: String,
        val html: String
    ) : ApkMirrorFetchResult

    data class HttpError(
        val code: Int,
        val message: String
    ) : ApkMirrorFetchResult

    data class NetworkError(
        val message: String
    ) : ApkMirrorFetchResult
}
