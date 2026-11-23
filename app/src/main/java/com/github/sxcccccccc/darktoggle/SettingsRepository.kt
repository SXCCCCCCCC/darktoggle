package com.github.sxcccccccc.darktoggle

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 读写系统深色强制设置，并在本地保存一份镜像。
 *
 * 列表键在 Settings.System，需要 WRITE_SETTINGS；Android 6+ 对非公开
 * system 键的写入按 targetSdk 分级，targetSdk <= 22 时只告警不抛异常。
 * 全局开关在 Settings.Secure，需要 WRITE_SECURE_SETTINGS（adb 授予）。
 *
 * Android 12+ 会拦截非公开 system 键的读取，读失败时回落到本地镜像。
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSecureWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            PERMISSION_WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasWriteSettingsPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(appContext)
        } else {
            true
        }

    /** 返回 null 表示系统读取被拦截且没有本地镜像。 */
    fun readGlobalEnabled(): Boolean? {
        try {
            when (Settings.Secure.getInt(appContext.contentResolver, KEY_GLOBAL, -1)) {
                1 -> {
                    prefs.edit().putBoolean(PREF_GLOBAL, true).apply()
                    return true
                }
                0 -> {
                    prefs.edit().putBoolean(PREF_GLOBAL, false).apply()
                    return false
                }
            }
        } catch (_: Throwable) {
            // 读取被拦截，走本地镜像
        }
        return if (prefs.contains(PREF_GLOBAL)) prefs.getBoolean(PREF_GLOBAL, false) else null
    }

    fun writeGlobalEnabled(enabled: Boolean): Result<Unit> = try {
        val ok = Settings.Secure.putInt(
            appContext.contentResolver,
            KEY_GLOBAL,
            if (enabled) 1 else 0,
        )
        if (ok) {
            prefs.edit().putBoolean(PREF_GLOBAL, enabled).apply()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Settings.Secure.putInt returned false"))
        }
    } catch (t: Throwable) {
        Result.failure(t)
    }

    fun readExcluded(): Set<String>? = readPackages(KEY_DISABLE)

    fun writeExcluded(packages: Set<String>): Result<Unit> = writePackages(KEY_DISABLE, packages)

    private fun readPackages(key: String): Set<String>? {
        val raw = try {
            Settings.System.getString(appContext.contentResolver, key)
        } catch (_: Throwable) {
            querySystemSetting(key)
        }
        if (raw != null) {
            val parsed = parsePackageList(raw)
            prefs.edit().putStringSet(prefKey(key), parsed).apply()
            return parsed
        }
        return prefs.getStringSet(prefKey(key), null)?.toSet()
    }

    private fun writePackages(key: String, packages: Set<String>): Result<Unit> = try {
        val value = packages.sorted().joinToString(",")
        val ok = Settings.System.putString(appContext.contentResolver, key, value)
        if (ok) {
            prefs.edit().putStringSet(prefKey(key), packages.toSet()).apply()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Settings.System.putString returned false"))
        }
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun querySystemSetting(key: String): String? = try {
        appContext.contentResolver.query(
            Uri.parse("content://settings/system/$key"),
            arrayOf("value"),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun prefKey(key: String) = "mirror_$key"

    companion object {
        private const val KEY_GLOBAL = "accessibility_force_invert_color_enabled"
        private const val KEY_DISABLE = "accessibility_force_invert_color_override_packages_to_disable"
        private const val PREFS_NAME = "darktoggle_state"
        private const val PREF_GLOBAL = "global_enabled"
        private const val PERMISSION_WRITE_SECURE_SETTINGS =
            "android.permission.WRITE_SECURE_SETTINGS"

        fun parsePackageList(raw: String?): Set<String> =
            raw.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
    }
}
