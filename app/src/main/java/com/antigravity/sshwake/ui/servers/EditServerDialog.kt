package com.antigravity.sshwake.ui.servers

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.databinding.DialogEditServerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditServerDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val existingServer: ServerEntity? = null,
    private val onSaved: () -> Unit
) {
    fun show() {
        val binding = DialogEditServerBinding.inflate(LayoutInflater.from(context))
        var allKeys = listOf<KeyEntity>()

        if (existingServer != null) {
            binding.tvDialogTitle.text = "编辑服务器"
            binding.etServerName.setText(existingServer.name)
            binding.etHost.setText(existingServer.host)
            binding.etPort.setText(existingServer.port.toString())
            binding.etUsername.setText(existingServer.username)

            if (existingServer.authType == AuthType.KEY) {
                binding.rbAuthKey.isChecked = true
            } else {
                binding.rbAuthPassword.isChecked = true
            }
        }

        fun updateKeySpinner() {
            val isKeyMode = binding.rbAuthKey.isChecked
            val filteredKeys = allKeys.filter {
                if (isKeyMode) it.type == KeyType.PRIVATE_KEY else it.type == KeyType.PASSWORD
            }
            val keyNames = filteredKeys.map { "${it.name} (${if (it.type == KeyType.PRIVATE_KEY) "私钥" else "密码"})" }
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, keyNames)
            binding.spinnerKeys.adapter = adapter

            if (existingServer != null) {
                val index = filteredKeys.indexOfFirst { it.id == existingServer.keyId }
                if (index >= 0) binding.spinnerKeys.setSelection(index)
            }
        }

        binding.rgAuthType.setOnCheckedChangeListener { _, _ ->
            updateKeySpinner()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        scope.launch {
            allKeys = App.database.keyDao().getAll()
            withContext(Dispatchers.Main) {
                updateKeySpinner()
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etServerName.text?.toString()?.trim() ?: ""
                val host = binding.etHost.text?.toString()?.trim() ?: ""
                val portStr = binding.etPort.text?.toString()?.trim() ?: "22"
                val port = portStr.toIntOrNull() ?: 22
                val username = binding.etUsername.text?.toString()?.trim() ?: "root"
                val authType = if (binding.rbAuthKey.isChecked) AuthType.KEY else AuthType.PASSWORD

                if (name.isEmpty()) {
                    binding.etServerName.error = "请输入服务器名称"
                    return@setOnClickListener
                }
                if (host.isEmpty()) {
                    binding.etHost.error = "请输入主机 IP 或域名"
                    return@setOnClickListener
                }
                if (username.isEmpty()) {
                    binding.etUsername.error = "请输入用户名"
                    return@setOnClickListener
                }

                val isKeyMode = (authType == AuthType.KEY)
                val filteredKeys = allKeys.filter {
                    if (isKeyMode) it.type == KeyType.PRIVATE_KEY else it.type == KeyType.PASSWORD
                }
                if (filteredKeys.isEmpty()) {
                    Toast.makeText(context, "请先在【凭证】页添加对应的${if (isKeyMode) "私钥" else "密码"}", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedKeyIndex = binding.spinnerKeys.selectedItemPosition
                val keyId = filteredKeys.getOrNull(selectedKeyIndex)?.id ?: ""

                scope.launch {
                    val serverDao = App.database.serverDao()
                    if (existingServer == null) {
                        val newServer = ServerEntity(
                            name = name,
                            host = host,
                            port = port,
                            username = username,
                            authType = authType,
                            keyId = keyId
                        )
                        serverDao.insert(newServer)
                    } else {
                        val updated = existingServer.copy(
                            name = name,
                            host = host,
                            port = port,
                            username = username,
                            authType = authType,
                            keyId = keyId
                        )
                        serverDao.update(updated)
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
