package com.apkscout.app.apkmirror

import com.apkscout.app.core.compat.CompatibilityPolicy
import com.apkscout.app.core.model.ApkFormat
import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.core.model.DeviceSpec
import com.apkscout.app.core.model.UpdateCandidate

object ApkMirrorUpdateChecker {
    suspend fun check(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean
    ): AppUpdateStatus {
        val searchResult = ApkMirrorHtmlFetcher.fetchSearchPage(packageName)

        return when (searchResult) {
            is ApkMirrorFetchResult.Success -> handleSearchSuccess(
                packageName = packageName,
                installedVersionCode = installedVersionCode,
                device = device,
                regularApkOnly = regularApkOnly,
                html = searchResult.html
            )

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

    private suspend fun handleSearchSuccess(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean,
        html: String
    ): AppUpdateStatus {
        val links = ApkMirrorSearchParser.parseReleaseLinks(html)
        val firstReleaseUrl = links.firstOrNull() ?: return AppUpdateStatus.NoCompatibleApk

        return when (val releaseResult = ApkMirrorHtmlFetcher.fetchReleasePage(firstReleaseUrl)) {
            is ApkMirrorFetchResult.Success -> handleReleaseSuccess(
                packageName = packageName,
                installedVersionCode = installedVersionCode,
                device = device,
                regularApkOnly = regularApkOnly,
                html = releaseResult.html,
                releaseUrl = releaseResult.url
            )

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

    private suspend fun handleReleaseSuccess(
        packageName: String,
        installedVersionCode: Long,
        device: DeviceSpec,
        regularApkOnly: Boolean,
        html: String,
        releaseUrl: String
    ): AppUpdateStatus {
        val metadata = ApkMirrorReleaseParser.parse(
            html = html,
            releaseUrl = releaseUrl
        ) ?: return AppUpdateStatus.Error(
            message = "Release page loaded, but metadata could not be parsed."
        )

        val variants = ApkMirrorReleaseParser.parseVariantLinks(html)

        if (variants.isEmpty()) {
            return AppUpdateStatus.ReleaseMetadataParsed(
                title = metadata.title,
                versionCode = metadata.versionCode,
                releaseUrl = metadata.releaseUrl
            )
        }

        val regularApkVariants = variants.filter { it.format == ApkFormat.APK }
        val nonApkCount = variants.size - regularApkVariants.size

        if (regularApkVariants.isEmpty() && nonApkCount > 0) {
            return AppUpdateStatus.OnlyBundleFound
        }

        val compatibleRegularApks = regularApkVariants.filter { variant ->
            val candidate = UpdateCandidate(
                packageName = packageName,
                versionName = metadata.title,
                versionCode = metadata.versionCode ?: installedVersionCode,
                format = variant.format,
                minSdk = variant.minSdk,
                architectures = variant.architectures,
                dpi = variant.dpi,
                webUrl = variant.url
            )

            CompatibilityPolicy.isCompatible(
                candidate = candidate,
                device = device,
                regularApkOnly = regularApkOnly
            )
        }

        val firstCompatibleVariant = compatibleRegularApks.firstOrNull()
            ?: return AppUpdateStatus.NoCompatibleApk

        return when (val variantResult = ApkMirrorHtmlFetcher.fetchVariantPage(firstCompatibleVariant.url)) {
            is ApkMirrorFetchResult.Success -> {
                val variantMetadata = ApkMirrorVariantParser.parse(
                    html = variantResult.html,
                    url = variantResult.url
                )

                val versionCode = variantMetadata.versionCode ?: metadata.versionCode

                if (versionCode == null) {
                    AppUpdateStatus.CompatibleApkCandidatesParsed(
                        title = metadata.title,
                        totalCount = variants.size,
                        regularApkCount = regularApkVariants.size,
                        compatibleApkCount = compatibleRegularApks.size,
                        nonApkCount = nonApkCount,
                        releaseUrl = metadata.releaseUrl
                    )
                } else {
                    val candidate = UpdateCandidate(
                        packageName = packageName,
                        versionName = variantMetadata.title ?: metadata.title,
                        versionCode = versionCode,
                        format = variantMetadata.format,
                        minSdk = variantMetadata.minSdk ?: firstCompatibleVariant.minSdk,
                        architectures = variantMetadata.architectures.ifEmpty { firstCompatibleVariant.architectures },
                        dpi = variantMetadata.dpi ?: firstCompatibleVariant.dpi,
                        webUrl = firstCompatibleVariant.url
                    )

                    if (!CompatibilityPolicy.isCompatible(candidate, device, regularApkOnly)) {
                        AppUpdateStatus.NoCompatibleApk
                    } else if (versionCode > installedVersionCode) {
                        AppUpdateStatus.UpdateAvailable(
                            versionName = candidate.versionName,
                            versionCode = versionCode,
                            format = candidate.format
                        )
                    } else {
                        AppUpdateStatus.UpToDate
                    }
                }
            }

            is ApkMirrorFetchResult.HttpError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror variant HTTP ${variantResult.code}: ${variantResult.message}"
                )
            }

            is ApkMirrorFetchResult.NetworkError -> {
                AppUpdateStatus.Error(
                    message = "APKMirror variant network error: ${variantResult.message}"
                )
            }
        }
    }
}
