package com.example.ownmyway

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BudgetActivity : AppCompatActivity() {

    private var currentBalance = 1250.00
    private lateinit var tvBalance: TextView
    private lateinit var etAmount: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        tvBalance = findViewById(R.id.tvTotalBalance)
        etAmount = findViewById(R.id.etAmount)

        updateBalanceDisplay()

        findViewById<Button>(R.id.btnAddMoney).setOnClickListener {
            processTransaction(true)
        }

        findViewById<Button>(R.id.btnWithdrawMoney).setOnClickListener {
            processTransaction(false)
        }
    }

    private fun processTransaction(isAdding: Boolean) {
        val input = etAmount.text.toString()
        if (input.isBlank()) {
            Toast.makeText(this, "Digite um valor!", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = input.toDoubleOrNull() ?: 0.0
        if (isAdding) {
            currentBalance += amount
            Toast.makeText(this, "R$ $amount guardados com sucesso!", Toast.LENGTH_SHORT).show()
        } else {
            if (amount > currentBalance) {
                Toast.makeText(this, "Saldo insuficiente!", Toast.LENGTH_SHORT).show()
                return
            }
            currentBalance -= amount
            Toast.makeText(this, "R$ $amount retirados com sucesso!", Toast.LENGTH_SHORT).show()
        }

        updateBalanceDisplay()
        etAmount.text.clear()
    }

    private fun updateBalanceDisplay() {
        tvBalance.text = "R$ %.2f".format(currentBalance)
    }
}
