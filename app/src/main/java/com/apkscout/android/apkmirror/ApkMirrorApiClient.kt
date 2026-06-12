package com.apkscout.android.apkmirror

import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import com.apkscout.android.InstalledApp
import com.apkscout.android.UpdateInfo
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApkMirrorCheckResult(
    val updates: Map<String, UpdateInfo>,
    val error: String?
)

object ApkMirrorApiClient {
    private const val ENDPOINT = "https://www.apkmirror.com/wp-json/apkm/v1/app_exists/"
    private const val API_USER = "api-apkupdater"
    private const val API_TOKEN = "rm5rcfruUjKy04sMpyMPJXW8"
    private const val PACKAGE_CHUNK_SIZE = 60

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun checkUpdates(
        apps: List<InstalledApp>,
        packageManager: PackageManager? = null
    ): ApkMirrorCheckResult {
        return withContext(Dispatchers.IO) {
            val updates = linkedMapOf<String, UpdateInfo>()

            for (chunk in apps.chunked(PACKAGE_CHUNK_SIZE)) {
                val result = runCatching {
                    checkChunk(
                        apps = chunk,
                        packageManager = packageManager
                    )
                }.getOrElse { error ->
                    return@withContext ApkMirrorCheckResult(
                        updates = updates,
                        error = error.message ?: error::class.java.simpleName
                    )
                }

                updates.putAll(result)
            }

            ApkMirrorCheckResult(
                updates = updates,
                error = null
            )
        }
    }

    private fun checkChunk(
        apps: List<InstalledApp>,
        packageManager: PackageManager?
    ): Map<String, UpdateInfo> {
        if (apps.isEmpty()) return emptyMap()

        val installedByPackage = apps.associateBy { it.packageName }

        val bodyJson = JSONObject()
            .put("pnames", JSONArray(apps.map { it.packageName }))

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "APKScout/${Build.VERSION.SDK_INT}")
            .header("Authorization", Credentials.basic(API_USER, API_TOKEN))
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response: Response = client.newCall(request).execute()

