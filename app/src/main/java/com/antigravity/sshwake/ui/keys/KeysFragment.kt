package com.antigravity.sshwake.ui.keys

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
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.databinding.FragmentKeysBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class KeysFragment : Fragment() {

    private var _binding: FragmentKeysBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: KeyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = KeyAdapter(
            onEditClick = { key ->
                EditKeyDialog(requireContext(), viewLifecycleOwner.lifecycleScope, key) {
                    Toast.makeText(requireContext(), R.string.toast_key_updated, Toast.LENGTH_SHORT).show()
                }.show()
            },
            onDeleteClick = { key -> confirmDelete(key) }
        )

        binding.recyclerKeys.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerKeys.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddKey.setOnClickListener {
            EditKeyDialog(requireContext(), viewLifecycleOwner.lifecycleScope) {
                Toast.makeText(requireContext(), R.string.toast_key_added, Toast.LENGTH_SHORT).show()
            }.show()
        }
    }

    private fun observeData() {
        App.database.keyDao().getAllLiveData().observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerKeys.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerKeys.visibility = View.VISIBLE
                adapter.submitList(list)
            }
        }
    }

    private fun confirmDelete(key: KeyEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(R.string.confirm_delete_key)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    App.database.keyDao().delete(key)
                    Toast.makeText(requireContext(), R.string.toast_key_deleted, Toast.LENGTH_SHORT).show()
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
