package com.example.ownmyway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PlacePhotoAdapter(private val urls: List<String>) :
    RecyclerView.Adapter<PlacePhotoAdapter.PhotoVH>() {

    inner class PhotoVH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH =
        PhotoVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_photo, parent, false))

    override fun onBindViewHolder(holder: PhotoVH, position: Int) {
        Glide.with(holder.image.context)
            .load(urls[position])
            .centerCrop()
            .placeholder(android.R.color.darker_gray)
            .into(holder.image)
    }

    override fun getItemCount() = urls.size
}
