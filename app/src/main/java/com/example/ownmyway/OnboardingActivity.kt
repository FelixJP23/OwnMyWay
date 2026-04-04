package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ownmyway.network.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var chipGroupInterests: ChipGroup
    private lateinit var radioGroupBudget: RadioGroup
    private lateinit var btnFinish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        chipGroupInterests = findViewById(R.id.chipGroupInterests)
        radioGroupBudget = findViewById(R.id.radioGroupBudget)
        btnFinish = findViewById(R.id.btnFinishOnboarding)

        btnFinish.setOnClickListener {
            saveOnboardingData()
        }
    }

    private fun saveOnboardingData() {
        val selectedInterests = mutableListOf<String>()
        for (i in 0 until chipGroupInterests.childCount) {
            val chip = chipGroupInterests.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedInterests.add(chip.text.toString())
            }
        }

        val budgetId = radioGroupBudget.checkedRadioButtonId
        val budget = when (budgetId) {
            R.id.radioBudgetLow -> "low"
            R.id.radioBudgetMid -> "medium"
            R.id.radioBudgetHigh -> "high"
            else -> "medium"
        }

        lifecycleScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val profile = UserProfile(
                        id = user.id,
                        onboarding_completed = true,
                        budget_level = budget,
                        interests = selectedInterests
                    )
                    
                    SupabaseClient.client.postgrest["profiles"].update(profile) {
                        filter {
                            eq("id", user.id)
                        }
                    }

                    Toast.makeText(this@OnboardingActivity, "Preferências salvas!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnboardingActivity, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
