package com.inexra.nova

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the user-enabled lightweight background service after reboot or app update. */
class NovaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val enabled = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
            .getBoolean("background_mode", false)
        if (!enabled) return

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BackgroundKeepAliveService::class.java)
            )
        }
    }
}
