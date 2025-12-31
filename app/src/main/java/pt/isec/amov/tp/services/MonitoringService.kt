package pt.isec.amov.tp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.google.firebase.firestore.auth.User
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.ui.screens.AlertActivity
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

class MonitoringService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var activeRules: List<SafetyRule> = emptyList()
    private var rulesListener: ListenerRegistration? = null
    private var lastMovementTime: Long = System.currentTimeMillis()
    private var lastSpeedKmh = 0.0

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                if (magnitude > 25f) {
                    val fallRule = activeRules.find { it.type == RuleType.FALL_DETECTION }
                    if (fallRule != null && fallRule.shouldCheckNow()) {
                        triggerAlertProcess(fallRule)
                    }
                }

                if (abs(magnitude - 9.81f) > 0.5f) {
                    lastMovementTime = System.currentTimeMillis()
                } else {
                    val inactivityRule = activeRules.find { it.type == RuleType.INACTIVITY }
                    if (inactivityRule != null && inactivityRule.shouldCheckNow()) {
                        val maxMinutes = (inactivityRule.params["duration"] as? Number)?.toLong() ?: 15L
                        if ((System.currentTimeMillis() - lastMovementTime) > (maxMinutes * 60000)) {
                            triggerAlertProcess(inactivityRule)
                            lastMovementTime = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                updateFirestoreLocation(location.latitude, location.longitude)
                checkSecurityRules(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startListeningToRules()
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
                    Log.d("MonitoringService", getString(R.string.log_rules_updated, activeRules.size))
                }
            }
    }

    private fun checkSecurityRules(location: Location) {
        // 1. Convertir la velocidad actual de m/s a km/h para las comparaciones
        val currentSpeedKmh = location.speed * 3.6

        // 2. Calcular la caída de velocidad (deceleración brusca)
        val speedDrop = lastSpeedKmh - currentSpeedKmh
        for (rule in activeRules) {
            // Solo procesar si la regla está autorizada y dentro de la ventana temporal [cite: 22, 25]
            if (!rule.shouldCheckNow()) continue
            when (rule.type) {
                RuleType.SPEED_LIMIT -> {
                    // Regla: Control de velocidad excesiva
                    val maxSpeed = (rule.params["max_speed"] as? Number)?.toDouble() ?: 120.0
                    if (currentSpeedKmh > maxSpeed) {
                        triggerAlertProcess(rule)
                    }
                }
                RuleType.CAR_ACCIDENT -> {
                    // Regla: Detección de accidentes de carretera por deceleración
                    // Se dispara si la velocidad baja más de 25 km/h en un solo intervalo (aprox. 2s)
                    // y el usuario circulaba previamente a más de 30 km/h
                    if (speedDrop > 25.0 && lastSpeedKmh > 30.0) {
                        triggerAlertProcess(rule)
                    }
                }

                RuleType.GEOFENCING -> {
                    // Regla: El usuario está fuera del área definida
                    checkGeofence(rule, location)
                }

                else -> { /* Otras reglas como FALL o INACTIVITY se manejan por sensores  */ }
            }
        }
        lastSpeedKmh = currentSpeedKmh
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
        Log.w("MonitoringService", getString(R.string.log_rule_broken, rule.name, rule.type.name))

        val intent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("RULE_ID", rule.id)
            putExtra("RULE_TYPE", rule.type.name)
        }
        startActivity(intent)
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
        sensorManager.unregisterListener(sensorListener)
        rulesListener?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}