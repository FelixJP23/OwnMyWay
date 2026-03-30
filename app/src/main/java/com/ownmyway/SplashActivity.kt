package com.ownmyway

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd

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

        // Hide status bar for full-screen effect
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_splash)

        rootLayout       = findViewById(R.id.rootLayout)
        welcomePhase     = findViewById(R.id.welcomePhase)
        mainPhase        = findViewById(R.id.mainPhase)
        welcomeText      = findViewById(R.id.welcomeText)
        welcomeUnderline = findViewById(R.id.welcomeUnderline)
        logoImage        = findViewById(R.id.logoImage)
        tagline          = findViewById(R.id.tagline)
        btnLogin         = findViewById(R.id.btnLogin)
        btnRegister      = findViewById(R.id.btnRegister)

        startAnimationSequence()

        btnLogin.setOnClickListener {
            // TODO: navigate to LoginActivity
            // startActivity(Intent(this, LoginActivity::class.java))
            // finish()
        }

        btnRegister.setOnClickListener {
            // TODO: navigate to RegisterActivity
            // startActivity(Intent(this, RegisterActivity::class.java))
            // finish()
        }
    }

    // ── Full animation timeline ──────────────────────────────────────────────
    private fun startAnimationSequence() {

        // t=0ms: "Welcome!" fades in
        welcomeText.alpha = 0f
        ObjectAnimator.ofFloat(welcomeText, "alpha", 0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            start()
        }

        // t=1000ms: purple underline sweeps in
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
                duration = 400; start()
            }
        }, 1000)

        // t=1900ms: gentle pulse
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

        // t=2800ms: fade out welcome phase
        handler.postDelayed({ fadeOutWelcome() }, 2800)
    }

    // ── Phase transition: white → purple ────────────────────────────────────
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

    // ── Reveal logo, tagline, buttons ───────────────────────────────────────
    private fun showMainPhase() {
        mainPhase.visibility = View.VISIBLE

        // Logo bounces in
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

        // Tagline slides up
        handler.postDelayed({ slideUp(tagline, 600) }, 650)

        // Buttons slide up staggered
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
