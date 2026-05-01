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
import com.example.ownmyway.model.*
import com.example.ownmyway.repository.FriendRepository
import com.bumptech.glide.Glide
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
            if (query.isNotEmpty()) performSearchByUsername(query)
        }

        // Configuração dos Botões de Paginação
        setupPaginationButtons()

        // Carga Inicial
        loadIncomingRequests()
        loadMyFriends()
        loadExploreUsers(page = 1)
    }

    private fun setupPaginationButtons() {
        findViewById<Button>(R.id.btnPage1).setOnClickListener { loadExploreUsers(1) }
        findViewById<Button>(R.id.btnPage2).setOnClickListener { loadExploreUsers(2) }
        findViewById<Button>(R.id.btnPage3).setOnClickListener { loadExploreUsers(3) }
        findViewById<Button>(R.id.btnPage4).setOnClickListener { loadExploreUsers(4) }
    }

    // --- FUNÇÕES DE CARGA DE DADOS para correção de erro---

    private fun loadIncomingRequests() {
        lifecycleScope.launch {
            try {
                // Busca pedidos com detalhes (Nome e Foto) do remetente
                val requests = FriendRepository.getIncomingRequestsWithDetails()
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

    private fun loadMyFriends() {
        lifecycleScope.launch {
            try {
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
                val users = FriendRepository.getUsersPaginated(limit = pageSize, offset = offset)
                rvSearch.adapter = SearchAdapter(users, ::sendRequest)
                Toast.makeText(this@FriendManagerActivity, "Página $page carregada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro na paginação: ${e.message}")
            }
        }
    }

    private fun performSearchByUsername(query: String) {
        lifecycleScope.launch {
            try {
                // Remove o @ caso o usuário tenha digitado (ex: @arturzera -> arturzera)
                val cleanQuery = query.replace("@", "").trim()

                val users = FriendRepository.searchUsersByUsername(cleanQuery)

                if (users.isEmpty()) {
                    Toast.makeText(this@FriendManagerActivity, "Usuário '$cleanQuery' não encontrado", Toast.LENGTH_SHORT).show()
                }

                rvSearch.adapter = SearchAdapter(users, ::sendRequest)
            } catch (e: Exception) {
                Log.e("FriendManager", "Erro na busca: ${e.message}")
            }
        }
    }

    // --- AÇÕES DE AMIZADE ---

    private fun respondToRequest(senderId: String, accept: Boolean) {
        lifecycleScope.launch {
            try {
                FriendRepository.respondToFriendRequest(senderId, accept)
                Toast.makeText(this@FriendManagerActivity, if(accept) "Novo amigo!" else "Recusado", Toast.LENGTH_SHORT).show()
                loadIncomingRequests()
                loadMyFriends()
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

// 1. Adapter para busca e exploração (Usa item_user.xml)
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
        holder.nameText.text = "${user.full_name} (${user.username ?: "@viajante"})"
        holder.addButton.setOnClickListener { onAddClick(user.id) }
    }
    override fun getItemCount() = users.size
}

// 2. Adapter para Meus Amigos (Usa item_user.xml)
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
        holder.actionButton.text = "Perfil"
    }
    override fun getItemCount() = friends.size
}

// 3. Adapter para Pedidos Recebidos (Usa item_pedido_recebido.xml com Foto)
class RequestAdapter(
    private val requests: List<IncomingRequestDetail>,
    private val onRespond: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RequestAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvUserName)
        val userPhoto: ImageView = view.findViewById(R.id.ivUserPhoto)
        val btnAccept: Button = view.findViewById(R.id.btnAccept)
        val btnDecline: Button = view.findViewById(R.id.btnDecline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pedido_recebido, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = requests[position]
        val profile = item.senderDetail
        val friendship = item.friendship

        // Exibe o Nome
        holder.nameText.text = profile.full_name ?: profile.username ?: "Usuário"

        // Carrega foto com Glide
        Glide.with(holder.itemView.context)
            .load(profile.avatar_url)
            .placeholder(R.drawable.ic_user_placeholder)
            .circleCrop()
            .into(holder.userPhoto)

        holder.btnAccept.setOnClickListener { onRespond(friendship.sender_id, true) }
        holder.btnDecline.setOnClickListener { onRespond(friendship.sender_id, false) }
    }

    override fun getItemCount() = requests.size
}