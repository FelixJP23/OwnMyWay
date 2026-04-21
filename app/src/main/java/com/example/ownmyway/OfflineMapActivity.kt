package com.example.ownmyway

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

class OfflineMapActivity : AppCompatActivity() {

    private lateinit var ivMap: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var layoutProgress: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton

    // Pinch-zoom & pan
    private val matrix      = Matrix()
    private val savedMatrix = Matrix()
    private var scaleDetector: ScaleGestureDetector? = null
    private var mode        = NONE
    private val startPoint  = android.graphics.PointF()
    private val midPoint    = android.graphics.PointF()
    private var oldDist     = 1f
    private var currentScale = 1f
    private val minScale    = 0.5f
    private val maxScale    = 5f

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
        const val EXTRA_ROUTE_ID   = "route_id"
        const val EXTRA_STOPS_JSON = "stops_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        ivMap          = findViewById(R.id.ivOfflineMap)
        tvStatus       = findViewById(R.id.tvOfflineStatus)
        layoutProgress = findViewById(R.id.layoutProgress)
        progressBar    = findViewById(R.id.progressOffline)
        tvProgress     = findViewById(R.id.tvOfflineProgress)
        btnClose       = findViewById(R.id.btnCloseOffline)
        btnZoomIn      = findViewById(R.id.btnZoomIn)
        btnZoomOut     = findViewById(R.id.btnZoomOut)

        btnClose.setOnClickListener { finish() }
        btnZoomIn.setOnClickListener  { zoomBy(1.3f) }
        btnZoomOut.setOnClickListener { zoomBy(0.7f) }

        val routeId   = intent.getStringExtra(EXTRA_ROUTE_ID)   ?: "route"
        val stopsJson = intent.getStringExtra(EXTRA_STOPS_JSON) ?: "[]"
        val stops     = parseStops(stopsJson)

        setupZoomPan()

        val existing = OfflineMapManager.getOfflineMap(this, routeId)
        if (existing != null) {
            showMap(existing)
        } else {
            downloadMap(routeId, stops)
        }
    }

    // ── Download ──────────────────────────────────────────────────────────
    private fun downloadMap(routeId: String, stops: List<NearbyPlace>) {
        tvStatus.text          = "Downloading offline map..."
        tvStatus.visibility    = View.VISIBLE
        layoutProgress.visibility = View.VISIBLE  // show card

        CoroutineScope(Dispatchers.Main).launch {
            val file = OfflineMapManager.downloadRouteMap(
                context    = this@OfflineMapActivity,
                stops      = stops,
                routeId    = routeId,
                onProgress = { p ->
                    progressBar.max      = p.total
                    progressBar.progress = p.current
                    tvProgress.text      = "${p.current}/${p.total} tiles"
                }
            )

            layoutProgress.visibility = View.GONE   // hide card when done
            tvStatus.visibility        = View.GONE

            if (file != null) {
                showMap(file)
                saveToGallery(file, routeId)
            } else {
                Toast.makeText(
                    this@OfflineMapActivity,
                    "❌ Download failed. Check your connection.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Save to gallery (MediaStore, works on all Android versions) ───────
    private fun saveToGallery(file: File, routeId: String) {
        try {
            val fileName  = "OwnMyWay_Route_$routeId.png"
            val mimeType  = "image/png"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OwnMyWay")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream: InputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }

                Toast.makeText(
                    this,
                    "✅ Map saved to Gallery → Pictures/OwnMyWay",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMap", "Gallery save error", e)
            Toast.makeText(
                this,
                "Map downloaded but could not save to gallery.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Show map ──────────────────────────────────────────────────────────
    private fun showMap(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ivMap.setImageBitmap(bitmap)
        ivMap.imageMatrix = matrix
    }

    // ── Pinch-zoom & pan ──────────────────────────────────────────────────
    private fun setupZoomPan() {
        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor   = detector.scaleFactor
                    val newScale = currentScale * factor
                    if (newScale in minScale..maxScale) {
                        currentScale = newScale
                        matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                        ivMap.imageMatrix = matrix
                    }
                    return true
                }
            })

        ivMap.setOnTouchListener { _, event ->
            scaleDetector?.onTouchEvent(event)
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    startPoint.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(midPoint, event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG && scaleDetector?.isInProgress != true) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - startPoint.x, event.y - startPoint.y)
                        ivMap.imageMatrix = matrix
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> mode = NONE
            }
            true
        }
    }

    private fun zoomBy(factor: Float) {
        val newScale = currentScale * factor
        if (newScale in minScale..maxScale) {
            currentScale = newScale
            matrix.postScale(factor, factor, ivMap.width / 2f, ivMap.height / 2f)
            ivMap.imageMatrix = matrix
        }
    }

    private fun spacing(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun midPoint(point: android.graphics.PointF, event: MotionEvent) {
        point.set(
            (event.getX(0) + event.getX(1)) / 2,
            (event.getY(0) + event.getY(1)) / 2
        )
    }

    // ── Parse stops JSON ──────────────────────────────────────────────────
    private fun parseStops(json: String): List<NearbyPlace> {
        val result = mutableListOf<NearbyPlace>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = org.json.JSONObject(arr.getString(i))
                result.add(NearbyPlace(
                    place_id = obj.getString("place_id"),
                    name     = obj.getString("name"),
                    geometry = PlaceGeometry(PlaceLocation(
                        obj.getDouble("lat"), obj.getDouble("lng")
                    )),
                    vicinity = obj.optString("vicinity")
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMap", "Parse stops error", e)
        }
        return result
    }
}