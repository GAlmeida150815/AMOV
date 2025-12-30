package pt.isec.amov.tp.ui.screens

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.model.Alert
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

@OptIn(UnstableApi::class)
@Composable
fun AlertDetailScreen(
    alert: Alert,
    onBack: () -> Unit,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val videoUri = alert.videoUrl ?: ""

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (videoUri.isNotEmpty()) {
                setMediaItem(MediaItem.fromUri(videoUri))
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = "Detalle de Emergencia",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // --- CUERPO DE LA PANTALLA ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta informativa del tipo de alerta
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TIPO: ${alert.type}", style = MaterialTheme.typography.headlineSmall)
                    Text("ID Protegido: ${alert.protectedId}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reproductor de Vídeo (ExoPlayer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (videoUri.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Video no disponible", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            // Botón de resolución
            Button(
                onClick = {
                    viewModel.resolveAlert(alert.protectedId)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("TODO OK")
            }
        }
    }
}