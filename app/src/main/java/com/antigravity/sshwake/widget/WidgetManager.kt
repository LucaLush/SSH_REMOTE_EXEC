package com.antigravity.sshwake.widget

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

enum class WidgetState {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR
}

object WidgetManager {
    private const val PREFS_NAME = "widget_bindings"
    private const val KEY_PREFIX = "widget_command_"

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
        getPrefs(context).edit().remove(KEY_PREFIX + appWidgetId).apply()
    }

    fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        command: CommandEntity?,
        state: WidgetState = WidgetState.IDLE
    ) {
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

    fun pinCommandToDesktop(context: Context, command: CommandEntity) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(context, SSHWidgetProvider::class.java)

            // 创建桌面图标暂存绑定
            val pinnedIntent = Intent(context, SSHWidgetProvider::class.java).apply {
                action = SSHWidgetProvider.ACTION_TRIGGER_COMMAND
                putExtra("PENDING_COMMAND_ID", command.id)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
