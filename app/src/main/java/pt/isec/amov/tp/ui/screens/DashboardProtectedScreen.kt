package pt.isec.amov.tp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.services.MonitoringService
import pt.isec.amov.tp.ui.components.*
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel
import androidx.core.content.edit

@Composable
fun DashboardProtectedScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onLogout: () -> Unit,
    onNavigate: (MainTab, String?) -> Unit
) {
    val user = authViewModel.user
    val monitors = dashboardViewModel.myMonitors
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("monitoring_prefs", Context.MODE_PRIVATE) }
    val isServiceRunning = dashboardViewModel.isServiceRunning

    // Plantilla para el Toast informativo
    val monitorInfoTemplate = stringResource(R.string.msg_monitor_profile_info)

    var selectedMonitorForDetails by remember { mutableStateOf<User?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (dashboardViewModel.isFirstLoad) {
            dashboardViewModel.isServiceRunning = sharedPrefs.getBoolean("is_service_active", false)
            dashboardViewModel.isFirstLoad = false
        }
        dashboardViewModel.startListening()
    }

    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            dashboardViewModel.isServiceRunning = true
            sharedPrefs.edit { putBoolean("is_service_active", true) }
            Toast.makeText(context, R.string.msg_monitor_started, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            WelcomeHeader(user?.name)
            Spacer(modifier = Modifier.height(24.dp))

            // Card de estado del servicio (Switch de compartición de ubicación)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isServiceRunning) stringResource(R.string.lbl_monitoring_active) else stringResource(R.string.lbl_monitoring_paused), fontWeight = FontWeight.Bold)
                        Text(text = if (isServiceRunning) stringResource(R.string.msg_sharing_location) else stringResource(R.string.msg_not_sharing_location), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { active ->
                            if (active) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            else {
                                context.stopService(Intent(context, MonitoringService::class.java))
                                dashboardViewModel.isServiceRunning = false
                                sharedPrefs.edit { putBoolean("is_service_active", false) }
                                Toast.makeText(context, R.string.msg_monitor_stopped, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.title_my_monitors))
                IconButton(onClick = { showLinkDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (monitors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.msg_no_monitors))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(monitors) { monitor ->
                        UserCard(
                            user = monitor,
                            isProtectedUser = false,
                            onRemove = {
                                dashboardViewModel.removeAssociation(monitor.uid, false, {
                                    Toast.makeText(context, R.string.msg_monitor_removed, Toast.LENGTH_SHORT).show()
                                }, {})
                            },
                            onClick = {
                                selectedMonitorForDetails = monitor
                                Toast.makeText(context, String.format(monitorInfoTemplate, monitor.name), Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Diálogo de información del monitor y sus reglas configuradas
        if (selectedMonitorForDetails != null) {
            MonitorDetailsDialog(
                monitor = selectedMonitorForDetails!!,
                // Mostramos las reglas que están configuradas actualmente
                rules = dashboardViewModel.myRules,
                onDismiss = { selectedMonitorForDetails = null }
            )
        }

        if (showLinkDialog) {
            AddMonitorDialog(viewModel = dashboardViewModel, onDismiss = { showLinkDialog = false }, onSuccess = { showLinkDialog = false })
        }
    }
}

@Composable
fun MonitorDetailsDialog(monitor: User, rules: List<SafetyRule>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = monitor.name, style = MaterialTheme.typography.headlineSmall)
                Text(text = monitor.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- CAMBIO: Uso de stringResource ---
                Text(
                    text = stringResource(R.string.lbl_rules_by_monitor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (rules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_rules_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                } else {
                    rules.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rule.type.name.replace("_", " "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- CAMBIO: Uso de stringResource ---
                Text(
                    text = stringResource(R.string.msg_only_monitor_can_modify),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            // --- CAMBIO: Uso de stringResource ---
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}