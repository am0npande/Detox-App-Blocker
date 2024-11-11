package com.example.appblocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class App:Application() {

    override fun onCreate() {
        super.onCreate()
        val notificationChannel = NotificationChannel(
            "channel1",
            "appBlockerChannel",
            NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }
}