package com.antigravity.sshwake.ui.keys

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.sshwake.R
import com.antigravity.sshwake.data.KeyEntity
import com.antigravity.sshwake.data.KeyType
import com.antigravity.sshwake.databinding.ItemKeyBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyAdapter(
    private val onEditClick: (KeyEntity) -> Unit,
    private val onDeleteClick: (KeyEntity) -> Unit
) : ListAdapter<KeyEntity, KeyAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    inner class ViewHolder(val binding: ItemKeyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(key: KeyEntity) {
            val context = binding.root.context
            val typeStr = if (key.type == KeyType.PRIVATE_KEY) context.getString(R.string.key_type_key_desc) else context.getString(R.string.key_type_password_desc)
            val dateStr = dateFormat.format(Date(key.createdAt))
            binding.tvKeyType.text = "$typeStr • $dateStr"

            binding.root.setOnLongClickListener {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Key Name", key.name)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, context.getString(R.string.toast_copied_key, key.name), android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            binding.btnEditKey.setOnClickListener { onEditClick(key) }
            binding.btnDeleteKey.setOnClickListener { onDeleteClick(key) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKeyBinding.inflate(
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
        private val DiffCallback = object : DiffUtil.ItemCallback<KeyEntity>() {
            override fun areItemsTheSame(oldItem: KeyEntity, newItem: KeyEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: KeyEntity, newItem: KeyEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
