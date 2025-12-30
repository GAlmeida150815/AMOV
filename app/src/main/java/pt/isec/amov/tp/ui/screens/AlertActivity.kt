package pt.isec.amov.tp.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.WindowManager
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.model.Alert
import pt.isec.amov.tp.ui.theme.TP_theme
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AlertActivity : ComponentActivity() {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configuración para mostrar sobre pantalla de bloqueo (Compatibilidad)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            TP_theme {
                AlertCountdownScreen(
                    onTimerFinished = { startRecording() },
                    onCorrectPin = { finish() }
                )
            }
        }
    }

    private fun startRecording() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Recorder para grabar el vídeo
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.LOWEST)) // Baja calidad para subir rápido
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Usar cámara frontal por defecto
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)

                // Preparar archivo de salida
                val videoFile = File(externalCacheDir, "alert_${System.currentTimeMillis()}.mp4")
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                // Iniciar la grabación
                recording = videoCapture?.output
                    ?.prepareRecording(this, outputOptions)
                    ?.start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (!event.hasError()) {
                                uploadVideoToFirebase(videoFile)
                            } else {
                                Log.e("AlertActivity", "Error grabación: ${event.error}")
                            }
                        }
                    }

                // Detener automáticamente a los 30 segundos
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    recording?.stop()
                }, 30000)

            } catch (e: Exception) {
                Log.e("AlertActivity", "Error vinculando CameraX", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun uploadVideoToFirebase(file: File) {
        val storageRef = FirebaseStorage.getInstance().reference
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        val userId = auth.currentUser?.uid ?: return
        val videoRef = storageRef.child("alerts/$userId/${file.name}")

        videoRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                videoRef.downloadUrl.addOnSuccessListener { uri ->
                    // Crear el objeto Alerta usando tu modelo Alert.kt
                    val newAlert = Alert(
                        protectedId = userId,
                        type = RuleType.valueOf(intent.getStringExtra("RULE_TYPE") ?: "PANIC_BUTTON"),
                        ruleId = intent.getStringExtra("RULE_ID"),
                        videoUrl = uri.toString(),
                        resolved = false
                    )

                    // Guardar en Firestore
                    db.collection("alerts").add(newAlert)
                        .addOnSuccessListener { finish() }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AlertActivity", "Error subida: ${e.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun AlertCountdownScreen(
    onTimerFinished: () -> Unit,
    onCorrectPin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val correctPin = authViewModel.user?.cancellationCode ?: ""
    var timeLeft by remember { mutableStateOf(10) }
    var inputPin by remember { mutableStateOf("") }
    var recordingStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                if (inputPin != correctPin) {
                    recordingStarted = true
                    onTimerFinished()
                }
            }
        }.start()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            if (!recordingStarted) {
                Text("¡ALERTA DE SEGURIDAD!", style = MaterialTheme.typography.headlineLarge, color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tiempo para cancelar:", style = MaterialTheme.typography.titleMedium)
                Text("$timeLeft", fontSize = 100.sp, color = Color.Red, style = MaterialTheme.typography.headlineLarge)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inputPin,
                    onValueChange = {
                        inputPin = it
                        if (it == correctPin) onCorrectPin()
                    },
                    label = { Text("Introduce PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                CircularProgressIndicator(color = Color.Red)
                Text("Grabando y enviando alerta...", modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
