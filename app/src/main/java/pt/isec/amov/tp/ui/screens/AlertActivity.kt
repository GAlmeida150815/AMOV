package pt.isec.amov.tp.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import pt.isec.amov.tp.R
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
    private var currentAlertId: String? = null // ID para actualizar el mismo documento con el vídeo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configuración para mostrar sobre pantalla de bloqueo
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
                    onTimerFinished = {
                        sendInitialAlert() // 1. Envío inmediato a Firestore
                        startRecording()   // 2. Inicio de grabación técnica
                    },
                    onCorrectPin = { finish() }
                )
            }
        }
    }

    // Envía el documento inicial para que el Monitor sepa de la emergencia al instante
    private fun sendInitialAlert() {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return

        val typeStr = intent.getStringExtra("RULE_TYPE") ?: "PANIC_BUTTON"

        val alertData = hashMapOf(
            "protectedId" to userId,
            "protectedName" to (auth.currentUser?.displayName ?: getString(R.string.default_user_name)),
            "timestamp" to com.google.firebase.Timestamp.now(),
            "type" to typeStr,
            "ruleId" to intent.getStringExtra("RULE_ID"),
            "resolved" to false,
            "videoUrl" to null
        )

        db.collection("alerts").add(alertData)
            .addOnSuccessListener { doc ->
                currentAlertId = doc.id
                Log.d("AlertActivity", getString(R.string.log_monitor_notified_success))
            }
            .addOnFailureListener { e ->
                Log.e("AlertActivity", "${getString(R.string.log_monitor_notified_error)}: ${e.message}")
            }
    }

    private fun startRecording() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, videoCapture)

                val videoFile = File(externalCacheDir, "alert_${System.currentTimeMillis()}.mp4")
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                recording = videoCapture?.output
                    ?.prepareRecording(this, outputOptions)
                    ?.start(ContextCompat.getMainExecutor(this)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (!event.hasError()) {
                                uploadVideoToFirebase(videoFile)
                            } else {
                                Log.e("AlertActivity", getString(R.string.log_recording_error))
                                finish() // Evitar quedar preso en caso de error
                            }
                        }
                    }

                // Graba durante 10 segundos y detiene
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    recording?.stop()
                }, 10000)

            } catch (e: Exception) {
                Log.e("AlertActivity", getString(R.string.log_camera_error), e)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun uploadVideoToFirebase(file: File) {
        val storageRef = FirebaseStorage.getInstance().reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val videoRef = storageRef.child("alerts/$userId/${file.name}")

        videoRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                videoRef.downloadUrl.addOnSuccessListener { uri ->
                    // Actualiza el campo videoUrl del alerta que ya enviamos
                    currentAlertId?.let { id ->
                        FirebaseFirestore.getInstance().collection("alerts").document(id)
                            .update("videoUrl", uri.toString())
                            .addOnSuccessListener {
                                Toast.makeText(this, getString(R.string.msg_monitor_notified), Toast.LENGTH_SHORT).show()
                                finish() // CIERRE FINAL DE LA ACTIVIDAD
                            }
                            .addOnFailureListener { finish() }
                    } ?: finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("AlertActivity", getString(R.string.log_upload_error))
                finish()
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
                // MODIFICACIÓN: Al llegar a 0, forzamos el cambio de estado independientemente del PIN
                timeLeft = 0
                recordingStarted = true // Esto oculta el PIN y el contador inmediatamente
                onTimerFinished() // Ejecuta el envío a Firestore y grabación
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
                // Interfaz de cuenta atrás y cancelación
                Text(stringResource(R.string.alert_title), style = MaterialTheme.typography.headlineLarge, color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.alert_cancel_label), style = MaterialTheme.typography.titleMedium)
                Text("$timeLeft", fontSize = 100.sp, color = Color.Red, style = MaterialTheme.typography.headlineLarge)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = inputPin,
                    onValueChange = {
                        inputPin = it
                        // La cancelación solo ocurre si el código coincide antes de que acabe el tiempo
                        if (correctPin.isNotEmpty() && it == correctPin) {
                            onCorrectPin()
                        }
                    },
                    label = { Text(stringResource(R.string.alert_pin_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                // Interfaz de carga: Aparece justo después de los 10 segundos
                CircularProgressIndicator(color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.alert_sending_msg))
            }
        }
    }
}