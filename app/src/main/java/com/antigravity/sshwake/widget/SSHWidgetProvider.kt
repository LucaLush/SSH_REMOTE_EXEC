package com.antigravity.sshwake.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.antigravity.sshwake.App
import com.antigravity.sshwake.ssh.SSHExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SSHWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_TRIGGER_COMMAND = "com.antigravity.sshwake.ACTION_TRIGGER_COMMAND"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scope.launch {
            val db = App.database
            for (appWidgetId in appWidgetIds) {
                val commandId = WidgetManager.getCommandIdForWidget(context, appWidgetId)
                val command = if (commandId != null) db.commandDao().getById(commandId) else null
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetManager.removeBinding(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TRIGGER_COMMAND) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

            // 如果是通过 requestPinAppWidget 触发绑定的初始事件
            val pendingCommandId = intent.getStringExtra("PENDING_COMMAND_ID")
            if (pendingCommandId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetManager.saveBinding(context, appWidgetId, pendingCommandId)
            }

            val commandId = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetManager.getCommandIdForWidget(context, appWidgetId)
            } else {
                pendingCommandId
            }

            if (commandId.isNullOrBlank()) {
                Toast.makeText(context, "未找到关联命令，请重新配置小组件", Toast.LENGTH_SHORT).show()
                return
            }

            scope.launch {
                val db = App.database
                val command = db.commandDao().getById(commandId)
                if (command == null) {
                    Toast.makeText(context, "关联命令已被删除", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val server = db.serverDao().getById(command.serverId)
                if (server == null) {
                    Toast.makeText(context, "关联服务器不存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val key = if (server.keyId.isNotBlank()) db.keyDao().getById(server.keyId) else null

                // 1. 设置小组件为运行态
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.RUNNING)
                }
                Toast.makeText(context, "正在执行: ${command.name}...", Toast.LENGTH_SHORT).show()

                // 2. 发起 SSH 执行
                val result = SSHExecutor.executeCommand(
                    server = server,
                    key = key,
                    command = command.command,
                    timeoutSeconds = command.timeoutSeconds
                )

                // 3. 反馈结果
                if (result.isSuccess) {
                    Toast.makeText(context, "✔ ${command.name} 执行完成", Toast.LENGTH_SHORT).show()
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.SUCCESS)
                    }
                } else {
                    Toast.makeText(context, "✖ ${command.name} 执行失败: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.ERROR)
                    }
                }

                // 4. 成功后短暂展示反馈（800毫秒瞬发复原），失败展示 2 秒让用户看清
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val resetDelay = if (result.isSuccess) 800L else 2000L
                    delay(resetDelay)
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                }
            }
        }
    }
}
