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

    private var allServers: List<ServerEntity> = emptyList()
    private var selectedPackage: String? = null
    private var currentPackageList: List<String> = emptyList()

    private fun setupRecyclerView() {
        adapter = ServerAdapter(
            onTestClick = { server -> testServer(server) },
            onEditClick = { server ->
                EditServerDialog(requireContext(), viewLifecycleOwner.lifecycleScope, existingServer = server) {
                    Toast.makeText(requireContext(), R.string.toast_server_updated, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), R.string.empty_keys, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                EditServerDialog(requireContext(), viewLifecycleOwner.lifecycleScope, defaultPackage = selectedPackage) {
                    Toast.makeText(requireContext(), R.string.toast_server_created, Toast.LENGTH_SHORT).show()
                }.show()
            }
        }
    }

    private fun observeData() {
        App.database.serverDao().getAllLiveData().observe(viewLifecycleOwner) { list ->
            allServers = list ?: emptyList()
            updatePackageChipsAndFilter()
        }
    }

    private fun updatePackageChipsAndFilter() {
        val packages = allServers.map { it.packageGroup }.distinct().sorted()

        if (packages != currentPackageList) {
            currentPackageList = packages
            binding.chipGroupPackages.removeAllViews()

            val allChip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = getString(R.string.all_packages)
                isCheckable = true
                isChecked = (selectedPackage == null || selectedPackage !in packages)
                setOnClickListener {
                    selectedPackage = null
                    filterAndSubmit()
                }
            }
            binding.chipGroupPackages.addView(allChip)

            packages.forEach { pkgName ->
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = pkgName
                    isCheckable = true
                    isChecked = (selectedPackage == pkgName)
                    setOnClickListener {
                        selectedPackage = pkgName
                        filterAndSubmit()
                    }
                }
                binding.chipGroupPackages.addView(chip)
            }

            if (selectedPackage != null && selectedPackage !in packages) {
                selectedPackage = null
            }
        }

        filterAndSubmit()
    }

    private fun filterAndSubmit() {
        val filtered = if (selectedPackage.isNullOrEmpty()) {
            allServers
        } else {
            allServers.filter { it.packageGroup == selectedPackage }
        }

        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerServers.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerServers.visibility = View.VISIBLE
            adapter.submitList(filtered)
        }
    }

    private fun testServer(server: ServerEntity) {
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.testing_connection)
            .setMessage(R.string.test_dialog_connecting)
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
                    .setMessage(getString(R.string.test_dialog_success_msg, server.host, server.port, server.username))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.test_dialog_failed_title)
                    .setIcon(R.drawable.ic_error_outline)
                    .setMessage(result.errorMessage ?: "Unknown error")
                    .setPositiveButton(R.string.btn_ok, null)
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
                    Toast.makeText(requireContext(), R.string.toast_server_deleted, Toast.LENGTH_SHORT).show()
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