        try {
            val body = response.body.string()

            if (response.code !in 200..299) {
                error("APKMirror API HTTP ${response.code}: ${body.cleanErrorBody()}")
            }

            return parseResponse(
                body = body,
                installedByPackage = installedByPackage,
                packageManager = packageManager
            )
        } finally {
            response.close()
        }
    }

    private fun parseResponse(
        body: String,
        installedByPackage: Map<String, InstalledApp>,
        packageManager: PackageManager?
    ): Map<String, UpdateInfo> {
        val root = JSONObject(body)

        if (root.has("status")) {
            val status = root.optInt("status", -1)

            if (status != 200) {
                error("APKMirror API status $status: ${body.cleanErrorBody()}")
            }
        }

        val data = root.optJSONArray("data")
            ?: error("APKMirror API response missing data: ${body.cleanErrorBody()}")

        val updates = linkedMapOf<String, UpdateInfo>()

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val packageName = item.optString("pname")
            val installed = installedByPackage[packageName] ?: continue

            if (!item.optBoolean("exists", true)) continue
            if (!isDeviceTargetCompatible(item, packageManager)) continue

            val release = item.optJSONObject("release") ?: continue

            if (!isDeviceTargetCompatible(release, packageManager)) continue

            val apks = item.optJSONArray("apks") ?: continue

            val selection = selectBestApk(
                apks = apks,
                installedVersionCode = installed.versionCode,
                packageManager = packageManager,
                item = item,
                release = release
            ) ?: continue

            val foundVersionCode = selection.apk.optVersionCode() ?: continue

            if (foundVersionCode <= installed.versionCode) continue

            val foundVersionName = release
                .optString("version")
                .takeIf { it.isNotBlank() }
                ?: continue

            val releaseUrl = ApkMirrorSource.absoluteUrl(release.optString("link"))
                ?: selection.apk.optString("link").takeIf { it.isNotBlank() }?.let { ApkMirrorSource.absoluteUrl(it) }
                ?: ApkMirrorSource.searchUrl(packageName).toString()

            updates[packageName] = UpdateInfo(
                versionName = foundVersionName,
                versionCode = foundVersionCode,
                url = releaseUrl,
                formatLabel = selection.formatLabel,
    packageFormat = resolveApkMirrorPackageFormat(
        html = body,
        versionCode = foundVersionCode,
        url = releaseUrl
    ))
        }

        return updates
    }

    private data class ApkSelection(
        val apk: JSONObject,
        val formatLabel: String
    )

    private fun selectBestApk(
        apks: JSONArray,
        installedVersionCode: Long,
        packageManager: PackageManager?,
        item: JSONObject,
        release: JSONObject
    ): ApkSelection? {
        val candidates = mutableListOf<JSONObject>()

        for (index in 0 until apks.length()) {
            val apk = apks.optJSONObject(index) ?: continue
            val versionCode = apk.optVersionCode() ?: continue

            if (versionCode <= installedVersionCode) continue
            if (!apk.isPrimaryReleaseVariant()) continue
            if (!isSdkCompatible(apk)) continue
            if (!isArchitectureCompatible(apk.optFlexibleArray("arches"))) continue
            if (!isDeviceTargetCompatible(apk, packageManager)) continue

            candidates += apk
        }

        if (candidates.isEmpty()) return null

        val bestApk = candidates.maxWithOrNull(
            compareBy<JSONObject> { it.optVersionCode() ?: 0L }
                .thenBy { deviceTargetScore(it, packageManager) }
                .thenBy { architectureScore(it) }
                .thenBy { dpiScore(it) }
                .thenBy { packageFormatScore(it) }
        ) ?: return null

        return ApkSelection(
            apk = bestApk,
            formatLabel = detectDisplayedPackageFormat(candidates, release, item)
        )
    }

    private fun JSONObject.optVersionCode(): Long? {
        val raw = opt("version_code") ?: opt("versionCode") ?: return null

        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> raw.toString().toLongOrNull()
        }
    }

    private fun JSONObject.isPrimaryReleaseVariant(): Boolean {
        return !variantTrackMetadata().containsNonPrimaryTrackSignal()
    }

    private fun JSONObject.variantTrackMetadata(): String {
        val values = mutableListOf<String>()

        val keys = listOf(
            "name",
            "title",
            "label",
            "slug",
            "link",
            "url",
            "download_url",
            "filename",
            "file_name",
            "variant",
            "variant_name",
            "variant_title",
            "channel",
            "track"
        )

        keys.forEach { key ->
            val value = opt(key)

            if (value != null && value != JSONObject.NULL) {
                values += key
                values += value.collectVariantTrackText()
            }
        }

        return values.joinToString(" ").lowercase()
    }

    private fun Any?.collectVariantTrackText(): String {
        return when (this) {
            null, JSONObject.NULL -> ""
            is JSONArray -> {
                (0 until length()).joinToString(" ") { index ->
                    opt(index).collectVariantTrackText()
                }
            }
            is JSONObject -> {
                val values = mutableListOf<String>()
                val iterator = keys()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    values += key
                    values += opt(key).collectVariantTrackText()
                }

                values.joinToString(" ")
            }
            else -> toString()
        }
    }

    private fun String.containsNonPrimaryTrackSignal(): Boolean {
        val normalized = lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(".", " ")

        return Regex("\\bbeta\\b").containsMatchIn(normalized) ||
            Regex("\\balpha\\b").containsMatchIn(normalized) ||
            Regex("\\bcanary\\b").containsMatchIn(normalized) ||
            Regex("\\bnightly\\b").containsMatchIn(normalized) ||
            Regex("\\bpreview\\b").containsMatchIn(normalized) ||
            Regex("\\bprerelease\\b").containsMatchIn(normalized) ||
            Regex("\\bpre release\\b").containsMatchIn(normalized) ||
            Regex("\\brc\\d*\\b").containsMatchIn(normalized) ||
            Regex("\\bdeveloper preview\\b").containsMatchIn(normalized) ||
            Regex("\\blite release\\b").containsMatchIn(normalized)
    }

    private fun detectDisplayedPackageFormat(
        candidates: List<JSONObject>,
        vararg sources: JSONObject?
    ): String {
        if (candidates.any { it.detectPackageFormat() == "APK" }) return "APK"
        if (candidates.any { it.detectPackageFormat() == "APKM" }) return "APKM"

        val metadata = packageFormatMetadata(*sources)

        return when {
            metadata.containsPlainApkSignal() -> "APK"
            metadata.containsApkmSignal() -> "APKM"
            else -> "APK"
        }
    }

    private fun JSONObject.detectPackageFormat(): String {
        val metadata = packageFormatMetadata(this)

        return when {
            metadata.containsPlainApkSignal() -> "APK"
            metadata.containsApkmSignal() -> "APKM"
            else -> "APK"
        }
    }

    private fun packageFormatScore(apk: JSONObject): Int {
        return when (apk.detectPackageFormat()) {
            "APK" -> 2
            "APKM" -> 1
            else -> 0
        }
    }

    private fun packageFormatMetadata(vararg sources: JSONObject?): String {
        return sources
            .filterNotNull()
            .joinToString(" ") { source -> source.collectPackageFormatText() }
            .lowercase()
    }

    private fun Any?.collectPackageFormatText(): String {
        return when (this) {
            null, JSONObject.NULL -> ""
            is JSONArray -> {
                (0 until length()).joinToString(" ") { index ->
                    opt(index).collectPackageFormatText()
                }
            }
            is JSONObject -> {
                val values = mutableListOf<String>()
                val iterator = keys()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val value = opt(key)
                    val normalizedKey = key.lowercase()

                    if (value != null && value != JSONObject.NULL) {
                        if (normalizedKey.isPackageFormatSignalKey()) {
                            values += normalizedKey
                        }

                        values += value.collectPackageFormatText()
                    }
                }

                values.joinToString(" ")
            }
            is Boolean -> if (this) "true" else ""
            else -> toString()
        }
    }

    private fun String.isPackageFormatSignalKey(): Boolean {
        val value = lowercase()

        return "apkm" in value ||
            "bundle" in value ||
            "split" in value ||
            "format" in value ||
            "apk_type" in value ||
            "file_type" in value ||
            "variant_type" in value ||
            "download_type" in value
    }

    private fun String.containsApkmSignal(): Boolean {
        val normalized = lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace("%5b", "[")
            .replace("%5d", "]")

        return "apkm" in normalized ||
            "apk bundle" in normalized ||
            "apk bundles" in normalized ||
            "app bundle" in normalized ||
            "split apk" in normalized ||
            "split apks" in normalized ||
            "split config" in normalized ||
            "base split" in normalized ||
            "base config" in normalized ||
            Regex("\\bbundle\\b").containsMatchIn(normalized) ||
            Regex("\\bbundles\\b").containsMatchIn(normalized)
    }

    private fun String.containsPlainApkSignal(): Boolean {
        val strong = lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace("%5b", "[")
            .replace("%5d", "]")

        if (
            Regex("\\bapk files\\b").containsMatchIn(strong) ||
            Regex("\\bapk file\\b").containsMatchIn(strong) ||
            Regex("\\bplain apk\\b").containsMatchIn(strong) ||
            Regex("\\bapk type\\s*apk\\b").containsMatchIn(strong) ||
            Regex("\\bfile type\\s*apk\\b").containsMatchIn(strong) ||
            Regex("\\bvariant type\\s*apk\\b").containsMatchIn(strong) ||
            Regex("\\bdownload type\\s*apk\\b").containsMatchIn(strong) ||
            Regex("\\bformat\\s*apk\\b").containsMatchIn(strong)
        ) {
            return true
        }

        if (containsApkmSignal()) return false

        val normalized = strong
            .replace("apkmirror", " ")
            .replace("android apk download", " ")
            .replace("apk download", " ")
            .replace("apkm", " ")

        return Regex("\\bapk\\b").containsMatchIn(normalized)
    }

    private data class DeviceTargetProfile(
        val isWatch: Boolean,
        val isTelevision: Boolean,
        val isAutomotive: Boolean,
        val isVr: Boolean
    ) {
        val isPhoneLike: Boolean
            get() = !isWatch && !isTelevision && !isAutomotive && !isVr
    }

    private fun isDeviceTargetCompatible(
        source: JSONObject,
        packageManager: PackageManager?
    ): Boolean {
        val targets = source.deviceTargetTokens()

        if (targets.isEmpty()) return true

        val profile = packageManager.deviceTargetProfile()

        return targets.any { target ->
            when (target) {
                "phone", "tablet" -> profile.isPhoneLike
                "wear" -> profile.isWatch
                "tv" -> profile.isTelevision
                "automotive", "auto" -> profile.isAutomotive
                "daydream", "cardboard", "vr" -> profile.isVr
                else -> false
            }
        }
    }

    private fun deviceTargetScore(
        apk: JSONObject,
        packageManager: PackageManager?
    ): Int {
        val targets = apk.deviceTargetTokens()

        if (targets.isEmpty()) return 1

        return if (isDeviceTargetCompatible(apk, packageManager)) {
            2
        } else {
            0
        }
    }

    private fun PackageManager?.deviceTargetProfile(): DeviceTargetProfile {
        if (this == null) {
            return DeviceTargetProfile(
                isWatch = false,
                isTelevision = false,
                isAutomotive = false,
                isVr = false
            )
        }

        return DeviceTargetProfile(
            isWatch = hasSystemFeature(PackageManager.FEATURE_WATCH),
            isTelevision = hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY) ||
                hasSystemFeature(PackageManager.FEATURE_TELEVISION),
            isAutomotive = hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
            isVr = hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)
        )
    }

    private fun JSONObject.deviceTargetTokens(): Set<String> {
        val metadata = deviceTargetMetadata()

        if (metadata.isBlank()) return emptySet()

        return extractDeviceTargetTokens(metadata)
    }

    private fun JSONObject.deviceTargetMetadata(): String {
        val values = mutableListOf<String>()

        val keys = listOf(
            "name",
            "title",
            "label",
            "slug",
            "link",
            "url",
            "html_url",
            "download_url",
            "release",
            "releases",
            "release_name",
            "release_title",
            "release_link",
            "release_url",
            "app",
            "apps",
            "app_name",
            "app_title",
            "platform",
            "platforms",
            "device",
            "devices",
            "device_type",
            "device_types",
            "target",
            "targets",
            "targeting",
            "android_variant",
            "android_variants",
            "form_factor",
            "form_factors",
            "supported_devices",
            "supported_device",
            "supported_platforms",
            "features",
            "requirements",
            "tags"
        )

        keys.forEach { key ->
            val value = opt(key)

            if (value != null && value != JSONObject.NULL) {
                values += key
                values += value.collectDeviceTargetText()
            }
        }

        return values.joinToString(" ").lowercase()
    }

    private fun Any?.collectDeviceTargetText(): String {
        return when (this) {
            null, JSONObject.NULL -> ""
            is JSONArray -> {
                (0 until length()).joinToString(" ") { index ->
                    opt(index).collectDeviceTargetText()
                }
            }
            is JSONObject -> {
                val values = mutableListOf<String>()
                val iterator = keys()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    values += key
                    values += opt(key).collectDeviceTargetText()
                }

                values.joinToString(" ")
            }
            else -> toString()
        }
    }

    private fun extractDeviceTargetTokens(raw: String): Set<String> {
        val normalized = raw
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")

        val result = linkedSetOf<String>()

        if (
            "phone" in normalized ||
            "mobile" in normalized ||
            "handheld" in normalized
        ) {
            result += "phone"
        }

        if ("tablet" in normalized) {
            result += "tablet"
        }

        if (
            "wear os" in normalized ||
            "wearos" in normalized ||
            "android wear" in normalized ||
            "androidwear" in normalized ||
            "wearable" in normalized ||
            Regex("\\bwatch\\s*true\\b").containsMatchIn(normalized) ||
            Regex("\\bwear\\s*true\\b").containsMatchIn(normalized)
        ) {
            result += "wear"
        }

        if (
            "android tv" in normalized ||
            "androidtv" in normalized ||
            "leanback" in normalized ||
            Regex("\\btv\\s*true\\b").containsMatchIn(normalized)
        ) {
            result += "tv"
        }

        if (
            "android automotive" in normalized ||
            "androidautomotive" in normalized ||
            "automotive" in normalized
        ) {
            result += "automotive"
        }

        if (
            "android auto" in normalized ||
            "androidauto" in normalized
        ) {
            result += "auto"
        }

        if ("daydream" in normalized) {
            result += "daydream"
        }

        if ("cardboard" in normalized) {
            result += "cardboard"
        }

        if (Regex("\\bvr\\b").containsMatchIn(normalized)) {
            result += "vr"
        }

        return result
    }

    private data class DpiTarget(
        val hasMetadata: Boolean,
        val isDensityIndependent: Boolean,
        val densities: Set<Int>,
        val ranges: List<IntRange>
    )

    private fun dpiScore(apk: JSONObject): Int {
        val target = apk.dpiTarget()
        val deviceDensity = currentDeviceDensityDpi()

        if (!target.hasMetadata) return 95_000
        if (target.densities.contains(deviceDensity)) return 100_000
        if (target.ranges.any { deviceDensity in it }) return 98_000
        if (target.isDensityIndependent) return 90_000
        if (target.densities.isEmpty()) return 85_000

        val bestDensity = target.densities.minWithOrNull(
            compareBy<Int> { abs(it - deviceDensity) }
                .thenBy { if (it >= deviceDensity) 0 else 1 }
        ) ?: return 80_000

        val distance = abs(bestDensity - deviceDensity).coerceAtMost(50_000)
        val higherTieBonus = if (bestDensity >= deviceDensity) 1 else 0

        return 80_000 - distance + higherTieBonus
    }

    private fun currentDeviceDensityDpi(): Int {
        return Resources.getSystem()
            .displayMetrics
            .densityDpi
            .takeIf { it > 0 }
            ?: 420
    }

    private fun JSONObject.dpiTarget(): DpiTarget {
        val metadata = dpiTargetMetadata()

        if (metadata.isBlank()) {
            return DpiTarget(
                hasMetadata = false,
                isDensityIndependent = false,
                densities = emptySet(),
                ranges = emptyList()
            )
        }

        val normalized = metadata
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")

        val densityIndependent =
            "nodpi" in normalized ||
            "no dpi" in normalized ||
            "anydpi" in normalized ||
            "any dpi" in normalized ||
            "all dpi" in normalized ||
            "all densities" in normalized ||
            "universal" in normalized

        val allowedDensities = listOf(
            120,
            160,
            213,
            240,
            280,
            320,
            360,
            400,
            420,
            480,
            560,
            640
        )

        val densityPattern = allowedDensities.joinToString("|")
        val densities = linkedSetOf<Int>()
        val ranges = mutableListOf<IntRange>()

        Regex("\\b($densityPattern)\\s+($densityPattern)\\s*(?:dpi|dpis|density|densities)\\b")
            .findAll(normalized)
            .forEach { match ->
                val first = match.groupValues.getOrNull(1)?.toIntOrNull()
                val second = match.groupValues.getOrNull(2)?.toIntOrNull()

                if (first != null && second != null) {
                    val start = minOf(first, second)
                    val end = maxOf(first, second)
                    ranges += start..end
                    densities += first
                    densities += second
                }
            }

        Regex("\\b(?:dpi|dpis|density|densities)\\s*($densityPattern)\\b")
            .findAll(normalized)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
            .forEach { value -> densities += value }

        Regex("\\b($densityPattern)\\s*(?:dpi|dpis|density|densities)\\b")
            .findAll(normalized)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
            .forEach { value -> densities += value }

        return DpiTarget(
            hasMetadata = true,
            isDensityIndependent = densityIndependent,
            densities = densities,
            ranges = ranges
        )
    }

    private fun JSONObject.dpiTargetMetadata(): String {
        val values = mutableListOf<String>()

        val keys = listOf(
            "dpi",
            "dpis",
            "density",
            "densities",
            "density_dpi",
            "screen_density",
            "screen_densities",
            "display_density",
            "display_densities",
            "variant_dpi",
            "variant_density",
            "supported_dpi",
            "supported_dpis",
            "supported_density",
            "supported_densities",
            "resolution",
            "resolutions",
            "filename",
            "file_name",
            "name",
            "title",
            "label",
            "link",
            "url",
            "download_url"
        )

        keys.forEach { key ->
            val value = opt(key)

            if (value != null && value != JSONObject.NULL) {
                values += key
                values += value.collectDpiTargetText()
            }
        }

        return values.joinToString(" ")
    }

    private fun Any?.collectDpiTargetText(): String {
        return when (this) {
            null, JSONObject.NULL -> ""
            is JSONArray -> {
                (0 until length()).joinToString(" ") { index ->
                    opt(index).collectDpiTargetText()
                }
            }
            is JSONObject -> {
                val values = mutableListOf<String>()
                val iterator = keys()

                while (iterator.hasNext()) {
                    val key = iterator.next()
                    values += key
                    values += opt(key).collectDpiTargetText()
                }

                values.joinToString(" ")
            }
            else -> toString()
        }
    }

    private fun isSdkCompatible(apk: JSONObject): Boolean {
        val minApi = apk.firstString(
            "minapi",
            "min_api",
            "minSdk",
            "min_sdk",
            "minsdk",
            "minimum_api",
            "minimum_android"
        )

        val maxApi = apk.firstString(
            "maxapi",
            "max_api",
            "maxSdk",
            "max_sdk",
            "maxsdk",
            "maximum_api",
            "maximum_android"
        )

        return isMinApiCompatible(minApi) && isMaxApiCompatible(maxApi)
    }

    private fun isMinApiCompatible(minApi: String?): Boolean {
        val apiValue = extractSdkValue(minApi) ?: return true
        return apiValue <= Build.VERSION.SDK_INT
    }

    private fun isMaxApiCompatible(maxApi: String?): Boolean {
        val apiValue = extractSdkValue(maxApi) ?: return true
        return apiValue >= Build.VERSION.SDK_INT
    }

    private fun extractSdkValue(raw: String?): Int? {
        val value = raw?.trim()?.lowercase().orEmpty()

        if (value.isBlank()) return null

        Regex("api\\s*(\\d{1,2})")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        Regex("sdk\\D*(\\d{1,2})")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        if (value.matches(Regex("\\d{1,2}"))) {
            return value.toIntOrNull()
        }

        Regex("android\\s*(\\d{1,2})(?:\\.\\d+)?")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { major -> return androidMajorToSdk(major) }

        return null
    }

    private fun androidMajorToSdk(major: Int): Int? {
        return when (major) {
            4 -> 14
            5 -> 21
            6 -> 23
            7 -> 24
            8 -> 26
            9 -> 28
            10 -> 29
            11 -> 30
            12 -> 31
            13 -> 33
            14 -> 34
            15 -> 35
            16 -> 36
            else -> null
        }
    }

    private fun isArchitectureCompatible(arches: JSONArray?): Boolean {
        if (arches == null || arches.length() == 0) return true

        val apkArches = arches.extractArchitectureSet()

        if (apkArches.isEmpty()) return true
        if ("universal" in apkArches) return true

        val deviceArches = Build.SUPPORTED_ABIS
            .flatMap { abi -> extractArchitectureTokens(abi) }
            .toSet()

        return apkArches.any { arch -> arch in deviceArches }
    }

    private fun architectureScore(apk: JSONObject): Int {
        val arches = apk.optFlexibleArray("arches") ?: return 1

        if (arches.length() == 0) return 1

        val apkArches = arches.extractArchitectureSet()

        if ("universal" in apkArches) return 1

        val primaryDeviceArches = Build.SUPPORTED_ABIS
            .firstOrNull()
            ?.let { extractArchitectureTokens(it) }
            .orEmpty()

        val allDeviceArches = Build.SUPPORTED_ABIS
            .flatMap { abi -> extractArchitectureTokens(abi) }
            .toSet()

        return when {
            apkArches.any { arch -> arch in primaryDeviceArches } -> 3
            apkArches.any { arch -> arch in allDeviceArches } -> 2
            else -> 0
        }
    }

    private fun JSONArray.extractArchitectureSet(): Set<String> {
        val result = linkedSetOf<String>()

        for (index in 0 until length()) {
            result += extractArchitectureTokens(optString(index))
        }

        return result
    }

    private fun extractArchitectureTokens(raw: String): Set<String> {
        val result = linkedSetOf<String>()

        normalizeArchitecture(raw)?.let { result += it }

        raw.lowercase()
            .replace("_", "-")
            .split(Regex("[,;+/\\s]+"))
            .forEach { token ->
                normalizeArchitecture(token)?.let { result += it }
            }

        return result
    }

    private fun normalizeArchitecture(raw: String): String? {
        val value = raw
            .lowercase()
            .replace("_", "-")
            .trim()

        return when {
            value.isBlank() -> null
            value in setOf("universal", "noarch", "all", "any") -> "universal"
            "arm64" in value || "aarch64" in value -> "arm64-v8a"
            "armeabi-v7a" in value || "arm-v7a" in value || "armv7" in value || value == "armeabi" || value == "arm" -> "armeabi-v7a"
            "x86-64" in value || "x8664" in value || value == "x64" -> "x86_64"
            value == "x86" || "x86" in value -> "x86"
            else -> null
        }
    }

    private fun JSONObject.optFlexibleArray(name: String): JSONArray? {
        val value = opt(name) ?: return null

        return when (value) {
            is JSONArray -> value
            JSONObject.NULL -> null
            is String -> value.takeIf { it.isNotBlank() }?.let { JSONArray().put(it) }
            else -> JSONArray().put(value.toString())
        }
    }

    private fun JSONObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val value = opt(key) ?: continue
            val text = value.toString().trim()

            if (text.isNotBlank() && text != "null") {
                return text
            }
        }

        return null
    }

    private fun String.cleanErrorBody(): String {
        return replace(Regex("\\s+"), " ")
            .take(220)
            .ifBlank { "empty response body" }
    }
}
private fun resolveApkMirrorPackageFormat(
    html: String,
    versionCode: Long,
    url: String
): String {
    val urlUpper = url.uppercase()

    if (urlUpper.contains(".APKM") || urlUpper.contains("/APKM/")) {
        return "APKM"
    }

    val code = versionCode.toString()

    if (code.isBlank() || code == "0") {
        return "APK"
    }

    val rowCandidates = buildList {
        Regex("(?is)<tr[^>]*>.*?</tr>").findAll(html).forEach { add(it.value) }
        Regex("(?is)<li[^>]*>.*?</li>").findAll(html).forEach { add(it.value) }
        Regex("(?is)<div[^>]*(?:table-row|variant|download)[^>]*>.*?</div>").findAll(html).forEach { add(it.value) }
    }

    val exactRows = rowCandidates.filter { it.contains(code) }

    if (exactRows.isEmpty()) {
        return "APK"
    }

    return if (exactRows.any { row ->
            val rowText = row
                .replace("&nbsp;", " ")
                .replace("&#8211;", "-")
                .replace("&#8212;", "-")
                .replace("&amp;", "&")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .uppercase()

            rowText.contains("BUNDLE") || rowText.contains("APKM") || rowText.contains("APK BUNDLE")
        }
    ) {
        "APKM"
    } else {
        "APK"
    }
}


