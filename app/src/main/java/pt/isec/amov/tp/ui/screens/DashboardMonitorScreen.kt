package pt.isec.amov.tp.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.model.Alert
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.ui.components.* // Importa AlertsDetailDialog y otros componentes
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
    // --- ViewModel State ---
    val user = authViewModel.user
    val protecteds = dashboardViewModel.myProtecteds
    val context = LocalContext.current

    // --- Orientation Check ---
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // --- State Variables ---
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<Alert?>(null) }

    // VARIABLE CLAVE: Protegido seleccionado para ver el historial
    var selectedProtected by remember { mutableStateOf<User?>(null) }
    // Al principio de DashboardMonitorScreen, junto a 'user' y 'protecteds'
    val errorRemoveTemplate = stringResource(R.string.err_assoc_remove)

    if (selectedAlert != null) {
        AlertDetailScreen(
            alert = selectedAlert!!,
            onBack = { selectedAlert = null },
            viewModel = dashboardViewModel
        )
        return
    }

    // --- Main Scaffold ---
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    dashboardViewModel.generateCode()
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text(stringResource(R.string.btn_add_protected)) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->

        if (isLandscape) {
            // --- LAYOUT LANDSCAPE (Horizontal) ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.weight(0.4f).verticalScroll(rememberScrollState())) {
                    WelcomeHeader(user?.name)
                    Spacer(modifier = Modifier.height(16.dp))

                    StatisticsSection(
                        protectedCount = protecteds.size,
                        activeAlerts = dashboardViewModel.activeAlerts.size
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AlertsSection(
                        alerts = dashboardViewModel.activeAlerts.values.toList(),
                        onAlertClick = { selectedAlert = it })
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(modifier = Modifier.weight(0.6f)) {
                    SectionTitle(stringResource(R.string.sect_protecteds))
                    Spacer(modifier = Modifier.height(8.dp))

                    ProtectedsList(
                        protecteds = protecteds,
                        // Al hacer clic en Landscape, abrimos el historial
                        onProtectedClick = { selectedProtected = it },
                        viewModel = dashboardViewModel
                    )
                }
            }
        } else {
            // --- LAYOUT PORTRAIT (Vertical) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                WelcomeHeader(user?.name)
                Spacer(modifier = Modifier.height(16.dp))

                StatisticsSection(
                    protectedCount = protecteds.size,
                    activeAlerts = dashboardViewModel.activeAlerts.size
                )

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        AlertsSection(
                            alerts = dashboardViewModel.activeAlerts.values.toList(),
                            onAlertClick = { selectedAlert = it }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        SectionTitle(stringResource(R.string.sect_protecteds))
                    }

                    if (protecteds.isEmpty()) {
                        item {
                            EmptyState(stringResource(R.string.msg_no_protecteds))
                        }
                    } else {
                        items(protecteds) { protectedUser ->
                            UserCard(
                                user = protectedUser,
                                isProtectedUser = true,
                                onRemove = {
                                    dashboardViewModel.removeAssociation(
                                        otherUserId = protectedUser.uid,
                                        amIMonitor = true,
                                        onSuccess = {
                                            Toast.makeText(context, R.string.msg_assoc_removed_success, Toast.LENGTH_SHORT).show()
                                        },
                                        onFailure = { errorMsg ->
                                            // Usamos la plantilla pre-cargada y le pasamos el error dinámico
                                            val message = String.format(errorRemoveTemplate, errorMsg)
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                onClick = { selectedProtected = protectedUser }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // --- DIÁLOGOS ---

    // Historial de Alertas (Activas vs Pasadas) para el Monitor
    if (selectedProtected != null) {
        AlertsDetailDialog(
            protectedId = selectedProtected!!.uid,
            protectedName = selectedProtected!!.name,
            viewModel = dashboardViewModel,
            onDismiss = { selectedProtected = null }
        )
    }

    if (showAddDialog) {
        AddProtectedDialog(
            code = dashboardViewModel.generatedCode,
            onDismiss = {
                showAddDialog = false
                dashboardViewModel.clearAssociationState()
            }
        )
    }
}

// --- SUB-COMPONENTES UI ---

@Composable
fun ProtectedsList(
    protecteds: List<User>,
    viewModel: DashboardViewModel,
    onProtectedClick: (User) -> Unit
) {
    val context = LocalContext.current
    LazyColumn {
        items(protecteds) { protectedUser ->
            UserCard(
                user = protectedUser,
                isProtectedUser = true,
                onRemove = {
                    viewModel.removeAssociation(protectedUser.uid, true, {
                        Toast.makeText(context, R.string.msg_assoc_removed_success, Toast.LENGTH_SHORT).show()
                    }, {})
                },
                onClick = { onProtectedClick(protectedUser) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun StatisticsSection(protectedCount: Int, activeAlerts: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "$protectedCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.sect_protecteds), style = MaterialTheme.typography.labelSmall)
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = if (activeAlerts > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = if (activeAlerts > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (activeAlerts > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "$activeAlerts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.sect_alerts), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AlertsSection(alerts: List<Alert>, onAlertClick: (Alert) -> Unit) {
    SectionTitle(stringResource(R.string.sect_alerts))
    Spacer(modifier = Modifier.height(8.dp))

    if (alerts.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.5f))) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.msg_no_active_alerts))
            }
        }
    } else {
        alerts.forEach { alert ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                onClick = { onAlertClick(alert) }
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.lbl_emergency_type, alert.type), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}