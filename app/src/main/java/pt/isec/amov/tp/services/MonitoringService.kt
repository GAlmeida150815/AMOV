package pt.isec.amov.tp.services

import android.app.*
import android.content.*
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

class MonitoringService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var activeRules: List<SafetyRule> = emptyList()
    private var rulesListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentUserId: String? = null
    private var gpsUpdateCount = 0
    private var lastSpeedKmh = 0.0

    private var lastAlertTimestamp: Long = 0
    private val ALERT_COOLDOWN_MS = 60000 // 1 minuto de espera obligatoria entre alertas

    override fun onCreate() {
        super.onCreate()
        currentUserId = auth.currentUser?.uid
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startListeningToRules()
    }

    private fun updateFirestoreLocation(lat: Double, lng: Double) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "location" to GeoPoint(lat, lng),
                // Esto arregla el error de que la fecha se quedara fija en "30 de diciembre"
                "lastUpdate" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun checkSecurityRules(location: Location) {
        gpsUpdateCount++
        val currentSpeedKmh = location.speed * 3.6

        // Esperamos 3 actualizaciones para que el GPS sea preciso
        if (gpsUpdateCount < 3) {
            lastSpeedKmh = currentSpeedKmh
            return
        }

        activeRules.forEach { rule ->
            // Si ya saltó una alerta hace menos de un minuto, ignoramos el chequeo
            if (System.currentTimeMillis() - lastAlertTimestamp < ALERT_COOLDOWN_MS) return@forEach

            when (rule.type) {
                RuleType.CAR_ACCIDENT -> {
                    // Detección de frenada brusca (caída de más de 20km/h en un instante)
                    if (lastSpeedKmh > 15.0 && (lastSpeedKmh - currentSpeedKmh) > 20.0) {
                        triggerAlertProcess(rule)
                    }
                }
                RuleType.SPEED_LIMIT -> {
                    val max = (rule.params["max_speed"] as? Number)?.toDouble() ?: 120.0
                    if (currentSpeedKmh > max) triggerAlertProcess(rule)
                }
                RuleType.GEOFENCING -> {
                    val centerLat = (rule.params["lat"] as? Number)?.toDouble() ?: 0.0
                    val centerLng = (rule.params["lng"] as? Number)?.toDouble() ?: 0.0
                    val radius = (rule.params["radius"] as? Number)?.toDouble() ?: 100.0
                    val results = FloatArray(1)
                    Location.distanceBetween(location.latitude, location.longitude, centerLat, centerLng, results)
                    if (results[0] > radius) triggerAlertProcess(rule)
                }
                else -> {}
            }
        }
        lastSpeedKmh = currentSpeedKmh
    }

    private fun triggerAlertProcess(rule: SafetyRule) {
        lastAlertTimestamp = System.currentTimeMillis()

        val intent = Intent(this, AlertActivity::class.java).apply {
            // FLAG_ACTIVITY_CLEAR_TOP asegura que si la ventana ya existe, se reutilice y no se apile
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("RULE_TYPE", rule.type.name)
            putExtra("RULE_ID", rule.id)
        }
        startActivity(intent)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            res.lastLocation?.let {
                updateFirestoreLocation(it.latitude, it.longitude)
                checkSecurityRules(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // --- CONFIGURACIÓN DE NOTIFICACIÓN (Requisito Foreground Service) ---
        val channelId = "monitoring_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "SafetYSec Tracking", NotificationManager.IMPORTANCE_LOW)
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.lbl_monitoring_active))
            .setContentText(getString(R.string.msg_sharing_location))
            .setSmallIcon(R.drawable.ic_logo_safetysec) // Asegúrate de que este icono existe
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        // Solicitamos actualizaciones cada 2 segundos para balancear batería y precisión
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("MonitoringService", "Sin permisos de GPS")
        }

        return START_STICKY
    }

    private fun startListeningToRules() {
        val uid = currentUserId ?: return
        // Escuchamos solo las reglas que el monitor haya marcado como autorizadas
        rulesListener = db.collection("users").document(uid).collection("safety_rules")
            .whereEqualTo("authorized", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    activeRules = snapshot.toObjects(SafetyRule::class.java)
                }
            }
    }

    override fun onDestroy() {
        // Limpieza de recursos al cerrar el servicio
        rulesListener?.remove()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}