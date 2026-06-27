package com.tachiup

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.tachiup.ui.SettingsScreen
import com.tachiup.ui.theme.TachiUpTheme
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
                )
            }
        }

        vm.refreshAll()
        vm.refreshIssues()
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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var showLog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                    label = { Text("Extensions") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; vm.refreshIssues() },
                    icon = { Icon(Icons.Filled.Forum, contentDescription = null) },
                    label = { Text("Community") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
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
                    onUpdate = { vm.update(it) },
                    onOpenLog = { showLog = true },
                )
                1 -> CommunityScreen(
                    state = state,
                    onRefresh = { vm.refreshIssues() },
                    onOpenUrl = onOpenUrl,
                )
                2 -> SettingsScreen(
                    state = state,
                    onToggleShizuku = { vm.setUseShizuku(it) },
                    onRequestShizuku = onRequestShizuku,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
    }

    if (showLog) {
        ModalBottomSheet(onDismissRequest = { showLog = false }) {
            LogPanel(state)
        }
    }
}
