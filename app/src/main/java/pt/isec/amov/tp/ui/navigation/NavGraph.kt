package pt.isec.amov.tp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import pt.isec.amov.tp.ui.screens.LoginScreen
import pt.isec.amov.tp.ui.screens.MainScreen
import pt.isec.amov.tp.ui.screens.RegisterScreen
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel
import pt.isec.amov.tp.ui.viewmodel.DashboardViewModel

// --- Routes ---
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
}

@Composable
fun SetupNavGraph(navController: NavHostController) {
    // --- ViewModels ---
    val authViewModel: AuthViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // --- Login ---
        composable(route = Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        // --- Register ---
        composable(route = Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }
        // --- Main ---
        composable(route = Screen.Main.route) {
            MainScreen(
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}