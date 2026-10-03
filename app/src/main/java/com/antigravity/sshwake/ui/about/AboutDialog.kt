package com.antigravity.sshwake.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.antigravity.sshwake.BuildConfig
import com.antigravity.sshwake.R
import com.antigravity.sshwake.databinding.DialogAboutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutDialog(private val context: Context) {

    fun show() {
        val binding = DialogAboutBinding.inflate(LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        binding.chipVersion.text = "v$versionName ($versionCode)"

        val repoUrl = "https://github.com/LucaLush/SSH_REMOTE_EXEC"
        binding.btnGithub.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub URL", repoUrl))
            Toast.makeText(context, context.getString(R.string.about_copied_url), Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseAbout.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
