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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.model.User
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
    // --- ViewModel State ---
    val user = authViewModel.user
    val monitors = dashboardViewModel.myMonitors
    val context = LocalContext.current

    val sharedPrefs = remember { context.getSharedPreferences("monitoring_prefs", Context.MODE_PRIVATE) }

    // Usamos el estado del ViewModel para que no se pierda al cambiar de pestaña (Home -> Profile)
    val isServiceRunning = dashboardViewModel.isServiceRunning

    // Cargamos el valor guardado solo la primera vez que se crea la pantalla
    LaunchedEffect(Unit) {
        if (dashboardViewModel.isFirstLoad) {
            dashboardViewModel.isServiceRunning = sharedPrefs.getBoolean("is_service_active", false)
            dashboardViewModel.isFirstLoad = false
        }
        dashboardViewModel.startListening()
    }
    val errorMsg = dashboardViewModel.errorMessage
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            dashboardViewModel.errorMessage = null // Limpiamos para que no salga repetido
        }
    }

    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // --- State Variables ---
    var showLinkDialog by remember { mutableStateOf(false) }
    var selectedUserForAlerts by remember { mutableStateOf<User?>(null) } // Para el diálogo de historial

    // --- Permissions & Launcher ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fine || coarse) {
            dashboardViewModel.isServiceRunning = true
            sharedPrefs.edit { putBoolean("is_service_active", true) }
            Toast.makeText(context, R.string.msg_monitor_started, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.msg_perm_location_required, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            WelcomeHeader(user?.name)
            Spacer(modifier = Modifier.height(24.dp))

            // --- Localization Monitoring Card ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceRunning) stringResource(R.string.lbl_monitoring_active) else stringResource(R.string.lbl_monitoring_paused),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServiceRunning) stringResource(R.string.msg_sharing_location) else stringResource(R.string.msg_not_sharing_location),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { shouldStart ->
                            if (shouldStart) {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            } else {
                                val intent = Intent(context, MonitoringService::class.java)
                                context.stopService(intent)
                                dashboardViewModel.isServiceRunning = false
                                sharedPrefs.edit { putBoolean("is_service_active", false) }
                                Toast.makeText(context, R.string.msg_monitor_stopped, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.scale(1.2f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            // --- Monitor List ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(stringResource(R.string.title_my_monitors))
                IconButton(onClick = { showLinkDialog = true }) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                                dashboardViewModel.removeAssociation(
                                    otherUserId = monitor.uid,
                                    amIMonitor = false,
                                    onSuccess = {
                                        Toast.makeText(context, R.string.msg_monitor_removed, Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { errorMsg ->
                                        dashboardViewModel.errorMessage = errorMsg
                                    }
                                )
                            },

                            onClick = { selectedUserForAlerts = monitor }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (selectedUserForAlerts != null) {
            AlertsDetailDialog(
                protectedId = selectedUserForAlerts!!.uid,
                protectedName = selectedUserForAlerts!!.name,
                viewModel = dashboardViewModel,
                onDismiss = { }
            )
        }

        if (showLinkDialog) {
            AddMonitorDialog(
                viewModel = dashboardViewModel,
                onDismiss = { },
                onSuccess = {
                    Toast.makeText(context, R.string.assoc_success, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}