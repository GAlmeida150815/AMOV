package pt.isec.amov.tp.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.model.User
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
    onLogout: () -> Unit
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

                    StatisticsSection(protecteds.size, 0) //TODO: substituir 0 por alertas ativos reais

                    Spacer(modifier = Modifier.height(24.dp))

                    AlertsSection(emptyList()) // TODO: substituir por alertas reais
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Right Column: Protegidos List
                Column(modifier = Modifier.weight(0.6f)) {
                    SectionTitle(stringResource(R.string.sect_protecteds))

                    Spacer(modifier = Modifier.height(8.dp))

                    ProtectedsList(
                        protecteds = protecteds,
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

                // Stats
                StatisticsSection(protecteds.size, 0)

                Spacer(modifier = Modifier.height(24.dp))

                // Scrollable List Section
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Alerts Section
                    item {
                        AlertsSection(emptyList())
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
                                onClick = { /* TODO: Abrir detalhes/mapa do user */ }
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

// --- SUB-COMPONENTES UI ---
// (Nota: WelcomeHeader, SectionTitle, EmptyState e UnifiedUserCard foram removidos daqui pois agora vêm da package components)

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
fun AlertsSection(alerts: List<Any>) {
    SectionTitle(stringResource(R.string.sect_alerts))

    Spacer(modifier = Modifier.height(8.dp))

    if (alerts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, Color(0xFF4CAF50))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.msg_no_active_alerts), color = Color(0xFF1B5E20))
            }
        }
    } else {
        // TODO: Listar alertas aqui
    }
}

@Composable
fun ProtectedsList(protecteds: List<User>, viewModel: DashboardViewModel) {
    val context = LocalContext.current

    LazyColumn {
        if (protecteds.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.msg_no_protecteds))
            }
        } else {
            items(protecteds) { user ->
                UserCard(
                    user = user,
                    isProtectedUser = true,
                    onRemove = {
                        viewModel.removeAssociation(
                            otherUserId = user.uid,
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
                    onClick = {}
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AddProtectedDialog(code: String?, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val msgCopied = stringResource(R.string.msg_code_copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_add_protected)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (code == null) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.msg_share_code))
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, msgCopied, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = code,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(stringResource(R.string.lbl_click_to_copy), fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}