package com.antigravity.dualcam.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.dualcam.R
import com.antigravity.dualcam.model.VideoItem
import com.antigravity.dualcam.recorder.MediaStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryAdapter(
    private val context: Context,
    private val videos: MutableList<VideoItem>,
    private val onVideoClick: (VideoItem) -> Unit,
    private val onShareClick: (VideoItem) -> Unit,
    private val onTrimClick: (VideoItem) -> Unit,
    private val onDeleteClick: (VideoItem, Int) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val btnShare: ImageView = view.findViewById(R.id.btnShare)
        val btnTrim: ImageView = view.findViewById(R.id.btnTrim)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = videos[position]
        holder.tvTitle.text = item.name
        holder.tvDuration.text = item.durationFormatted
        holder.tvMeta.text = "${item.resolutionFormatted} • ${item.sizeFormatted}"

        val dateStr = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT
        ).format(java.util.Date(item.dateAddedMs))
        holder.tvDate.text = dateStr

        // Load thumbnail in background
        holder.ivThumbnail.setImageDrawable(null)
        CoroutineScope(Dispatchers.IO).launch {
            val thumb = MediaStoreHelper.extractThumbnail(context, item)
            withContext(Dispatchers.Main) {
                if (thumb != null) {
                    holder.ivThumbnail.setImageBitmap(thumb)
                } else {
                    holder.ivThumbnail.setImageResource(R.drawable.ic_gallery)
                }
            }
        }

        holder.itemView.setOnClickListener { onVideoClick(item) }
        holder.btnShare.setOnClickListener { onShareClick(item) }
        holder.btnTrim.setOnClickListener { onTrimClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item, holder.adapterPosition) }
    }

    override fun getItemCount(): Int = videos.size

    fun removeAt(position: Int) {
        if (position in 0 until videos.size) {
            videos.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
