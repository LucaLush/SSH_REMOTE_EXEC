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
    private val defaultPackage: String? = null,
    private val onSaved: () -> Unit
) {
    fun show() {
        val binding = DialogEditServerBinding.inflate(LayoutInflater.from(context))
        var allKeys = listOf<KeyEntity>()

        val initialPackage = existingServer?.packageGroup ?: (defaultPackage ?: "Default")
        binding.actvPackage.setText(initialPackage, false)

        if (existingServer != null) {
            binding.tvDialogTitle.text = context.getString(R.string.edit_server)
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
            val currentServerPackage = binding.actvPackage.text?.toString()?.trim() ?: "Default"
            val matchingKeys = allKeys.filter {
                if (isKeyMode) it.type == KeyType.PRIVATE_KEY else it.type == KeyType.PASSWORD
            }
            // 智能排序：优先将同分组的凭证排在最前面，其余按分组和名称排序
            currentFilteredKeys = matchingKeys.sortedWith(
                compareByDescending<KeyEntity> { it.packageGroup.equals(currentServerPackage, ignoreCase = true) }
                    .thenBy { it.packageGroup }
                    .thenBy { it.name }
            )

            val typeSuffix = if (isKeyMode) context.getString(R.string.auth_key) else context.getString(R.string.auth_password)
            val keyNames = currentFilteredKeys.map { "[${it.packageGroup}] ${it.name} ($typeSuffix)" }
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
                binding.layoutKeys.helperText = context.getString(R.string.helper_no_credential, typeSuffix)
            }

            binding.actvKeys.setOnItemClickListener { _, _, position, _ ->
                selectedKeyId = currentFilteredKeys.getOrNull(position)?.id ?: ""
            }
        }

        binding.actvPackage.setOnItemClickListener { _, _, _, _ ->
            updateKeyDropdown()
        }

        binding.rgAuthType.setOnCheckedChangeListener { _, checkedId ->
            updateKeyDropdown()
            if (checkedId == R.id.rb_auth_key && currentFilteredKeys.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.toast_no_keys_in_db), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnQuickAddKey.setOnClickListener {
            val isKeyMode = binding.rbAuthKey.isChecked
            val targetType = if (isKeyMode) KeyType.PRIVATE_KEY else KeyType.PASSWORD
            EditKeyDialog(
                context = context,
                scope = scope,
                defaultType = targetType,
                defaultPackage = binding.actvPackage.text?.toString()?.trim()?.ifEmpty { "Default" },
                onSaved = { newKey: KeyEntity ->
                    scope.launch {
                        allKeys = App.database.keyDao().getAll()
                        withContext(Dispatchers.Main) {
                            selectedKeyId = newKey.id
                            updateKeyDropdown()
                            Toast.makeText(context, context.getString(R.string.toast_created_and_selected, newKey.name), Toast.LENGTH_SHORT).show()
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
            val allServers = App.database.serverDao().getAll()
            val distinctPackages = (allServers.map { it.packageGroup } + listOf("Default")).distinct().filter { it.isNotBlank() }

            withContext(Dispatchers.Main) {
                updateKeyDropdown()
                val pkgAdapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    distinctPackages
                )
                binding.actvPackage.setAdapter(pkgAdapter)
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
                val pkgGroup = binding.actvPackage.text?.toString()?.trim()?.ifBlank { "Default" } ?: "Default"

                if (name.isEmpty()) {
                    binding.etServerName.error = context.getString(R.string.err_enter_server_name)
                    return@setOnClickListener
                }
                if (host.isEmpty()) {
                    binding.etHost.error = context.getString(R.string.err_enter_host)
                    return@setOnClickListener
                }
                if (username.isEmpty()) {
                    binding.etUsername.error = context.getString(R.string.err_enter_username)
                    return@setOnClickListener
                }

                val isKeyMode = (authType == AuthType.KEY)
                if (currentFilteredKeys.isEmpty() || selectedKeyId.isEmpty()) {
                    val typeName = if (isKeyMode) context.getString(R.string.auth_key) else context.getString(R.string.auth_password)
                    Toast.makeText(context, context.getString(R.string.toast_need_credential, typeName), Toast.LENGTH_SHORT).show()
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
                            keyId = keyId,
                            packageGroup = pkgGroup
                        )
                        serverDao.insert(newServer)
                    } else {
                        val updated = existingServer.copy(
                            name = name,
                            host = host,
                            port = port,
                            username = username,
                            authType = authType,
                            keyId = keyId,
                            packageGroup = pkgGroup
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
