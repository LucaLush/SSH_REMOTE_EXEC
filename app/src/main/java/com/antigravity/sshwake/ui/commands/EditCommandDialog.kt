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
    private val defaultPackage: String? = null,
    private val onSaved: () -> Unit
) {
    fun show() {
        val binding = DialogEditCommandBinding.inflate(LayoutInflater.from(context))
        var servers = listOf<ServerEntity>()

        val initialPackage = existingCommand?.packageGroup ?: (defaultPackage ?: "Default")
        binding.actvPackage.setText(initialPackage, false)

        if (existingCommand != null) {
            binding.tvDialogTitle.text = context.getString(R.string.edit_command)
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

        // 异步加载服务器列表及已存在的分组填充下拉菜单
        scope.launch {
            servers = App.database.serverDao().getAll()
            val allCommands = App.database.commandDao().getAllWithServer()
            val distinctPackages = (allCommands.map { it.command.packageGroup } + listOf("Default")).distinct().filter { it.isNotBlank() }

            withContext(Dispatchers.Main) {
                var currentSortedServers = servers

                fun updateServerDropdown() {
                    val currentCommandPackage = binding.actvPackage.text?.toString()?.trim() ?: "Default"
                    currentSortedServers = servers.sortedWith(
                        compareByDescending<ServerEntity> { it.packageGroup.equals(currentCommandPackage, ignoreCase = true) }
                            .thenBy { it.packageGroup }
                            .thenBy { it.name }
                    )

                    val serverNames = currentSortedServers.map { "[${it.packageGroup}] ${it.name} (${it.host}:${it.port})" }
                    val dropdownAdapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        serverNames
                    )
                    binding.actvServer.setAdapter(dropdownAdapter)

                    if (currentSortedServers.isNotEmpty()) {
                        val initialIndex = currentSortedServers.indexOfFirst { it.id == selectedServerId }.let { if (it >= 0) it else 0 }
                        binding.actvServer.setText(serverNames[initialIndex], false)
                        selectedServerId = currentSortedServers[initialIndex].id
                    }
                }

                updateServerDropdown()

                val pkgAdapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    distinctPackages
                )
                binding.actvPackage.setAdapter(pkgAdapter)
                binding.actvPackage.setOnItemClickListener { _, _, _, _ ->
                    updateServerDropdown()
                }

                binding.actvServer.setOnItemClickListener { _, _, position, _ ->
                    selectedServerId = currentSortedServers.getOrNull(position)?.id ?: ""
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etCommandName.text?.toString()?.trim() ?: ""
                val script = binding.etCommandScript.text?.toString()?.trim() ?: ""
                val timeoutStr = binding.etTimeout.text?.toString()?.trim() ?: "8"
                val timeout = timeoutStr.toIntOrNull() ?: 8
                val pkgGroup = binding.actvPackage.text?.toString()?.trim()?.ifBlank { "Default" } ?: "Default"

                if (name.isEmpty()) {
                    binding.etCommandName.error = context.getString(R.string.err_enter_command_name)
                    return@setOnClickListener
                }
                if (script.isEmpty()) {
                    binding.etCommandScript.error = context.getString(R.string.err_enter_command_script)
                    return@setOnClickListener
                }
                if (servers.isEmpty() || selectedServerId.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.toast_need_server_first), Toast.LENGTH_SHORT).show()
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
                            timeoutSeconds = timeout,
                            packageGroup = pkgGroup
                        )
                        commandDao.insert(newCmd)
                    } else {
                        val updated = existingCommand.copy(
                            name = name,
                            serverId = serverId,
                            command = script,
                            timeoutSeconds = timeout,
                            packageGroup = pkgGroup
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
