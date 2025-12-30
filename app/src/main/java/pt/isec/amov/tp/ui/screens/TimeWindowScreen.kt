package pt.isec.amov.tp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.enums.UserRole
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWindowScreen(viewModel: DashboardViewModel) {
    // Sincronizamos con los datos del ViewModel al cargar
    LaunchedEffect(Unit) {
        viewModel.loadPrivacySettings()
    }

    // Usamos delegados 'by' para simplificar (necesita imports de runtime)
    var selectedDays by remember { mutableStateOf(viewModel.authorizedDays) }
    var startH by remember { mutableStateOf(viewModel.startHour.toString()) }
    var endH by remember { mutableStateOf(viewModel.endHour.toString()) }
    val currentRole by viewModel.currentRole.collectAsState()
    val visibleTabs by remember(currentRole) {
        derivedStateOf {
            if (currentRole == UserRole.MONITOR) {
                listOf(MainTab.HOME, MainTab.CONNECTIONS, MainTab.MAP, MainTab.PROFILE)
            } else {
                // Añadimos PRIVACY al final para que el Protegido pueda navegar a ella
                listOf(MainTab.HOME, MainTab.CONNECTIONS, MainTab.HISTORY, MainTab.PROFILE, MainTab.PRIVACY)
            }
        }
    }

    // Actualizamos el estado local cuando el ViewModel cambie (si es necesario)
    LaunchedEffect(viewModel.authorizedDays) {
        selectedDays = viewModel.authorizedDays
        startH = viewModel.startHour.toString()
        endH = viewModel.endHour.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.lbl_time_window),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Selector de Días (Multi-selección)
        val daysArray = stringArrayResource(R.array.days_short)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysArray.forEachIndexed { index, day ->
                val dayNum = index + 1
                FilterChip(
                    selected = selectedDays.contains(dayNum),
                    onClick = {
                        selectedDays = if (selectedDays.contains(dayNum)) {
                            selectedDays - dayNum
                        } else {
                            selectedDays + dayNum
                        }
                    },
                    label = { Text(day) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Rango de Horas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = startH,
                onValueChange = { startH = it },
                label = { Text(stringResource(R.string.lbl_start_h)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = endH,
                onValueChange = { endH = it },
                label = { Text(stringResource(R.string.lbl_end_h)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val s = startH.toIntOrNull() ?: 0
                val e = endH.toIntOrNull() ?: 23
                viewModel.savePrivacySettings(selectedDays, s, e)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.btn_save))
        }
    }
}