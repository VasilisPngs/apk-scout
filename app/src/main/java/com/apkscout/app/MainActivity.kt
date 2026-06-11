package com.apkscout.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkscout.app.apkmirror.ApkMirrorApiClient
import com.apkscout.app.apkmirror.ApkMirrorSource
import com.apkscout.app.settings.ReleaseChannelFilter
import com.apkscout.app.settings.ReleaseChannelSettings
import com.apkscout.app.settings.SettingsStore
import com.apkscout.app.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val icon: Bitmap?
)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val url: String
)

data class DeviceProfile(
    val deviceName: String,
    val sdk: Int,
    val densityDpi: Int,
    val abis: String
)

enum class AppListFilter {
    ALL,
    USER,
    SYSTEM,
    UPDATES
}

private enum class RootScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            APKScoutTheme {
                APKScoutRoot()
            }
        }
    }
}

@Composable
fun APKScoutTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val dark = isSystemInDarkTheme()

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

@Composable
fun APKScoutRoot() {
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(RootScreen.HOME) }
    var releaseSettings by remember { mutableStateOf(SettingsStore.read(context)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = currentScreen == RootScreen.HOME,
                    onClick = { currentScreen = RootScreen.HOME },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = null,
                    alwaysShowLabel = false
                )

                NavigationBarItem(
                    selected = currentScreen == RootScreen.SETTINGS,
                    onClick = { currentScreen = RootScreen.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = null,
                    alwaysShowLabel = false
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            RootScreen.HOME -> {
                APKScoutScreen(
                    modifier = Modifier.padding(innerPadding),
                    releaseSettings = releaseSettings
                )
            }

            RootScreen.SETTINGS -> {
                SettingsScreen(
                    settings = releaseSettings,
                    onDevChanged = { value ->
                        val next = releaseSettings.copy(includeDev = value)
                        releaseSettings = next
                        SettingsStore.write(context, next)
                    },
                    onAlphaChanged = { value ->
                        val next = releaseSettings.copy(includeAlpha = value)
                        releaseSettings = next
                        SettingsStore.write(context, next)
                    },
                    onBetaChanged = { value ->
                        val next = releaseSettings.copy(includeBeta = value)
                        releaseSettings = next
                        SettingsStore.write(context, next)
                    },
                    onRcChanged = { value ->
                        val next = releaseSettings.copy(includeRc = value)
                        releaseSettings = next
                        SettingsStore.write(context, next)
                    },
                    onPrereleaseChanged = { value ->
                        val next = releaseSettings.copy(includePrerelease = value)
                        releaseSettings = next
                        SettingsStore.write(context, next)
                    }
                )
            }
        }
    }
}

