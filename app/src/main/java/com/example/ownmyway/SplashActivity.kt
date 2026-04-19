package com.example.ownmyway

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import android.util.Log
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var welcomePhase: LinearLayout
    private lateinit var mainPhase: LinearLayout
    private lateinit var welcomeText: TextView
    private lateinit var welcomeUnderline: View
    private lateinit var logoImage: ImageView
    private lateinit var tagline: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Configuração de UI
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        window.statusBarColor = Color.TRANSPARENT

        // 1. Inicialização das Views
        rootLayout = findViewById(R.id.rootLayout)
        welcomePhase = findViewById(R.id.welcomePhase)
        mainPhase = findViewById(R.id.mainPhase)
        welcomeText = findViewById(R.id.welcomeText)
        welcomeUnderline = findViewById(R.id.welcomeUnderline)
        logoImage = findViewById(R.id.logoImage)
        tagline = findViewById(R.id.tagline)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)

        // 2. Lógica de "Manter Conectado" com Aguardo de Reidratação
        lifecycleScope.launch {
            try {
                // Aguarda até que o status não seja mais 'Initializing'
                // Isso garante que o Supabase tentou ler o token do celular
                val finalStatus = SupabaseClient.client.auth.sessionStatus
                    .filter { it !is SessionStatus.Initializing }
                    .first()

                if (finalStatus is SessionStatus.Authenticated) {
                    Log.d("SplashActivity", "Sessão recuperada com sucesso!")
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                } else {
                    Log.d("SplashActivity", "Nenhuma sessão válida. Iniciando animações.")
                    handleNavigationFlow()
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "Erro ao verificar sessão: ${e.message}")
                handleNavigationFlow()
            }
        }

        // 3. Configuração dos Cliques
        btnLogin.setOnClickListener { goToLogin() }
        btnRegister.setOnClickListener { goToRegister() }
    }

    private fun handleNavigationFlow() {
        val skipAnim = intent.getBooleanExtra("SKIP_ANIMATION", false)
        if (skipAnim) {
            showButtonsImmediately()
        } else {
            startAnimationSequence()
        }
    }

    // --- MÉTODOS DE APOIO E ANIMAÇÕES ---

    private fun showButtonsImmediately() {
        welcomePhase.visibility = View.GONE
        mainPhase.visibility = View.VISIBLE
        rootLayout.setBackgroundResource(R.drawable.bg_purple_gradient)
        logoImage.alpha = 1f
        logoImage.scaleX = 1f
        logoImage.scaleY = 1f
        tagline.alpha = 1f
        tagline.translationY = 0f
        btnLogin.alpha = 1f
        btnLogin.translationY = 0f
        btnRegister.alpha = 1f
        btnRegister.translationY = 0f
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
        finish()
    }

    private fun startAnimationSequence() {
        welcomeText.alpha = 0f
        ObjectAnimator.ofFloat(welcomeText, "alpha", 0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            start()
        }

        handler.postDelayed({
            welcomeUnderline.visibility = View.VISIBLE
            val params = welcomeUnderline.layoutParams
            val targetWidth = welcomeText.width
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    params.width = it.animatedValue as Int
                    welcomeUnderline.layoutParams = params
                }
                start()
            }
            ObjectAnimator.ofFloat(welcomeUnderline, "alpha", 0f, 1f).apply {
                duration = 400
                start()
            }
        }, 1000)

        handler.postDelayed({
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(welcomeText, "scaleX", 1f, 1.06f, 1f),
                    ObjectAnimator.ofFloat(welcomeText, "scaleY", 1f, 1.06f, 1f)
                )
                duration = 900
                start()
            }
        }, 1900)

        handler.postDelayed({ fadeOutWelcome() }, 2800)
    }

    private fun fadeOutWelcome() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(welcomeText, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(welcomeUnderline, "alpha", 1f, 0f)
            )
            duration = 600
            doOnEnd {
                welcomePhase.visibility = View.GONE
                animateBackgroundToPurple()
            }
            start()
        }
    }

    private fun animateBackgroundToPurple() {
        ValueAnimator.ofArgb(
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#2D1060")
        ).apply {
            duration = 850
            interpolator = DecelerateInterpolator()
            addUpdateListener { rootLayout.setBackgroundColor(it.animatedValue as Int) }
            doOnEnd {
                rootLayout.setBackgroundResource(R.drawable.bg_purple_gradient)
                showMainPhase()
            }
            start()
        }
    }

    private fun showMainPhase() {
        mainPhase.visibility = View.VISIBLE
        handler.postDelayed({
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(logoImage, "scaleX", 0.3f, 1f),
                    ObjectAnimator.ofFloat(logoImage, "scaleY", 0.3f, 1f),
                    ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f)
                )
                duration = 800
                interpolator = OvershootInterpolator(1.3f)
                start()
            }
        }, 100)
        handler.postDelayed({ slideUp(tagline, 600) }, 650)
        handler.postDelayed({ slideUp(btnLogin, 650) }, 950)
        handler.postDelayed({ slideUp(btnRegister, 650) }, 1120)
    }

    private fun slideUp(view: View, duration: Long) {
        view.translationY = 80f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "translationY", 80f, 0f),
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            )
            this.duration = duration
            interpolator = DecelerateInterpolator(1.8f)
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}