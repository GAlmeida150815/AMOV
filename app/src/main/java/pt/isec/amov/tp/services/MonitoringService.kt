package pt.isec.amov.tp.services

import android.app.*
import android.content.*
import android.hardware.*
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.ui.screens.AlertActivity
import kotlin.math.*

class MonitoringService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var activeRules: List<SafetyRule> = emptyList()
    private var rulesListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val lastAlertTime = mutableMapOf<String, Long>()
    private var lastSpeedKmh = 0.0
    private var lastMovementLocation: Location? = null
    private var lastMovementTimestamp: Long = System.currentTimeMillis()
    private var currentUserId: String? = null
    private var serviceStartTime: Long = 0
    private val WARM_UP_PERIOD = 10000L
    private var gpsUpdateCount = 0
    private val MIN_GPS_FIXES = 3

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (System.currentTimeMillis() - serviceStartTime < WARM_UP_PERIOD) return
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val magnitude = sqrt((event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)).toDouble()).toFloat()

                if (magnitude > 18f) {
                    activeRules.find { it.type == RuleType.FALL_DETECTION }?.let {
                        if (it.shouldCheckNow()) triggerAlertProcess(it)
                    }
                }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun triggerAlertProcess(rule: SafetyRule) {
        val userId = currentUserId ?: return

        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            val isProtected = document.getBoolean("protected") ?: false

            if (isProtected) {
                val now = System.currentTimeMillis()
                val lastTime = lastAlertTime[rule.type.name] ?: 0L

                // Cooldown para evitar spam de alertas (60 segundos)
                if (now - lastTime < 60000) return@addOnSuccessListener
                lastAlertTime[rule.type.name] = now

                Log.w("MonitoringService", "Situación de alerta detectada: ${rule.type.name}")

                val intent = Intent(this, AlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("RULE_TYPE", rule.type.name)
                    putExtra("RULE_ID", rule.id)
                }
                startActivity(intent)
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            res.lastLocation?.let { location ->
                updateFirestoreLocation(location.latitude, location.longitude)
                checkSecurityRules(location)
            }
        }
    }

    private fun checkSecurityRules(location: Location) {
        gpsUpdateCount++
        if (gpsUpdateCount <= MIN_GPS_FIXES) {
            lastSpeedKmh = location.speed * 3.6
            lastMovementLocation = location
            lastMovementTimestamp = System.currentTimeMillis()
            return
        }
        val currentSpeedKmh = location.speed * 3.6

        activeRules.forEach { rule ->
            if (!rule.shouldCheckNow()) return@forEach

            when (rule.type) {
                // 1. CONTROL DE VELOCIDAD
                RuleType.SPEED_LIMIT -> {
                    val max = (rule.params["max_speed"] as? Number)?.toDouble() ?: 120.0
                    if (currentSpeedKmh > max) triggerAlertProcess(rule)
                }

                // 2. GEOFENCING (SALIDA DE PERÍMETRO)
                RuleType.GEOFENCING -> {
                    val centerLat = (rule.params["lat"] as? Number)?.toDouble() ?: 0.0
                    val centerLng = (rule.params["lng"] as? Number)?.toDouble() ?: 0.0
                    val radius = (rule.params["radius"] as? Number)?.toDouble() ?: 100.0

                    val results = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, centerLat, centerLng, results)
                    val distanceInMeters = results[0]

                    if (distanceInMeters > radius) {
                        triggerAlertProcess(rule)
                    }
                }

                // 3. ACCIDENTE (DESACELERACIÓN SÚBITA)
                RuleType.CAR_ACCIDENT -> {
                    // Detecta una caída brusca de velocidad (ej: 30 km/h en 1 segundo)
                    val decelerationThreshold = 20.0
                    if ((lastSpeedKmh - currentSpeedKmh) > decelerationThreshold) {
                        triggerAlertProcess(rule)
                    }
                }

                // 4. INACTIVIDAD PROLONGADA
                RuleType.INACTIVITY -> {
                    val durationMin = (rule.params["duration"] as? Number)?.toLong() ?: 30L
                    val durationMs = durationMin * 60 * 1000

                    // Consideramos "movimiento" si se ha desplazado más de 5 metros
                    val distanceResult = FloatArray(1)
                    lastMovementLocation?.let {
                        Location.distanceBetween(location.latitude, location.longitude, it.latitude, it.longitude, distanceResult)
                    }

                    if (lastMovementLocation == null || distanceResult[0] > 5.0) {
                        // Si hay movimiento, reseteamos el temporizador
                        lastMovementLocation = location
                        lastMovementTimestamp = System.currentTimeMillis()
                    } else {
                        // Si no hay movimiento, comprobamos cuánto tiempo ha pasado
                        if (System.currentTimeMillis() - lastMovementTimestamp > durationMs) {
                            triggerAlertProcess(rule)
                        }
                    }
                }
                else -> {}
            }
        }

        // Actualizamos la última velocidad para la siguiente comparación de accidentes
        lastSpeedKmh = currentSpeedKmh
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartTime = System.currentTimeMillis()
        gpsUpdateCount = 0
        currentUserId = auth.currentUser?.uid
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_GAME)

        startListeningToRules()
    }

    private fun startListeningToRules() {
        val userId = currentUserId ?: return
        rulesListener = db.collection("users").document(userId).collection("safety_rules")
            .whereEqualTo("authorized", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) activeRules = snapshot.toObjects(SafetyRule::class.java)
            }
    }

    private fun updateFirestoreLocation(lat: Double, lng: Double) {
        val userId = currentUserId ?: return
        db.collection("users").document(userId).update(mapOf("location" to GeoPoint(lat, lng)))
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        createNotificationChannel()
        startLocationUpdates()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channelId = "monitoring_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Safety Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitoring Active").setSmallIcon(R.drawable.ic_logo_safetysec).build()
        startForeground(1, notif)
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500).build()
        try { fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper()) } catch (e: SecurityException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(sensorListener)
        rulesListener?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}