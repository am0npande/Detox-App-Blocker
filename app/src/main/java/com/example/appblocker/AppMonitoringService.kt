package com.example.appblocker

import android.annotation.SuppressLint
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TreeMap
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isTimerRunning = false
    private var packageName = ""
    private var totalDuration: Long = 0L
    private var iscoolDownRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            timerDetectedReciever, IntentFilter("com.example.appblocker.TIMER_TRIGGER"),
            RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.toString() -> startMonitoring()
            Actions.STOP.toString() -> {
                NotificationManagerCompat.from(this@AppMonitoringService).cancelAll()
                serviceScope.cancel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("ForegroundServiceType")
    private fun startMonitoring() {
        Toast.makeText(this, "Monitoring Started", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            monitorForegroundApp()
        }
    }

    @SuppressLint("ForegroundServiceType")
    private suspend fun monitorForegroundApp() {

        val notification = NotificationCompat.Builder(this, "channel1")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("App Blocker")
            .setContentText("Monitoring is running")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        startForeground(2, notification)


        var lastForegroundApp = ""
        while (!isTimerRunning) {
            val currentForegroundApp = getForegroundAppPackageName(this@AppMonitoringService)

//            if (currentForegroundApp == lastForegroundApp) {
//                return
//            }
//            lastForegroundApp = currentForegroundApp

            if (!iscoolDownRunning && currentForegroundApp != "unknown" ) sendAppDetectedBroadcast(currentForegroundApp)

            if (iscoolDownRunning && packageName == currentForegroundApp) {
                Intent(this, MainActivity::class.java).apply {
                    flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(this)
                }

                //Toast.makeText(this, "Wait for CoolDown", Toast.LENGTH_SHORT).show()
            }
            delay(1500L)
        }
    }

    private fun sendAppDetectedBroadcast(packageName: String) {
        val intent = Intent("com.example.appblocker.APP_DETECTED")
        intent.putExtra("PACKAGE_NAME", packageName)
        intent.putExtra("SHOW_OVERLAY_VISIBLE", !isTimerRunning)
        sendBroadcast(intent)
    }

    private fun updateCoolDownTimer(timeRemaining: Int, isActive: Boolean) {
        val intent = Intent("com.example.appblocker.COOL_DOWN_TIMER")
        intent.putExtra("COOL_DOWN_TIMER", timeRemaining)
        intent.putExtra("IS_ACTIVE", isActive)
        sendBroadcast(intent)
    }

    private fun getForegroundAppPackageName(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - TimeUnit.SECONDS.toMillis(1), // Check recent usage
            currentTime
        )

        val sortedMap = TreeMap<Long, UsageStats>()
        for (stat in stats) {
            sortedMap[stat.lastTimeUsed] = stat
        }

        return if (sortedMap.isNotEmpty()) sortedMap[sortedMap.lastKey()]?.packageName ?: "unknown"
        else "unknown"

    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(timerDetectedReciever)
        Toast.makeText(this, "Monitoring has been Stopped ", Toast.LENGTH_SHORT).show()
    }

    private val timerDetectedReciever = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isTimerRunning = true
            packageName = intent?.getStringExtra("PACKAGE_NAME") ?: ""
            totalDuration = intent?.getLongExtra("TIMER", 3000L) ?: 3000L

            if (isTimerRunning) {
                startTimerNotification(totalDuration)
            }

        }
    }

    @SuppressLint("DefaultLocale")
    fun formatTime(timeRemaining: Long): String {
        val hours = timeRemaining / 3600
        val minutes = (timeRemaining % 3600) / 60
        val seconds = timeRemaining % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @SuppressLint("MissingPermission", "ForegroundServiceType")
    private fun startTimerNotification(duration: Long) {

        val notification = NotificationCompat.Builder(this, "channel1")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("App Blocker Timer")
            .setContentText("Timer is Starting")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        // Start the timer logic in the background
        serviceScope.launch {
            var timeRemaining = duration / 1000

            while (timeRemaining > 0 && isTimerRunning) {
                updateNotification(timeRemaining)
                delay(1000L)  // Wait for 1 second
                timeRemaining--
            }

            isTimerRunning = false
            NotificationManagerCompat.from(this@AppMonitoringService).cancel(1)

            stopForeground(true)

            //starting cooldown
            iscoolDownRunning = true
            startCooldownTimer()
            monitorForegroundApp()


        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(secondsRemaining: Long) {
        val notification = NotificationCompat.Builder(this, "channel1")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("App Blocker Timer")
            .setContentText("Time remaining for $packageName: ${formatTime(secondsRemaining)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(this).notify(1, notification)
    }

    private fun startCooldownTimer() {

        serviceScope.launch {

            var cooldownTimeRemaining = 1

            while (cooldownTimeRemaining <= 30) {

                if (cooldownTimeRemaining == 30) iscoolDownRunning = !iscoolDownRunning
                updateCoolDownTimer(cooldownTimeRemaining, iscoolDownRunning)
                delay(1000L)
                cooldownTimeRemaining++
            }
        }
    }
    enum class Actions {
        START, STOP
    }
}


