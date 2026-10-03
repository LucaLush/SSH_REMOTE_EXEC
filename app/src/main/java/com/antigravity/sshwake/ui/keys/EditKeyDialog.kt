package com.antigravity.sshwake.ui.keys

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.databinding.DialogEditKeyBinding
import com.antigravity.sshwake.security.CryptoHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditKeyDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val existingKey: KeyEntity? = null,
    private val defaultType: KeyType = KeyType.PASSWORD,
    private val defaultPackage: String? = null,
    private val onSaved: (KeyEntity) -> Unit = {}
) {
    fun show() {
        val binding = DialogEditKeyBinding.inflate(LayoutInflater.from(context))

        val initialPackage = existingKey?.packageGroup ?: (defaultPackage ?: "Default")
        binding.actvPackage.setText(initialPackage, false)

        fun updateUIForKeyType(isKey: Boolean) {
            if (isKey) {
                binding.layoutSecret.hint = context.getString(R.string.hint_private_key_secret)
                binding.layoutPassphrase.visibility = View.VISIBLE
                binding.etSecretContent.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                binding.etSecretContent.minLines = 4
                binding.etSecretContent.maxLines = 10
            } else {
                binding.layoutSecret.hint = context.getString(R.string.hint_password_secret)
                binding.layoutPassphrase.visibility = View.GONE
                binding.etSecretContent.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.etSecretContent.minLines = 1
                binding.etSecretContent.maxLines = 1
            }
        }

        if (existingKey != null) {
            binding.tvDialogTitle.text = context.getString(R.string.edit_key)
            binding.etKeyName.setText(existingKey.name)
            val isKey = (existingKey.type == KeyType.PRIVATE_KEY)
            if (isKey) {
                binding.rbTypePrivateKey.isChecked = true
            } else {
                binding.rbTypePassword.isChecked = true
            }
            updateUIForKeyType(isKey)
            binding.etSecretContent.setText(CryptoHelper.decrypt(existingKey.encryptedSecret))
            if (isKey && existingKey.encryptedPassphrase.isNotBlank()) {
                binding.etPassphrase.setText(CryptoHelper.decrypt(existingKey.encryptedPassphrase))
            }
        } else {
            val isKey = (defaultType == KeyType.PRIVATE_KEY)
            if (isKey) {
                binding.rbTypePrivateKey.isChecked = true
            } else {
                binding.rbTypePassword.isChecked = true
            }
            updateUIForKeyType(isKey)
        }

        binding.rgKeyType.setOnCheckedChangeListener { _, checkedId ->
            updateUIForKeyType(checkedId == R.id.rb_type_private_key)
        }

        scope.launch {
            val allKeys = App.database.keyDao().getAll()
            val distinctPackages = (allKeys.map { it.packageGroup } + listOf("Default")).distinct().filter { it.isNotBlank() }
            withContext(Dispatchers.Main) {
                val packageAdapter = android.widget.ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    distinctPackages
                )
                binding.actvPackage.setAdapter(packageAdapter)
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etKeyName.text?.toString()?.trim() ?: ""
                val secret = binding.etSecretContent.text?.toString()?.trim() ?: ""
                val isPrivateKey = binding.rbTypePrivateKey.isChecked
                val passphrase = binding.etPassphrase.text?.toString()?.trim() ?: ""
                val packageGroup = binding.actvPackage.text?.toString()?.trim()?.ifEmpty { "Default" } ?: "Default"

                if (name.isEmpty()) {
                    binding.etKeyName.error = context.getString(R.string.err_enter_key_name)
                    return@setOnClickListener
                }
                if (secret.isEmpty()) {
                    binding.etSecretContent.error = context.getString(R.string.err_enter_secret)
                    return@setOnClickListener
                }

                val encryptedSecret = CryptoHelper.encrypt(secret)
                val encryptedPassphrase = if (passphrase.isNotEmpty()) CryptoHelper.encrypt(passphrase) else ""
                val keyType = if (isPrivateKey) KeyType.PRIVATE_KEY else KeyType.PASSWORD

                scope.launch {
                    val keyDao = App.database.keyDao()
                    val savedEntity = if (existingKey == null) {
                        val newKey = KeyEntity(
                            name = name,
                            type = keyType,
                            encryptedSecret = encryptedSecret,
                            encryptedPassphrase = encryptedPassphrase,
                            packageGroup = packageGroup
                        )
                        keyDao.insert(newKey)
                        newKey
                    } else {
                        val updated = existingKey.copy(
                            name = name,
                            type = keyType,
                            encryptedSecret = encryptedSecret,
                            encryptedPassphrase = encryptedPassphrase,
                            packageGroup = packageGroup
                        )
                        keyDao.update(updated)
                        updated
                    }

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        onSaved(savedEntity)
                    }
                }
            }
        }

        dialog.show()
    }
}
