package pt.isec.amov.tp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import pt.isec.amov.tp.R

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        // --- Configurar FusedLocationProviderClient ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- Configurar LocationCallback ---
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // DEBUG: Log da localização
                    Log.d("LocationService", "New Location: ${location.latitude}, ${location.longitude}")

                    // --- Atualizar Firestore ---
                    updateFirestoreLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    // --- Iniciar Serviço ---
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startLocationUpdates()

        return START_STICKY
    }

    // --- Criar Canal de Notificação e Notificação ---
    private fun createNotificationChannel() {
        val channelId = "location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }


        // --- Criar a notificação [PERSISTENTE] ---
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title_protected))
            .setContentText(getString(R.string.notif_content_tracking))
            .setSmallIcon(R.drawable.ic_logo_safetysec)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    // --- Iniciar Atualizações de Localização ---
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000
        ).apply {
            setMinUpdateIntervalMillis(5000)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission Lost: $e")
            stopSelf()
        }
    }

    // --- Atualizar Localização na Firestore ---
    private fun updateFirestoreLocation(lat: Double, lng: Double) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val updates = mapOf(
            "location" to GeoPoint(lat, lng),
            "lastUpdate" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(user.uid).update(updates)
            .addOnFailureListener { e ->
                // DEBUG: Log de erro
                Log.e("LocationService", "Error updating Firebase : $e")
            }
    }

    // --- Parar Serviço ---
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // --- Binder não utilizado ---
    override fun onBind(intent: Intent?): IBinder? = null
}