package com.livestream.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.livestream.app.MainActivity
import com.livestream.domain.channel.repository.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备开机自启动广播接收器
 * 当用户在应用设置中开启“开机自启动”开关后，系统引导完成自动拉起直播播放主界面
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = userPreferencesRepository.userPreferences.first()
                    if (prefs.isBootStartupEnabled) {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(launchIntent)
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
