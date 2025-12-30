package pt.isec.amov.tp.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.enums.UserRole
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.ui.components.AddMonitorDialog
import pt.isec.amov.tp.ui.components.AddProtectedDialog
import pt.isec.amov.tp.ui.components.AddRuleDialog
import pt.isec.amov.tp.ui.components.EmptyState
import pt.isec.amov.tp.ui.components.SectionTitle
import pt.isec.amov.tp.ui.components.UserCard
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel
import pt.isec.amov.tp.ui.viewmodel.RulesViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun getRuleIcon(type: RuleType): ImageVector {
    return when (type) {
        RuleType.FALL_DETECTION -> Icons.Default.Dangerous
        RuleType.CAR_ACCIDENT -> Icons.Default.CarCrash
        RuleType.GEOFENCING -> Icons.Default.Map
        RuleType.SPEED_LIMIT -> Icons.Default.Speed
        RuleType.INACTIVITY -> Icons.Default.Timer
        RuleType.PANIC_BUTTON -> Icons.Default.NotificationsActive
    }
}

@Composable
fun ConnectionsScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    rulesViewModel: RulesViewModel,
    initialUserId: String? = null
) {
    // --- State Variables ---
    var selectedUserId by remember { mutableStateOf(initialUserId) }

    LaunchedEffect(initialUserId) {
        if (initialUserId != null) selectedUserId = initialUserId
    }
    BackHandler(enabled = selectedUserId != null) {
        selectedUserId = null
    }

    if (selectedUserId == null) {
        ConnectionsListContent(
            authViewModel = authViewModel,
            dashboardViewModel = dashboardViewModel,
            onUserClick = { userId -> selectedUserId = userId }
        )
    } else {
        UserDetailContent(
            authViewModel,
            dashboardViewModel = dashboardViewModel,
            rulesViewModel,
            targetUserId = selectedUserId!!,
            onBack = { selectedUserId = null }
        )
    }


}

