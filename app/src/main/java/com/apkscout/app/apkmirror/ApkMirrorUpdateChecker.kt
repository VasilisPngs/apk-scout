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
        val searchResult = ApkMirrorHtmlFetcher.fetchSearchPage(packageName)

        return when (searchResult) {
            is ApkMirrorFetchResult.Success -> {
                val links = ApkMirrorSearchParser.parseReleaseLinks(searchResult.html)
                val firstReleaseUrl = links.firstOrNull()

                if (firstReleaseUrl == null) {
                    AppUpdateStatus.NoCompatibleApk
                } else {
                    when (val releaseResult = ApkMirrorHtmlFetcher.fetchReleasePage(firstReleaseUrl)) {
                        is ApkMirrorFetchResult.Success -> {
                            AppUpdateStatus.ReleasePageLoaded(
                                releaseUrl = releaseResult.url
                            )
                        }

                        is ApkMirrorFetchResult.HttpError -> {
                            AppUpdateStatus.Error(
                                message = "APKMirror release HTTP ${releaseResult.code}: ${releaseResult.message}"
                            )
                        }

                        is ApkMirrorFetchResult.NetworkError -> {
                            AppUpdateStatus.Error(
                                message = "APKMirror release network error: ${releaseResult.message}"
                            )
                        }
                    }
                }
            }

            is ApkMirrorFetchResult.HttpError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror search HTTP ${searchResult.code}: ${searchResult.message}"
                )
            }

            is ApkMirrorFetchResult.NetworkError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror search network error: ${searchResult.message}"
                )
            }
        }
    }
}
