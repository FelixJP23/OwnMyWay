package com.example.ownmyway

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ownmyway.model.BudgetTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class BudgetActivity : AppCompatActivity() {

    private var currentBalance = 0.0
    private var transactions = mutableListOf<BudgetTransaction>()
    
    private lateinit var tvBalance: TextView
    private lateinit var etAmount: EditText
    
    private val prefs by lazy { getSharedPreferences("budget_prefs", MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        loadData()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarBudget)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvBalance = findViewById(R.id.tvTotalBalance)
        etAmount = findViewById(R.id.etAmount)

        updateBalanceDisplay()
        updateChart()

        findViewById<Button>(R.id.btnAddMoney).setOnClickListener {
            showDescriptionDialog(true)
        }

        findViewById<Button>(R.id.btnWithdrawMoney).setOnClickListener {
            showDescriptionDialog(false)
        }
        
        findViewById<View>(R.id.btnShowReport).setOnClickListener {
            showReportDialog()
        }
    }

    private fun loadData() {
        currentBalance = prefs.getFloat("balance", 0f).toDouble()
        val txJson = prefs.getString("transactions", "[]") ?: "[]"
        try {
            transactions = json.decodeFromString<List<BudgetTransaction>>(txJson).toMutableList()
        } catch (e: Exception) {
            transactions = mutableListOf()
        }
    }

    private fun saveData() {
        prefs.edit().apply {
            putFloat("balance", currentBalance.toFloat())
            putString("transactions", json.encodeToString(transactions))
            apply()
        }
    }

    private fun showDescriptionDialog(isAdding: Boolean) {
        val input = etAmount.text.toString()
        if (input.isBlank()) {
            Toast.makeText(this, "Digite um valor!", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = input.toDoubleOrNull() ?: 0.0
        if (!isAdding && amount > currentBalance) {
            Toast.makeText(this, "Saldo insuficiente!", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (isAdding) "Guardar Dinheiro" else "Retirar Dinheiro")
        
        val container = android.widget.FrameLayout(this)
        val etDesc = EditText(this).apply {
            hint = "Descrição (ex: Salário)"
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        container.addView(etDesc)
        builder.setView(container)

        builder.setPositiveButton("OK") { _, _ ->
            val description = etDesc.text.toString().ifBlank { if (isAdding) "Depósito" else "Retirada" }
            processTransaction(isAdding, amount, description)
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun processTransaction(isAdding: Boolean, amount: Double, description: String) {
        if (isAdding) {
            currentBalance += amount
        } else {
            currentBalance -= amount
        }

        val transaction = BudgetTransaction(amount, isAdding, description)
        transactions.add(transaction)
        
        saveData()
        updateBalanceDisplay()
        updateChart()
        etAmount.text.clear()
        
        val msg = if (isAdding) "R$ $amount guardados!" else "R$ $amount retirados!"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun updateBalanceDisplay() {
        tvBalance.text = "R$ %.2f".format(currentBalance)
    }

    private fun updateChart() {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMM", Locale("pt", "BR"))
        
        // Vamos pegar os últimos 6 meses
        val monthsData = DoubleArray(6) { 0.0 }
        val monthNames = Array(6) { "" }
        
        for (i in 5 downTo 0) {
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.MONTH, -i)
            monthNames[5 - i] = sdf.format(tempCal.time).replaceFirstChar { it.uppercase() }
            
            // Calcula o saldo acumulado até o fim desse mês
            val targetMonthEnd = tempCal.apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis
            
            monthsData[5 - i] = transactions.filter { it.timestamp <= targetMonthEnd }
                .sumOf { if (it.isAddition) it.amount else -it.amount }
        }

        // Atualiza as views do gráfico
        val bars = listOf(
            findViewById<View>(R.id.bar1), findViewById<View>(R.id.bar2),
            findViewById<View>(R.id.bar3), findViewById<View>(R.id.bar4),
            findViewById<View>(R.id.bar5), findViewById<View>(R.id.bar6)
        )
        val labels = listOf(
            findViewById<TextView>(R.id.tvMonth1), findViewById<TextView>(R.id.tvMonth2),
            findViewById<TextView>(R.id.tvMonth3), findViewById<TextView>(R.id.tvMonth4),
            findViewById<TextView>(R.id.tvMonth5), findViewById<TextView>(R.id.tvMonth6)
        )

        val maxVal = monthsData.maxOfOrNull { Math.abs(it) }?.coerceAtLeast(1.0) ?: 1.0
        
        monthsData.forEachIndexed { index, value ->
            labels[index].text = monthNames[index]
            val params = bars[index].layoutParams
            // Altura proporcional (max 100dp)
            val heightDp = (Math.abs(value) / maxVal * 100).toInt().coerceAtLeast(10)
            params.height = (heightDp * resources.displayMetrics.density).toInt()
            bars[index].layoutParams = params
            
            // Cor: roxo se positivo, vermelho se negativo no mês
            bars[index].setBackgroundColor(
                if (value >= 0) android.graphics.Color.parseColor("#4A2080")
                else android.graphics.Color.parseColor("#FF5252")
            )
        }
    }

    private fun showReportDialog() {
        if (transactions.isEmpty()) {
            Toast.makeText(this, "Nenhuma transação registrada.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val report = transactions.sortedByDescending { it.timestamp }.joinToString("\n\n") {
            val type = if (it.isAddition) "[GUARDOU]" else "[RETIROU]"
            "$type ${sdf.format(Date(it.timestamp))}\nValor: R$ ${it.amount}\nMotivo: ${it.description}"
        }

        AlertDialog.Builder(this)
            .setTitle("Relatório de Manutenção")
            .setMessage(report)
            .setPositiveButton("Fechar", null)
            .show()
    }
}
