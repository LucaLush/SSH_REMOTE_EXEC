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

    companion object {
        const val ACTION_TRIGGER_COMMAND = "com.antigravity.sshwake.ACTION_TRIGGER_COMMAND"
        const val ACTION_RESET_WIDGET_STATE = "com.antigravity.sshwake.ACTION_RESET_WIDGET_STATE"
        const val ACTION_PINNED_WIDGET = "com.antigravity.sshwake.ACTION_PINNED_WIDGET"

        // 内存级互斥锁：锁定整个【RUNNING -> 变色反馈 -> 彻底变回 IDLE】的全生命周期
        private val activeLockedWidgets = ConcurrentHashMap.newKeySet<Int>()
        // 记录最近一次有效触发的时间戳，实现硬件级防抖（间隔 200ms）
        private val lastTriggerTimestamps = ConcurrentHashMap<Int, Long>()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 更新小组件时，同时巡检重置任何异常驻留的变色状态
        WidgetManager.checkAndResetExpiredWidgets(context)

        App.applicationScope.launch {
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
            activeLockedWidgets.remove(appWidgetId)
            lastTriggerTimestamps.remove(appWidgetId)
            WidgetManager.removeBinding(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            // 系统级闹钟或定时恢复事件：彻底复原回待命态
            ACTION_RESET_WIDGET_STATE -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    activeLockedWidgets.remove(appWidgetId)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val commandId = WidgetManager.getCommandIdForWidget(context, appWidgetId)
                    App.applicationScope.launch {
                        val command = if (commandId != null) App.database.commandDao().getById(commandId) else null
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    }
                }
            }

            // 应用内“添加到桌面”成功回调：仅保存关联关系并初始化为待命态，绝对不触发执行！
            ACTION_PINNED_WIDGET -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val pendingCommandId = intent.getStringExtra("PENDING_COMMAND_ID")
                if (pendingCommandId != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetManager.saveBinding(context, appWidgetId, pendingCommandId)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    App.applicationScope.launch {
                        val command = App.database.commandDao().getById(pendingCommandId)
                        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    }
                }
            }

            // 用户在桌面点击小组件触发执行
            ACTION_TRIGGER_COMMAND -> {
                handleTriggerCommand(context, intent)
            }
        }
    }

    private fun handleTriggerCommand(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        val commandId = WidgetManager.getCommandIdForWidget(context, appWidgetId)
        if (commandId.isNullOrBlank()) {
            Toast.makeText(context, R.string.widget_missing_command, Toast.LENGTH_SHORT).show()
            return
        }

        // ================= 严格单次触发拦截（绝对禁止排队、禁止并发、静默忽略） =================
        val now = System.currentTimeMillis()
        val lastTrigger = lastTriggerTimestamps[appWidgetId] ?: 0L
        val currentState = WidgetManager.getWidgetState(context, appWidgetId)
        val stateTime = WidgetManager.getWidgetStateTimestamp(context, appWidgetId)
        val elapsed = now - stateTime

        // 异常脱困保护：如果停留在非 IDLE 状态已超过 25 秒（极少见卡死），允许脱困重试
        val isStale = (currentState != WidgetState.IDLE && elapsed > 25000L)
        if (isStale) {
            activeLockedWidgets.remove(appWidgetId)
        }

        // 核心铁律：
        // 只要小组件当前不是完全就绪的 IDLE 待命态（即正在执行中，或正在展示绿色/红色结果），
        // 或者还在锁定集合中 -> 一律在主线程极速静默拦截并丢弃！
        // 一旦完全恢复为默认 IDLE 待命态，用户再次按下即可零延迟瞬间响应！
        if (!isStale) {
            if (activeLockedWidgets.contains(appWidgetId) ||
                currentState != WidgetState.IDLE ||
                (now - lastTrigger < 200L)
            ) {
                return
            }
        }

        // 立即同步加锁，抢占后续所有高频并发点击
        activeLockedWidgets.add(appWidgetId)
        lastTriggerTimestamps[appWidgetId] = now
        WidgetManager.saveWidgetState(context, appWidgetId, WidgetState.RUNNING)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        // 立即更新桌面小组件为运行态，并在 RemoteViews 中立即清除 OnClickPendingIntent (设为 null)
        // 从而在物理层禁止用户在执行及反馈期间继续点击！
        WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.RUNNING)

        val pendingResult = goAsync()
        var isPendingResultFinished = false
        fun finishBroadcastSafely() {
            if (!isPendingResultFinished) {
                isPendingResultFinished = true
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }

        App.applicationScope.launch {
            try {
                val db = App.database
                val command = db.commandDao().getById(commandId)
                if (command == null) {
                    Toast.makeText(context, R.string.widget_command_deleted, Toast.LENGTH_SHORT).show()
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.IDLE)
                    activeLockedWidgets.remove(appWidgetId)
                    finishBroadcastSafely()
                    return@launch
                }

                val server = db.serverDao().getById(command.serverId)
                if (server == null) {
                    Toast.makeText(context, R.string.widget_server_deleted, Toast.LENGTH_SHORT).show()
                    WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                    activeLockedWidgets.remove(appWidgetId)
                    finishBroadcastSafely()
                    return@launch
                }

                val key = if (server.keyId.isNotBlank()) db.keyDao().getById(server.keyId) else null

                // 1. 设置小组件为 RUNNING 运行态（蓝色背景 + 旋转图标）
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.RUNNING)
                Toast.makeText(context, context.getString(R.string.widget_executing, command.name), Toast.LENGTH_SHORT).show()

                // 2. 发起 SSH 执行 (在 Dispatchers.IO 线程池中执行)
                val result = SSHExecutor.executeCommand(
                    server = server,
                    key = key,
                    command = command.command,
                    timeoutSeconds = command.timeoutSeconds
                )

                // 3. 状态转换：切换为绿色对勾或红色感叹号
                val nextState = if (result.isSuccess) WidgetState.SUCCESS else WidgetState.ERROR
                val resetDelay = if (result.isSuccess) 800L else 2000L

                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.widget_success, command.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.widget_error, command.name, result.errorMessage ?: "Failed"), Toast.LENGTH_LONG).show()
                }

                // 切换为绿色或红色状态
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, nextState)

                // 注册系统底层 AlarmManager 闹钟保障（即使熄屏休眠也必定唤醒复位）
                WidgetManager.scheduleStateReset(context, appWidgetId, resetDelay)

                // 【核心解法】：网络请求已完全结束，UI已变色呈现！
                // 立即结束当前 Broadcast，让 AMS 积压的连击事件倾泻出来！
                // 此时组件处于 SUCCESS / ERROR 态且被 activeLockedWidgets 锁死，所有积压广播会被毫秒级全部丢弃！
                finishBroadcastSafely()

                // 进程存活时的快速路径重置：在独立的后台协程中等待视觉展示后复原
                delay(resetDelay)
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, command, WidgetState.IDLE)
                // 只有在彻底变回 IDLE 待命态后，才释放点击锁定，迎接下一次用户操作！
                activeLockedWidgets.remove(appWidgetId)
            } catch (e: Exception) {
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.ERROR)
                WidgetManager.scheduleStateReset(context, appWidgetId, 2000L)
                finishBroadcastSafely()
                delay(2000L)
                WidgetManager.updateWidgetView(context, appWidgetManager, appWidgetId, null, WidgetState.IDLE)
                activeLockedWidgets.remove(appWidgetId)
            } finally {
                finishBroadcastSafely()
            }
        }
    }
}
