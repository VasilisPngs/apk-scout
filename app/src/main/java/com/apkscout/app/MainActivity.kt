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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.apkscout.app.apkmirror.ApkMirrorSource
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

data class DeviceProfile(
    val deviceName: String,
    val sdk: Int,
    val densityDpi: Int,
    val abis: String
)

enum class AppListFilter {
    ALL,
    USER,
    SYSTEM
}

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
fun APKScoutScreen() {
    val context = LocalContext.current
    val profile = rememberDeviceProfile(context)

    var includeSystemApps by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(AppListFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var scanRequest by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(includeSystemApps, scanRequest) {
        loading = true
        apps = withContext(Dispatchers.Default) {
            scanInstalledApps(
                packageManager = context.packageManager,
                includeSystemApps = includeSystemApps
            )
        }
        loading = false
    }

    val visibleApps = remember(apps, selectedFilter, searchQuery) {
        filterApps(
            apps = apps,
            filter = selectedFilter,
            query = searchQuery
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
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
                        loading = loading,
                        searchVisible = searchVisible,
                        onToggleSearch = {
                            searchVisible = !searchVisible
                            if (!searchVisible) {
                                searchQuery = ""
                            }
                        },
                        onRefresh = { scanRequest++ }
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
                        loading = loading
                    )
                }

                item {
                    ControlsCard(
                        includeSystemApps = includeSystemApps,
                        onIncludeSystemAppsChange = { includeSystemApps = it },
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
                        onOpenAPKMirror = {
                            openAPKMirror(
                                context = context,
                                packageName = app.packageName
                            )
                        }
                    )
                }
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
                contentDescription = "Refresh apps"
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
    loading: Boolean
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
                text = when {
                    loading -> "Scanning installed apps..."
                    visibleCount == totalCount -> "$totalCount apps loaded"
                    else -> "$visibleCount of $totalCount apps visible"
                },
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
    selectedFilter: AppListFilter,
    onFilterChange: (AppListFilter) -> Unit
) {
    GlassCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingRow(
                title = "Show system apps",
                checked = includeSystemApps,
                onCheckedChange = onIncludeSystemAppsChange
            )

            Row(
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
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

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
                        text = "Installed: ${app.versionName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Version code: ${app.versionCode}",
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
    filter: AppListFilter,
    query: String
): List<InstalledApp> {
    val normalizedQuery = query.trim().lowercase()

    return apps.filter { app ->
        val matchesFilter = when (filter) {
            AppListFilter.ALL -> true
            AppListFilter.USER -> !app.isSystem
            AppListFilter.SYSTEM -> app.isSystem
        }

        val matchesQuery = normalizedQuery.isEmpty() ||
            app.label.lowercase().contains(normalizedQuery) ||
            app.packageName.lowercase().contains(normalizedQuery)

        matchesFilter && matchesQuery
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
    packageName: String
) {
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            ApkMirrorSource.searchUrl(packageName)
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
