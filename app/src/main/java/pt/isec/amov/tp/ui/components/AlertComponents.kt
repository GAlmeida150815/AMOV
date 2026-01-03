package pt.isec.amov.tp.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

@Composable
fun AlertsDetailDialog(
    protectedId: String,
    protectedName: String,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    // Escuchamos las alertas del protegido en tiempo real
    LaunchedEffect(protectedId) {
        viewModel.fetchAlertsForProtected(protectedId)
    }

    val allAlerts = viewModel.alertsForSelectedProtected

    // Diferenciación de alertas
    val activeAlerts = allAlerts.filter { (it["resolved"] as? Boolean) == false }
    val historyAlerts = allAlerts.filter { (it["resolved"] as? Boolean) == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_protected_history, protectedName), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                // Estadísticas rápidas
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatCard(stringResource(R.string.stat_total_alerts), allAlerts.size.toString())
                    StatCard(stringResource(R.string.stat_active_now), activeAlerts.size.toString())
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // --- SECCIÓN: ALERTAS ACTIVAS ---
                    item { Text(stringResource(R.string.title_active_alerts), color = Color.Red, fontWeight = FontWeight.Bold) }
                    if (activeAlerts.isEmpty()) {
                        item { Text(stringResource(R.string.msg_no_active_alerts), style = MaterialTheme.typography.bodySmall) }
                    } else {
                        items(activeAlerts) { alert ->
                            AlertItem(alert, isActive = true) {
                                viewModel.resolveAlert(alert["id"].toString())
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    // --- SECCIÓN: ALERTAS PASADAS ---
                    item { Text(stringResource(R.string.title_alerts_history), fontWeight = FontWeight.Bold) }
                    if (historyAlerts.isEmpty()) {
                        item { Text(stringResource(R.string.msg_no_history_alerts), style = MaterialTheme.typography.bodySmall) }
                    } else {
                        items(historyAlerts) { alert ->
                            AlertItem(alert, isActive = false, onResolve = null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    )
}

@Composable
fun AlertItem(alert: Map<String, Any>, isActive: Boolean, onResolve: (() -> Unit)?) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.lbl_alert_type, alert["type"].toString()), fontWeight = FontWeight.Bold)
            val timestamp = alert["timestamp"] as? com.google.firebase.Timestamp
            Text(text = stringResource(R.string.lbl_alert_date, timestamp?.toDate()?.toLocaleString() ?: ""), style = MaterialTheme.typography.bodySmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Botón de Vídeo
                val videoUrl = alert["videoUrl"]?.toString()
                if (!videoUrl.isNullOrEmpty()) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.btn_view_video))
                    }
                }

                if (isActive && onResolve != null) {
                    Button(onClick = onResolve, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))) {
                        Text(stringResource(R.string.btn_resolve), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.width(130.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}