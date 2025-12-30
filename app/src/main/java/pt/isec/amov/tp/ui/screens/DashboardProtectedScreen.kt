package pt.isec.amov.tp.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.services.MonitoringService
import pt.isec.amov.tp.ui.components.AddMonitorDialog
import pt.isec.amov.tp.ui.components.EmptyState
import pt.isec.amov.tp.ui.components.SectionTitle
import pt.isec.amov.tp.ui.components.UserCard
import pt.isec.amov.tp.ui.components.WelcomeHeader
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

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

    // --- State Variables ---
    var showLinkDialog by remember { mutableStateOf(false) }

    // --- GPS State ---
    var isServiceRunning by remember { mutableStateOf(false) }

    // --- Permissions & Launcher ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fine || coarse) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            isServiceRunning = true
            Toast.makeText(context, context.getString(R.string.msg_monitor_started), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.msg_perm_location_required), Toast.LENGTH_LONG).show()
        }
    }

    // --- Data Listener (Associações) ---
    LaunchedEffect(Unit) {
        dashboardViewModel.startListening()
    }

    // --- Landscape Orientation ---
    //val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // --- Header ---
            WelcomeHeader(user?.name)

            Spacer(modifier = Modifier.height(24.dp))

            // --- Localization Monitoring Card ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if(isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                            text = if(isServiceRunning) stringResource(R.string.lbl_monitoring_active) else stringResource(R.string.lbl_monitoring_paused),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if(isServiceRunning) stringResource(R.string.msg_sharing_location) else stringResource(R.string.msg_not_sharing_location),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { shouldStart ->
                            if (shouldStart) {
                                // --- Check Permissions ---
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                // --- Stop Service ---
                                val intent = Intent(context, MonitoringService::class.java)
                                context.stopService(intent)
                                isServiceRunning = false
                                Toast.makeText(context, context.getString(R.string.msg_monitor_stopped), Toast.LENGTH_SHORT).show()
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

                // --- Add Monitor Button ---
                IconButton(onClick = { showLinkDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.title_add_monitor),
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                                        Toast.makeText(context, context.getString(R.string.msg_monitor_removed), Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onClick = {
                                onNavigate(MainTab.CONNECTIONS, monitor.uid)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showLinkDialog) {
        AddMonitorDialog (
            viewModel = dashboardViewModel,
            onDismiss = { showLinkDialog = false },
            onSuccess = {
                Toast.makeText(context, context.getString(R.string.assoc_success), Toast.LENGTH_SHORT).show()
                showLinkDialog = false
            }
        )
    }
}

