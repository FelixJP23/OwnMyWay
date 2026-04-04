package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.ownmyway.network.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnContinue: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStepIndicator: TextView

    private var selectedInterests = mutableListOf<String>()
    private var selectedBudget = "medium"
    private var selectedPace = "moderate"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPagerOnboarding)
        btnContinue = findViewById(R.id.btnContinue)
        progressBar = findViewById(R.id.onboardingProgress)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)

        val adapter = OnboardingStepsAdapter()
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false 

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })

        btnContinue.setOnClickListener {
            handleNextStep()
        }
    }

    private fun updateUI(position: Int) {
        val totalSteps = 3
        progressBar.max = totalSteps
        progressBar.progress = position + 1
        tvStepIndicator.text = "Passo ${position + 1} de $totalSteps"
        
        btnContinue.text = if (position == totalSteps - 1) "Finalizar e Explorar" else "Continuar"
    }

    private fun handleNextStep() {
        val recyclerView = viewPager.getChildAt(0) as RecyclerView
        val currentView = recyclerView.findViewHolderForAdapterPosition(viewPager.currentItem)?.itemView

        when (viewPager.currentItem) {
            0 -> {
                val chipGroup = currentView?.findViewById<ChipGroup>(R.id.chipGroupInterests)
                selectedInterests.clear()
                chipGroup?.let {
                    for (i in 0 until it.childCount) {
                        val chip = it.getChildAt(i) as Chip
                        if (chip.isChecked) selectedInterests.add(chip.text.toString())
                    }
                }
                if (selectedInterests.isEmpty()) {
                    Toast.makeText(this, "Escolha pelo menos um interesse!", Toast.LENGTH_SHORT).show()
                    return
                }
                viewPager.currentItem = 1
            }
            1 -> {
                val radioGroup = currentView?.findViewById<RadioGroup>(R.id.radioGroupBudget)
                selectedBudget = when (radioGroup?.checkedRadioButtonId) {
                    R.id.radioBudgetLow -> "low"
                    R.id.radioBudgetHigh -> "high"
                    else -> "medium"
                }
                viewPager.currentItem = 2
            }
            2 -> {
                val radioGroup = currentView?.findViewById<RadioGroup>(R.id.radioGroupPace)
                selectedPace = when (radioGroup?.checkedRadioButtonId) {
                    R.id.radioPaceRelaxed -> "relaxed"
                    R.id.radioPaceFast -> "fast"
                    else -> "moderate"
                }
                saveAndFinish()
            }
        }
    }

    private fun saveAndFinish() {
        lifecycleScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    val profile = UserProfile(
                        id = user.id,
                        onboarding_completed = true,
                        budget_level = selectedBudget,
                        travel_pace = selectedPace,
                        interests = selectedInterests
                    )
                    
                    SupabaseClient.client.postgrest["profiles"].update(profile) {
                        filter { eq("id", user.id) }
                    }

                    Toast.makeText(this@OnboardingActivity, "Tudo pronto, vamos nessa!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@OnboardingActivity, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    inner class OnboardingStepsAdapter : RecyclerView.Adapter<OnboardingStepsAdapter.StepViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
            val layoutRes = when(viewType) {
                0 -> R.layout.item_onboarding_interests
                1 -> R.layout.item_onboarding_budget
                else -> R.layout.item_onboarding_pace
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
            return StepViewHolder(view)
        }
        override fun onBindViewHolder(holder: StepViewHolder, position: Int) {}
        override fun getItemViewType(position: Int): Int = position
        override fun getItemCount(): Int = 3
        inner class StepViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
