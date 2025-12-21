package pt.isec.amov.tp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import pt.isec.amov.tp.R

@Composable
fun WelcomeHeader(name: String?, titleResId: Int = R.string.title_dashboard) {
    Column {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.msg_welcome, name ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}