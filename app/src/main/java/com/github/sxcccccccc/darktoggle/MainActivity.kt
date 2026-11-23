package com.github.sxcccccccc.darktoggle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.sxcccccccc.darktoggle.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: SettingsRepository
    private lateinit var adapter: AppListAdapter

    private val loadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconCache = LruCache<String, Drawable>(256)

    private var allApps: List<AppEntry> = emptyList()
    private var excluded: MutableSet<String> = mutableSetOf()
    private var query = ""
    private var includeSystem = false
    private var suppressGlobalCallback = false
    private var loadGeneration = 0
    private var destroyed = false

    private val uiPrefs by lazy { getSharedPreferences("ui", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SettingsRepository(this)
        includeSystem = uiPrefs.getBoolean(PREF_INCLUDE_SYSTEM, false)

        adapter = AppListAdapter({ loadIcon(it) }) { entry -> toggle(entry) }
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.globalSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppressGlobalCallback) onGlobalToggled(checked)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                query = s?.toString().orEmpty().trim()
                applyFilter()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.permissionHint.setOnClickListener { showPermissionDialog() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) {
            syncGlobalSwitch()
            updateStatus()
        }
    }

    override fun onDestroy() {
        destroyed = true
        loadGeneration++
        loadExecutor.shutdownNow()
        iconCache.evictAll()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_include_system)?.isChecked = includeSystem
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            loadData()
            true
        }

        R.id.action_include_system -> {
            includeSystem = !includeSystem
            item.isChecked = includeSystem
            uiPrefs.edit().putBoolean(PREF_INCLUDE_SYSTEM, includeSystem).apply()
            loadData()
            true
        }

        R.id.action_about -> {
            showAbout()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        val generation = ++loadGeneration

        excluded = (repo.readExcluded() ?: emptySet()).toMutableSet()
        syncGlobalSwitch()
        updateStatus()
        adapter.updateSelected(excluded)

        binding.loading.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE

        val currentIncludeSystem = includeSystem
        loadExecutor.execute {
            val apps = try {
                loadApps(currentIncludeSystem)
            } catch (_: Throwable) {
                emptyList()
            }
            mainHandler.post {
                if (destroyed || generation != loadGeneration) return@post
                allApps = apps
                binding.loading.visibility = View.GONE
                applyFilter()
            }
        }
    }

    private fun loadApps(includeSystem: Boolean): List<AppEntry> {
        val pm = packageManager
        val collator = Collator.getInstance(Locale.getDefault())
        val result = ArrayList<AppEntry>()

        for (info in pm.getInstalledApplications(0)) {
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) continue
            if (pm.getLaunchIntentForPackage(info.packageName) == null) continue

            val label = try {
                pm.getApplicationLabel(info).toString()
            } catch (_: Throwable) {
                info.packageName
            }
            result.add(AppEntry(info.packageName, label))

            try {
                iconCache.put(info.packageName, pm.getApplicationIcon(info))
            } catch (_: Throwable) {
                // 图标缺失不影响列表
            }
        }

        result.sortWith { a, b ->
            val byLabel = collator.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
        }
        return result
    }

    private fun loadIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName).also {
                iconCache.put(packageName, it)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyFilter() {
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered, excluded)
        val loading = binding.loading.visibility == View.VISIBLE
        binding.emptyView.visibility =
            if (filtered.isEmpty() && !loading) View.VISIBLE else View.GONE
    }

    private fun toggle(entry: AppEntry) {
        val pkg = entry.packageName
        val wasExcluded = pkg in excluded
        if (wasExcluded) excluded.remove(pkg) else excluded.add(pkg)

        val result = repo.writeExcluded(excluded)
        if (result.isFailure) {
            if (wasExcluded) excluded.add(pkg) else excluded.remove(pkg)
            showWriteFailure(result)
        }

        adapter.updateSelected(excluded)
        updateStatus()
    }

    private fun syncGlobalSwitch() {
        val enabled = repo.readGlobalEnabled()
        suppressGlobalCallback = true
        binding.globalSwitch.isChecked = enabled == true
        suppressGlobalCallback = false

        binding.globalHint.text = when {
            enabled == null -> getString(R.string.global_unknown)
            repo.hasSecureWritePermission() -> getString(R.string.global_hint)
            else -> getString(R.string.global_needs_permission)
        }
    }

    private fun updateStatus() {
        val text = getString(R.string.excluded_count, excluded.size)
        binding.status.text = if (repo.hasWriteSettingsPermission()) {
            text
        } else {
            text + "\n" + getString(R.string.write_settings_missing)
        }
        binding.permissionHint.visibility =
            if (repo.hasSecureWritePermission()) View.GONE else View.VISIBLE
    }

    private fun onGlobalToggled(checked: Boolean) {
        if (!repo.hasSecureWritePermission()) {
            revertGlobalSwitch(checked)
            showPermissionDialog()
            return
        }

        val result = repo.writeGlobalEnabled(checked)
        if (result.isFailure) {
            revertGlobalSwitch(checked)
            showWriteFailure(result)
        } else {
            syncGlobalSwitch()
        }
        updateStatus()
    }

    private fun revertGlobalSwitch(checked: Boolean) {
        suppressGlobalCallback = true
        binding.globalSwitch.isChecked = !checked
        suppressGlobalCallback = false
    }

    private fun showWriteFailure(result: Result<Unit>) {
        val detail = result.exceptionOrNull()?.let {
            "${it.javaClass.simpleName}: ${it.message}"
        }.orEmpty()
        val message = getString(R.string.write_failed) +
            if (detail.isNotEmpty()) "\n$detail" else ""
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showPermissionDialog() {
        val commands = buildString {
            append("adb install -r --bypass-low-target-sdk-block darktoggle-release.apk\n")
            append("adb shell pm grant ")
            append(packageName)
            append(" android.permission.WRITE_SECURE_SETTINGS")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(getString(R.string.permission_dialog_message, commands))
            .setPositiveButton(R.string.copy) { _, _ -> copyToClipboard(commands) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Throwable) {
            "?"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_message, version))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("adb", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREF_INCLUDE_SYSTEM = "include_system"
    }
}
