package com.apkscout.app.apkmirror

import com.apkscout.app.core.model.ApkFormat

object ApkMirrorVariantParser {
    private val titleRegex = Regex(
        pattern = """<title[^>]*>(.*?)</title>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val versionCodeRegexes = listOf(
        Regex("""\bversion\s*code\b[^0-9]{0,180}([0-9]{2,})""", RegexOption.IGNORE_CASE),
        Regex("""\bversioncode\b[^0-9]{0,140}([0-9]{2,})""", RegexOption.IGNORE_CASE),
        Regex("""\bversion\b[^()]{0,180}\(([0-9]{2,})\)""", RegexOption.IGNORE_CASE)
    )

    private val architectureValues = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "armeabi",
        "x86_64",
        "x86",
        "universal",
        "noarch"
    )

    private val dpiRegex = Regex(
        pattern = """\b(nodpi|alldpi|\d{2,4}\s*-\s*\d{2,4}dpi|\d{2,4}dpi)\b""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(
        html: String,
        url: String
    ): ApkMirrorVariantMetadata {
        val normalized = html.stripHtml().decodeBasicEntities().cleanSpaces()

        return ApkMirrorVariantMetadata(
            title = findTitle(html),
            versionCode = findVersionCode(normalized),
            format = inferFormat(url, normalized),
            architectures = findArchitectures(normalized),
            dpi = findDpi(normalized),
            minSdk = findMinSdk(normalized)
        )
    }

    private fun findTitle(html: String): String? {
        val raw = titleRegex
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return raw
            .stripHtml()
            .decodeBasicEntities()
            .replace(Regex("""\s+APK Download.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+APKMirror\s*$""", RegexOption.IGNORE_CASE), "")
            .cleanSpaces()
            .ifBlank { null }
    }

    private fun findVersionCode(text: String): Long? {
        return versionCodeRegexes.firstNotNullOfOrNull { regex ->
            regex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
        }
    }

    private fun inferFormat(
        url: String,
        text: String
    ): ApkFormat {
        val lowerUrl = url.lowercase()
        val lowerText = text.lowercase()

        return when {
            "-apkm-download/" in lowerUrl || "apkm" in lowerText -> ApkFormat.APKM
            "-apks-download/" in lowerUrl || "apks" in lowerText -> ApkFormat.APKS
            "-xapk-download/" in lowerUrl || "xapk" in lowerText -> ApkFormat.XAPK
            "-bundle-download/" in lowerUrl || "bundle" in lowerText -> ApkFormat.BUNDLE
            "-apk-download/" in lowerUrl -> ApkFormat.APK
            else -> ApkFormat.UNKNOWN
        }
    }

    private fun findArchitectures(text: String): List<String> {
        val lower = text.lowercase()

        return architectureValues
            .filter { it in lower }
            .distinct()
    }

    private fun findDpi(text: String): String? {
        return dpiRegex
            .find(text)
            ?.value
            ?.lowercase()
            ?.replace(Regex("""\s+"""), "")
    }

    private fun findMinSdk(text: String): Int? {
        Regex("""Android\s+([0-9]+)(?:\.[0-9]+)?\+""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { major -> return androidMajorToSdk(major) }

        Regex("""min(?:imum)?\s*SDK[^0-9]{0,60}([0-9]{1,2})""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        return null
    }

    private fun androidMajorToSdk(major: Int): Int? {
        return when (major) {
            1 -> 1
            2 -> 5
            3 -> 11
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

    private fun String.stripHtml(): String {
        return replace(Regex("""<[^>]+>"""), " ")
    }

    private fun String.decodeBasicEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun String.cleanSpaces(): String {
        return replace(Regex("""\s+"""), " ").trim()
    }
}
