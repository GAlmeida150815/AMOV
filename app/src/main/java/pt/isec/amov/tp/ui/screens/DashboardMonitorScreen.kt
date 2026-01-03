package pt.isec.amov.tp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.model.Alert
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.ui.components.*
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardMonitorScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onLogout: () -> Unit,
    onNavigate: (MainTab, String?) -> Unit
) {
    val protecteds = dashboardViewModel.myProtecteds
    val context = LocalContext.current

    var selectedProtected by remember { mutableStateOf<User?>(null) }
    var alertForVideo by remember { mutableStateOf<Alert?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dashboardViewModel.generateCode(); showAddDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.btn_add_protected)) }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            WelcomeHeader(authViewModel.user?.name)
            Spacer(modifier = Modifier.height(16.dp))

            StatisticsSection(protecteds.size, dashboardViewModel.activeAlerts.size)

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    AlertsSection(
                        alerts = dashboardViewModel.activeAlerts.values.toList(),
                        onAlertClick = { alertForVideo = it },
                        onResolveClick = { dashboardViewModel.resolveAlert(it.protectedId) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle(stringResource(R.string.sect_protecteds))
                }

                items(protecteds) { protectedUser ->
                    UserCard(
                        user = protectedUser,
                        isProtectedUser = true,
                        onRemove = { dashboardViewModel.removeAssociation(protectedUser.uid, true, {}, {}) },
                        onClick = {
                            if (protectedUser.isProtected) {
                                dashboardViewModel.fetchAlertsForProtected(protectedUser.uid)
                                selectedProtected = protectedUser
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // --- DIÁLOGO QUE APARECE EN TU CAPTURA ---
    if (alertForVideo != null) {
        AlertVideoDialog(
            alert = alertForVideo!!,
            onDismiss = { alertForVideo = null }
        )
    }

    // --- DIÁLOGO DE HISTORIAL ---
    if (selectedProtected != null) {
        AlertDialog(
            onDismissRequest = { selectedProtected = null },
            title = { Text("${stringResource(R.string.sect_history)}: ${selectedProtected!!.name}") },
            text = {
                val history = dashboardViewModel.alertsForSelectedProtected
                if (history.isEmpty()) {
                    Text(stringResource(R.string.msg_no_active_alerts))
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(history) { data ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clickable {
                                        // --- CORRECCIÓN: Al pulsar en el historial, mostramos el vídeo ---
                                        alertForVideo = Alert(
                                            id = data["id"] as? String ?: "",
                                            type = data["type"] as? String ?: "",
                                            batteryLevel = (data["batteryLevel"] as? Long)?.toInt() ?: -1,
                                            videoUrl = data["videoUrl"] as? String
                                        )
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(data["type"] as? String ?: "---", fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.msg_click_to_view_video), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedProtected = null }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    if (showAddDialog) {
        AddProtectedDialog(code = dashboardViewModel.generatedCode, onDismiss = { showAddDialog = false })
    }
}

@Composable
fun AlertVideoDialog(alert: Alert, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_alert_details)) },
        text = {
            Column {
                // --- CORRECCIÓN: Aquí se muestra el valor real capturado ---
                Text("${stringResource(R.string.lbl_battery)}: ${alert.batteryLevel}%", fontWeight = FontWeight.Bold)
                Text("${stringResource(R.string.lbl_type)}: ${alert.type}")
                Spacer(modifier = Modifier.height(16.dp))

                if (!alert.videoUrl.isNullOrEmpty()) {
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alert.videoUrl))) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_view_video))
                    }
                    Text(text = "URL: ${alert.videoUrl}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                } else {
                    Text(stringResource(R.string.msg_video_uploading), color = Color.Gray)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    )
}

@Composable
fun StatisticsSection(protectedCount: Int, activeAlerts: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(title = stringResource(R.string.sect_protecteds), value = protectedCount.toString(), icon = Icons.Default.People, modifier = Modifier.weight(1f))
        StatCard(title = stringResource(R.string.sect_alerts), value = activeAlerts.toString(), icon = Icons.Default.Warning,
            color = if (activeAlerts > 0) Color.Red else MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AlertsSection(alerts: List<Alert>, onAlertClick: (Alert) -> Unit, onResolveClick: (Alert) -> Unit) {
    SectionTitle(stringResource(R.string.sect_alerts))
    if (alerts.isEmpty()) {
        Text(stringResource(R.string.msg_no_active_alerts), color = Color.Gray)
    } else {
        alerts.forEach { alert ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onAlertClick(alert) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alert.type, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onResolveClick(alert) }) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}