@Composable
fun APKScoutScreen(
    modifier: Modifier,
    releaseSettings: ReleaseChannelSettings
) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile(context)

    var selectedFilter by remember { mutableStateOf(AppListFilter.USER) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var scanRequest by remember { mutableIntStateOf(0) }
    var updateRequest by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var rawUpdates by remember { mutableStateOf<Map<String, UpdateInfo>>(emptyMap()) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var loadingApps by remember { mutableStateOf(true) }
    var checkingUpdates by remember { mutableStateOf(false) }

    LaunchedEffect(scanRequest) {
        loadingApps = true
        apps = withContext(Dispatchers.Default) {
            scanInstalledApps(packageManager = context.packageManager)
        }
        loadingApps = false
    }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            updateRequest++
        }
    }

    LaunchedEffect(updateRequest) {
        if (updateRequest <= 0 || apps.isEmpty()) return@LaunchedEffect

        checkingUpdates = true
        updateError = null

        val result = ApkMirrorApiClient.checkUpdates(apps)

        rawUpdates = result.updates
        updateError = result.error
        checkingUpdates = false
    }

    val updates = remember(rawUpdates, releaseSettings) {
        rawUpdates.filterValues { update ->
            ReleaseChannelFilter.isAllowed(
                versionName = update.versionName,
                settings = releaseSettings
            )
        }
    }

    val visibleApps = remember(apps, updates, selectedFilter, searchQuery) {
        filterApps(
            apps = apps,
            updates = updates,
            filter = selectedFilter,
            query = searchQuery
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 16.dp,
                end = 12.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TopBar(
                    loading = loadingApps || checkingUpdates,
                    searchVisible = searchVisible,
                    onToggleSearch = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            searchQuery = ""
                        }
                    },
                    onRefresh = {
                        scanRequest++
                        updateRequest++
                    }
                )
            }

            if (searchVisible) {
                item {
                    SearchBarCard(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                }
            }

            item {
                HeaderCard(
                    profile = profile,
                    totalCount = apps.size,
                    visibleCount = visibleApps.size,
                    updateCount = updates.size,
                    loadingApps = loadingApps,
                    checkingUpdates = checkingUpdates,
                    updateError = updateError
                )
            }

            item {
                ControlsCard(
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it }
                )
            }

            items(
                items = visibleApps,
                key = { it.packageName }
            ) { app ->
                InstalledAppCard(
                    app = app,
                    update = updates[app.packageName],
                    onOpenAPKMirror = {
                        openAPKMirror(
                            context = context,
                            packageName = app.packageName,
                            update = updates[app.packageName]
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TopBar(
    loading: Boolean,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "APKScout",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = if (searchVisible) "Close search" else "Open search"
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !loading
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh"
            )
        }
    }
}

@Composable
fun SearchBarCard(
    query: String,
    onQueryChange: (String) -> Unit
) {
    GlassCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search apps") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun rememberDeviceProfile(context: Context): DeviceProfile {
    return remember {
        DeviceProfile(
            deviceName = resolveDeviceName(),
            sdk = Build.VERSION.SDK_INT,
            densityDpi = context.resources.displayMetrics.densityDpi,
            abis = Build.SUPPORTED_ABIS.joinToString()
        )
    }
}

@Composable
fun HeaderCard(
    profile: DeviceProfile,
    totalCount: Int,
    visibleCount: Int,
    updateCount: Int,
    loadingApps: Boolean,
    checkingUpdates: Boolean,
    updateError: String?
) {
    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = profile.deviceName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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

                AssistChip(
                    onClick = {},
                    label = { Text("Upd: $updateCount") }
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
                text = when {
                    loadingApps -> "Scanning installed apps..."
                    checkingUpdates -> "Checking APKMirror updates..."
                    updateError != null -> "APKMirror check failed: $updateError"
                    visibleCount == totalCount -> "$totalCount apps loaded"
                    else -> "$visibleCount of $totalCount apps visible"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (updateError == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
fun ControlsCard(
    selectedFilter: AppListFilter,
    onFilterChange: (AppListFilter) -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == AppListFilter.ALL,
                onClick = { onFilterChange(AppListFilter.ALL) },
                label = { Text("All") }
            )

            FilterChip(
                selected = selectedFilter == AppListFilter.USER,
                onClick = { onFilterChange(AppListFilter.USER) },
                label = { Text("User") }
            )

            FilterChip(
                selected = selectedFilter == AppListFilter.SYSTEM,
                onClick = { onFilterChange(AppListFilter.SYSTEM) },
                label = { Text("System") }
            )

            FilterChip(
                selected = selectedFilter == AppListFilter.UPDATES,
                onClick = { onFilterChange(AppListFilter.UPDATES) },
                label = { Text("Updates") }
            )
        }
    }
}

@Composable
fun InstalledAppCard(
    app: InstalledApp,
    update: UpdateInfo?,
    onOpenAPKMirror: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AssistChip(
                        onClick = {},
                        label = { Text(if (app.isSystem) "S" else "U") }
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (update == null) {
                            "Installed: ${app.versionName}"
                        } else {
                            "${app.versionName} -> ${update.versionName}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (update == null) {
                            "Version code: ${app.versionCode}"
                        } else {
                            "${app.versionCode} -> ${update.versionCode}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onOpenAPKMirror,
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text("APKMirror")
                    }
                }
            }
        }
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
            modifier = Modifier.padding(18.dp)
        ) {
            content()
        }
    }
}

fun filterApps(
    apps: List<InstalledApp>,
    updates: Map<String, UpdateInfo>,
    filter: AppListFilter,
    query: String
): List<InstalledApp> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)

    return apps
        .filter { app ->
            val matchesFilter = when (filter) {
                AppListFilter.ALL -> true
                AppListFilter.USER -> !app.isSystem
                AppListFilter.SYSTEM -> app.isSystem
                AppListFilter.UPDATES -> updates.containsKey(app.packageName)
            }

            val matchesQuery = normalizedQuery.isEmpty() ||
                app.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)

            matchesFilter && matchesQuery
        }
        .sortedWith(
            compareBy<InstalledApp> { app ->
                if (updates.containsKey(app.packageName)) 0 else 1
            }.thenBy { app ->
                app.label.lowercase(Locale.ROOT)
            }.thenBy { app ->
                app.packageName
            }
        )
}

fun scanInstalledApps(
    packageManager: PackageManager
): List<InstalledApp> {
    return packageManager
        .getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        .mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = info.packageName,
                versionName = info.versionName ?: "Unknown",
                versionCode = info.longVersionCode,
                isSystem = isSystem,
                icon = appInfo.loadIcon(packageManager).toBitmap(size = 96)
            )
        }
        .sortedWith(
            compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName }
        )
}

fun Drawable.toBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}

fun openAPKMirror(
    context: Context,
    packageName: String,
    update: UpdateInfo?
) {
    val uri = update?.url?.let { android.net.Uri.parse(it) }
        ?: ApkMirrorSource.searchUrl(packageName)

    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            uri
        )
    )
}

fun resolveDeviceName(): String {
    val manufacturerRaw = Build.MANUFACTURER.orEmpty().trim()
    val modelRaw = Build.MODEL.orEmpty().trim()

    val manufacturer = manufacturerRaw
        .lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }

    if (manufacturer.isBlank() && modelRaw.isBlank()) {
        return "This device"
    }

    if (manufacturer.isBlank()) {
        return modelRaw
    }

    if (modelRaw.isBlank()) {
        return manufacturer
    }

    return if (modelRaw.startsWith(manufacturerRaw, ignoreCase = true)) {
        modelRaw
    } else {
        "$manufacturer $modelRaw"
    }
}
