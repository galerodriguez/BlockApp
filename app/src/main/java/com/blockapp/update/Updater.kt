package com.blockapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones desde las Releases de GitHub.
 * Cada compilación de GitHub Actions publica la etiqueta "build-N" con versionCode = N + 1.
 */
object Updater {
    private const val REPO = "galerodriguez/BlockApp"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val APK_URL = "https://github.com/$REPO/releases/latest/download/BlockApp.apk"

    data class Release(val versionCode: Long, val name: String)

    fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (_: Exception) {
        0L
    }

    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    /** Última versión publicada, o null si no hay conexión. Bloqueante. */
    fun latest(): Release? {
        val conn = URL(LATEST_API).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode !in 200..299) return null
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val build = o.optString("tag_name").removePrefix("build-").toLongOrNull() ?: return null
            Release(versionCode = build + 1, name = "1.0.$build")
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga el APK más reciente. Bloqueante; [onProgress] recibe 0..100 (o -1 si no se sabe). */
    fun download(context: Context, onProgress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "BlockApp.apk")
        val conn = URL(APK_URL).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            if (conn.responseCode !in 200..299) return null
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -2
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) ((done * 100) / total).toInt() else -1
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            file
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** true si Android ya permite a BlockApp instalar apps. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste «Instalar apps desconocidas» de BlockApp. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Lanza el instalador de Android con el APK descargado. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
