package pt.isec.amov.tp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.RuleType
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.model.TimeWindow
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    existingRule: SafetyRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (SafetyRule) -> Unit
) {
    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var description by remember { mutableStateOf(existingRule?.description ?: "") }
    var selectedType by remember { mutableStateOf(existingRule?.type ?: RuleType.GEOFENCING) }

    var paramValue by remember {
        mutableStateOf(
            if (existingRule != null) {
                when (existingRule.type) {
                    RuleType.GEOFENCING -> existingRule.params["radius"]?.toString() ?: "100"
                    RuleType.SPEED_LIMIT -> existingRule.params["max_speed"]?.toString() ?: "120"
                    RuleType.INACTIVITY -> existingRule.params["duration"]?.toString() ?: "30"
                    else -> ""
                }
            } else "100"
        )
    }

    var expanded by remember { mutableStateOf(false) }

    val initialWindow = existingRule?.timeWindows?.firstOrNull()

    // --- Time Window State ---
    var is24h by remember { mutableStateOf(existingRule == null || existingRule.timeWindows.isEmpty()) }
    var startHour by remember {
        mutableStateOf(if (initialWindow != null) String.format("%02d", initialWindow.startHour) else "09")
    }
    var endHour by remember {
        mutableStateOf(if (initialWindow != null) String.format("%02d", initialWindow.endHour) else "18")
    }
    val allDays = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    var selectedDays by remember {
        mutableStateOf(
            initialWindow?.daysOfWeek?.toSet() ?: allDays.toSet()
        )
    }
    val daysLabels = stringArrayResource(R.array.days_short)
    val isEditing = existingRule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_rule)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 1. Name
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.lbl_rule_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text(stringResource(R.string.lbl_description)) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth()
                )

                // 3. Rule Type
                ExposedDropdownMenuBox(
                    expanded = expanded && !isEditing,
                    onExpandedChange = { if (!isEditing) expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = stringResource(selectedType.labelRes),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.lbl_rule_type)) },
                        enabled = !isEditing,
                        colors = if (isEditing) OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) else OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    if (!isEditing) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            RuleType.entries.filter { it != RuleType.PANIC_BUTTON }.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(type.labelRes)) },
                                    onClick = {
                                        selectedType = type
                                        expanded = false
                                        // Reset param default only if switching types manually
                                        paramValue = when (type) {
                                            RuleType.SPEED_LIMIT -> "120"
                                            RuleType.INACTIVITY -> "30"
                                            else -> "100"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Dynamic Parameter (Radius or Speed)
                when (selectedType) {
                    RuleType.GEOFENCING -> {
                        OutlinedTextField(
                            value = paramValue, onValueChange = { paramValue = it },
                            label = { Text(stringResource(R.string.lbl_radius)) },
                            suffix = { Text("m") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RuleType.SPEED_LIMIT -> {
                        OutlinedTextField(
                            value = paramValue, onValueChange = { paramValue = it },
                            label = { Text(stringResource(R.string.lbl_max_speed)) },
                            suffix = { Text("km/h") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RuleType.INACTIVITY -> {
                        OutlinedTextField(
                            value = paramValue, onValueChange = { paramValue = it },
                            label = { Text(stringResource(R.string.lbl_duration)) },
                            suffix = { Text("min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 5. Time Window
                Text(stringResource(R.string.lbl_time_window), fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    allDays.forEachIndexed { index, dayConst ->
                        val isSelected = selectedDays.contains(dayConst)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
                                .clickable {
                                    selectedDays = if (isSelected) selectedDays - dayConst else selectedDays + dayConst
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = daysLabels.getOrElse(index) { "?" },
                                color = if (isSelected) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = is24h, onCheckedChange = { is24h = it })
                    Text(stringResource(R.string.lbl_24h_active))
                }

                if (!is24h) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { input ->
                                if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                    val num = input.toIntOrNull()
                                    if (num == null || num in 0..23) {
                                        startHour = input
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.lbl_start_h)) },
                            placeholder = { Text("09") },
                            supportingText = { Text("00 - 23") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { input ->
                                if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                    val num = input.toIntOrNull()
                                    if (num == null || num in 0..23) {
                                        endHour = input
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.lbl_end_h)) },
                            placeholder = { Text("18") },
                            supportingText = { Text("00 - 23") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    val params = mutableMapOf<String, Any>()
                    val value = paramValue.toDoubleOrNull() ?: 0.0

                    when (selectedType) {
                        RuleType.GEOFENCING -> params["radius"] = value
                        RuleType.SPEED_LIMIT -> params["max_speed"] = value
                        RuleType.INACTIVITY -> params["duration"] = value
                        else -> {}
                    }

                    val timeWindows = if (is24h && selectedDays.size == 7) {
                        emptyList()
                    } else {
                        val startH = if (is24h) 0 else (startHour.toIntOrNull() ?: 0)
                        val endH = if (is24h) 23 else (endHour.toIntOrNull() ?: 23)

                        listOf(
                            TimeWindow(
                                name = "Standard",
                                daysOfWeek = selectedDays.toList(),
                                startHour = startH,
                                startMinute = 0,
                                endHour = endH,
                                endMinute = 59
                            )
                        )
                    }

                    val ruleToSave = if (existingRule != null) {
                        existingRule.copy(
                            name = name,
                            description = description,
                            params = params,
                            timeWindows = timeWindows
                        )
                    } else {
                        SafetyRule(
                            name = name,
                            description = description,
                            type = selectedType,
                            params = params,
                            timeWindows = timeWindows
                        )
                    }
                    onConfirm(ruleToSave)
                }
            }) { Text(stringResource(R.string.btn_create_rule)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}