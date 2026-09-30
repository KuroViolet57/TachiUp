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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tachiup.BuildConfig
import com.tachiup.data.CatalogEntry
import com.tachiup.data.ContentWarning
import com.tachiup.data.ExtensionRepo
import com.tachiup.data.ExtensionStatus
import com.tachiup.data.GithubIssue
import com.tachiup.data.ReaderApp
import com.tachiup.data.Repos
import com.tachiup.data.UpdateState

@Composable
fun ExtensionsScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onUpdateAll: () -> Unit,
    onReinstallForeign: () -> Unit,
    onUpdate: (ExtensionStatus) -> Unit,
    onUninstall: (ExtensionStatus) -> Unit,
    onOpenLog: () -> Unit,
    onOpenTrustSetup: () -> Unit,
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
        StoreErrors(state)
        Text(
            "${state.installed.size} installed · ${state.updatable.size} updates available",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.batch?.let { BatchProgressRow(it) }
        Spacer(Modifier.height(8.dp))
        if (state.updatable.isNotEmpty()) {
            Button(
                onClick = onUpdateAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.updatable.any { it.pkg !in state.workingPkgs },
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Update all (${state.updatable.size})")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.foreignSigned.isNotEmpty()) {
            ForeignSignerCard(
                state = state,
                onReinstall = onReinstallForeign,
                onOpenTrustSetup = onOpenTrustSetup,
            )
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
                    onUninstall = { onUninstall(status) },
                )
            }
        }
    }
}

