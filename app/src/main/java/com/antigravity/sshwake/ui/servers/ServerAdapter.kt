package com.antigravity.sshwake.ui.servers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.AuthType
import com.antigravity.sshwake.data.ServerEntity
import com.antigravity.sshwake.databinding.ItemServerBinding

class ServerAdapter(
    private val onTestClick: (ServerEntity) -> Unit,
    private val onEditClick: (ServerEntity) -> Unit,
    private val onDeleteClick: (ServerEntity) -> Unit
) : ListAdapter<ServerEntity, ServerAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(server: ServerEntity) {
            binding.tvServerName.text = server.name
            binding.tvServerEndpoint.text = "${server.username}@${server.host}:${server.port}"

            if (server.authType == AuthType.KEY) {
                binding.chipAuthType.text = binding.root.context.getString(R.string.auth_key)
            } else {
                binding.chipAuthType.text = binding.root.context.getString(R.string.auth_password)
            }

            binding.btnTestConnection.setOnClickListener { onTestClick(server) }
            binding.btnEditServer.setOnClickListener { onEditClick(server) }
            binding.btnDeleteServer.setOnClickListener { onDeleteClick(server) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ServerEntity>() {
            override fun areItemsTheSame(oldItem: ServerEntity, newItem: ServerEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ServerEntity, newItem: ServerEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
