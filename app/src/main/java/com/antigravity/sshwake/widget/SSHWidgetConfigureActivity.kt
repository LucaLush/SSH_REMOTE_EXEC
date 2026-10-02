package com.antigravity.sshwake.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.sshwake.App
import com.antigravity.sshwake.databinding.ActivityWidgetConfigureBinding
import com.antigravity.sshwake.ui.commands.CommandAdapter
import kotlinx.coroutines.launch

class SSHWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigureBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val adapter = CommandAdapter(
            onRunClick = { /* 不在配置页直接执行 */ },
            onPinClick = { /* 不需要在这里加图钉 */ },
            onEditClick = { /* 配置页不可编辑 */ },
            onDeleteClick = { /* 配置页不可删除 */ },
            onItemClick = { commandWithServer ->
                selectCommand(commandWithServer.command.id)
            }
        )

        binding.recyclerCommands.layoutManager = LinearLayoutManager(this)
        binding.recyclerCommands.adapter = adapter

        App.database.commandDao().getAllWithServerLiveData().observe(this) { list ->
            if (list.isNullOrEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerCommands.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerCommands.visibility = View.VISIBLE
                adapter.submitList(list)
            }
        }
    }

    private fun selectCommand(commandId: String) {
        lifecycleScope.launch {
            WidgetManager.saveBinding(this@SSHWidgetConfigureActivity, appWidgetId, commandId)

            val appWidgetManager = AppWidgetManager.getInstance(this@SSHWidgetConfigureActivity)
            val command = App.database.commandDao().getById(commandId)
            WidgetManager.updateWidgetView(
                this@SSHWidgetConfigureActivity,
                appWidgetManager,
                appWidgetId,
                command,
                WidgetState.IDLE
            )

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}
