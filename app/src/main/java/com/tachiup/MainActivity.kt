package com.tachiup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tachiup.install.SessionInstaller
import com.tachiup.ui.AppViewModel
import com.tachiup.ui.CommunityScreen
import com.tachiup.ui.ExtensionsScreen
import com.tachiup.ui.LogPanel
import com.tachiup.data.ExtensionRepo
import com.tachiup.data.ReaderApp
import com.tachiup.data.ReaderApps
import com.tachiup.install.ShizukuInstaller
import com.tachiup.ui.BrowseScreen
import com.tachiup.ui.SettingsScreen
import com.tachiup.ui.theme.TachiUpTheme
import com.tachiup.util.Logger
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        refreshShizukuState(granted = result == PackageManager.PERMISSION_GRANTED)
    }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizukuState() }
    private val binderDead = Shizuku.OnBinderDeadListener { vm.updateShizukuState(false, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionInstaller.ResultReceiver.register(applicationContext)

        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        refreshShizukuState()

        setContent {
            TachiUpTheme {
                AppRoot(
                    vm = vm,
                    onOpenUrl = ::openUrl,
                    onRequestShizuku = ::requestShizuku,
                    onUninstall = { pkg, label -> uninstall(pkg, label) },
                    onAddStoreToApp = ::addStoreToApp,
                    onShareLog = ::shareLog,
                    onCopyLog = ::copyLog,
                    onClearLog = { Logger.clear() },
                )
            }
        }

        vm.refreshAll()
        vm.refreshIssues()
    }

    override fun onResume() {
        super.onResume()
        // Reflect installs/uninstalls that happened via the system dialog.
        vm.scan()
    }

    /** Routes uninstall to silent Shizuku when possible, else the system uninstall dialog. */
    private fun uninstall(pkg: String, label: String) {
        val s = vm.state.value
        val canSilent = s.useShizuku &&
            ShizukuInstaller.isAvailable() && ShizukuInstaller.hasPermission()
        if (canSilent) {
            vm.uninstallSilent(pkg, label)
        } else {
            Logger.i("Requesting system uninstall for $label")
            runCatching {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            }.onFailure { Logger.e("Failed to launch uninstall for $label", it) }
        }
    }

    /** Opens the reader app's "add extension store" dialog so its extensions are trusted automatically. */
    private fun addStoreToApp(app: ReaderApp, repo: ExtensionRepo) {
        Logger.i("Opening ${app.label} to add the ${repo.name} store")
        runCatching { startActivity(ReaderApps.addStoreIntent(app, repo)) }
            .onFailure { Logger.e("Failed to open ${app.label}", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    private fun refreshShizukuState(granted: Boolean? = null) {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val isGranted = granted ?: runCatching {
            available && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        vm.updateShizukuState(available, isGranted)
    }

    private fun requestShizuku() {
        runCatching {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    refreshShizukuState(true)
                } else {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun shareLog() {
        runCatching {
            val file = Logger.exportFile(this)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "TachiUp log")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Export TachiUp log"))
        }.onFailure { Logger.e("Failed to share log", it) }
    }

    private fun copyLog() {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TachiUp log", Logger.exportText()))
            Logger.i("Log copied to clipboard")
        }.onFailure { Logger.e("Failed to copy log", it) }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 4001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    vm: AppViewModel,
    onOpenUrl: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onShareLog: () -> Unit,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onUninstall: (String, String) -> Unit,
    onAddStoreToApp: (ReaderApp, ExtensionRepo) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logEntries by Logger.entries.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showLog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                    label = { Text("Installed") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Explore, contentDescription = null) },
                    label = { Text("Browse") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2; vm.refreshIssues() },
                    icon = { Icon(Icons.Filled.Forum, contentDescription = null) },
                    label = { Text("Community") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ExtensionsScreen(
                    state = state,
                    onRefresh = { vm.refreshAll() },
                    onUpdateAll = { vm.updateAll() },
                    onReinstallForeign = { vm.reinstallForeignSigned() },
                    onUpdate = { vm.update(it) },
                    onUninstall = { onUninstall(it.pkg, it.label) },
                    onOpenLog = { showLog = true },
                    onOpenTrustSetup = { tab = 3 },
                )
                1 -> BrowseScreen(
                    state = state,
                    onInstall = { vm.installCatalog(it) },
                    onUninstall = { onUninstall(it.pkg, it.ext.name) },
                    onToggleNsfw = { vm.setIncludeNsfw(it) },
                    onToggleSelected = { vm.toggleSelected(it) },
                    onSelect = { vm.select(it) },
                    onClearSelection = { vm.clearSelection() },
                    onInstallSelected = { vm.installSelected() },
                )
                2 -> CommunityScreen(
                    state = state,
                    onRefresh = { vm.refreshIssues() },
                    onOpenUrl = onOpenUrl,
                )
                3 -> SettingsScreen(
                    state = state,
                    onToggleShizuku = { vm.setUseShizuku(it) },
                    onRequestShizuku = onRequestShizuku,
                    onOpenUrl = onOpenUrl,
                    onAddStoreToApp = onAddStoreToApp,
                )
            }
        }
    }

    if (showLog) {
        ModalBottomSheet(onDismissRequest = { showLog = false }) {
            LogPanel(
                entries = logEntries,
                onShare = onShareLog,
                onCopy = onCopyLog,
                onClear = onClearLog,
            )
        }
    }
}
