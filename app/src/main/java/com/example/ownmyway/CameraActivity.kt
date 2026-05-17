package com.example.ownmyway

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ownmyway.network.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: View
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val geminiService: GeminiApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(GeminiApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)

        btnBack.setOnClickListener { finish() }
        btnCapture.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        btnCapture.isEnabled = false
        progressBar.visibility = View.VISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    val base64 = bitmapToBase64(bitmap)
                    analyzeWithGemini(base64)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "Capture failed", exception)
                    progressBar.visibility = View.GONE
                    btnCapture.isEnabled = true
                    Toast.makeText(this@CameraActivity, "Falha ao capturar", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun analyzeWithGemini(base64Image: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val prompt = """
            Você é um assistente guia de viagem. Analise esta imagem e identifique o que você vê.
            Responda APENAS com um objeto JSON válido neste formato exato, sem markdown, sem texto extra.
            Todo o conteúdo dos campos deve estar em português brasileiro:
            {"name":"nome do lugar ou objeto","description":"descrição de 2-3 frases para turistas","fact1":"curiosidade turística interessante","fact2":"outra curiosidade interessante","category":"Monumento / Natureza / Comida / Museu / etc"}
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inline_data = GeminiInlineData(
                                mime_type = "image/jpeg",
                                data = base64Image
                            )
                        )
                    )
                )
            )
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = geminiService.analyzeImage(apiKey, request)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnCapture.isEnabled = true
                    if (response.isSuccessful) {
                        val text = response.body()
                            ?.candidates?.firstOrNull()
                            ?.content?.parts?.firstOrNull()
                            ?.text ?: ""
                        Log.d("Gemini", "Response: $text")
                        parseAndShowResult(text)
                    } else {
                        Log.e("Gemini", "Error: ${response.code()} ${response.errorBody()?.string()}")
                        Toast.makeText(
                            this@CameraActivity,
                            "Falha na análise: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnCapture.isEnabled = true
                    Log.e("Gemini", "Exception", e)
                    Toast.makeText(this@CameraActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseAndShowResult(jsonText: String) {
        try {
            val clean = jsonText
                .replace("```json", "")
                .replace("```", "")
                .trim()
            val result = Gson().fromJson(clean, GeminiResult::class.java)
            GeminiResultBottomSheet.newInstance(result)
                .show(supportFragmentManager, "gemini_result")
        } catch (e: Exception) {
            Log.e("Gemini", "Parse error: $jsonText", e)
            Toast.makeText(this, "Não foi possível interpretar o resultado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
