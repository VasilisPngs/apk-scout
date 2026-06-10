package com.apkscout.app.core.model

sealed interface AppUpdateStatus {
    data object NotChecked : AppUpdateStatus
    data object Checking : AppUpdateStatus

    data class UpdateAvailable(
        val versionName: String,
        val versionCode: Long,
        val format: ApkFormat
    ) : AppUpdateStatus

    data object UpToDate : AppUpdateStatus
    data object NoCompatibleApk : AppUpdateStatus
    data object OnlyBundleFound : AppUpdateStatus
    data object IncompatibleVariant : AppUpdateStatus

    data class Error(
        val message: String
    ) : AppUpdateStatus
}
