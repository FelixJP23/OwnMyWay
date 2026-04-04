package com.example.ownmyway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var profilePhoto: ImageView
    private lateinit var fabAdd: FloatingActionButton

    private var isFirstLocation = true

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profilePhoto = findViewById(R.id.profilePhoto)
        fabAdd = findViewById(R.id.fabAdd)

        // ── Profile photo ──────────────────────────────────────────────────
        // When your database is ready, load the user photo like this:
        //
        // fun loadUserPhoto(photoUrl: String) {
        //     Glide.with(this)
        //         .load(photoUrl)
        //         .circleCrop()
        //         .placeholder(R.drawable.profile_photo_bg)
        //         .into(profilePhoto)
        // }

        fabAdd.setOnClickListener {
            // TODO: implement later
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestLocationPermission()
    }

    // ── Map ready ─────────────────────────────────────────────────────────
    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
        } catch (e: Exception) {
            // Falls back to default style
        }

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isCompassEnabled = false
            isMapToolbarEnabled = false
        }

        enableMyLocation()
    }

    // ── Live location ─────────────────────────────────────────────────────
    private fun startLocationUpdates() {
        // Double-check permission before starting updates
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000L
        ).setMinUpdateIntervalMillis(1500L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)

                if (isFirstLocation) {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, 17f)
                    )
                    isFirstLocation = false
                } else {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLng(latLng)
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, mainLooper
        )
    }

    private fun enableMyLocation() {
        // Explicit permission check — fixes the SecurityException crash
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────
    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            if (::googleMap.isInitialized) enableMyLocation()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        if (::locationCallback.isInitialized &&
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}