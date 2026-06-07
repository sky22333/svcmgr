package com.androidservice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class AndroidServiceApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "binary_service_channel"
        const val NOTIFICATION_CHANNEL_NAME = "服务进程"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示二进制进程服务状态"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
