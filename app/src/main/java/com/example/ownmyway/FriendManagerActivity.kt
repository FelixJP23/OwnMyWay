package com.example.ownmyway

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
        supportActionBar?.hide()
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
    }

    override fun onResume() {
        super.onResume()
        loadIncomingRequests() // Atualiza sempre que a tela ganha foco
    }

    private fun loadIncomingRequests() {
        lifecycleScope.launch {
            try {
                val requests = FriendRepository.getIncomingRequests()
                Log.d("FRIEND_DEBUG", "Pedidos encontrados para mim: ${requests.size}")

                if (requests.isNotEmpty()) {
                    tvPedidosTitulo.visibility = View.VISIBLE
                    rvIncoming.visibility = View.VISIBLE
                    rvIncoming.adapter = RequestAdapter(requests, ::respondToRequest)
                } else {
                    tvPedidosTitulo.visibility = View.GONE
                    rvIncoming.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro ao carregar: ${e.message}")
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
                val msg = if(accept) "Amigo adicionado!" else "Pedido recusado"
                Toast.makeText(this@FriendManagerActivity, msg, Toast.LENGTH_SHORT).show()
                loadIncomingRequests() // Recarrega para sumir da lista
            } catch (e: Exception) {
                Toast.makeText(this@FriendManagerActivity, "Erro ao responder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendRequest(targetId: String) {
        lifecycleScope.launch {
            try {
                FriendRepository.sendFriendRequest(targetId)
                Toast.makeText(this@FriendManagerActivity, "Convite enviado!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@FriendManagerActivity, "Erro ao enviar pedido", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// --- Adapters Mantidos no mesmo arquivo para facilitar ---

class SearchAdapter(private val users: List<UserDetail>, private val onAddClick: (String) -> Unit) : RecyclerView.Adapter<SearchAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val addButton: Button = view.findViewById(R.id.btnAddFriend)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
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
        // Exibe o ID curto enquanto você não implementa o JOIN para pegar nomes
        holder.nameText.text = "Pedido de: ${req.sender_id.take(8)}..."
        holder.btnAccept.setOnClickListener { onRespond(req.sender_id, true) }
        holder.btnDecline.setOnClickListener { onRespond(req.sender_id, false) }
    }
    override fun getItemCount() = requests.size
}