package pt.isec.amov.tp.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.MainTab
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onLogout: () -> Unit
) {
    val user = authViewModel.user

    // --- State Variables ---
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val visibleTabs = remember(user?.isMonitor) {
        if (user?.isMonitor == true) {
            MainTab.entries
        } else {
            MainTab.entries.filter { it != MainTab.MAP }
        }
    }

    if (user == null) {
        CircularProgressIndicator()
        return
    }

    // --- Start Listening for Associations ---
    LaunchedEffect(Unit) {
        dashboardViewModel.startListening()
    }

    // --- Stop Listening on Dispose ---
    DisposableEffect(Unit) {
        onDispose {
            dashboardViewModel.stopListening()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = if (isLandscape) {
                            { Text(stringResource(tab.labelRes)) }
                        } else null,
                        alwaysShowLabel = isLandscape,
                        selected = currentTab == tab,
                        onClick = { currentTab = tab }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                MainTab.HOME -> {
                    when {
                        user.isMonitor && user.isProtected -> {
                            TabbedDashboard(authViewModel, dashboardViewModel, onLogout)
                        }
                        user.isMonitor -> {
                            DashboardMonitorScreen(authViewModel, dashboardViewModel, onLogout)
                        }
                        user.isProtected -> {
                            DashboardProtectedScreen(authViewModel, dashboardViewModel, onLogout)
                        }
                    }
                }
                MainTab.MAP -> {
                    if (user.isMonitor) {
                        MapScreen(dashboardViewModel)
                    }
                }
                MainTab.HISTORY -> {
                    // TODO: HISTORICO
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Histórico de Alertas (Em Construção)")
                    }
                }
                MainTab.PROFILE -> {
                    ProfileScreen(authViewModel, onLogout)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedDashboard(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    onLogout: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val titles = listOf(
        stringResource(R.string.tab_monitor),
        stringResource(R.string.tab_protected)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .height(4.dp)
                                    .padding(horizontal = 20.dp)
                                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    divider = { /* no grey line */ }
                ) {
                    titles.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DashboardMonitorScreen(authViewModel, dashboardViewModel, onLogout)
                    1 -> DashboardProtectedScreen(authViewModel, dashboardViewModel, onLogout)
                }
            }
        }
    }
}