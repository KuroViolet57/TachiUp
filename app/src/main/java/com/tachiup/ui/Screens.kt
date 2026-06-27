package com.tachiup.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tachiup.data.ExtensionStatus
import com.tachiup.data.GithubIssue
import com.tachiup.data.Repos
import com.tachiup.data.UpdateState

@Composable
fun ExtensionsScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onUpdateAll: () -> Unit,
    onUpdate: (ExtensionStatus) -> Unit,
    onOpenLog: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Extensions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenLog) { Icon(Icons.Filled.Chat, contentDescription = "Log") }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
        Spacer(Modifier.height(4.dp))
        val busy = state.scanning || state.refreshing
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(if (state.refreshing) "Fetching repositories…" else "Scanning device…")
            }
        }
        Text(
            "${state.installed.size} installed · ${state.updatable.size} updates available",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (state.updatable.isNotEmpty()) {
            Button(
                onClick = onUpdateAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.workingPkgs.isEmpty(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Update all (${state.updatable.size})")
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.installed.isEmpty() && !busy) {
            Text(
                "No Tachiyomi/Mihon extensions found. Pull refresh after granting the app permission to see installed apps.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.statuses, key = { it.pkg }) { status ->
                ExtensionRow(
                    status = status,
                    working = status.pkg in state.workingPkgs,
                    onUpdate = { onUpdate(status) },
                )
            }
        }
    }
}

@Composable
private fun ExtensionRow(status: ExtensionStatus, working: Boolean, onUpdate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(status.label, fontWeight = FontWeight.SemiBold)
                val sub = when (status.state) {
                    UpdateState.UPDATE_AVAILABLE ->
                        "${status.installedVersion} → ${status.latestVersion}  ·  ${status.repo?.name}"
                    UpdateState.UP_TO_DATE -> "${status.installedVersion} · up to date"
                    UpdateState.NOT_IN_REPO -> "${status.installedVersion} · not in repos"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            when {
                working -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                status.state == UpdateState.UPDATE_AVAILABLE ->
                    Button(onClick = onUpdate) {
                        Icon(Icons.Filled.Download, contentDescription = "Update", modifier = Modifier.size(18.dp))
                    }
                status.state == UpdateState.UP_TO_DATE ->
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Up to date", tint = MaterialTheme.colorScheme.primary)
                else -> {}
            }
        }
    }
}

@Composable
fun CommunityScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Community", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (repo in Repos.ALL) {
                item(key = "header-${repo.key}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(repo.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOpenUrl(repo.discordUrl) }) {
                                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Discord")
                                }
                                OutlinedButton(onClick = { onOpenUrl("https://github.com/${repo.githubRepo}/issues") }) {
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("All issues")
                                }
                            }
                        }
                    }
                }
                val issues = state.issues[repo.key].orEmpty()
                if (state.loadingIssues && issues.isEmpty()) {
                    item(key = "loading-${repo.key}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Loading issues…")
                        }
                    }
                }
                items(issues, key = { "${repo.key}-${it.number}" }) { issue ->
                    IssueRow(issue) { onOpenUrl(issue.htmlUrl) }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: GithubIssue, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${issue.number}  ${issue.title}", style = MaterialTheme.typography.bodyMedium)
                if (issue.comments > 0) {
                    Text("${issue.comments} comments", style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    state: UiState,
    onToggleShizuku: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Silent install via Shizuku", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Install updates without confirmation dialogs.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = state.useShizuku, onCheckedChange = onToggleShizuku)
                }
                Spacer(Modifier.height(8.dp))
                val statusText = when {
                    !state.shizukuAvailable -> "Shizuku service not detected"
                    state.shizukuGranted -> "Shizuku connected and permission granted"
                    else -> "Shizuku running — permission not granted"
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall)
                if (state.shizukuAvailable && !state.shizukuGranted) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestShizuku) { Text("Grant Shizuku permission") }
                }
                if (!state.shizukuAvailable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenUrl("https://shizuku.rikka.app/download/") }) {
                        Text("Get Shizuku")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Repositories", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        for (repo in Repos.ALL) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(repo.name, fontWeight = FontWeight.SemiBold)
                    Text(repo.baseUrl, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("TachiUp v8", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LogPanel(state: UiState) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Activity log", style = MaterialTheme.typography.titleMedium)
        Divider(Modifier.padding(vertical = 8.dp))
        if (state.log.isEmpty()) {
            Text("No activity yet.", style = MaterialTheme.typography.bodySmall)
        }
        state.log.asReversed().forEach { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
