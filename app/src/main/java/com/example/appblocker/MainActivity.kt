package com.example.appblocker

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.appblocker.ui.theme.AppBlockerTheme

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {


    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var packageName: String? = null
    private var frameLayout: FrameLayout? = null
    private var coolDownIsActive = mutableStateOf(false)
    private var coolDowntimeRemaining = mutableIntStateOf(0)
    private var isTimmerRunning = mutableStateOf(false)


    @SuppressLint("InlinedApi", "DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestOverlayPermission(this)
        requestUsageAccessPermission(this)
        requestNotificationPermission()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        registerReceiver(
            appDetectedReceiver, IntentFilter("com.example.appblocker.APP_DETECTED"),
            RECEIVER_EXPORTED
        )
        registerReceiver(
            coolDownTimerReceiver,
            IntentFilter("com.example.appblocker.COOL_DOWN_TIMER"),
            RECEIVER_EXPORTED
        )



        setContent {

            AppBlockerTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    if (coolDownIsActive.value) {
                        Text(
                            text = "CoolDown In:",
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = String.format("00:%02d", coolDowntimeRemaining.intValue),
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    } else {
                        Button(
                            onClick = { startMonitoringService() },
                            enabled = !isTimmerRunning.value
                        ) {
                            Text("Start Monitoring")
                        }
                    }

                    Spacer(modifier = Modifier.padding(5.dp))
                    Button(
                        onClick = { stopMonitoringService() }
                    ) {
                        Text("Stop Monitoring")
                    }
                }
            }
        }
    }

    private fun sendTimerBroadcast(packageName: String?, timer: Long) {
        removeOverlay()
        val intent = Intent("com.example.appblocker.TIMER_TRIGGER")
        intent.putExtra("PACKAGE_NAME", packageName)
        intent.putExtra("TIMER", timer)
        sendBroadcast(intent)
    }

    private val appList = mutableListOf(
        "com.instagram.android",
        "com.snapchat.android",
        "com.google.android.youtube",
        "com.twitter.android"
    )

    private val appDetectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            packageName = intent?.getStringExtra("PACKAGE_NAME")
            isTimmerRunning.value = intent?.getBooleanExtra("SHOW_OVERLAY_VISIBLE", false) ?: false

            Log.d("app detected", packageName!!)
            if (packageName!! in appList)
                showOverlay()
            else
                removeOverlay()

        }
    }
    private val coolDownTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coolDowntimeRemaining.intValue = intent?.getIntExtra("COOL_DOWN_TIMER", 0)!!
            coolDownIsActive.value = intent?.getBooleanExtra("IS_ACTIVE", false)!!
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    fun showOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (composeView == null) {
            // Initialize ComposeView
            composeView = ComposeView(this).apply {
                setContent {
                    OverlayContent() // Your composable content
                }
            }

            // Initialize frameLayout and add composeView to it
            frameLayout = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(this@MainActivity)
                setViewTreeSavedStateRegistryOwner(this@MainActivity)
                addView(composeView)
            }

            // Set layout parameters for the overlay
            val overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            overlayParams.gravity = Gravity.CENTER

            // Add the frameLayout containing composeView to the WindowManager
            windowManager.addView(frameLayout, overlayParams)
        }
    }

    private fun removeOverlay() {
        frameLayout?.let { frame ->
            // Ensure WindowManager is initialized
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(frame)
            frameLayout = null
            composeView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(appDetectedReceiver)
        unregisterReceiver(coolDownTimerReceiver)
        removeOverlay()
    }

    private fun startMonitoringService() {
        val startIntent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.Actions.START.toString()
        }
        startForegroundService(startIntent)
    }

    private fun stopMonitoringService() {

        coolDownIsActive.value = false
        isTimmerRunning.value = false
        val stopIntent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.Actions.STOP.toString()
        }
        startService(stopIntent)
    }

    //........................permissions.......................................
    private fun requestOverlayPermission(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    }

    private fun requestUsageAccessPermission(context: Context) {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // Permission already granted
                }

                else -> {
                    // Request notification permission
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }


    @Composable
    fun OverlayContent() {
        val timers = listOf(
            "30 sec" to 30_000L,
            "1 min" to 60_000L,
            "5 min" to 300_000L,
            "10 min" to 600_000L,
            "1 hr" to 3_600_000L,
            "2 hr" to 7_200_000L
        )



        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "App Monitoring Active",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(timers.size) {
                        Button(
                            onClick = { sendTimerBroadcast(packageName, timers[it].second) },
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(timers[it].first)
                        }
                    }
                }
            }
        }
    }
}

