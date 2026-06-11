package com.apkscout.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkscout.app.settings.ReleaseChannelSettings

@Composable
fun SettingsScreen(
    settings: ReleaseChannelSettings,
    onDevChanged: (Boolean) -> Unit,
    onAlphaChanged: (Boolean) -> Unit,
    onBetaChanged: (Boolean) -> Unit,
    onRcChanged: (Boolean) -> Unit,
    onPrereleaseChanged: (Boolean) -> Unit
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
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Release channels",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )

                    ChannelRow(
                        title = "Dev",
                        subtitle = "Nightly, canary, snapshot, internal",
                        checked = settings.includeDev,
                        onCheckedChange = onDevChanged
                    )

                    ChannelRow(
                        title = "Alpha",
                        subtitle = null,
                        checked = settings.includeAlpha,
                        onCheckedChange = onAlphaChanged
                    )

                    ChannelRow(
                        title = "Beta",
                        subtitle = null,
                        checked = settings.includeBeta,
                        onCheckedChange = onBetaChanged
                    )

                    ChannelRow(
                        title = "RC",
                        subtitle = "Release candidate",
                        checked = settings.includeRc,
                        onCheckedChange = onRcChanged
                    )

                    ChannelRow(
                        title = "Prerelease",
                        subtitle = "Preview, pre-release, early access",
                        checked = settings.includePrerelease,
                        onCheckedChange = onPrereleaseChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
