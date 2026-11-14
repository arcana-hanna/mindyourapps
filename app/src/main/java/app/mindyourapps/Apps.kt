package app.mindyourapps

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Apps we never offer, so nobody can lock themselves out of turning this off. */
private val NEVER_OFFER = setOf("com.android.settings")

/** Small in-memory cache so lists don't reload icons while scrolling. */
object AppCache {
    private val map = HashMap<String, AppEntry>()

    fun put(entry: AppEntry) {
        map[entry.pkg] = entry
    }

    fun get(pm: PackageManager, pkg: String): AppEntry =
        map.getOrPut(pkg) { load(pm, pkg) }

    private fun load(pm: PackageManager, pkg: String): AppEntry = try {
        val info = pm.getApplicationInfo(pkg, 0)
        AppEntry(
            pkg,
            pm.getApplicationLabel(info).toString(),
            pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        )
    } catch (e: Exception) {
        AppEntry(pkg, pkg, null)
    }
}

/** Every app with a launcher icon, minus this app, home launchers and Settings. */
suspend fun loadApps(pm: PackageManager, ownPackage: String): List<AppEntry> =
    withContext(Dispatchers.Default) {
        val homePackages = pm
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()

        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter {
                val p = it.activityInfo.packageName
                p != ownPackage && p !in homePackages && p !in NEVER_OFFER
            }
            .map {
                AppEntry(
                    pkg = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = try {
                        it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                )
            }
            .sortedBy { it.label.lowercase() }
    }
