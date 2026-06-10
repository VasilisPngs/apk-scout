package com.apkscout.app.apkmirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

object ApkMirrorHtmlFetcher {
    suspend fun fetchSearchPage(packageName: String): ApkMirrorFetchResult {
        return withContext(Dispatchers.IO) {
            val url = ApkMirrorSource.searchUrl(packageName).toString()

            runCatching {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection

                connection.connectTimeout = 12_000
                connection.readTimeout = 12_000
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android 16; Mobile) APKScout/0.1.0"
                )

                connection.useCaches = false

                val code = connection.responseCode
                val message = connection.responseMessage.orEmpty()

                if (code in 200..299) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }

                    ApkMirrorFetchResult.Success(
                        url = url,
                        html = html
                    )
                } else {
                    ApkMirrorFetchResult.HttpError(
                        code = code,
                        message = message
                    )
                }
            }.getOrElse { error ->
                ApkMirrorFetchResult.NetworkError(
                    message = error.message ?: error::class.java.simpleName
                )
            }
        }
    }
}
