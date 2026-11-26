package com.blockapp.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.blockapp.MainActivity
import com.blockapp.R
import com.blockapp.data.Prefs
import com.blockapp.lock.UsageTracker
import java.util.concurrent.TimeUnit

/**
 * Móvil del PADRE: cada ~15 min (lo mínimo que permite Android en segundo plano)
 * revisa si el hijo pidió más tiempo o instaló apps, y muestra una notificación.
 */
class ParentEventsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        Prefs.init(ctx)
        if (Prefs.role != Prefs.ROLE_PARENT || !ParentLink.isLinked()) return Result.success()
        val events = try {
            ParentLink.fetchEvents()
        } catch (_: Exception) {
            return Result.retry()
        }
        val child = Prefs.parentChildName
        for (e in events) {
            val label = e.optString("label").ifBlank { e.optString("pkg") }
            when (e.optString("type")) {
                "time_request" -> notify(
                    ctx,
                    "$child pide más tiempo",
                    "$label · usó ${UsageTracker.format(e.optLong("used"))}. Toca para responder."
                )
                "new_app" -> notify(
                    ctx,
                    "$child instaló una app",
                    if (e.optBoolean("locked")) "$label (quedó con PIN). Toca para revisarla."
                    else "$label. Toca para revisarla."
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "avisos_hijo"
        private const val WORK = "parent-events"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ParentEventsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        private fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Avisos del móvil del hijo", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            try {
                NotificationManagerCompat.from(context).notify((title + text).hashCode(), n)
            } catch (_: SecurityException) {
            }
        }
    }
}
