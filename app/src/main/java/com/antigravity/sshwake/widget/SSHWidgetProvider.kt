package com.antigravity.sshwake.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.ssh.SSHExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SSHWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_TRIGGER_COMMAND = "com.antigravity.sshwake.ACTION_TRIGGER_COMMAND"
        const val ACTION_RESET_WIDGET_STATE = "com.antigravity.sshwake.ACTION_RESET_WIDGET_STATE"

        // 内存级并发锁，杜绝在执行过程中因用户连续按键导致并发触发
        private val activeRunningWidgets = ConcurrentHashMap.newKeySet<Int>()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 更新小组件时，同时巡检重置任何异常驻留的变色状态
        WidgetManager.checkAndResetExpiredWidgets(context)

        scope.launch {
            val db = App.database
            for (appWidgetId in appWidgetIds) {
                val commandId = WidgetManager.getCommandIdForWidget(context, appWidgetId)
                val command = if (commandId != null) db.commandDao().getById(commandId) else null
                val currentState = WidgetManager.getWidgetState(context, appWidgetId)
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, currentState)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            activeRunningWidgets.remove(appWidgetId)
            WidgetManager.removeBinding(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_RESET_WIDGET_STATE -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val commandId = WidgetManager.getCommandIdForWidget(context, appWidgetId)
                    scope.launch {
                        val command = if (commandId != null) App.database.commandDao().getById(commandId) else null
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    }
                }
            }

            ACTION_TRIGGER_COMMAND -> {
                handleTriggerCommand(context, intent)
            }
        }
    }

    private fun handleTriggerCommand(context: Context, intent: Intent) {
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
            Toast.makeText(context, R.string.widget_missing_command, Toast.LENGTH_SHORT).show()
            return
        }

        // ================= 状态机防重与并发互斥校验 =================
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val currentState = WidgetManager.getWidgetState(context, appWidgetId)
            val stateTime = WidgetManager.getWidgetStateTimestamp(context, appWidgetId)
            val elapsed = System.currentTimeMillis() - stateTime
            val isRunningStale = (currentState == WidgetState.RUNNING && elapsed > 25000L) // 超过25秒视为卡死的异常态，允许脱困重试

            // 若正处于 RUNNING 状态且未超期，直接拦截并提示用户，坚决不进行重复并发请求
            if (activeRunningWidgets.contains(appWidgetId) || (currentState == WidgetState.RUNNING && !isRunningStale)) {
                Toast.makeText(context, R.string.widget_already_running, Toast.LENGTH_SHORT).show()
                return
            }

            // 若之前处于 SUCCESS 或 ERROR 的延时展示窗口，用户再次按下说明希望立刻触发新一轮，取消旧重置定时器
            WidgetManager.cancelStateReset(context, appWidgetId)
            activeRunningWidgets.add(appWidgetId)
        }

        // 申请异步广播执行权，告知操作系统正在进行耗时异步操作，避免广播进程被系统过早冻结
        val pendingResult = goAsync()

        scope.launch {
            try {
                val db = App.database
                val command = db.commandDao().getById(commandId)
                if (command == null) {
                    Toast.makeText(context, R.string.widget_command_deleted, Toast.LENGTH_SHORT).show()
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.IDLE)
                    }
                    return@launch
                }

                val server = db.serverDao().getById(command.serverId)
                if (server == null) {
                    Toast.makeText(context, R.string.widget_server_deleted, Toast.LENGTH_SHORT).show()
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    }
                    return@launch
                }

                val key = if (server.keyId.isNotBlank()) db.keyDao().getById(server.keyId) else null

                // 1. 设置小组件为 RUNNING 运行态（蓝色背景 + 旋转图标）
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.RUNNING)
                }
                Toast.makeText(context, context.getString(R.string.widget_executing, command.name), Toast.LENGTH_SHORT).show()

                // 2. 发起 SSH 执行 (在 Dispatchers.IO 中执行)
                val result = SSHExecutor.executeCommand(
                    server = server,
                    key = key,
                    command = command.command,
                    timeoutSeconds = command.timeoutSeconds
                )

                // 3. 状态转换：判定成功或失败
                val nextState = if (result.isSuccess) WidgetState.SUCCESS else WidgetState.ERROR
                val resetDelay = if (result.isSuccess) 800L else 2000L

                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.widget_success, command.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.widget_error, command.name, result.errorMessage ?: "Failed"), Toast.LENGTH_LONG).show()
                }

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // 切换为绿色或红色状态
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, nextState)

                    // 核心保障：注册系统级 AlarmManager 定时重置广播
                    // 即使此时息屏或 App 进程被休眠冻结，Android 系统 Alarm 到期必定发送广播唤醒重置！
                    WidgetManager.scheduleStateReset(context, appWidgetId, resetDelay)

                    // 进程存活时的快速路径重置
                    scope.launch {
                        delay(resetDelay)
                        // 确保未被用户的新点击切换为其他状态
                        if (WidgetManager.getWidgetState(context, appWidgetId) == nextState) {
                            WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                        }
                    }
                }
            } catch (e: Exception) {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.ERROR)
                    WidgetManager.scheduleStateReset(context, appWidgetId, 2000L)
                }
            } finally {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    activeRunningWidgets.remove(appWidgetId)
                }
                // 通知操作系统该次广播生命周期圆满结束
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }
    }
}
