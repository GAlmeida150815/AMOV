package pt.isec.amov.tp.enums

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.ui.graphics.vector.ImageVector
import pt.isec.amov.tp.R

enum class MainTab(val icon: ImageVector, val labelRes: Int) {
    HOME(Icons.Default.Home, R.string.nav_home),
    HISTORY(Icons.Default.History, R.string.nav_history),
    CONNECTIONS(Icons.Default.SupervisorAccount, R.string.nav_connections),
    SOS(Icons.Default.Sos, R.string.nav_sos),
    MAP(Icons.Default.Map, R.string.nav_map),
    PROFILE(Icons.Default.ManageAccounts, R.string.nav_profile),
}