package com.antigravity.sshwake.ui.commands

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.CommandEntity
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.databinding.DialogEditCommandBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditCommandDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val existingCommand: CommandEntity? = null,
    private val onSaved: () -> Unit
) {
    fun show() {
        val binding = DialogEditCommandBinding.inflate(LayoutInflater.from(context))
        var servers = listOf<ServerEntity>()

        if (existingCommand != null) {
            binding.tvDialogTitle.text = "编辑命令"
            binding.etCommandName.setText(existingCommand.name)
            binding.etCommandScript.setText(existingCommand.command)
            binding.etTimeout.setText(existingCommand.timeoutSeconds.toString())
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        var selectedServerId: String = existingCommand?.serverId ?: ""

        // 异步加载服务器列表填充下拉菜单
        scope.launch {
            servers = App.database.serverDao().getAll()
            withContext(Dispatchers.Main) {
                val serverNames = servers.map { "${it.name} (${it.host})" }
                val dropdownAdapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    serverNames
                )
                binding.actvServer.setAdapter(dropdownAdapter)

                if (servers.isNotEmpty()) {
                    val initialIndex = servers.indexOfFirst { it.id == selectedServerId }.let { if (it >= 0) it else 0 }
                    binding.actvServer.setText(serverNames[initialIndex], false)
                    selectedServerId = servers[initialIndex].id
                }

                binding.actvServer.setOnItemClickListener { _, _, position, _ ->
                    selectedServerId = servers.getOrNull(position)?.id ?: ""
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etCommandName.text?.toString()?.trim() ?: ""
                val script = binding.etCommandScript.text?.toString()?.trim() ?: ""
                val timeoutStr = binding.etTimeout.text?.toString()?.trim() ?: "8"
                val timeout = timeoutStr.toIntOrNull() ?: 8

                if (name.isEmpty()) {
                    binding.etCommandName.error = "请输入命令名称"
                    return@setOnClickListener
                }
                if (script.isEmpty()) {
                    binding.etCommandScript.error = "请输入执行脚本"
                    return@setOnClickListener
                }
                if (servers.isEmpty() || selectedServerId.isEmpty()) {
                    Toast.makeText(context, "请先添加至少一台服务器！", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val serverId = selectedServerId

                scope.launch {
                    val commandDao = App.database.commandDao()
                    if (existingCommand == null) {
                        val newCmd = CommandEntity(
                            name = name,
                            serverId = serverId,
                            command = script,
                            timeoutSeconds = timeout
                        )
                        commandDao.insert(newCmd)
                    } else {
                        val updated = existingCommand.copy(
                            name = name,
                            serverId = serverId,
                            command = script,
                            timeoutSeconds = timeout
                        )
                        commandDao.update(updated)
                    }

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        onSaved()
                    }
                }
            }
        }

        dialog.show()
    }
}