@Composable
fun ConnectionsListContent(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onUserClick: (String) -> Unit
) {

    // --- ViewModel State ---
    val monitors = dashboardViewModel.myMonitors
    val protecteds = dashboardViewModel.myProtecteds
    val context = LocalContext.current

    val currentRole by dashboardViewModel.currentRole.collectAsState()

    // --- State Variables ---
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (currentRole == UserRole.MONITOR) {
                    dashboardViewModel.generateCode()
                }
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.desc_add_connection))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            if (currentRole == UserRole.MONITOR) {
                SectionTitle(stringResource(R.string.title_my_protecteds))
                if (protecteds.isEmpty()) EmptyState(stringResource(R.string.msg_no_protecteds))
                else {
                    LazyColumn {
                        items(protecteds) { u ->
                            UserCard(user = u, isProtectedUser = true, onRemove = {}, onClick = { onUserClick(u.uid) })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else  {
                SectionTitle(stringResource(R.string.title_my_monitors))
                if (monitors.isEmpty()) EmptyState(stringResource(R.string.msg_no_monitors))
                else {
                    LazyColumn {
                        items(monitors) { u ->
                            UserCard(user = u, isProtectedUser = false, onRemove = {}, onClick = { onUserClick(u.uid) })
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (showAddDialog) {
        if (currentRole == UserRole.MONITOR) {
            AddProtectedDialog(
                code = dashboardViewModel.generatedCode,
                onDismiss = {
                    showAddDialog = false
                    dashboardViewModel.clearAssociationState()
                }
            )
        } else {
            AddMonitorDialog (
                viewModel = dashboardViewModel,
                onDismiss = { showAddDialog = false },
                onSuccess = {
                    Toast.makeText(context, context.getString(R.string.assoc_success), Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailContent(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    rulesViewModel: RulesViewModel,
    targetUserId: String,
    onBack: () -> Unit
) {
    // --- ViewModel State ---
    val currentUser = authViewModel.user ?: return
    val currentRole by dashboardViewModel.currentRole.collectAsState()

    // --- State Variables ---
    var targetUser by remember { mutableStateOf<User?>(null) }
    var rules by remember { mutableStateOf<List<SafetyRule>>(emptyList()) }
    var ruleToDelete by remember { mutableStateOf<SafetyRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var selectedRule by remember { mutableStateOf<SafetyRule?>(null) }

    // --- Derived States ---
    val isViewingSelf = targetUserId == currentUser.uid
    val protectedUserId = if (currentRole == UserRole.MONITOR) targetUserId else currentUser.uid
    val monitorFilterId = if (isViewingSelf) null
        else if (currentRole == UserRole.MONITOR) currentUser.uid
        else targetUserId
    val canEditRules = currentRole == UserRole.MONITOR && !isViewingSelf

    LaunchedEffect(targetUserId, currentRole) {
        rulesViewModel.getProtectedUser(targetUserId) { u -> targetUser = u }
        rulesViewModel.getRulesForProtected(
            protectedId = protectedUserId,
            monitorId = monitorFilterId
        ) { r -> rules = r }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Header Navigation ---
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                }
            }

            // --- Profile Info ---
            targetUser?.let { tUser ->
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tUser.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tUser.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                if (currentRole == UserRole.MONITOR || isViewingSelf) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BatteryStd, null, modifier = Modifier.size(16.dp))
                                Text(" ${tUser.batteryLevel ?: "--"}%", fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.lbl_last_update), style = MaterialTheme.typography.labelSmall)
                                val date = tUser.lastUpdate?.toDate()
                                val dateStr = if (date != null) SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date) else "--"
                                Text(dateStr, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Rules List ---
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SectionTitle(stringResource(R.string.title_my_rules))

                if (rules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(stringResource(R.string.msg_no_rules_protected))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(rules) { rule ->
                            RuleItemCard(
                                rule = rule,
                                canEdit = canEditRules,
                                onToggle = { isChecked ->
                                    rulesViewModel.toggleRuleAuthorization(protectedUserId, rule.id, isChecked)
                                },
                                onDelete = {
                                    if (canEditRules) ruleToDelete = rule
                                },
                                onClick = {
                                    if (canEditRules) {
                                        selectedRule = rule
                                        showRuleDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- FAB ---
        if (canEditRules) {
            FloatingActionButton(
                onClick = {
                    selectedRule = null
                    showRuleDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.desc_add_rule))
            }
        }
    }

    // --- DIALOGS ---
    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_rule_title)) },
            text = { Text(stringResource(R.string.dialog_delete_rule_msg, ruleToDelete?.name ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        rulesViewModel.deleteRule(targetUserId, ruleToDelete!!.id)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showRuleDialog) {
        AddRuleDialog(
            existingRule = selectedRule,
            onDismiss = { showRuleDialog = false },
            onConfirm = { newRule ->
                val finalRule = if (newRule.type == RuleType.GEOFENCING && targetUser?.location != null) {
                    val params = newRule.params.toMutableMap()
                    params["lat"] = targetUser!!.location!!.latitude
                    params["lng"] = targetUser!!.location!!.longitude
                    newRule.copy(params = params)
                } else {
                    newRule
                }

                if (selectedRule == null) {
                    // CREATE MODE
                    rulesViewModel.addRuleToProtected(protectedUserId, finalRule) {
                        showRuleDialog = false
                    }
                } else {
                    // UPDATE MODE
                    rulesViewModel.updateRule(protectedUserId, finalRule) {
                        showRuleDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun RuleItemCard(
    rule: SafetyRule,
    canEdit: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val daysMap = mapOf(
        Calendar.MONDAY to stringResource(R.string.day_mon),
        Calendar.TUESDAY to stringResource(R.string.day_tue),
        Calendar.WEDNESDAY to stringResource(R.string.day_wed),
        Calendar.THURSDAY to stringResource(R.string.day_thu),
        Calendar.FRIDAY to stringResource(R.string.day_fri),
        Calendar.SATURDAY to stringResource(R.string.day_sat),
        Calendar.SUNDAY to stringResource(R.string.day_sun)
    )
    val everydayStr = stringResource(R.string.lbl_everyday)

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.clickable(enabled = canEdit) { onClick() }
    ) {
        ListItem(
            headlineContent = { Text(rule.name, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    if (rule.description.isNotBlank()) {
                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    val typeLabel = stringResource(rule.type.labelRes)
                    val details = when (rule.type) {
                        RuleType.GEOFENCING -> stringResource(R.string.fmt_radius, rule.params["radius"].toString())
                        RuleType.SPEED_LIMIT -> stringResource(R.string.fmt_speed, rule.params["max_speed"].toString())
                        RuleType.INACTIVITY -> stringResource(R.string.fmt_duration, rule.params["duration"].toString())
                        else -> typeLabel
                    }
                    Text(details, style = MaterialTheme.typography.bodyMedium)

                    if (rule.timeWindows.isNotEmpty()) {
                        val tw = rule.timeWindows.first()

                        val daysStr = if(tw.daysOfWeek.size == 7) {
                            everydayStr
                        } else {
                            tw.daysOfWeek.sortedBy { if (it == Calendar.SUNDAY) 7 else it - 1 }
                                .joinToString(", ") { dayConst ->
                                    daysMap[dayConst] ?: ""
                                }
                        }

                        val startStr = String.format("%02d:%02d", tw.startHour, tw.startMinute)
                        val endStr = String.format("%02d:%02d", tw.endHour, tw.endMinute)

                        Text("$daysStr | $startStr - $endStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            leadingContent = {
                Icon(getRuleIcon(rule.type), null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rule.isAuthorized, onCheckedChange = onToggle)
                    if (canEdit) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
            }
        )
    }
}