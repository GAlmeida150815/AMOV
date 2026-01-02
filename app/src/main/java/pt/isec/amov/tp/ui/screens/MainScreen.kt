package pt.isec.amov.tp.ui.screens

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.enums.UserRole
import pt.isec.amov.tp.services.MonitoringService
import pt.isec.amov.tp.ui.components.RoleSwitchTopBar
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel
import pt.isec.amov.tp.ui.viewmodel.RulesViewModel

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    rulesViewModel: RulesViewModel,
    onLogout: () -> Unit
) {
    // --- User State ---
    val user = authViewModel.user
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentRole by dashboardViewModel.currentRole.collectAsState()

    LaunchedEffect(currentRole) {
        val intent = Intent(context, MonitoringService::class.java)
        if (currentRole == UserRole.MONITOR) {
            // Si cambia a monitor, detenemos el servicio por seguridad
            context.stopService(intent)
            dashboardViewModel.isServiceRunning = false
        }
        // Opcional: Si quieres que se inicie solo al entrar a Protegido
        // else if (currentRole == UserRole.PROTECTED && dashboardViewModel.isServiceRunning) {
        //    context.startForegroundService(intent)
        // }
    }

    if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    // --- User Role State ---
    LaunchedEffect(Unit) {
        dashboardViewModel.initRole(user)
    }
    val isDualRole = user.isMonitor && user.isProtected

    var selectedUserId by remember { mutableStateOf<String?>(null) }

    // --- Current Tab State ---
    val visibleTabs by remember(currentRole) {
        derivedStateOf {
            if (currentRole == UserRole.MONITOR) {
                listOf(MainTab.HOME, MainTab.CONNECTIONS, MainTab.MAP, MainTab.PROFILE)
            } else {
                listOf(
                    MainTab.HOME,
                    MainTab.CONNECTIONS,
                    MainTab.HISTORY,
                    MainTab.PROFILE,
                    MainTab.PRIVACY
                )
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
    val currentTab = visibleTabs.getOrElse(pagerState.currentPage) { MainTab.HOME }
    val isSwipeEnabled = currentTab != MainTab.MAP

    val onNavigate: (MainTab, String?) -> Unit = { tab, userId ->
        selectedUserId = userId
        val index = visibleTabs.indexOf(tab)
        if (index != -1) {
            scope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    // --- Associations Listeners ---
    LaunchedEffect(Unit) { dashboardViewModel.startListening() }
    DisposableEffect(Unit) { onDispose { dashboardViewModel.stopListening() } }

    Scaffold(
        topBar = {
            if (isDualRole) {
                RoleSwitchTopBar(
                    currentRole = currentRole,
                    onRoleChange = { newRole -> dashboardViewModel.setRole(newRole) }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (currentRole == UserRole.PROTECTED) {
                FloatingActionButton(
                    onClick = {
                        // Acción para lanzar el protocolo de emergencia (Punto 52 del enunciado)
                        val intent = Intent(context, AlertActivity::class.java).apply {
                            putExtra("RULE_TYPE", "PANIC_BUTTON")
                        }
                        context.startActivity(intent)
                    },
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(80.dp)
                        .offset(y = 50.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sos,
                        contentDescription = "SOS",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                // Filtramos PRIVACY para que el usuario no vea un icono de candado extra abajo
                visibleTabs.filter { it != MainTab.PRIVACY }.forEach { tab ->
                    val isSelected = currentTab == tab
                    val isSOS = tab == MainTab.SOS

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (isSOS) {
                                // Acción SOS
                                val intent = Intent(context, AlertActivity::class.java).apply {
                                    putExtra("RULE_TYPE", "PANIC_BUTTON")
                                }
                                context.startActivity(intent)
                            } else {
                                // Navegación normal
                                val index = visibleTabs.indexOf(tab)
                                scope.launch { pagerState.animateScrollToPage(index) }
                                if (tab != MainTab.CONNECTIONS) selectedUserId = null
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            userScrollEnabled = isSwipeEnabled
        ) { pageIndex ->
            if (pageIndex >= visibleTabs.size) return@HorizontalPager

            val tab = visibleTabs[pageIndex]

            when (tab) {
                MainTab.HOME -> {
                    if (currentRole == UserRole.MONITOR) {
                        DashboardMonitorScreen(
                            authViewModel = authViewModel,
                            dashboardViewModel = dashboardViewModel,
                            onLogout = onLogout,
                            onNavigate = onNavigate,
                        )
                    } else {
                        DashboardProtectedScreen(
                            authViewModel = authViewModel,
                            dashboardViewModel = dashboardViewModel,
                            onLogout = onLogout,
                            onNavigate = onNavigate,
                        )
                    }
                }

                MainTab.CONNECTIONS -> {
                    ConnectionsScreen(
                        authViewModel = authViewModel,
                        dashboardViewModel = dashboardViewModel,
                        rulesViewModel = rulesViewModel,
                        initialUserId = selectedUserId,
                    )
                }

                MainTab.MAP -> {
                    MapScreen(dashboardViewModel)
                }

                MainTab.HISTORY -> {
                    AlertHistoryScreen(dashboardViewModel)
                }

                MainTab.PROFILE -> {
                    ProfileScreen(authViewModel, onLogout)
                }

                MainTab.SOS -> {
                    // FAB action only, no tab content
                    Box(modifier = Modifier.fillMaxSize())
                }

                MainTab.PRIVACY -> {
                    TimeWindowScreen(viewModel = dashboardViewModel)

                }
            }
        }
    }
}