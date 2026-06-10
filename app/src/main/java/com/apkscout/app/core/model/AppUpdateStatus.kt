package com.apkscout.app.core.model

sealed interface AppUpdateStatus {
    data object NotChecked : AppUpdateStatus
    data object Checking : AppUpdateStatus

    data class UpdateAvailable(
        val versionName: String,
        val versionCode: Long,
        val format: ApkFormat,
        val webUrl: String
    ) : AppUpdateStatus

    data class SearchResultsFound(
        val count: Int
    ) : AppUpdateStatus

    data class ReleasePageLoaded(
        val releaseUrl: String
    ) : AppUpdateStatus

    data class ReleaseMetadataParsed(
        val title: String,
        val versionCode: Long?,
        val releaseUrl: String
    ) : AppUpdateStatus

    data class VariantLinksParsed(
        val title: String,
        val totalCount: Int,
        val regularApkCount: Int,
        val nonApkCount: Int,
        val releaseUrl: String
    ) : AppUpdateStatus

    data class CompatibleApkCandidatesParsed(
        val title: String,
        val totalCount: Int,
        val regularApkCount: Int,
        val compatibleApkCount: Int,
        val nonApkCount: Int,
        val releaseUrl: String
    ) : AppUpdateStatus

    data object UpToDate : AppUpdateStatus
    data object NoCompatibleApk : AppUpdateStatus
    data object OnlyBundleFound : AppUpdateStatus
    data object IncompatibleVariant : AppUpdateStatus

    data class AutomatedCheckBlocked(
        val message: String
    ) : AppUpdateStatus

    data class Error(
        val message: String
    ) : AppUpdateStatus
}
