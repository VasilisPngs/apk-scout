package com.apkscout.android

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkscout.android.apkmirror.ApkMirrorApiClient
import com.apkscout.android.apkmirror.ApkMirrorSource
import com.apkscout.android.settings.ReleaseChannelFilter
import com.apkscout.android.settings.ReleaseChannelSettings
import com.apkscout.android.settings.SettingsStore
import com.apkscout.android.ui.SettingsScreen
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
    val url: String,
    val formatLabel: String? = null
)

data class DeviceProfile(
    val deviceName: String,
    val androidVersion: String,
    val densityDpi: Int
)

enum class AppListFilter {
    ALL,
    USER,
    SYSTEM,
    UPDATES
}

private enum class RootScreen {
    HOME,
    SEARCH,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var darkMode by remember { mutableStateOf(SettingsStore.readDarkMode(context)) }

            APKScoutTheme(darkMode = darkMode) {
                APKScoutRoot(
                    darkMode = darkMode,
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        SettingsStore.writeDarkMode(context, enabled)
                    }
                )
            }
        }
    }
}

private fun buildUpdateLine(installedVersionName: String, update: UpdateInfo): String {
    return "$installedVersionName -> ${update.versionName}"
}

@Composable
private fun PackageFormatLabel(
    formatLabel: String?,
    modifier: Modifier = Modifier
) {
    val label = formatLabel
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "APK"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1
        )
    }
}

