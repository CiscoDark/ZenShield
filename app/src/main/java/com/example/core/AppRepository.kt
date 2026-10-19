package com.example.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val category: Int // ApplicationInfo.category (e.g., CATEGORY_GAME, CATEGORY_SOCIAL, CATEGORY_PRODUCTIVITY)
)

class AppRepository(private val context: Context) {

    private var cachedApps: List<InstalledAppInfo>? = null

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            return@withContext cachedApps!!
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val appMap = mutableMapOf<String, InstalledAppInfo>()
        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) continue // Exclude self
            if (!appMap.containsKey(pkg)) {
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category
                } else {
                    ApplicationInfo.CATEGORY_UNDEFINED
                }

                appMap[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    isSystemApp = isSystem,
                    category = category
                )
            }
        }

        val sortedList = appMap.values.sortedBy { it.appName.lowercase() }
        cachedApps = sortedList
        sortedList
    }
}
