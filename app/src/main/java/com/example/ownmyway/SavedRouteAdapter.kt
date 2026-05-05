package com.example.ownmyway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SavedRouteAdapter(
    private val routes: List<SavedRoute>,
    private val onOpen: (SavedRoute) -> Unit,
    private val onDelete: (SavedRoute) -> Unit
) : RecyclerView.Adapter<SavedRouteAdapter.RouteVH>() {

    inner class RouteVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:        TextView    = view.findViewById(R.id.tvRouteName)
        val tvDescription: TextView    = view.findViewById(R.id.tvRouteDescription)
        val tvMeta:        TextView    = view.findViewById(R.id.tvRouteMeta)
        val tvDate:        TextView    = view.findViewById(R.id.tvRouteDate)
        val btnDelete:     ImageButton = view.findViewById(R.id.btnDeleteRoute)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteVH =
        RouteVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_route, parent, false))

    override fun onBindViewHolder(holder: RouteVH, position: Int) {
        val route = routes[position]

        holder.tvName.text = route.name

        if (!route.description.isNullOrBlank()) {
            holder.tvDescription.text       = route.description
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        holder.tvMeta.text = buildString {
            append("📍 ${route.stop_count} paradas")
            if (route.total_cost > 0) append("  •  💰 R$ ${route.total_cost}")
        }

        holder.tvDate.text = route.created_at
            ?.take(10)
            ?.replace("-", "/")
            ?.split("/")
            ?.reversed()
            ?.joinToString("/")
            ?: ""

        holder.itemView.setOnClickListener { onOpen(route) }
        holder.btnDelete.setOnClickListener { onDelete(route) }
    }

    override fun getItemCount() = routes.size
}
