package com.antigravity.sshwake.ui.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.CommandWithServer
import com.antigravity.sshwake.databinding.DialogCommandResultBinding
import com.antigravity.sshwake.databinding.FragmentCommandsBinding
import com.antigravity.sshwake.ssh.SSHExecutor
import com.antigravity.sshwake.ssh.SSHResult
import com.antigravity.sshwake.widget.WidgetManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CommandsFragment : Fragment() {

    private var _binding: FragmentCommandsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CommandAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommandsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = CommandAdapter(
            onRunClick = { item -> runCommand(item) },
            onPinClick = { item ->
                WidgetManager.pinCommandToDesktop(requireContext(), item.command)
                Toast.makeText(requireContext(), R.string.pinned_to_desktop, Toast.LENGTH_SHORT).show()
            },
            onEditClick = { item ->
                EditCommandDialog(requireContext(), viewLifecycleOwner.lifecycleScope, item.command) {
                    Toast.makeText(requireContext(), "命令已更新", Toast.LENGTH_SHORT).show()
                }.show()
            },
            onDeleteClick = { item -> confirmDelete(item) }
        )

        binding.recyclerCommands.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCommands.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddCommand.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val servers = App.database.serverDao().getAll()
                if (servers.isEmpty()) {
                    Toast.makeText(requireContext(), "请先添加至少一台服务器！", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                EditCommandDialog(requireContext(), viewLifecycleOwner.lifecycleScope) {
                    Toast.makeText(requireContext(), "命令已创建", Toast.LENGTH_SHORT).show()
                }.show()
            }
        }
    }

    private fun observeData() {
        App.database.commandDao().getAllWithServerLiveData().observe(viewLifecycleOwner) { list ->
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

    private fun runCommand(item: CommandWithServer) {
        val server = item.server
        if (server == null) {
            Toast.makeText(requireContext(), "此命令未关联可用服务器", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在执行...")
            .setMessage("正在连接 ${server.host}:${server.port} 并执行命令...")
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val key = if (server.keyId.isNotBlank()) App.database.keyDao().getById(server.keyId) else null
            val result = SSHExecutor.executeCommand(
                server = server,
                key = key,
                command = item.command.command,
                timeoutSeconds = item.command.timeoutSeconds
            )
            progressDialog.dismiss()
            showResultDialog(item.command.name, result)
        }
    }

    private fun showResultDialog(commandName: String, result: SSHResult) {
        val resultBinding = DialogCommandResultBinding.inflate(layoutInflater)
        resultBinding.tvResultTitle.text = commandName
        if (result.isSuccess) {
            resultBinding.ivResultStatus.setImageResource(R.drawable.ic_check_circle)
            resultBinding.ivResultStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_success))
            resultBinding.tvResultSummary.text = "执行成功 (Exit: ${result.exitCode ?: 0})"
        } else {
            resultBinding.ivResultStatus.setImageResource(R.drawable.ic_error_outline)
            resultBinding.ivResultStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_error))
            resultBinding.tvResultSummary.text = "执行失败: ${result.errorMessage}"
        }

        resultBinding.tvResultOutput.text = result.output

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(resultBinding.root)
            .create()

        resultBinding.btnCopyOutput.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SSH Output", result.output)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "回显内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        resultBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmDelete(item: CommandWithServer) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(R.string.confirm_delete_command)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    App.database.commandDao().delete(item.command)
                    Toast.makeText(requireContext(), "已删除命令", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