@Composable
fun APKScoutTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkMode) {
        darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF242424),
            surfaceVariant = Color(0xFF242424),
            primary = Color(0xFFE3E3E3),
            onPrimary = Color(0xFF121212),
            onBackground = Color(0xFFF1F1F1),
            onSurface = Color(0xFFF1F1F1),
            onSurfaceVariant = Color(0xFFD6D6D6),
            outline = Color(0xFF8D8D8D),
            outlineVariant = Color(0xFF6E6E6E)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF3F3F3),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFFFFFFF),
            primary = Color(0xFF1A1A1A),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF171717),
            onSurface = Color(0xFF171717),
            onSurfaceVariant = Color(0xFF4A4A4A),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFFD0D0D0)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun APKScoutRoot(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(RootScreen.HOME) }
    var releaseSettings by remember { mutableStateOf(SettingsStore.read(context)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            APKScoutBottomBar(
                currentScreen = currentScreen,
                onScreenChange = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        when (currentScreen) {
            RootScreen.HOME,
            RootScreen.SEARCH -> {
                APKScoutScreen(
                    modifier = Modifier.padding(innerPadding),
                    releaseSettings = releaseSettings,
                    darkMode = darkMode,
                    searchActive = currentScreen == RootScreen.SEARCH,
                    onDarkModeChange = onDarkModeChange
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
private fun APKScoutBottomBar(
    currentScreen: RootScreen,
    onScreenChange: (RootScreen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            NavigationBarItem(
                selected = currentScreen == RootScreen.HOME,
                onClick = { onScreenChange(RootScreen.HOME) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            )

            NavigationBarItem(
                selected = currentScreen == RootScreen.SEARCH,
                onClick = { onScreenChange(RootScreen.SEARCH) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            )

            NavigationBarItem(
                selected = currentScreen == RootScreen.SETTINGS,
                onClick = { onScreenChange(RootScreen.SETTINGS) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            )
        }
    }
}

@Composable
fun APKScoutScreen(
    modifier: Modifier,
    releaseSettings: ReleaseChannelSettings,
    darkMode: Boolean,
    searchActive: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(AppListFilter.UPDATES) }
    var searchQuery by remember { mutableStateOf("") }
    var scanRequest by remember { mutableIntStateOf(0) }
    var updateRequest by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var rawUpdates by remember { mutableStateOf<Map<String, UpdateInfo>>(emptyMap()) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var loadingApps by remember { mutableStateOf(true) }
    var checkingUpdates by remember { mutableStateOf(false) }

    LaunchedEffect(searchActive) {
        if (!searchActive) {
            searchQuery = ""
        }
    }

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

    val homeVisibleApps = remember(apps, updates, selectedFilter) {
        filterApps(
            apps = apps,
            updates = updates,
            filter = selectedFilter,
            query = ""
        )
    }

    val searchActiveApps = remember(apps, updates, searchQuery) {
        filterApps(
            apps = apps,
            updates = updates,
            filter = AppListFilter.ALL,
            query = searchQuery
        )
    }

    val activeVisibleApps = if (searchActive) {
        searchActiveApps
    } else {
        homeVisibleApps
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    darkMode = darkMode,
                    onToggleTheme = { onDarkModeChange(!darkMode) },
                    onRefresh = {
                        scanRequest++
                        updateRequest++
                    }
                )
            }

            if (searchActive) {
                item {
                    SearchBarCard(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                }
            } else {
                item {
                    ControlsCard(
                        selectedFilter = selectedFilter,
                        visibleCount = homeVisibleApps.size,
                        totalCount = apps.size,
                        loadingApps = loadingApps,
                        checkingUpdates = checkingUpdates,
                        updateError = updateError,
                        onFilterChange = { selectedFilter = it }
                    )
                }
            }
            items(
                items = activeVisibleApps,
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
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
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

        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = if (darkMode) "Switch to light mode" else "Switch to dark mode"
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
    UniformCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
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
            androidVersion = resolveAndroidVersion(),
            densityDpi = context.resources.displayMetrics.densityDpi
        )
    }
}

@Composable
fun HeaderCard(
    profile: DeviceProfile,
    totalCount: Int,
    visibleCount: Int,
    loadingApps: Boolean,
    checkingUpdates: Boolean,
    updateError: String?
) {
    UniformCard {
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
                    label = { Text("Android ${profile.androidVersion}") }
                )

                AssistChip(
                    onClick = {},
                    label = { Text("${profile.densityDpi} dpi") }
                )
            }

            Text(
                text = when {
                    loadingApps -> "Scanning installed apps..."
                    checkingUpdates -> "Checking APKMirror updates..."
                    updateError != null -> "APKMirror check failed: $updateError"
                    visibleCount == totalCount -> "$totalCount apps found"
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
    visibleCount: Int,
    totalCount: Int,
    loadingApps: Boolean,
    checkingUpdates: Boolean,
    updateError: String?,
    onFilterChange: (AppListFilter) -> Unit
) {
    UniformCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactFilterButton(
                    label = "Updates",
                    selected = selectedFilter == AppListFilter.UPDATES,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.UPDATES) }
                )

                CompactFilterButton(
                    label = "User",
                    selected = selectedFilter == AppListFilter.USER,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.USER) }
                )

                CompactFilterButton(
                    label = "System",
                    selected = selectedFilter == AppListFilter.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.SYSTEM) }
                )

                CompactFilterButton(
                    label = "All",
                    selected = selectedFilter == AppListFilter.ALL,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.ALL) }
                )
            }

            Text(
                text = when {
                    loadingApps -> "Loading apps..."
                    checkingUpdates -> "$visibleCount of $totalCount visible • checking updates..."
                    updateError != null -> "$visibleCount of $totalCount visible • update check failed"
                    else -> "$visibleCount of $totalCount visible"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (updateError == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun CompactFilterButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp
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
                            buildUpdateLine(app.versionName, update)
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
                    PackageFormatLabel(
                        formatLabel = update.formatLabel,
                        modifier = Modifier.padding(end = 8.dp)
                    )

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
fun UniformCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(32.dp)
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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

fun resolveAndroidVersion(): String {
    val release = Build.VERSION.RELEASE.orEmpty().trim()

    return release.ifBlank {
        Build.VERSION.SDK_INT.toString()
    }
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
