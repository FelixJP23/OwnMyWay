package com.example.ownmyway

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

object OfflineMapManager {

    private const val TILE_SIZE    = 256
    private const val ZOOM         = 14
    private const val ROUTE_FOLDER = "offline_routes"
    private val client             = OkHttpClient()

    data class DownloadProgress(val current: Int, val total: Int, val done: Boolean = false)

    suspend fun downloadRouteMap(
        context: Context,
        stops: List<NearbyPlace>,
        routeId: String,
        onProgress: suspend (DownloadProgress) -> Unit   // ← now suspend
    ): File? = withContext(Dispatchers.IO) {

        if (stops.isEmpty()) return@withContext null

        val bounds = buildBounds(stops)
        val (xMin, xMax, yMin, yMax) = getTileRange(bounds, ZOOM)

        val totalTiles = (xMax - xMin + 1) * (yMax - yMin + 1)

        var downloaded = 0
        val tileMap = mutableMapOf<Pair<Int, Int>, Bitmap>()

        for (x in xMin..xMax) {
            for (y in yMin..yMax) {
                val bitmap = fetchTile(x, y, ZOOM)
                if (bitmap != null) tileMap[x to y] = bitmap
                downloaded++
                // ── Switch to Main before touching any UI ──────────────────
                withContext(Dispatchers.Main) {
                    onProgress(DownloadProgress(downloaded, totalTiles))
                }
            }
        }

        val cols   = xMax - xMin + 1
        val rows   = yMax - yMin + 1
        val result = Bitmap.createBitmap(cols * TILE_SIZE, rows * TILE_SIZE, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        canvas.drawColor(Color.parseColor("#242f3e"))

        tileMap.forEach { (pos, tile) ->
            val (tx, ty) = pos
            canvas.drawBitmap(tile, ((tx - xMin) * TILE_SIZE).toFloat(), ((ty - yMin) * TILE_SIZE).toFloat(), null)
            tile.recycle()
        }

        drawStopPins(canvas, stops, xMin, yMin, ZOOM)

        val folder = File(context.filesDir, ROUTE_FOLDER).also { it.mkdirs() }
        val file   = File(folder, "route_$routeId.png")
        FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.PNG, 90, it) }
        result.recycle()

        // ── Final "done" callback also on Main ─────────────────────────────
        withContext(Dispatchers.Main) {
            onProgress(DownloadProgress(totalTiles, totalTiles, done = true))
        }

        Log.d("OfflineMap", "Saved to ${file.absolutePath}")
        file
    }

    fun getOfflineMap(context: Context, routeId: String): File? {
        val file = File(context.filesDir, "$ROUTE_FOLDER/route_$routeId.png")
        return if (file.exists()) file else null
    }

    fun deleteOfflineMap(context: Context, routeId: String) {
        File(context.filesDir, "$ROUTE_FOLDER/route_$routeId.png").delete()
    }

    fun listSavedRoutes(context: Context): List<String> {
        val folder = File(context.filesDir, ROUTE_FOLDER)
        return folder.listFiles()
            ?.filter { it.name.startsWith("route_") && it.name.endsWith(".png") }
            ?.map { it.name.removePrefix("route_").removeSuffix(".png") }
            ?: emptyList()
    }

    private fun buildBounds(stops: List<NearbyPlace>): LatLngBounds {
        val builder = LatLngBounds.builder()
        stops.forEach { builder.include(LatLng(it.geometry.location.lat, it.geometry.location.lng)) }
        return builder.build()
    }

    private data class TileRange(val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int)

    private fun getTileRange(bounds: LatLngBounds, zoom: Int): TileRange {
        val pad  = 0.008
        val xMin = lonToTile(bounds.southwest.longitude - pad, zoom)
        val xMax = lonToTile(bounds.northeast.longitude + pad, zoom)
        val yMin = latToTile(bounds.northeast.latitude  + pad, zoom)
        val yMax = latToTile(bounds.southwest.latitude  - pad, zoom)
        return TileRange(xMin, xMax, yMin, yMax)
    }

    private fun lonToTile(lon: Double, zoom: Int): Int =
        floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    private fun latToTile(lat: Double, zoom: Int): Int {
        val rad = Math.toRadians(lat)
        return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun latLngToPixel(lat: Double, lon: Double, xMin: Int, yMin: Int, zoom: Int): Pair<Float, Float> {
        val xTile  = (lon + 180.0) / 360.0 * (1 shl zoom)
        val radLat = Math.toRadians(lat)
        val yTile  = (1.0 - ln(tan(radLat) + 1.0 / cos(radLat)) / PI) / 2.0 * (1 shl zoom)
        return ((xTile - xMin) * TILE_SIZE).toFloat() to ((yTile - yMin) * TILE_SIZE).toFloat()
    }

    private fun fetchTile(x: Int, y: Int, zoom: Int): Bitmap? {
        val server = listOf("a", "b", "c")[(x + y) % 3]
        val url    = "https://$server.tile.openstreetmap.org/$zoom/$x/$y.png"
        return try {
            val bytes = client.newCall(
                Request.Builder().url(url).addHeader("User-Agent", "OwnMyWayApp/1.0").build()
            ).execute().body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("OfflineMap", "Tile fetch error $x/$y: ${e.message}")
            null
        }
    }

    private fun drawStopPins(canvas: Canvas, stops: List<NearbyPlace>, xMin: Int, yMin: Int, zoom: Int) {
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4A2080"); style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
        val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        stops.forEachIndexed { idx, stop ->
            val (px, py) = latLngToPixel(stop.geometry.location.lat, stop.geometry.location.lng, xMin, yMin, zoom)
            canvas.drawCircle(px, py, 24f, circlePaint)
            canvas.drawCircle(px, py, 24f, borderPaint)
            canvas.drawText("${idx + 1}", px, py + 10f, textPaint)
        }
    }
}