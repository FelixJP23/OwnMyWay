package com.example.ownmyway

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.model.UserDetail
import com.example.ownmyway.repository.FriendRepository
import kotlinx.coroutines.launch

class FriendManagerActivity : AppCompatActivity() {

    private lateinit var rvIncoming: RecyclerView
    private lateinit var rvSearch: RecyclerView
    private lateinit var tvPedidosTitulo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide() // Tira a barra superior
        setContentView(R.layout.activity_friend_manager)

        rvIncoming = findViewById(R.id.rvIncomingRequests)
        rvSearch = findViewById(R.id.rvSearchResults)
        tvPedidosTitulo = findViewById(R.id.tvPedidosTitulo)

        rvIncoming.layoutManager = LinearLayoutManager(this)
        rvSearch.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val query = findViewById<EditText>(R.id.etSearchUser).text.toString().trim()
            if (query.isNotEmpty()) performSearch(query)
        }

        loadIncomingRequests()
    }

    private fun loadIncomingRequests() {
        lifecycleScope.launch {
            try {
                val requests = FriendRepository.getIncomingRequests()
                if (requests.isNotEmpty()) {
                    tvPedidosTitulo.visibility = View.VISIBLE
                    rvIncoming.adapter = RequestAdapter(requests, ::respondToRequest)
                } else {
                    tvPedidosTitulo.visibility = View.GONE
                    rvIncoming.adapter = null
                }
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro ao carregar pedidos: ${e.message}")
            }
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                val users = FriendRepository.searchUsers(query)
                if (users.isEmpty()) {
                    Toast.makeText(this@FriendManagerActivity, "Ninguém encontrado", Toast.LENGTH_SHORT).show()
                }
                rvSearch.adapter = SearchAdapter(users, ::sendRequest)
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro na busca: ${e.message}")
            }
        }
    }

    private fun respondToRequest(senderId: String, accept: Boolean) {
        lifecycleScope.launch {
            try {
                FriendRepository.respondToFriendRequest(senderId, accept)
                Toast.makeText(this@FriendManagerActivity, if(accept) "Amigo adicionado!" else "Recusado", Toast.LENGTH_SHORT).show()
                loadIncomingRequests() // Recarrega a lista
            } catch (e: Exception) {
                Toast.makeText(this@FriendManagerActivity, "Erro", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendRequest(targetId: String) {
        lifecycleScope.launch {
            try {
                FriendRepository.sendFriendRequest(targetId)
                Toast.makeText(this@FriendManagerActivity, "Convite enviado!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@FriendManagerActivity, "Erro ao enviar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ================= ADAPTERS (Podem ficar neste mesmo arquivo por enquanto) =================

class SearchAdapter(private val users: List<UserDetail>, private val onAddClick: (String) -> Unit) : RecyclerView.Adapter<SearchAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val addButton: Button = view.findViewById(R.id.btnAddFriend)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // Usa o layout item_user que você já tinha criado nas mensagens anteriores
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = users[position]
        holder.nameText.text = user.full_name ?: user.email
        holder.addButton.setOnClickListener { onAddClick(user.id) }
    }
    override fun getItemCount() = users.size
}

class RequestAdapter(private val requests: List<Friendship>, private val onRespond: (String, Boolean) -> Unit) : RecyclerView.Adapter<RequestAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        // Tente usar IDs genéricos do seu item_user, ou crie um item_pedido_recebido.xml
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val btnAccept: Button = view.findViewById(R.id.btnAccept)
        val btnDecline: Button = view.findViewById(R.id.btnDecline)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pedido_recebido, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = requests[position]
        holder.nameText.text = "Pedido de: ${req.sender_id.take(5)}..." // Idealmente seria buscar o nome via Join no Supabase
        holder.btnAccept.setOnClickListener { onRespond(req.sender_id, true) }
        holder.btnDecline.setOnClickListener { onRespond(req.sender_id, false) }
    }
    override fun getItemCount() = requests.size
}