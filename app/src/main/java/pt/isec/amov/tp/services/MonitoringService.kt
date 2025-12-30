package pt.isec.amov.tp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.BatteryManager
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
import com.google.firebase.firestore.ListenerRegistration
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.model.SafetyRule

class MonitoringService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var activeRules: List<SafetyRule> = emptyList()
    private var rulesListener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startListeningToRules()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateFirestoreLocation(location.latitude, location.longitude)

                    checkSecurityRules(location)
                }
            }
        }
    }

    private fun startListeningToRules() {
        val userId = auth.currentUser?.uid ?: return

        rulesListener = db.collection("users").document(userId)
            .collection("safety_rules")
            .whereEqualTo("authorized", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MonitoringService", "Erro ao ler regras: $e")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    activeRules = snapshot.toObjects(SafetyRule::class.java)
                    Log.d("MonitoringService", "Regras atualizadas: ${activeRules.size} ativas")
                }
            }
    }

    private fun checkSecurityRules(location: Location) {
        for (rule in activeRules) {

            if (!rule.shouldCheckNow()) continue

            when (rule.type) {
                RuleType.GEOFENCING -> checkGeofence(rule, location)
                RuleType.SPEED_LIMIT -> checkSpeed(rule, location)
                else -> {}
            }
        }
    }

    private fun checkGeofence(rule: SafetyRule, currentLocation: Location) {
        val centerLat = (rule.params["lat"] as? Number)?.toDouble() ?: return
        val centerLng = (rule.params["lng"] as? Number)?.toDouble() ?: return
        val radius = (rule.params["radius"] as? Number)?.toDouble() ?: return

        val centerLoc = Location("provider").apply {
            latitude = centerLat
            longitude = centerLng
        }
        val distanceInMeters = currentLocation.distanceTo(centerLoc)

        if (distanceInMeters > radius) {
            triggerAlertProcess(rule)
        }
    }

    private fun checkSpeed(rule: SafetyRule, currentLocation: Location) {
        val maxSpeedKmh = (rule.params["max_speed"] as? Number)?.toDouble() ?: return

        val currentSpeedKmh = currentLocation.speed * 3.6

        if (currentSpeedKmh > maxSpeedKmh) {
            triggerAlertProcess(rule)
        }
    }

    private fun checkBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun triggerAlertProcess(rule: SafetyRule) {

        Log.w("MonitoringService", "REGRA QUEBRADA: ${rule.name} (Tipo: ${rule.type})")

        // Vamos lançar a Activity de Contagem Decrescente
        //TODO
        /*
        val intent = Intent(this, AlertCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK // Necessário porque estamos num Serviço
            putExtra("RULE_ID", rule.id)
            putExtra("RULE_TYPE", rule.type.name)
        }
        startActivity(intent)
        */
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startLocationUpdates()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channelId = "monitoring_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title_protected))
            .setContentText(getString(R.string.notif_content_tracking))
            .setSmallIcon(R.drawable.ic_logo_safetysec)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateIntervalMillis(2000)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun updateFirestoreLocation(lat: Double, lng: Double) {
        val batteryLevel = checkBatteryLevel()
        val user = auth.currentUser ?: return
        val updates = mapOf(
            "location" to GeoPoint(lat, lng),
            "batteryLevel" to batteryLevel,
            "lastUpdate" to com.google.firebase.Timestamp.now()
        )
        db.collection("users").document(user.uid).update(updates)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        rulesListener?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}