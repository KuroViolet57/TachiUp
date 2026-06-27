package com.tachiup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tachiup.data.ExtensionRepo
import com.tachiup.data.ExtensionScanner
import com.tachiup.data.ExtensionStatus
import com.tachiup.data.GithubIssue
import com.tachiup.data.InstalledExtension
import com.tachiup.data.RepoApi
import com.tachiup.data.RepoExtension
import com.tachiup.data.Repos
import com.tachiup.data.Settings
import com.tachiup.data.UpdateState
import com.tachiup.install.Installer
import com.tachiup.install.InstallResult
import com.tachiup.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val installed: List<InstalledExtension> = emptyList(),
    val statuses: List<ExtensionStatus> = emptyList(),
    val issues: Map<String, List<GithubIssue>> = emptyMap(),
    val scanning: Boolean = false,
    val refreshing: Boolean = false,
    val loadingIssues: Boolean = false,
    val workingPkgs: Set<String> = emptySet(),
    val useShizuku: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
) {
    val updatable: List<ExtensionStatus>
        get() = statuses.filter { it.state == UpdateState.UPDATE_AVAILABLE }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RepoApi()
    private val scanner = ExtensionScanner(app)
    private val settings = Settings(app)
    private val installer = Installer(app)

    private val _state = MutableStateFlow(UiState(useShizuku = settings.useShizuku))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** pkg -> (repo, extension) chosen across all repositories. */
    private var repoMap: Map<String, Pair<ExtensionRepo, RepoExtension>> = emptyMap()

    init {
        Logger.init(app)
    }

    fun refreshAll() {
        refreshRepos()
        scan()
    }

    fun setUseShizuku(value: Boolean) {
        settings.useShizuku = value
        _state.update { it.copy(useShizuku = value) }
    }

    fun updateShizukuState(available: Boolean, granted: Boolean) {
        _state.update { it.copy(shizukuAvailable = available, shizukuGranted = granted) }
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val installed = runCatching { scanner.scan() }.getOrElse {
                Logger.e("Scan failed", it); emptyList()
            }
            Logger.i("Scanned device: ${installed.size} extension(s) found")
            _state.update { it.copy(installed = installed, scanning = false) }
            recomputeStatuses()
        }
    }

    fun refreshRepos() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val map = mutableMapOf<String, Pair<ExtensionRepo, RepoExtension>>()
            for (repo in Repos.ALL) {
                runCatching { api.fetchIndex(repo) }
                    .onSuccess { list ->
                        Logger.i("${repo.name}: ${list.size} extensions in index")
                        for (ext in list) {
                            val existing = map[ext.pkg]
                            if (existing == null || isNewer(ext.version, existing.second.version)) {
                                map[ext.pkg] = repo to ext
                            }
                        }
                    }
                    .onFailure { Logger.e("${repo.name} index fetch failed", it) }
            }
            repoMap = map
            _state.update { it.copy(refreshing = false) }
            recomputeStatuses()
        }
    }

    fun refreshIssues() {
        viewModelScope.launch {
            _state.update { it.copy(loadingIssues = true) }
            val result = mutableMapOf<String, List<GithubIssue>>()
            for (repo in Repos.ALL) {
                runCatching { api.fetchIssues(repo) }
                    .onSuccess { result[repo.key] = it }
                    .onFailure { Logger.e("${repo.name} issues fetch failed", it) }
            }
            _state.update { it.copy(issues = result, loadingIssues = false) }
        }
    }

    private fun recomputeStatuses() {
        val statuses = _state.value.installed.map { inst ->
            val match = repoMap[inst.pkg]
            ExtensionStatus(
                pkg = inst.pkg,
                label = inst.label,
                installedVersion = inst.versionName,
                repoExtension = match?.second,
                repo = match?.first,
            )
        }.sortedWith(compareBy({ it.state.ordinal }, { it.label.lowercase() }))
        _state.update { it.copy(statuses = statuses) }
    }

    fun updateAll() {
        _state.value.updatable.forEach { update(it) }
    }

    fun update(status: ExtensionStatus) {
        val repo = status.repo ?: return
        val ext = status.repoExtension ?: return
        val useShizuku = _state.value.useShizuku
        viewModelScope.launch {
            _state.update { it.copy(workingPkgs = it.workingPkgs + status.pkg) }
            try {
                Logger.i("Updating ${status.label} ${status.installedVersion} → ${ext.version} from ${repo.name}")
                Logger.d("Downloading ${repo.apkUrl(ext.apk)}")
                val apk = api.downloadApk(repo, ext, getApplication<Application>().cacheDir)
                Logger.d("Downloaded ${apk.name} (${apk.length()} bytes)")
                when (val r = installer.install(apk, status.pkg, status.label, useShizuku)) {
                    is InstallResult.Success -> Logger.i("✓ ${status.label} updated to ${ext.version}")
                    is InstallResult.PendingUserAction -> Logger.i("Confirm ${status.label} install in the system dialog")
                    is InstallResult.Failure -> Logger.e("✗ ${status.label}: ${r.message}")
                }
                apk.delete()
            } catch (t: Throwable) {
                Logger.e("✗ ${status.label} update failed", t)
            } finally {
                _state.update { it.copy(workingPkgs = it.workingPkgs - status.pkg) }
            }
        }
    }

    private fun isNewer(a: String, b: String): Boolean = compareVersions(a, b) > 0

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
