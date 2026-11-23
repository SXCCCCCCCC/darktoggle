package com.github.sxcccccccc.darktoggle

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.sxcccccccc.darktoggle.databinding.ItemAppBinding

class AppListAdapter(
    private val loadIcon: (String) -> Drawable?,
    private val onToggle: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()
    private var selected: Set<String> = emptySet()

    fun submitList(newItems: List<AppEntry>, newSelected: Set<String>) {
        items = newItems
        selected = newSelected
        notifyDataSetChanged()
    }

    fun updateSelected(newSelected: Set<String>) {
        val old = selected
        selected = newSelected
        for (index in items.indices) {
            val pkg = items[index].packageName
            if ((pkg in old) != (pkg in selected)) {
                notifyItemChanged(index)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.bind(entry, entry.packageName in selected)
    }

    inner class Holder(
        private val binding: ItemAppBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: AppEntry, isChecked: Boolean) {
            binding.label.text = entry.label
            binding.packageName.text = entry.packageName
            binding.icon.setImageResource(R.drawable.ic_app_placeholder)
            loadIcon(entry.packageName)?.let { binding.icon.setImageDrawable(it) }

            binding.check.setOnCheckedChangeListener(null)
            binding.check.isChecked = isChecked
            binding.check.setOnCheckedChangeListener { _, _ -> onToggle(entry) }
            binding.root.setOnClickListener { onToggle(entry) }
        }
    }
}
