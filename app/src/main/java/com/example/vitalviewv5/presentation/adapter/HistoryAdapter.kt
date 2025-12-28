package com.example.vitalviewv5.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vitalviewv5.databinding.ItemHistoryBinding
import com.example.vitalviewv5.presentation.viewmodel.MetricDetailViewModel

class HistoryAdapter : ListAdapter<MetricDetailViewModel.HistoryPoint, HistoryAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MetricDetailViewModel.HistoryPoint) {
            binding.tvValue.text = if (item.label.contains("/")) { // Handling BP special case
                 // If label has BP "120/80 - Date", extracting value isn't straightforward from float only
                 // Ideally model should support string value explicitly, but for now label contains everything for BP
                 item.value.toInt().toString() // This might just be systolic
            } else {
                 item.value.toString()
            }
            // Actually, let's just trust label + value logic from ViewModel or adjust ViewModel to provide full text
            // In ViewModel logic: `HistoryPoint(systolic.toFloat(), timestamp, "$sys/$dia - $date")`
            // So label contains date primarily.
            
            // Let's refine binding logic
            binding.tvValue.text = item.value.toString()
            binding.tvDate.text = item.label
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        // Quick hack: if label contains full string (for BP), use it
        if (item.label.contains("/") && item.label.contains("-")) {
             // It's likely BP: "120/80 - 2025..."
             // Let's split it up
             val parts = item.label.split(" - ")
             holder.itemView.findViewById<android.widget.TextView>(com.example.vitalviewv5.R.id.tvValue).text = parts.getOrElse(0) { item.value.toString() }
             holder.itemView.findViewById<android.widget.TextView>(com.example.vitalviewv5.R.id.tvDate).text = parts.getOrElse(1) { item.label }
        } else {
            holder.itemView.findViewById<android.widget.TextView>(com.example.vitalviewv5.R.id.tvValue).text = item.value.toString()
            holder.itemView.findViewById<android.widget.TextView>(com.example.vitalviewv5.R.id.tvDate).text = item.label
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MetricDetailViewModel.HistoryPoint>() {
        override fun areItemsTheSame(oldItem: MetricDetailViewModel.HistoryPoint, newItem: MetricDetailViewModel.HistoryPoint): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: MetricDetailViewModel.HistoryPoint, newItem: MetricDetailViewModel.HistoryPoint): Boolean {
            return oldItem == newItem
        }
    }
}
