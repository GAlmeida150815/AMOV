package pt.isec.amov.tp.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import pt.isec.amov.tp.ui.components.AddProtectedDialog
import pt.isec.amov.tp.ui.components.EmptyState
import pt.isec.amov.tp.ui.components.SectionTitle
import pt.isec.amov.tp.ui.components.UserCard
import pt.isec.amov.tp.ui.components.WelcomeHeader
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
                // Left Column: Welcome, Stats, Alerts
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

                // Right Column: Protegidos List
                Column(modifier = Modifier.weight(0.6f)) {
                    SectionTitle(stringResource(R.string.sect_protecteds))

                    Spacer(modifier = Modifier.height(8.dp))

                    ProtectedsList(
                        protecteds = protecteds,
                        viewModel = dashboardViewModel,
                        onNavigate = onNavigate
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

                // Stats
                StatisticsSection(
                    protectedCount = protecteds.size,
                    activeAlerts = dashboardViewModel.activeAlerts.size
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Scrollable List Section
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Alerts Section
                    item {
                        AlertsSection(
                            alerts = dashboardViewModel.activeAlerts.values.toList(),
                            onAlertClick = { selectedAlert = it }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Protegidos Header
                    item {
                        SectionTitle(stringResource(R.string.sect_protecteds))
                    }

                    // Protegidos List
                    if (protecteds.isEmpty()) {
                        item {
                            EmptyState(stringResource(R.string.msg_no_protecteds))
                        }
                    } else {
                        items(protecteds) { protectedUser ->
                            val userAlert = dashboardViewModel.activeAlerts[protectedUser.uid]
                            UserCard(
                                user = protectedUser,
                                isProtectedUser = true,
                                onRemove = {
                                    // LOGIC WITH TOASTS HERE
                                    dashboardViewModel.removeAssociation(
                                        otherUserId = protectedUser.uid,
                                        amIMonitor = true,
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.msg_assoc_removed_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onFailure = { errorMsg ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.err_assoc_remove, errorMsg),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                },
                                onClick = {
                                    onNavigate(MainTab.CONNECTIONS, protectedUser.uid)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // --- ADD PROTECTED DIALOG ---
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

@Composable
fun ProtectedsList(
    protecteds: List<User>,
    viewModel: DashboardViewModel,
    onNavigate: (MainTab, String?) -> Unit
) {
    TODO("Not yet implemented")
}

// --- SUB-COMPONENTES UI ---
@Composable
fun StatisticsSection(protectedCount: Int, activeAlerts: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Statistics Card 1 (Protegidos)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "$protectedCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.sect_protecteds), style = MaterialTheme.typography.labelSmall)
            }
        }

        // Statistics Card 2 (Alertas Ativos)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = if (activeAlerts > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
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
fun AlertsSection(
    alerts: List<Alert>, // CAMBIADO: De List<Any> a List<Alert>
    onAlertClick: (Alert) -> Unit // CAMBIADO: Se añade el callback
) {
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
                onClick = { onAlertClick(alert) } // Ahora sí detecta onAlertClick
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.lbl_emergency_type, alert.type), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}


