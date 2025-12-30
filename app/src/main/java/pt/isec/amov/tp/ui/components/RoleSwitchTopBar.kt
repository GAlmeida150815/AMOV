package pt.isec.amov.tp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.UserRole

@Composable
fun RoleSwitchTopBar(
    currentRole: UserRole,
    onRoleChange: (UserRole) -> Unit
) {
    val tabs = listOf(UserRole.MONITOR, UserRole.PROTECTED)
    val selectedIndex = tabs.indexOf(currentRole)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box(modifier = Modifier.statusBarsPadding()) {
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, role ->
                    val isSelected = selectedIndex == index
                    val title = if (role == UserRole.MONITOR)  stringResource(R.string.tab_monitor) else stringResource(R.string.tab_protected)
                    val icon = if (role == UserRole.MONITOR) Icons.Default.SupervisorAccount else Icons.Default.Security

                    Tab(
                        selected = isSelected,
                        onClick = { onRoleChange(role) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(imageVector = icon, contentDescription = null)
                        },
                        unselectedContentColor = Color.Gray
                    )
                }
            }
        }
    }
}