package com.apkscout.app.apkmirror

import android.net.Uri

object ApkMirrorSource {
    fun searchUrl(packageName: String): Uri {
        return Uri.Builder()
            .scheme("https")
            .authority("www.apkmirror.com")
            .appendQueryParameter("post_type", "app_release")
            .appendQueryParameter("searchtype", "apk")
            .appendQueryParameter("s", packageName)
            .build()
    }

    fun absoluteUrl(path: String?): String? {
        val value = path?.trim().orEmpty()

        if (value.isBlank()) return null

        return when {
            value.startsWith("https://www.apkmirror.com") -> value
            value.startsWith("/") -> "https://www.apkmirror.com$value"
            else -> null
        }
    }
}
