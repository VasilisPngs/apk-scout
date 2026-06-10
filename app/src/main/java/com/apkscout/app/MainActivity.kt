package com.apkscout.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.apkscout.app.core.model.ApkFormat
import com.apkscout.app.core.model.DeviceSpec
import com.apkscout.app.core.model.AppUpdateStatus
import com.apkscout.app.apkmirror.ApkMirrorUpdateChecker
import com.apkscout.app.apkmirror.ApkMirrorSource
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean
)

data class DeviceProfile(
    val sdk: Int,
    val densityDpi: Int,
    val abis: String
)

private const val APKMIRROR_CHECK_CACHE_TTL_MS = 15L * 60L * 1000L

data class UpdateSummary(
    val total: Int,
    val checked: Int,
    val checking: Int,
    val updates: Int,
    val upToDate: Int,
    val errors: Int
)

data class CachedUpdateResult(
    val status: AppUpdateStatus,
    val checkedAtMillis: Long,
    val regularApkOnly: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            APKScoutTheme {
                APKScoutScreen()
            }
        }
    }
}

@Composable
fun APKScoutTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    val colors = when {
        isPreview && dark -> darkColorScheme()
        isPreview -> lightColorScheme()
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

fun CachedUpdateResult?.freshStatus(
    regularApkOnly: Boolean
): AppUpdateStatus? {
    if (this == null) return null
    if (this.regularApkOnly != regularApkOnly) return null

    val ageMillis = System.currentTimeMillis() - checkedAtMillis

    if (ageMillis !in 0..APKMIRROR_CHECK_CACHE_TTL_MS) return null

    return status
}

fun AppUpdateStatus.toCacheEntry(
    regularApkOnly: Boolean
): CachedUpdateResult? {
    if (!isReusableCachedResult()) return null

    return CachedUpdateResult(
        status = this,
        checkedAtMillis = System.currentTimeMillis(),
        regularApkOnly = regularApkOnly
    )
}

fun AppUpdateStatus.isReusableCachedResult(): Boolean {
    return when (this) {
        AppUpdateStatus.NotChecked,
        AppUpdateStatus.Checking -> false
        is AppUpdateStatus.Error -> false
        else -> true
    }
}

fun calculateUpdateSummary(
    apps: List<InstalledApp>,
    updateStates: Map<String, AppUpdateStatus>
): UpdateSummary {
    val states = apps.map { app ->
        updateStates[app.packageName] ?: AppUpdateStatus.NotChecked
    }

    return UpdateSummary(
        total = apps.size,
        checked = states.count { it !is AppUpdateStatus.NotChecked && it !is AppUpdateStatus.Checking },
        checking = states.count { it is AppUpdateStatus.Checking },
        updates = states.count { it is AppUpdateStatus.UpdateAvailable },
        upToDate = states.count { it is AppUpdateStatus.UpToDate },
        errors = states.count { it is AppUpdateStatus.Error }
    )
}

@Composable
fun APKScoutScreen() {
    val context = LocalContext.current
    val profile = rememberDeviceProfile(context)
    var includeSystemApps by remember { mutableStateOf(false) }
    var regularApkOnly by remember { mutableStateOf(true) }
    var checkingAll by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var updateStates by remember { mutableStateOf<Map<String, AppUpdateStatus>>(emptyMap()) }
    var checkCache by remember { mutableStateOf<Map<String, CachedUpdateResult>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    val updateSummary = remember(apps, updateStates) {
        calculateUpdateSummary(
            apps = apps,
            updateStates = updateStates
        )
    }

    LaunchedEffect(includeSystemApps) {
        loading = true
        apps = withContext(Dispatchers.Default) {
            scanInstalledApps(context.packageManager, includeSystemApps)
        }
        updateStates = apps.associate { it.packageName to AppUpdateStatus.NotChecked }
        loading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    HeaderCard(
                        profile = profile,
                        appCount = apps.size,
                        loading = loading
                    )
                }

                item {
                    ControlsCard(
                        includeSystemApps = includeSystemApps,
                        onIncludeSystemAppsChange = { includeSystemApps = it },
                        regularApkOnly = regularApkOnly,
                        onRegularApkOnlyChange = { regularApkOnly = it },
                        summary = updateSummary,
                        cachedCount = checkCache.size,
                        appCount = apps.size,
                        checkingAll = checkingAll,
                        onCheckVisibleApps = {
                            if (!checkingAll && apps.isNotEmpty()) {
                                scope.launch {
                                    checkingAll = true

                                    try {
                                        apps.forEachIndexed { index, app ->
                                            val cachedStatus = checkCache[app.packageName].freshStatus(regularApkOnly)

                                            if (cachedStatus != null) {
                                                updateStates = updateStates + (app.packageName to cachedStatus)
                                            } else {
                                                updateStates = updateStates + (app.packageName to AppUpdateStatus.Checking)

                                                val result = runCatching {
                                                    ApkMirrorUpdateChecker.check(
                                                        packageName = app.packageName,
                                                        installedVersionCode = app.versionCode,
                                                        device = profile.toDeviceSpec(),
                                                        regularApkOnly = regularApkOnly
                                                    )
                                                }.getOrElse { error ->
                                                    AppUpdateStatus.Error(
                                                        message = error.message ?: "Unexpected APKMirror check error"
                                                    )
                                                }

                                                updateStates = updateStates + (app.packageName to result)

                                                result.toCacheEntry(regularApkOnly)?.let { entry ->
                                                    checkCache = checkCache + (app.packageName to entry)
                                                }

                                                if (index < apps.lastIndex) {
                                                    delay(900)
                                                }
                                            }
                                        }
                                    } finally {
                                        checkingAll = false
                                    }
                                }
                            }
                        }
                    )
                }

                items(
                    items = apps,
                    key = { it.packageName }
                ) { app ->
                    InstalledAppCard(
                        app = app,
                        status = updateStates[app.packageName] ?: AppUpdateStatus.NotChecked,
                        onCheckSource = {
                            val cachedStatus = checkCache[app.packageName].freshStatus(regularApkOnly)

                            if (cachedStatus != null) {
                                updateStates = updateStates + (app.packageName to cachedStatus)
                            } else {
                                updateStates = updateStates + (app.packageName to AppUpdateStatus.Checking)

                                scope.launch {
                                    val result = ApkMirrorUpdateChecker.check(
                                        packageName = app.packageName,
                                        installedVersionCode = app.versionCode,
                                        device = profile.toDeviceSpec(),
                                        regularApkOnly = regularApkOnly
                                    )

                                    updateStates = updateStates + (app.packageName to result)

                                    result.toCacheEntry(regularApkOnly)?.let { entry ->
                                        checkCache = checkCache + (app.packageName to entry)
                                    }
                                }
                            }
                        },
                        onOpenSource = {
                            openAPKMirror(
                                context = context,
                                packageName = app.packageName,
                                status = updateStates[app.packageName] ?: AppUpdateStatus.NotChecked
                            )
                        }
                    )
                }
            }
        }
    }
}

