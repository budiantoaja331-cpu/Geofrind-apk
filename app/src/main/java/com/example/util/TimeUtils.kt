package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    /**
     * Formats user's online/offline status into a readable string.
     * e.g., "Online", "Aktif baru saja", "Aktif 5 mnt lalu", "Aktif 2 jam lalu", "Aktif kemarin"
     */
    fun formatPresenceStatus(context: android.content.Context, isOnline: Boolean, lastActive: Long): String {
        if (isOnline) {
            return context.getString(com.example.R.string.online)
        }

        if (lastActive <= 0) {
            return context.getString(com.example.R.string.offline)
        }

        val now = System.currentTimeMillis()
        val diffMillis = (now - lastActive).coerceAtLeast(0)
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            diffMinutes < 2 -> context.getString(com.example.R.string.active_just_now)
            diffMinutes < 60 -> context.getString(com.example.R.string.active_mins_ago, diffMinutes)
            diffHours < 24 -> context.getString(com.example.R.string.active_hours_ago, diffHours)
            diffDays == 1L -> context.getString(com.example.R.string.active_yesterday)
            diffDays < 7 -> context.getString(com.example.R.string.active_days_ago, diffDays)
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                context.getString(com.example.R.string.active_date, sdf.format(Date(lastActive)))
            }
        }
    }
}
