package com.antigravity.sshwake.ui.commands

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.sshwake.data.CommandWithServer
import com.antigravity.sshwake.databinding.ItemCommandBinding

class CommandAdapter(
    private val onRunClick: (CommandWithServer) -> Unit,
    private val onPinClick: (CommandWithServer) -> Unit,
    private val onEditClick: (CommandWithServer) -> Unit,
    private val onDeleteClick: (CommandWithServer) -> Unit,
    private val onItemClick: ((CommandWithServer) -> Unit)? = null
) : ListAdapter<CommandWithServer, CommandAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemCommandBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandWithServer) {
            binding.tvCommandName.text = item.command.name
            binding.tvServerName.text = if (item.server != null) {
                "${item.server.name} (${item.server.host}:${item.server.port})"
            } else {
                "⚠️ 未关联服务器或已丢失"
            }
            binding.tvCommandScript.text = item.command.command

            binding.btnRunCommand.setOnClickListener { onRunClick(item) }
            binding.btnPinToDesktop.setOnClickListener { onPinClick(item) }
            binding.btnEditCommand.setOnClickListener { onEditClick(item) }
            binding.btnDeleteCommand.setOnClickListener { onDeleteClick(item) }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommandBinding.inflate(
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
        private val DiffCallback = object : DiffUtil.ItemCallback<CommandWithServer>() {
            override fun areItemsTheSame(oldItem: CommandWithServer, newItem: CommandWithServer): Boolean {
                return oldItem.command.id == newItem.command.id
            }

            override fun areContentsTheSame(oldItem: CommandWithServer, newItem: CommandWithServer): Boolean {
                return oldItem == newItem
            }
        }
    }
}
