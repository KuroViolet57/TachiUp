package com.tachiup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tachiup.data.CatalogEntry
import com.tachiup.data.ContentWarning
import com.tachiup.data.ExtensionScanner
import com.tachiup.data.ExtensionStatus
import com.tachiup.data.ExtensionStore
import com.tachiup.data.GithubIssue
import com.tachiup.data.InstalledExtension
import com.tachiup.data.ReaderApp
import com.tachiup.data.ReaderApps
import com.tachiup.data.RepoApi
import com.tachiup.data.RepoExtension
import com.tachiup.data.Repos
import com.tachiup.data.Settings
import com.tachiup.data.UpdateState
import com.tachiup.install.Installer
import com.tachiup.install.InstallResult
import com.tachiup.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Progress of the installs queued since the queue was last empty. */
data class BatchProgress(val total: Int = 0, val done: Int = 0, val failed: Int = 0)

data class UiState(
    val installed: List<InstalledExtension> = emptyList(),
    val statuses: List<ExtensionStatus> = emptyList(),
    val catalog: List<CatalogEntry> = emptyList(),
    val stores: List<ExtensionStore> = emptyList(),
    /** repo key -> why its index couldn't be loaded. */
    val storeErrors: Map<String, String> = emptyMap(),
    val readerApps: List<ReaderApp> = emptyList(),
    val issues: Map<String, List<GithubIssue>> = emptyMap(),
    val scanning: Boolean = false,
    val refreshing: Boolean = false,
    val loadingIssues: Boolean = false,
    val workingPkgs: Set<String> = emptySet(),
    val selectedPkgs: Set<String> = emptySet(),
    val batch: BatchProgress? = null,
    val useShizuku: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val includeNsfw: Boolean = true,
) {
    val updatable: List<ExtensionStatus>
        get() = statuses.filter { it.state == UpdateState.UPDATE_AVAILABLE }

    /** Up to date, but signed with a key the reader app won't trust through the store. */
    val foreignSigned: List<ExtensionStatus>
        get() = statuses.filter { it.state == UpdateState.FOREIGN_SIGNATURE }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val api = RepoApi()
    private val scanner = ExtensionScanner(app)
    private val settings = Settings(app)
    private val installer = Installer(app)

    private val _state = MutableStateFlow(
        UiState(useShizuku = settings.useShizuku, includeNsfw = settings.includeNsfw),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** pkg -> (store, extension) chosen across all stores. */
    private var repoMap: Map<String, Pair<ExtensionStore, RepoExtension>> = emptyMap()

    /** Downloads run a few at a time; installs go one by one so `pm` isn't flooded. */
    private val downloadSlots = Semaphore(DOWNLOAD_CONCURRENCY)
    private val installLock = Mutex()

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

    fun setIncludeNsfw(value: Boolean) {
        settings.includeNsfw = value
        _state.update { it.copy(includeNsfw = value) }
        recomputeStatuses()
    }

    fun updateShizukuState(available: Boolean, granted: Boolean) {
        _state.update { it.copy(shizukuAvailable = available, shizukuGranted = granted) }
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            // One PackageManager call per extension for its signatures; keep it off the main thread.
            val installed = withContext(Dispatchers.IO) {
                runCatching { scanner.scan() }.getOrElse {
                    Logger.e("Scan failed", it); emptyList()
                }
            }
            val readerApps = withContext(Dispatchers.IO) {
                runCatching { ReaderApps.find(getApplication()) }.getOrElse {
                    Logger.e("Reader app lookup failed", it); emptyList()
                }
            }
            Logger.i("Scanned device: ${installed.size} extension(s) found")
            _state.update { it.copy(installed = installed, readerApps = readerApps, scanning = false) }
            recomputeStatuses()
        }
    }

    fun refreshRepos() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val stores = mutableListOf<ExtensionStore>()
            val errors = mutableMapOf<String, String>()
            for (repo in Repos.ALL) {
                runCatching { api.fetchStore(repo) }
                    .onSuccess { store ->
                        Logger.i("${store.name}: ${store.extensions.size} extensions (${store.indexUrl})")
                        stores += store
                    }
                    .onFailure {
                        Logger.e("${repo.name} index fetch failed", it)
                        errors[repo.key] = it.message ?: it.javaClass.simpleName
                    }
            }
            val map = mutableMapOf<String, Pair<ExtensionStore, RepoExtension>>()
            for (store in stores) {
                for (ext in store.extensions) {
                    val existing = map[ext.pkg]
                    if (existing == null || ext.versionCode > existing.second.versionCode) {
                        map[ext.pkg] = store to ext
                    }
                }
            }
            repoMap = map
            _state.update { it.copy(refreshing = false, stores = stores, storeErrors = errors) }
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
        val installedByPkg = _state.value.installed.associateBy { it.pkg }
        val statuses = _state.value.installed.map { inst ->
            val match = repoMap[inst.pkg]
            ExtensionStatus(installed = inst, repoExtension = match?.second, store = match?.first)
        }.sortedWith(compareBy({ it.state.ordinal }, { it.label.lowercase() }))

        val includeNsfw = _state.value.includeNsfw
        val catalog = repoMap.values
            .asSequence()
            .filter { includeNsfw || it.second.contentWarning != ContentWarning.NSFW }
            .map { (store, ext) -> CatalogEntry(store, ext, installedByPkg[ext.pkg]) }
            .sortedBy { it.ext.name.lowercase() }
            .toList()

        _state.update { it.copy(statuses = statuses, catalog = catalog) }
    }

    fun toggleSelected(pkg: String) {
        _state.update {
            it.copy(selectedPkgs = if (pkg in it.selectedPkgs) it.selectedPkgs - pkg else it.selectedPkgs + pkg)
        }
    }

    fun select(pkgs: Collection<String>) {
        _state.update { it.copy(selectedPkgs = it.selectedPkgs + pkgs) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPkgs = emptySet()) }
    }

    fun installSelected() {
        val selected = _state.value.selectedPkgs
        enqueue(selected.mapNotNull { pkg -> repoMap[pkg]?.let { (store, ext) -> jobFor(store, ext) } })
        clearSelection()
    }

    fun updateAll() {
        enqueue(_state.value.updatable.mapNotNull { it.toJob() })
    }

    /** Replaces every foreign-signed extension with the store's build so it's trusted automatically. */
    fun reinstallForeignSigned() {
        enqueue(_state.value.foreignSigned.mapNotNull { it.toJob() })
    }

    fun update(status: ExtensionStatus) {
        enqueue(listOfNotNull(status.toJob()))
    }

    fun installCatalog(entry: CatalogEntry) {
        enqueue(listOf(jobFor(entry.store, entry.ext)))
    }

    private data class InstallJob(
        val store: ExtensionStore,
        val ext: RepoExtension,
        val label: String,
        val fromVersion: String?,
    )

    private fun ExtensionStatus.toJob(): InstallJob? {
        val store = store ?: return null
        val ext = repoExtension ?: return null
        return InstallJob(store, ext, label, installedVersion)
    }

    private fun jobFor(store: ExtensionStore, ext: RepoExtension) = InstallJob(
        store = store,
        ext = ext,
        label = ext.name,
        fromVersion = _state.value.installed.firstOrNull { it.pkg == ext.pkg }?.versionName,
    )

    private fun enqueue(jobs: List<InstallJob>) {
        val busy = _state.value.workingPkgs
        val fresh = jobs.distinctBy { it.ext.pkg }.filter { it.ext.pkg !in busy }
        if (fresh.isEmpty()) return
        val useShizuku = _state.value.useShizuku
        if (fresh.size > 1) {
            Logger.i("Queued ${fresh.size} extensions (${if (useShizuku) "Shizuku" else "system installer"})")
        }
        _state.update { s ->
            val batch = s.batch ?: BatchProgress()
            s.copy(
                workingPkgs = s.workingPkgs + fresh.map { it.ext.pkg },
                batch = batch.copy(total = batch.total + fresh.size),
            )
        }
        for (job in fresh) {
            viewModelScope.launch { runJob(job, useShizuku) }
        }
    }

    private suspend fun runJob(job: InstallJob, useShizuku: Boolean) {
        val ext = job.ext
        var ok = false
        try {
            val action = if (job.fromVersion == null) "Installing" else "Updating"
            val versionInfo = job.fromVersion?.let { "$it → ${ext.versionName}" } ?: ext.versionName
            Logger.i("$action ${job.label} $versionInfo from ${job.store.name}")
            val apk = downloadSlots.withPermit {
                Logger.d("Downloading ${ext.apkUrl}")
                api.downloadApk(ext, getApplication<Application>().cacheDir)
            }
            Logger.d("Downloaded ${ext.apkFileName} (${apk.length()} bytes)")
            try {
                ok = when (val r = installLock.withLock { installer.install(apk, ext.pkg, job.label, useShizuku) }) {
                    is InstallResult.Success -> {
                        Logger.i("✓ ${job.label} ${ext.versionName} installed")
                        true
                    }
                    is InstallResult.PendingUserAction -> {
                        Logger.i("Confirm ${job.label} install in the system dialog")
                        true
                    }
                    is InstallResult.Failure -> {
                        Logger.e("✗ ${job.label}: ${r.message}")
                        false
                    }
                }
            } finally {
                apk.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.e("✗ ${job.label} install failed", t)
        } finally {
            finishJob(ext.pkg, ok)
        }
    }

    private fun finishJob(pkg: String, ok: Boolean) {
        var finished: BatchProgress? = null
        _state.update { s ->
            val batch = s.batch?.let { it.copy(done = it.done + 1, failed = it.failed + if (ok) 0 else 1) }
            val complete = batch == null || batch.done >= batch.total
            finished = if (complete) batch else null
            s.copy(workingPkgs = s.workingPkgs - pkg, batch = if (complete) null else batch)
        }
        finished?.let { batch ->
            if (batch.total > 1) {
                Logger.i("Batch finished: ${batch.total - batch.failed}/${batch.total} succeeded, ${batch.failed} failed")
            }
            scan()
        }
    }

    fun uninstallSilent(pkg: String, label: String) {
        viewModelScope.launch {
            _state.update { it.copy(workingPkgs = it.workingPkgs + pkg) }
            try {
                when (val r = installer.uninstallSilent(pkg, label)) {
                    is InstallResult.Success -> {
                        Logger.i("✓ $label uninstalled")
                        scan()
                    }
                    is InstallResult.PendingUserAction -> {}
                    is InstallResult.Failure -> Logger.e("✗ uninstall $label: ${r.message}")
                }
            } finally {
                _state.update { it.copy(workingPkgs = it.workingPkgs - pkg) }
            }
        }
    }

    companion object {
        private const val DOWNLOAD_CONCURRENCY = 4
    }
}
