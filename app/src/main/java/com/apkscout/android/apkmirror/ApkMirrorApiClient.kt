package com.apkscout.android.apkmirror

import android.os.Build
import com.apkscout.android.InstalledApp
import com.apkscout.android.UpdateInfo
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

    suspend fun checkUpdates(apps: List<InstalledApp>): ApkMirrorCheckResult {
        return withContext(Dispatchers.IO) {
            val updates = linkedMapOf<String, UpdateInfo>()

            for (chunk in apps.chunked(PACKAGE_CHUNK_SIZE)) {
                val result = runCatching {
                    checkChunk(chunk)
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

    private fun checkChunk(apps: List<InstalledApp>): Map<String, UpdateInfo> {
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
                installedByPackage = installedByPackage
            )
        } finally {
            response.close()
        }
    }

    private fun parseResponse(
        body: String,
        installedByPackage: Map<String, InstalledApp>
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

            if (!item.optBoolean("exists", true)) {
                continue
            }

            val release = item.optJSONObject("release") ?: continue
            val apks = item.optJSONArray("apks") ?: continue

            val bestApk = findBestApk(
                apks = apks,
                installedVersionCode = installed.versionCode
            ) ?: continue

            val foundVersionCode = bestApk.optVersionCode() ?: continue

            if (foundVersionCode <= installed.versionCode) {
                continue
            }

            val foundVersionName = release.optString("version").takeIf { it.isNotBlank() }
                ?: continue

            val releaseUrl = ApkMirrorSource.absoluteUrl(release.optString("link"))
                ?: bestApk.optString("link").takeIf { it.isNotBlank() }?.let { ApkMirrorSource.absoluteUrl(it) }
                ?: ApkMirrorSource.searchUrl(packageName).toString()

            updates[packageName] = UpdateInfo(
                versionName = foundVersionName,
                versionCode = foundVersionCode,
                url = releaseUrl,
                formatLabel = bestApk.detectPackageFormat()
            )
        }

        return updates
    }

    private fun findBestApk(
    apks: JSONArray,
    installedVersionCode: Long
): JSONObject? {
    val candidates = mutableListOf<JSONObject>()

    for (index in 0 until apks.length()) {
        val apk = apks.optJSONObject(index) ?: continue
        val versionCode = apk.optVersionCode() ?: continue

        if (versionCode <= installedVersionCode) continue
        if (!isSdkCompatible(apk)) continue
        if (!isArchitectureCompatible(apk.optFlexibleArray("arches"))) continue

        candidates += apk
    }

    return candidates.maxWithOrNull(
        compareBy<JSONObject> { it.optVersionCode() ?: 0L }
            .thenBy { packageFormatScore(it) }
            .thenBy { compatibilityScore(it) }
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

private fun compatibilityScore(apk: JSONObject): Int {
    val arches = apk.optFlexibleArray("arches") ?: return 1
    if (arches.length() == 0) return 1

    return if (hasDirectArchitectureMatch(arches)) {
        2
    } else {
        0
    }
}

private fun hasDirectArchitectureMatch(arches: JSONArray): Boolean {
    val apkArches = arches.extractArchitectureSet()

    if ("universal" in apkArches) return true

    val deviceArches = Build.SUPPORTED_ABIS
        .flatMap { abi -> extractArchitectureTokens(abi) }
        .toSet()

    return apkArches.any { arch -> arch in deviceArches }
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
