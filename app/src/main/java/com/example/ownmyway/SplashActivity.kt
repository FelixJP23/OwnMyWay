package com.example.ownmyway

import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var rootLayout: FrameLayout

    private lateinit var introPhase: LinearLayout
    private lateinit var videoTexture: TextureView
    private lateinit var videoCover: View

    private lateinit var mainPhase: LinearLayout
    private lateinit var topBannerImage: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var btnRegister: View
    private lateinit var btnLogin: View

    private val handler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null

    private var introFinished = false
    private var videoPrepared = false
    private var videoRevealed = false
    private var introStarted = false

    private var authResolved = false
    private var shouldGoToMainAfterIntro = false
    private var pendingPostIntroNavigation = false

    private var skipAnimation = false
    private var forceGoMainAfterIntro = false

    companion object {
        private const val VIDEO_PREPARE_TIMEOUT_MS = 3500L
        private const val VIDEO_REVEAL_DELAY_MS = 120L
        private const val VIDEO_FINISH_FALLBACK_MS = 9000L
        private const val INTRO_FADE_OUT_MS = 420L
    }

    private val prepareTimeoutRunnable = Runnable {
        if (!introFinished && !videoPrepared) {
            fallbackFromIntro()
        }
    }

    private val revealRunnable = Runnable {
        if (!introFinished && !videoRevealed) {
            revealVideo()
        }
    }

    private val finishFallbackRunnable = Runnable {
        if (!introFinished) {
            fadeOutIntroAndContinue()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT

        rootLayout = findViewById(R.id.rootLayout)

        introPhase = findViewById(R.id.introPhase)
        videoTexture = findViewById(R.id.videoTexture)
        videoCover = findViewById(R.id.videoCover)

        mainPhase = findViewById(R.id.mainPhase)
        topBannerImage = findViewById(R.id.topBannerImage)
        logoImage = findViewById(R.id.logoImage)
        btnRegister = findViewById(R.id.btnRegister)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener { goToLogin() }
        btnRegister.setOnClickListener { goToRegister() }

        skipAnimation = intent.getBooleanExtra("SKIP_ANIMATION", false)
        forceGoMainAfterIntro = intent.getBooleanExtra("FORCE_GO_MAIN", false)
        shouldGoToMainAfterIntro = forceGoMainAfterIntro

        observeSessionState()

        if (!skipAnimation) {
            startIntroSequence()
        }
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            try {
                val finalStatus = SupabaseClient.client.auth.sessionStatus
                    .filter { it !is SessionStatus.Initializing }
                    .first()

                if (isDestroyed || isFinishing) return@launch

                shouldGoToMainAfterIntro =
                    shouldGoToMainAfterIntro || (finalStatus is SessionStatus.Authenticated)

                Log.d(
                    "SplashActivity",
                    if (finalStatus is SessionStatus.Authenticated)
                        "Sessão válida encontrada."
                    else
                        "Nenhuma sessão válida encontrada."
                )
            } catch (e: Exception) {
                Log.e("SplashActivity", "Erro ao verificar sessão: ${e.message}", e)
            } finally {
                authResolved = true

                if (skipAnimation) {
                    if (shouldGoToMainAfterIntro) {
                        goToMain()
                    } else {
                        showMainPhaseImmediately()
                    }
                } else if (pendingPostIntroNavigation) {
                    continueAfterIntro()
                }
            }
        }
    }

    private fun startIntroSequence() {
        if (introStarted) return
        introStarted = true

        introFinished = false
        videoPrepared = false
        videoRevealed = false

        rootLayout.setBackgroundColor(Color.WHITE)

        introPhase.visibility = View.VISIBLE
        introPhase.alpha = 1f

        mainPhase.visibility = View.GONE

        videoCover.visibility = View.VISIBLE
        videoCover.alpha = 1f

        videoTexture.alpha = 1f
        videoTexture.surfaceTextureListener = this

        handler.postDelayed(prepareTimeoutRunnable, VIDEO_PREPARE_TIMEOUT_MS)
    }

    private fun startVideo(surfaceTexture: SurfaceTexture) {
        val videoResId = resources.getIdentifier("own_my_way_intro", "raw", packageName)
        if (videoResId == 0) {
            fallbackFromIntro()
            return
        }

        releasePlayer()

        videoSurface = Surface(surfaceTexture)

        val uri = Uri.parse("android.resource://$packageName/$videoResId")

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@SplashActivity, uri)
            setSurface(videoSurface)
            isLooping = false
            setVolume(0f, 0f)

            setOnPreparedListener {
                videoPrepared = true
                handler.removeCallbacks(prepareTimeoutRunnable)

                start()

                handler.postDelayed(revealRunnable, VIDEO_REVEAL_DELAY_MS)
                handler.postDelayed(finishFallbackRunnable, VIDEO_FINISH_FALLBACK_MS)
            }

            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    revealVideo()
                }
                false
            }

            setOnCompletionListener {
                fadeOutIntroAndContinue()
            }

            setOnErrorListener { _, _, _ ->
                fallbackFromIntro()
                true
            }

            prepareAsync()
        }
    }

    private fun revealVideo() {
        if (videoRevealed || introFinished) return
        videoRevealed = true

        videoCover.animate()
            .alpha(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                videoCover.visibility = View.GONE
            }
            .start()
    }

    private fun fadeOutIntroAndContinue() {
        if (introFinished) return
        introFinished = true

        handler.removeCallbacks(prepareTimeoutRunnable)
        handler.removeCallbacks(revealRunnable)
        handler.removeCallbacks(finishFallbackRunnable)

        introPhase.animate()
            .alpha(0f)
            .setDuration(INTRO_FADE_OUT_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                releasePlayer()
                introPhase.visibility = View.GONE

                if (authResolved) {
                    continueAfterIntro()
                } else {
                    pendingPostIntroNavigation = true
                }
            }
            .start()
    }

    private fun fallbackFromIntro() {
        if (introFinished) return
        introFinished = true

        handler.removeCallbacks(prepareTimeoutRunnable)
        handler.removeCallbacks(revealRunnable)
        handler.removeCallbacks(finishFallbackRunnable)

        releasePlayer()
        introPhase.visibility = View.GONE
        rootLayout.setBackgroundColor(Color.WHITE)

        if (authResolved) {
            continueAfterIntro()
        } else {
            pendingPostIntroNavigation = true
        }
    }

    private fun continueAfterIntro() {
        pendingPostIntroNavigation = false

        if (shouldGoToMainAfterIntro) {
            goToMain()
        } else {
            showMainPhase()
        }
    }

    private fun goToMain() {
        releasePlayer()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
        finish()
    }

    private fun showMainPhaseImmediately() {
        releasePlayer()

        rootLayout.setBackgroundColor(Color.WHITE)
        introPhase.visibility = View.GONE
        mainPhase.visibility = View.VISIBLE

        topBannerImage.alpha = 1f
        topBannerImage.translationY = 0f
        topBannerImage.scaleX = 1f
        topBannerImage.scaleY = 1f

        logoImage.alpha = 1f
        logoImage.translationY = 0f
        logoImage.scaleX = 1f
        logoImage.scaleY = 1f

        btnRegister.alpha = 1f
        btnRegister.translationY = 0f

        btnLogin.alpha = 1f
        btnLogin.translationY = 0f
    }

    private fun showMainPhase() {
        if (mainPhase.visibility == View.VISIBLE) return

        releasePlayer()
        handler.removeCallbacks(prepareTimeoutRunnable)
        handler.removeCallbacks(revealRunnable)
        handler.removeCallbacks(finishFallbackRunnable)

        rootLayout.setBackgroundColor(Color.WHITE)

        prepareMainViews()

        mainPhase.visibility = View.VISIBLE
        mainPhase.alpha = 1f

        animateMainViews()
    }

    private fun prepareMainViews() {
        topBannerImage.alpha = 0f
        topBannerImage.translationY = 64f
        topBannerImage.scaleX = 0.985f
        topBannerImage.scaleY = 0.985f

        logoImage.alpha = 0f
        logoImage.translationY = 42f
        logoImage.scaleX = 0.94f
        logoImage.scaleY = 0.94f

        btnRegister.alpha = 0f
        btnRegister.translationY = 38f

        btnLogin.alpha = 0f
        btnLogin.translationY = 26f
    }

    private fun animateMainViews() {
        topBannerImage.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(140L)
            .setDuration(1050L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        logoImage.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(340L)
            .setDuration(900L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        btnRegister.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(620L)
            .setDuration(820L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        btnLogin.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(800L)
            .setDuration(760L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.reset()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null

        try {
            videoSurface?.release()
        } catch (_: Exception) {
        }

        videoSurface = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startVideo(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDestroy()
    }
}