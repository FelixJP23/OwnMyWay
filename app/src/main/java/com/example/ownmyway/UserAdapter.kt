package com.example.ownmyway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ownmyway.model.UserDetail

class UserAdapter(
    private val users: List<UserDetail>,
    private val onAddClick: (UserDetail) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // Referencia os IDs do item_user.xml
    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val addButton: Button = view.findViewById(R.id.btnAddFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Exibe o nome (ou email se o nome for nulo)
        holder.nameText.text = user.full_name ?: user.email

        // Quando clicar no botão, dispara a função que passamos na Activity
        holder.addButton.setOnClickListener {
            onAddClick(user)
        }
    }

    override fun getItemCount() = users.size
}