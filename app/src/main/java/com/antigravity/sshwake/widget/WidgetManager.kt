package com.antigravity.sshwake.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.RemoteViews
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.CommandEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class WidgetState {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR
}

object WidgetManager {
    private const val PREFS_NAME = "widget_bindings"
    private const val KEY_PREFIX = "widget_command_"
    private const val STATE_PREFIX = "widget_state_"
    private const val TIMESTAMP_PREFIX = "widget_time_"
    private const val RESET_REQUEST_CODE_OFFSET = 100000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveBinding(context: Context, appWidgetId: Int, commandId: String) {
        getPrefs(context).edit().putString(KEY_PREFIX + appWidgetId, commandId).apply()
    }

    fun getCommandIdForWidget(context: Context, appWidgetId: Int): String? {
        return getPrefs(context).getString(KEY_PREFIX + appWidgetId, null)
    }

    fun removeBinding(context: Context, appWidgetId: Int) {
        cancelStateReset(context, appWidgetId)
        getPrefs(context).edit()
            .remove(KEY_PREFIX + appWidgetId)
            .remove(STATE_PREFIX + appWidgetId)
            .remove(TIMESTAMP_PREFIX + appWidgetId)
            .apply()
    }

    fun saveWidgetState(context: Context, appWidgetId: Int, state: WidgetState) {
        getPrefs(context).edit()
            .putString(STATE_PREFIX + appWidgetId, state.name)
            .putLong(TIMESTAMP_PREFIX + appWidgetId, System.currentTimeMillis())
            .apply()
    }

    fun getWidgetState(context: Context, appWidgetId: Int): WidgetState {
        val stateName = getPrefs(context).getString(STATE_PREFIX + appWidgetId, WidgetState.IDLE.name)
        return try {
            WidgetState.valueOf(stateName ?: WidgetState.IDLE.name)
        } catch (_: Exception) {
            WidgetState.IDLE
        }
    }

    fun getWidgetStateTimestamp(context: Context, appWidgetId: Int): Long {
        return getPrefs(context).getLong(TIMESTAMP_PREFIX + appWidgetId, 0L)
    }

    /**
     * 更新小组件界面，同时持久化当前状态与时间戳
     */
    fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        command: CommandEntity?,
        state: WidgetState = WidgetState.IDLE
    ) {
        saveWidgetState(context, appWidgetId, state)

        val views = RemoteViews(context.packageName, R.layout.widget_ssh)

        // 标签名称
        val label = command?.name ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_label, label)

        // 背景与图标状态切换
        when (state) {
            WidgetState.IDLE -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_power)
            }
            WidgetState.RUNNING -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_running)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_sync)
            }
            WidgetState.SUCCESS -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_success)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_check_circle)
            }
            WidgetState.ERROR -> {
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_error)
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_error_outline)
            }
        }

        // 绑定点击触发广播
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val triggerIntent = Intent(context, SSHWidgetProvider::class.java).apply {
            action = SSHWidgetProvider.ACTION_TRIGGER_COMMAND
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            triggerIntent,
            flags
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * 通过系统级 AlarmManager 注册定时重置唤醒
     * 无论屏幕是否熄灭、应用是否被冻结或进程被清理，操作系统级定时器均必定到达并唤醒接收器重置小部件回 IDLE！
     */
    fun scheduleStateReset(context: Context, appWidgetId: Int, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SSHWidgetProvider::class.java).apply {
            action = SSHWidgetProvider.ACTION_RESET_WIDGET_STATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + RESET_REQUEST_CODE_OFFSET,
            intent,
            flags
        )

        val triggerAtMillis = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 取消针对此小部件的延迟重置定时器（用于用户中途再次点击立即重新触发执行）
     */
    fun cancelStateReset(context: Context, appWidgetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SSHWidgetProvider::class.java).apply {
            action = SSHWidgetProvider.ACTION_RESET_WIDGET_STATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + RESET_REQUEST_CODE_OFFSET,
            intent,
            flags
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * 巡检并重置所有已超期停留的小部件（用于开机、应用启动、界面切换等场景兜底）
     */
    fun checkAndResetExpiredWidgets(context: Context) {
        val prefs = getPrefs(context)
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val allKeys = prefs.all.keys
        val currentTime = System.currentTimeMillis()

        val widgetIds = allKeys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { it.removePrefix(KEY_PREFIX).toIntOrNull() }

        for (appWidgetId in widgetIds) {
            val state = getWidgetState(context, appWidgetId)
            if (state != WidgetState.IDLE) {
                val timestamp = getWidgetStateTimestamp(context, appWidgetId)
                val elapsed = currentTime - timestamp
                val expiryThreshold = when (state) {
                    WidgetState.SUCCESS -> 1200L
                    WidgetState.ERROR -> 2500L
                    WidgetState.RUNNING -> 25000L
                    WidgetState.IDLE -> Long.MAX_VALUE
                }
                if (elapsed >= expiryThreshold) {
                    cancelStateReset(context, appWidgetId)
                    val commandId = getCommandIdForWidget(context, appWidgetId)
                    scope.launch {
                        val command = if (commandId != null) App.database.commandDao().getById(commandId) else null
                        updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    }
                }
            }
        }
    }

    fun pinCommandToDesktop(context: Context, command: CommandEntity) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(context, SSHWidgetProvider::class.java)

            // 创建桌面图标暂存绑定
            val pinnedIntent = Intent(context, SSHWidgetProvider::class.java).apply {
                action = SSHWidgetProvider.ACTION_TRIGGER_COMMAND
                putExtra("PENDING_COMMAND_ID", command.id)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val successCallback = PendingIntent.getBroadcast(
                context,
                command.id.hashCode(),
                pinnedIntent,
                flags
            )

            appWidgetManager.requestPinAppWidget(provider, null, successCallback)
        }
    }
}
