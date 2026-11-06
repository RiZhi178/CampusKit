package qdu.campus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.platform.designsystem.QduTheme
import qdu.campus.platform.system.AppForegroundTasks
import qdu.campus.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var foregroundTasks: AppForegroundTasks

    private val viewModel: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val scheme by viewModel.scheme.collectAsStateWithLifecycle()
            val mode by viewModel.mode.collectAsStateWithLifecycle()
            val darkTinted by viewModel.darkTinted.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            // 前台期间校验会话、按需刷新，并保持 6 小时一次的通知检查；离开前台即取消。
            LifecycleStartEffect(Unit) {
                val job = scope.launch { foregroundTasks.run() }
                onStopOrDispose { job.cancel() }
            }
            QduTheme(scheme = scheme, mode = mode, darkTinted = darkTinted) {
                AppNavHost()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
