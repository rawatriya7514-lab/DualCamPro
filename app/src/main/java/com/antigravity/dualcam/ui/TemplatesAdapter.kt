package com.antigravity.dualcam.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.dualcam.R
import com.antigravity.dualcam.templates.RecordingTemplate

class TemplatesAdapter(
    private val items: List<RecordingTemplate>,
    private val onSelected: (RecordingTemplate) -> Unit
) : RecyclerView.Adapter<TemplatesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardTemplate)
        val title: TextView = view.findViewById(R.id.tvTemplateTitle)
        val desc: TextView = view.findViewById(R.id.tvTemplateDesc)
        val badge: TextView = view.findViewById(R.id.tvTemplateBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_template, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.desc.text = item.description
        holder.badge.text = item.badge

        holder.card.setOnClickListener { onSelected(item) }
        holder.itemView.setOnClickListener { onSelected(item) }
    }

    override fun getItemCount(): Int = items.size
}