@Composable
private fun StoreErrors(state: UiState) {
    for (repo in Repos.ALL) {
        val error = state.storeErrors[repo.key] ?: continue
        Text(
            "Couldn't load ${repo.name}: $error",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BatchProgressRow(batch: BatchProgress) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LinearProgressIndicator(
            progress = { batch.done.toFloat() / batch.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        val failed = if (batch.failed > 0) " · ${batch.failed} failed" else ""
        Text("Installing ${batch.done}/${batch.total}$failed", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ForeignSignerCard(
    state: UiState,
    onReinstall: () -> Unit,
    onOpenTrustSetup: () -> Unit,
) {
    val count = state.foreignSigned.size
    val storeNames = state.foreignSigned.mapNotNull { it.store?.name }.distinct().joinToString()
    val canReplace = state.useShizuku && state.shizukuGranted
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("$count signed with another key", fontWeight = FontWeight.SemiBold)
            Text(
                "These weren't built by $storeNames (e.g. old Yuzono builds), so Komikku can't trust them " +
                    "through the store and keeps asking. Reinstalling swaps them for $storeNames's builds; " +
                    "your library and source settings are kept.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!canReplace) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Needs Shizuku silent install — the system installer can't replace an app signed with a different key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onReinstall,
                    enabled = canReplace && state.foreignSigned.any { it.pkg !in state.workingPkgs },
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Reinstall all ($count)")
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onOpenTrustSetup) { Text("Auto-trust setup") }
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    status: ExtensionStatus,
    working: Boolean,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(status.label, fontWeight = FontWeight.SemiBold)
                val sub = when (status.state) {
                    UpdateState.UPDATE_AVAILABLE ->
                        "${status.installedVersion} → ${status.latestVersion}  ·  ${status.store?.name}"
                    UpdateState.FOREIGN_SIGNATURE -> "${status.installedVersion} · signed by another key"
                    UpdateState.UP_TO_DATE -> "${status.installedVersion} · up to date"
                    UpdateState.NOT_IN_REPO -> "${status.installedVersion} · not in repos"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            if (working) {
                CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            } else {
                when (status.state) {
                    UpdateState.UPDATE_AVAILABLE ->
                        IconButton(onClick = onUpdate) {
                            Icon(Icons.Filled.Download, contentDescription = "Update", tint = MaterialTheme.colorScheme.primary)
                        }
                    UpdateState.FOREIGN_SIGNATURE ->
                        IconButton(onClick = onUpdate) {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = "Reinstall from ${status.store?.name}",
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    UpdateState.UP_TO_DATE ->
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Up to date", tint = MaterialTheme.colorScheme.primary)
                    else -> {}
                }
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Filled.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BrowseScreen(
    state: UiState,
    onInstall: (CatalogEntry) -> Unit,
    onUninstall: (CatalogEntry) -> Unit,
    onToggleNsfw: (Boolean) -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelect: (Collection<String>) -> Unit,
    onClearSelection: () -> Unit,
    onInstallSelected: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var onlyNotInstalled by remember { mutableStateOf(false) }

    val q = query.trim().lowercase()
    val filtered = remember(state.catalog, q, onlyNotInstalled) {
        state.catalog.asSequence()
            .filter { !onlyNotInstalled || !it.isInstalled }
            .filter {
                q.isEmpty() ||
                    it.ext.name.lowercase().contains(q) ||
                    it.ext.lang.lowercase().contains(q) ||
                    it.pkg.lowercase().contains(q)
            }
            .take(500)
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Browse extensions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search ${state.catalog.size} extensions") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onlyNotInstalled = !onlyNotInstalled },
                label = { Text(if (onlyNotInstalled) "Not installed ✓" else "Not installed") },
            )
            AssistChip(
                onClick = { onToggleNsfw(!state.includeNsfw) },
                label = { Text(if (state.includeNsfw) "NSFW shown" else "NSFW hidden") },
            )
        }

        val selectedCount = state.selectedPkgs.size
        if (selectedCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onInstallSelected, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Install $selectedCount selected")
                }
                TextButton(onClick = onClearSelection) { Text("Clear") }
            }
            if (!state.useShizuku) {
                Text(
                    "Without Shizuku silent install, each extension opens its own install dialog.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            val installable = filtered.filter { !it.isInstalled || it.hasUpdate }
            if (installable.isNotEmpty()) {
                TextButton(onClick = { onSelect(installable.map { it.pkg }) }) {
                    Text("Select ${installable.size} new or outdated")
                }
            }
        }
        state.batch?.let { BatchProgressRow(it) }

        Spacer(Modifier.height(4.dp))
        if (state.catalog.isEmpty()) {
            Text("Refresh the Extensions tab first to load repository indexes.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Showing ${filtered.size}${if (filtered.size >= 500) "+ (refine search)" else ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.pkg }) { entry ->
                CatalogRow(
                    entry = entry,
                    selected = entry.pkg in state.selectedPkgs,
                    working = entry.pkg in state.workingPkgs,
                    onToggleSelected = { onToggleSelected(entry.pkg) },
                    onInstall = { onInstall(entry) },
                    onUninstall = { onUninstall(entry) },
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    entry: CatalogEntry,
    selected: Boolean,
    working: Boolean,
    onToggleSelected: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggleSelected)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            Column(Modifier.weight(1f)) {
                val rating = when (entry.ext.contentWarning) {
                    ContentWarning.NSFW -> " · 18+"
                    ContentWarning.MIXED -> " · some 18+"
                    ContentWarning.SAFE -> ""
                }
                Text(entry.ext.name, fontWeight = FontWeight.SemiBold)
                val sub = when {
                    entry.hasUpdate -> "${entry.installedVersion} → ${entry.ext.versionName} · ${entry.store.name}"
                    entry.isInstalled -> "installed ${entry.installedVersion} · ${entry.store.name}"
                    else -> "${entry.ext.versionName} · ${entry.ext.lang} · ${entry.store.name}$rating"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            if (working) {
                CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onInstall) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = if (entry.hasUpdate) "Update" else "Install",
                        tint = if (entry.isInstalled && !entry.hasUpdate)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary,
                    )
                }
                if (entry.isInstalled) {
                    IconButton(onClick = onUninstall) {
                        Icon(Icons.Filled.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                    }
                }
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
    onAddStoreToApp: (ReaderApp, ExtensionRepo) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
        TrustCard(state, onAddStoreToApp)

        Spacer(Modifier.height(16.dp))
        Text("Repositories", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        for (repo in Repos.ALL) {
            val store = state.stores.firstOrNull { it.repo.key == repo.key }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(store?.name ?: repo.name, fontWeight = FontWeight.SemiBold)
                    val error = state.storeErrors[repo.key]
                    when {
                        store != null -> {
                            Text("${store.extensions.size} extensions", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Signing key ${store.signingKey.take(8)}…${store.signingKey.takeLast(8)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(store.indexUrl, style = MaterialTheme.typography.bodySmall)
                        }
                        error != null -> Text(
                            "Couldn't load: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Text(repo.storeUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("TachiUp ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrustCard(
    state: UiState,
    onAddStoreToApp: (ReaderApp, ExtensionRepo) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Skip the \"Trust\" prompt", fontWeight = FontWeight.SemiBold)
            Text(
                "Komikku and Mihon automatically trust every extension signed with the key of a store " +
                    "added in the app. Add the store once and extensions installed here load right away — " +
                    "no Trust tap, not even after updates.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (state.readerApps.isEmpty()) {
                Text(
                    "No Komikku or Mihon install found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            for (app in state.readerApps) {
                for (repo in Repos.ALL) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onAddStoreToApp(app, repo) }) { Text("Add ${repo.name}") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Confirm with Add in the dialog that opens. Extensions already installed with a different " +
                    "key (e.g. old Yuzono builds) still need a reinstall — the Installed tab lists them.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun LogPanel(
    entries: List<com.tachiup.util.Logger.Entry>,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Activity log (${entries.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Export") }
            IconButton(onClick = onClear) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear") }
        }
        Divider(Modifier.padding(vertical = 8.dp))
        if (entries.isEmpty()) {
            Text("No activity yet.", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries.asReversed()) { entry ->
                val color = when (entry.level) {
                    com.tachiup.util.Logger.Level.ERROR -> MaterialTheme.colorScheme.error
                    com.tachiup.util.Logger.Level.WARN -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    entry.format(),
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
