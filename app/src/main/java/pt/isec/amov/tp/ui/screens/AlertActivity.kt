package pt.isec.amov.tp.ui.screens

import android.content.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.theme.TP_theme
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import java.io.File

class AlertActivity : ComponentActivity() {
    private var recording: Recording? = null
    private var currentAlertId: String? = null
    private var timer: CountDownTimer? = null
    private var isCanceled = false
    private var isUploading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración de ventana para mostrarse sobre la pantalla de bloqueo
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        // --- SEGURIDAD: Temporizador Maestro para evitar bucles infinitos ---
        // Si en 60 segundos no se ha cerrado la actividad, se fuerza el cierre
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                Log.d("AlertActivity", "Timeout de seguridad alcanzado")
                finish()
            }
        }, 60000)

        setContent {
            TP_theme {
                val authViewModel: AuthViewModel = viewModel()
                val correctPin = authViewModel.user?.cancellationCode ?: ""

                AlertCountdownScreen(
                    correctPin = correctPin,
                    isUploading = isUploading,
                    onTimerFinished = {
                        if (!isCanceled) {
                            sendDetailedAlert()
                            startRecording()
                        }
                    },
                    onCanceled = {
                        isCanceled = true
                        timer?.cancel()
                        finish()
                    },
                    onTimerCreated = { timer = it }
                )
            }
        }
    }

    private fun sendDetailedAlert() {
        if (isCanceled) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val alertData = hashMapOf(
                "protectedId" to uid,
                "protectedName" to (doc.getString("name") ?: "User"),
                "timestamp" to com.google.firebase.Timestamp.now(),
                "type" to (intent.getStringExtra("RULE_TYPE") ?: "PANIC"),
                "location" to doc.getGeoPoint("location"),
                "batteryLevel" to batteryPct,
                "resolved" to false,
                "videoUrl" to null
            )
            db.collection("alerts").add(alertData).addOnSuccessListener { currentAlertId = it.id }
        }
    }

    private fun startRecording() {
        if (isCanceled) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, videoCapture)
                val videoFile = File(externalCacheDir, "alert_${System.currentTimeMillis()}.mp4")

                val recordingHandle = videoCapture.output.prepareRecording(this, FileOutputOptions.Builder(videoFile).build())
                    .start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (!event.hasError()) {
                                uploadVideoToFirebase(videoFile)
                            } else {
                                Log.e("AlertActivity", "Error de grabación: ${event.error}")
                                finish() // Cerramos en caso de error para evitar bucle
                            }
                        }
                    }

                recording = recordingHandle

                if (recording == null) {
                    finish()
                    return@addListener
                }

                // Grabación automática de 30 segundos
                Handler(Looper.getMainLooper()).postDelayed({
                    recording?.stop()
                    isUploading = true
                }, 30000)

            } catch (e: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun uploadVideoToFirebase(file: File) {
        val storageRef = FirebaseStorage.getInstance().reference.child("videos/${file.name}")
        storageRef.putFile(Uri.fromFile(file)).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                // Sincronización con Firestore antes de cerrar la actividad
                currentAlertId?.let { id ->
                    FirebaseFirestore.getInstance().collection("alerts").document(id)
                        .update("videoUrl", uri.toString())
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.msg_monitor_notified), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { finish() }
                } ?: finish()
            }
        }.addOnFailureListener { finish() }
    }
}

@Composable
fun AlertCountdownScreen(
    correctPin: String,
    isUploading: Boolean,
    onTimerFinished: () -> Unit,
    onCanceled: () -> Unit,
    onTimerCreated: (CountDownTimer) -> Unit
) {
    var timeLeft by remember { mutableStateOf(10) }
    var inputPin by remember { mutableStateOf("") }
    var isTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val t = object : CountDownTimer(10000, 1000) {
            override fun onTick(ms: Long) { timeLeft = (ms / 1000).toInt() }
            override fun onFinish() {
                if (!isTriggered) {
                    isTriggered = true
                    onTimerFinished()
                }
            }
        }
        onTimerCreated(t)
        t.start()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            if (!isTriggered) {
                Text(stringResource(R.string.alert_title), style = MaterialTheme.typography.headlineLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                Text("$timeLeft", fontSize = 100.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = inputPin,
                    onValueChange = {
                        inputPin = it
                        if (correctPin.isNotEmpty() && it == correctPin) onCanceled()
                    },
                    label = { Text(stringResource(R.string.alert_pin_label)) }
                )
            } else {
                CircularProgressIndicator(color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isUploading) stringResource(R.string.msg_video_uploading)
                    else stringResource(R.string.alert_sending_msg),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}