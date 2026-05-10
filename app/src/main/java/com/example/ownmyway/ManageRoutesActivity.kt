package com.example.ownmyway

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.json.JSONArray
import org.json.JSONObject

class ManageRoutesActivity : AppCompatActivity() {

    private lateinit var rvRoutes: RecyclerView
    private lateinit var tvEmpty: android.widget.LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSaveCurrent: View

    private val currentStopsJson by lazy {
        intent.getStringExtra(EXTRA_CURRENT_STOPS_JSON) ?: ""
    }
    private val currentTotalCost by lazy {
        intent.getIntExtra(EXTRA_CURRENT_COST, 0)
    }
    private val currentStopCount by lazy {
        intent.getIntExtra(EXTRA_CURRENT_STOP_COUNT, 0)
    }

    companion object {
        const val EXTRA_CURRENT_STOPS_JSON  = "current_stops_json"
        const val EXTRA_CURRENT_COST        = "current_cost"
        const val EXTRA_CURRENT_STOP_COUNT  = "current_stop_count"
        const val RESULT_STOPS_JSON         = "result_stops_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_routes)

        rvRoutes     = findViewById(R.id.rvSavedRoutes)
        tvEmpty      = findViewById(R.id.tvEmptyRoutes)
        progressBar  = findViewById(R.id.progressManageRoutes)
        btnSaveCurrent = findViewById(R.id.btnSaveCurrentRoute)

        rvRoutes.layoutManager = LinearLayoutManager(this)

        // Show/hide save button based on whether there's a current route
        btnSaveCurrent.visibility =
            if (currentStopsJson.isNotBlank()) View.VISIBLE else View.GONE

        btnSaveCurrent.setOnClickListener { showSaveDialog() }

        findViewById<ImageButton>(R.id.btnBackManage).setOnClickListener { finish() }

        loadSavedRoutes()
    }

    // ── Load routes from Supabase ─────────────────────────────────────────
    private fun loadSavedRoutes() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility     = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routes = SupabaseClient.client.postgrest["saved_routes"]
                    .select {
                        filter {
                            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                            if (userId != null) eq("user_id", userId)
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<SavedRoute>()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (routes.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rvRoutes.visibility = View.GONE
                    } else {
                        tvEmpty.visibility  = View.GONE
                        rvRoutes.visibility = View.VISIBLE
                        rvRoutes.adapter = SavedRouteAdapter(
                            routes    = routes,
                            onOpen    = { route -> openRouteOnMap(route) },
                            onDelete  = { route -> confirmDelete(route) }
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ManageRoutesActivity,
                        "Erro ao carregar rotas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Save dialog ───────────────────────────────────────────────────────
    private fun showSaveDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_save_route)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val etName        = dialog.findViewById<EditText>(R.id.etRouteName)
        val etDescription = dialog.findViewById<EditText>(R.id.etRouteDescription)
        val tvCostInfo    = dialog.findViewById<TextView>(R.id.tvSaveRouteCostInfo)
        val btnConfirm    = dialog.findViewById<Button>(R.id.btnConfirmSave)
        val btnCancel     = dialog.findViewById<TextView>(R.id.tvCancelSave)

        tvCostInfo.text = "📍 $currentStopCount paradas  •  💰 R$ $currentTotalCost estimado"

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Dê um nome para sua rota"
                return@setOnClickListener
            }
            dialog.dismiss()
            saveRoute(name, etDescription.text.toString().trim())
        }

        dialog.show()
    }

    // ── Persist to Supabase ───────────────────────────────────────────────
    private fun saveRoute(name: String, description: String) {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val route = SavedRoute(
                    user_id     = userId,
                    name        = name,
                    description = description.ifBlank { null },
                    stops_json  = currentStopsJson,
                    total_cost  = currentTotalCost,
                    stop_count  = currentStopCount
                )
                SupabaseClient.client.postgrest["saved_routes"].insert(route)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageRoutesActivity,
                        "✅ Rota \"$name\" salva!", Toast.LENGTH_SHORT).show()
                    loadSavedRoutes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageRoutesActivity,
                        "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Open route on map ─────────────────────────────────────────────────
    private fun openRouteOnMap(route: SavedRoute) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_STOPS_JSON, route.stops_json)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // ── Delete route ──────────────────────────────────────────────────────
    private fun confirmDelete(route: SavedRoute) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Excluir rota")
            .setMessage("Deseja excluir \"${route.name}\"?")
            .setPositiveButton("Excluir") { _, _ -> deleteRoute(route) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteRoute(route: SavedRoute) {
        val id = route.id ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseClient.client.postgrest["saved_routes"].delete {
                    filter { eq("id", id) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageRoutesActivity,
                        "Rota excluída", Toast.LENGTH_SHORT).show()
                    loadSavedRoutes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageRoutesActivity,
                        "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
