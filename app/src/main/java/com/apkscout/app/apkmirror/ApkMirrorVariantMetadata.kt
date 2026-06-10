package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.ApkFormat

data class ApkMirrorVariantMetadata(
    val title: String?,
    val versionCode: Long?,
    val format: ApkFormat,
    val architectures: List<String>,
    val dpi: String?,
    val minSdk: Int?
)