fun DeviceProfile.toDeviceSpec(): DeviceSpec {
    return DeviceSpec(
        sdk = sdk,
        densityDpi = densityDpi,
        abis = Build.SUPPORTED_ABIS.toList()
    )
}


@Composable
fun rememberDeviceProfile(context: Context): DeviceProfile {
    return remember {
        DeviceProfile(
            sdk = Build.VERSION.SDK_INT,
            densityDpi = context.resources.displayMetrics.densityDpi,
            abis = Build.SUPPORTED_ABIS.joinToString()
        )
    }
}

@Composable
fun HeaderCard(
    profile: DeviceProfile,
    appCount: Int,
    loading: Boolean
) {
    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "APKScout",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "APKMirror update scout for Android apps.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Android ${profile.sdk}") }
                )

                AssistChip(
                    onClick = {},
                    label = { Text("${profile.densityDpi} dpi") }
                )
            }

            Text(
                text = "ABI: ${profile.abis}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (loading) "Scanning installed apps..." else "$appCount apps loaded",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ControlsCard(
    includeSystemApps: Boolean,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    regularApkOnly: Boolean,
    onRegularApkOnlyChange: (Boolean) -> Unit,
    summary: UpdateSummary,
    cachedCount: Int,
    appCount: Int,
    checkingAll: Boolean,
    onCheckVisibleApps: () -> Unit
) {
    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingRow(
                title = "Show system apps",
                description = "Hidden by default to avoid OEM and core Android noise.",
                checked = includeSystemApps,
                onCheckedChange = onIncludeSystemAppsChange
            )

            SettingRow(
                title = "Regular APK only",
                description = "Bundles stay hidden unless explicitly enabled later.",
                checked = regularApkOnly,
                onCheckedChange = onRegularApkOnlyChange
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("APKMirror") }
                )

                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("APK-only filter") }
                )
            }

            Text(
                text = "Checked ${summary.checked}/${summary.total} • Checking ${summary.checking} • Updates ${summary.updates} • Up to date ${summary.upToDate} • Errors ${summary.errors} • Cached $cachedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onCheckVisibleApps,
                modifier = Modifier.fillMaxWidth(),
                enabled = !checkingAll && appCount > 0
            ) {
                Text(if (checkingAll) "Checking visible apps" else "Check visible apps")
            }

            Text(
                text = "Runs APKMirror checks one app at a time to avoid aggressive requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun InstalledAppCard(
    app: InstalledApp,
    status: AppUpdateStatus,
    onCheckSource: () -> Unit,
    onOpenSource: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                AssistChip(
                    onClick = {},
                    label = { Text(if (app.isSystem) "System" else "User") }
                )
            }

            Text(
                text = "Installed: ${app.versionName} (${app.versionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            UpdateStatusBlock(status = status)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCheckSource,
                    modifier = Modifier.weight(1f),
                    enabled = status !is AppUpdateStatus.Checking
                ) {
                    Text(if (status is AppUpdateStatus.Checking) "Checking" else "Check APKMirror")
                }

                Button(
                    onClick = onOpenSource,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(openActionLabel(status))
                }
            }
        }
    }
}

