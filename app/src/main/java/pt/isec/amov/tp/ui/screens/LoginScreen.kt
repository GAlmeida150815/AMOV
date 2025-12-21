package pt.isec.amov.tp.ui.screens

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel

@Composable
fun Login(
    viewModel: AuthViewModel = viewModel(),
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (Boolean) -> Unit
) {
    // --- State Variables ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isMonitor by remember { mutableStateOf(false) }

    // --- Error State ---
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // --- String Resources for Errors ---
    val errRequired = stringResource(R.string.err_field_empty)
    val errInvalidEmail = stringResource(R.string.err_invalid_email)

    // --- ViewModel State ---
    val isLoading = viewModel.isLoading
    val apiError = viewModel.errorMessage

    // --- UI Layout ---
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Title ---
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.login_welcome),
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(40.dp))

            // --- Input Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Campo Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = null },
                        label = { Text(stringResource(R.string.login_email_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        isError = emailError != null,
                        supportingText = { emailError?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = null },
                        label = { Text(stringResource(R.string.login_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = { passwordError?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Switch "Sou Monitor"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Checkbox(checked = isMonitor, onCheckedChange = { isMonitor = it })
                Text(stringResource(R.string.login_is_monitor_label))
            }
        }
    }

    Spacer(modifier = Modifier.height(30.dp))

    // --- API Error Message ---
    apiError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
    }

    // --- Loading Indicator ---
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
    } else {
        // --- Login Button ---
        Button(
            onClick = {
                // 1. Validação Local
                var isValid = true
                if (email.isBlank()) { emailError = errRequired; isValid = false }
                else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailError = errInvalidEmail; isValid = false }

                if (password.isBlank()) { passwordError = errRequired; isValid = false }

                // 2. Chama o Firebase se estiver tudo bem
                if (isValid) {
                    viewModel.login(email, password) {
                        onLoginSuccess(isMonitor)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.login_button), fontSize = 18.sp)
        }
    }

    // --- Go to Register ---
    TextButton(onClick = onNavigateToRegister) {
        Text(stringResource(R.string.reg_prompt))
    }
}