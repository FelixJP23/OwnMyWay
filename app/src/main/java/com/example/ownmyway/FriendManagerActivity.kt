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
    private lateinit var rvMyFriends: RecyclerView
    private lateinit var rvSearch: RecyclerView
    private lateinit var tvPedidosTitulo: TextView
    private lateinit var etSearchUser: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_friend_manager)

        // Inicialização de Views
        rvIncoming = findViewById(R.id.rvIncomingRequests)
        rvMyFriends = findViewById(R.id.rvMyFriends)
        rvSearch = findViewById(R.id.rvSearchResults)
        tvPedidosTitulo = findViewById(R.id.tvPedidosTitulo)
        etSearchUser = findViewById(R.id.etSearchUser)

        // Configuração de Layout Managers
        rvIncoming.layoutManager = LinearLayoutManager(this)
        rvMyFriends.layoutManager = LinearLayoutManager(this)
        rvSearch.layoutManager = LinearLayoutManager(this)

        // Botão Voltar
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Configuração da Busca por @Handle
        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val query = etSearchUser.text.toString().trim()
            if (query.isNotEmpty()) performSearchByHandle(query)
        }

        // Configuração do Carrossel de Paginação (10 usuários por página)
        setupPaginationButtons()

        // Carga Inicial
        loadIncomingRequests()
        loadMyFriends()
        loadExploreUsers(page = 1) // Começa na página 1 por padrão
    }

    private fun setupPaginationButtons() {
        findViewById<Button>(R.id.btnPage1).setOnClickListener { loadExploreUsers(1) }
        findViewById<Button>(R.id.btnPage2).setOnClickListener { loadExploreUsers(2) }
        findViewById<Button>(R.id.btnPage3).setOnClickListener { loadExploreUsers(3) }
        findViewById<Button>(R.id.btnPage4).setOnClickListener { loadExploreUsers(4) }
    }

    private fun loadMyFriends() {
        lifecycleScope.launch {
            try {
                // Supõe-se que você tenha essa função no Repository
                val friends = FriendRepository.getMyFriends()
                rvMyFriends.adapter = MyFriendsAdapter(friends)
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro ao carregar amigos: ${e.message}")
            }
        }
    }

    private fun loadExploreUsers(page: Int) {
        val pageSize = 10
        val offset = (page - 1) * pageSize

        lifecycleScope.launch {
            try {
                // Retira a chamada de todos e busca apenas 10 com base no offset
                val users = FriendRepository.getUsersPaginated(limit = pageSize, offset = offset)
                rvSearch.adapter = SearchAdapter(users, ::sendRequest)

                Toast.makeText(this@FriendManagerActivity, "Página $page carregada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro na paginação: ${e.message}")
            }
        }
    }

    private fun performSearchByHandle(query: String) {
        lifecycleScope.launch {
            try {
                // Formata a query para garantir que busca pelo @
                val handleQuery = if (query.startsWith("@")) query else "@$query"

                val users = FriendRepository.searchUsersByHandle(handleQuery)

                if (users.isEmpty()) {
                    Toast.makeText(this@FriendManagerActivity, "Nenhum @handle encontrado", Toast.LENGTH_SHORT).show()
                }
                rvSearch.adapter = SearchAdapter(users, ::sendRequest)
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro na busca: ${e.message}")
            }
        }
    }

    private fun loadIncomingRequests() {
        lifecycleScope.launch {
            try {
                val requests = FriendRepository.getIncomingRequests()
                if (requests.isNotEmpty()) {
                    tvPedidosTitulo.visibility = View.VISIBLE
                    rvIncoming.visibility = View.VISIBLE
                    rvIncoming.adapter = RequestAdapter(requests, ::respondToRequest)
                } else {
                    tvPedidosTitulo.visibility = View.GONE
                    rvIncoming.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro pedidos: ${e.message}")
            }
        }
    }

    private fun respondToRequest(senderId: String, accept: Boolean) {
        lifecycleScope.launch {
            try {
                FriendRepository.respondToFriendRequest(senderId, accept)
                Toast.makeText(this@FriendManagerActivity, if(accept) "Novo amigo!" else "Recusado", Toast.LENGTH_SHORT).show()
                loadIncomingRequests()
                loadMyFriends() // Atualiza lista de amigos se aceitou
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
                Toast.makeText(this@FriendManagerActivity, "Você já possui um vínculo com este usuário", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// --- ADAPTERS ---

// 1. Adapter para busca e exploração (com botão de adicionar)
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
        // Exibe o Nome e o Handle (ex: Artur (@artur123))
        holder.nameText.text = "${user.full_name} (${user.handle ?: "@viajante"})"
        holder.addButton.setOnClickListener { onAddClick(user.id) }
    }
    override fun getItemCount() = users.size
}

// 2. Adapter para Meus Amigos (Apenas listagem)
class MyFriendsAdapter(private val friends: List<UserDetail>) : RecyclerView.Adapter<MyFriendsAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val actionButton: Button = view.findViewById(R.id.btnAddFriend)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return VH(view)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val friend = friends[position]
        holder.nameText.text = friend.full_name
        holder.actionButton.text = "Perfil" // Muda o botão para algo que faça sentido para amigos
    }
    override fun getItemCount() = friends.size
}

// 3. Adapter para Pedidos Recebidos
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
        holder.nameText.text = "Pedido de: ${req.sender_id.take(6)}..."
        holder.btnAccept.setOnClickListener { onRespond(req.sender_id, true) }
        holder.btnDecline.setOnClickListener { onRespond(req.sender_id, false) }
    }
    override fun getItemCount() = requests.size
}