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
import com.antigravity.sshwake.ui.keys.EditKeyDialog
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

        var selectedKeyId: String = existingServer?.keyId ?: ""
        var currentFilteredKeys = listOf<KeyEntity>()

        fun updateKeyDropdown() {
            val isKeyMode = binding.rbAuthKey.isChecked
            currentFilteredKeys = allKeys.filter {
                if (isKeyMode) it.type == KeyType.PRIVATE_KEY else it.type == KeyType.PASSWORD
            }
            val keyNames = currentFilteredKeys.map { "${it.name} (${if (it.type == KeyType.PRIVATE_KEY) "私钥" else "密码"})" }
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, keyNames)
            binding.actvKeys.setAdapter(adapter)

            if (currentFilteredKeys.isNotEmpty()) {
                val index = currentFilteredKeys.indexOfFirst { it.id == selectedKeyId }.let { if (it >= 0) it else 0 }
                binding.actvKeys.setText(keyNames[index], false)
                selectedKeyId = currentFilteredKeys[index].id
                binding.layoutKeys.helperText = null
            } else {
                binding.actvKeys.setText("", false)
                selectedKeyId = ""
                binding.layoutKeys.helperText = "暂无${if (isKeyMode) "私钥" else "密码"}，点击右侧 ＋ 号添加"
            }

            binding.actvKeys.setOnItemClickListener { _, _, position, _ ->
                selectedKeyId = currentFilteredKeys.getOrNull(position)?.id ?: ""
            }
        }

        binding.rgAuthType.setOnCheckedChangeListener { _, checkedId ->
            updateKeyDropdown()
            if (checkedId == R.id.rb_auth_key && currentFilteredKeys.isEmpty()) {
                Toast.makeText(context, "凭据库中暂无私钥，请点击右侧 ＋ 号添加", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnQuickAddKey.setOnClickListener {
            val isKeyMode = binding.rbAuthKey.isChecked
            val targetType = if (isKeyMode) KeyType.PRIVATE_KEY else KeyType.PASSWORD
            EditKeyDialog(
                context = context,
                scope = scope,
                defaultType = targetType,
                onSaved = { newKey: KeyEntity ->
                    scope.launch {
                        allKeys = App.database.keyDao().getAll()
                        withContext(Dispatchers.Main) {
                            selectedKeyId = newKey.id
                            updateKeyDropdown()
                            Toast.makeText(context, "已创建并选中新凭证：${newKey.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        scope.launch {
            allKeys = App.database.keyDao().getAll()
            withContext(Dispatchers.Main) {
                updateKeyDropdown()
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
                if (currentFilteredKeys.isEmpty() || selectedKeyId.isEmpty()) {
                    Toast.makeText(context, "请先在【凭证】页添加对应的${if (isKeyMode) "私钥" else "密码"}", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val keyId = selectedKeyId

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