@Composable
fun UpdateStatusBlock(status: AppUpdateStatus) {
    val title = when (status) {
        AppUpdateStatus.NotChecked -> "Not checked"
        AppUpdateStatus.Checking -> "Checking APKMirror"
        is AppUpdateStatus.UpdateAvailable -> "Update available"
        is AppUpdateStatus.SearchResultsFound -> "APKMirror releases found"
        is AppUpdateStatus.ReleasePageLoaded -> "APKMirror release loaded"
        is AppUpdateStatus.ReleaseMetadataParsed -> "APKMirror release parsed"
        is AppUpdateStatus.VariantLinksParsed -> "APKMirror variants parsed"
        is AppUpdateStatus.CompatibleApkCandidatesParsed -> "Compatible APK candidates"
        AppUpdateStatus.UpToDate -> "Up to date"
        AppUpdateStatus.NoCompatibleApk -> "No compatible APK"
        AppUpdateStatus.OnlyBundleFound -> "Only bundle found"
        AppUpdateStatus.IncompatibleVariant -> "Incompatible variant"
        is AppUpdateStatus.Error -> "Error"
    }

    val description = when (status) {
        AppUpdateStatus.NotChecked -> "APKMirror has not been checked yet."
        AppUpdateStatus.Checking -> "Searching APKMirror for compatible APK variants."
        is AppUpdateStatus.UpdateAvailable -> "Latest compatible APK: ${status.versionName} (${status.versionCode}). Ready to open exact APKMirror page."
        is AppUpdateStatus.SearchResultsFound -> "${status.count} APKMirror release links found. Release parser is not connected yet."
        is AppUpdateStatus.ReleasePageLoaded -> "Release page fetched. Variant parser is not connected yet."
        is AppUpdateStatus.ReleaseMetadataParsed -> {
            val versionCodeText = status.versionCode?.let { "Version code: $it" } ?: "Version code not found yet"
            "Release: ${status.title}. $versionCodeText."
        }
        is AppUpdateStatus.VariantLinksParsed -> {
            "Release: ${status.title}. Variants found: ${status.totalCount}. Regular APK: ${status.regularApkCount}. Non-APK: ${status.nonApkCount}."
        }
        is AppUpdateStatus.CompatibleApkCandidatesParsed -> {
            "Release: ${status.title}. Compatible regular APK: ${status.compatibleApkCount}/${status.regularApkCount}. Version code check not confirmed yet. Non-APK hidden: ${status.nonApkCount}."
        }
        AppUpdateStatus.UpToDate -> "Installed version is already current."
        AppUpdateStatus.NoCompatibleApk -> "No regular APK matched this device."
        AppUpdateStatus.OnlyBundleFound -> "Latest result requires bundle handling."
        AppUpdateStatus.IncompatibleVariant -> "Latest result does not match this device."
        is AppUpdateStatus.Error -> status.message
    }

    val format = when (status) {
        is AppUpdateStatus.UpdateAvailable -> "Format: ${status.format.name}"
        AppUpdateStatus.OnlyBundleFound -> "Format: bundle"
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (format != null) {
            Text(
                text = format,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


fun openActionLabel(status: AppUpdateStatus): String {
    return when (status) {
        is AppUpdateStatus.UpdateAvailable -> "Open APK page"
        is AppUpdateStatus.CompatibleApkCandidatesParsed,
        is AppUpdateStatus.ReleaseMetadataParsed,
        is AppUpdateStatus.ReleasePageLoaded,
        is AppUpdateStatus.VariantLinksParsed -> "Open release"
        else -> "Open APKMirror"
    }
}


@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(32.dp)
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(20.dp)
        ) {
            content()
        }
    }
}

fun scanInstalledApps(
    packageManager: PackageManager,
    includeSystemApps: Boolean
): List<InstalledApp> {
    return packageManager
        .getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        .mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

            if (!includeSystemApps && isSystem) {
                return@mapNotNull null
            }

            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = info.packageName,
                versionName = info.versionName ?: "Unknown",
                versionCode = info.longVersionCode,
                isSystem = isSystem
            )
        }
        .sortedWith(
            compareBy<InstalledApp> { it.label.lowercase() }
                .thenBy { it.packageName }
        )
}

fun openAPKMirror(
    context: Context,
    packageName: String,
    status: AppUpdateStatus
) {
    val uri = when (status) {
        is AppUpdateStatus.UpdateAvailable -> Uri.parse(status.webUrl)
        is AppUpdateStatus.CompatibleApkCandidatesParsed -> Uri.parse(status.releaseUrl)
        is AppUpdateStatus.ReleaseMetadataParsed -> Uri.parse(status.releaseUrl)
        is AppUpdateStatus.ReleasePageLoaded -> Uri.parse(status.releaseUrl)
        is AppUpdateStatus.VariantLinksParsed -> Uri.parse(status.releaseUrl)
        else -> ApkMirrorSource.searchUrl(packageName)
    }

    context.startActivity(
        Intent(Intent.ACTION_VIEW, uri)
    )
}
