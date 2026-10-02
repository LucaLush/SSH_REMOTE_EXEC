package com.antigravity.sshwake.ui.servers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.sshwake.App
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.databinding.FragmentServersBinding
import com.antigravity.sshwake.ssh.SSHExecutor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ServersFragment : Fragment() {

    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ServerAdapter(
            onTestClick = { server -> testServer(server) },
            onEditClick = { server ->
                EditServerDialog(requireContext(), viewLifecycleOwner.lifecycleScope, server) {
                    Toast.makeText(requireContext(), "服务器信息已更新", Toast.LENGTH_SHORT).show()
                }.show()
            },
            onDeleteClick = { server -> confirmDelete(server) }
        )

        binding.recyclerServers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerServers.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddServer.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val keys = App.database.keyDao().getAll()
                if (keys.isEmpty()) {
                    Toast.makeText(requireContext(), "请先在【凭证】页添加至少一个私钥或密码！", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                EditServerDialog(requireContext(), viewLifecycleOwner.lifecycleScope) {
                    Toast.makeText(requireContext(), "服务器已添加", Toast.LENGTH_SHORT).show()
                }.show()
            }
        }
    }

    private fun observeData() {
        App.database.serverDao().getAllLiveData().observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerServers.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerServers.visibility = View.VISIBLE
                adapter.submitList(list)
            }
        }
    }

    private fun testServer(server: ServerEntity) {
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.testing_connection)
            .setMessage("正在尝试建立 SSH 握手并验证鉴权信息...")
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val key = if (server.keyId.isNotBlank()) App.database.keyDao().getById(server.keyId) else null
            val result = SSHExecutor.testConnection(server, key)
            progressDialog.dismiss()

            if (result.isSuccess) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.connection_success)
                    .setIcon(R.drawable.ic_check_circle)
                    .setMessage("成功连接到 ${server.host}:${server.port}，用户 ${server.username} 鉴权通过！")
                    .setPositiveButton("确定", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("连接或认证失败")
                    .setIcon(R.drawable.ic_error_outline)
                    .setMessage(result.errorMessage ?: "未知错误")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun confirmDelete(server: ServerEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(R.string.confirm_delete_server)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    App.database.serverDao().delete(server)
                    Toast.makeText(requireContext(), "已删除服务器", Toast.LENGTH_SHORT).show()
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
