package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.core.model.DeviceSpec

object ApkMirrorUpdateChecker {
    suspend fun check(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): AppUpdateStatus {
        val result = ApkMirrorHtmlFetcher.fetchSearchPage(packageName)

        return when (result) {
            is ApkMirrorFetchResult.Success -> {
                if (result.html.contains("APKMirror", ignoreCase = true)) {
                    AppUpdateStatus.Error(
                        message = "APKMirror search loaded. Parser is not connected yet."
                    )
                } else {
                    AppUpdateStatus.Error(
                        message = "APKMirror returned an unexpected page."
                    )
                }
            }

            is ApkMirrorFetchResult.HttpError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror HTTP ${result.code}: ${result.message}"
                )
            }

            is ApkMirrorFetchResult.NetworkError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror network error: ${result.message}"
                )
            }
        }
    }
}
