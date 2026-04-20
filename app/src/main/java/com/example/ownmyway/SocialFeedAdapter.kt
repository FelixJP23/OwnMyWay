package com.example.ownmyway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ownmyway.model.SocialAction

class SocialFeedAdapter(private val actions: List<SocialAction>) :
    RecyclerView.Adapter<SocialFeedAdapter.SocialViewHolder>() {

    class SocialViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvFriendName)
        val action: TextView = view.findViewById(R.id.tvSocialAction)
        val dest: TextView = view.findViewById(R.id.tvDestination)
        val time: TextView = view.findViewById(R.id.tvTimeAgo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocialViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social_feed, parent, false)
        return SocialViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocialViewHolder, position: Int) {
        val item = actions[position]
        holder.name.text = item.friendName
        holder.action.text = item.actionText
        holder.dest.text = item.destination
        holder.time.text = item.timeAgo
    }

    override fun getItemCount() = actions.size
}
