package pt.isec.amov.tp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.tp.R
import pt.isec.amov.tp.model.RegisterData
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = viewModel(),
    onBackToLogin: () -> Unit
) {
    // --- State Variables ---
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isMonitor by remember { mutableStateOf(false) }
    var isProtected by remember { mutableStateOf(false) }
    var cancelCode by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    // --- Error State ---
    var errors by remember { mutableStateOf(mapOf<String, Int>()) }

    // --- ViewModel State ---
    val isLoading = viewModel.isLoading
    val registerError = viewModel.registerError

    // --- Context ---
    val context = LocalContext.current

    // --- UI Layout ---
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Title ---
            Text(
                text = stringResource(R.string.reg_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Form Fields ---
            // Campo Nome
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.reg_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = errors.containsKey("name"),
                supportingText = { errors["name"]?.let { Text(stringResource(it)) } },
                singleLine = true
            )

            // Campo Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = errors.containsKey("email"),
                supportingText = { errors["email"]?.let { Text(stringResource(it)) } },
                singleLine = true
            )

            // Campo Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (isPasswordVisible) "Hide password" else "Show password"
                        Icon(imageVector = icon, contentDescription = description)
                    }
                },
                isError = errors.containsKey("password"),
                supportingText = { errors["password"]?.let { Text(stringResource(it)) } },
                singleLine = true
            )

            // Campo Confirmar Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.reg_confirm_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                        val icon = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                        Icon(imageVector = icon, contentDescription = description)
                    }
                },
                isError = errors.containsKey("confirmPassword"),
                supportingText = { errors["confirmPassword"]?.let { Text(stringResource(it)) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Seleção de Perfil
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.reg_select_role),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMonitor, onCheckedChange = { isMonitor = it })
                        Text(stringResource(R.string.reg_role_monitor))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isProtected, onCheckedChange = { isProtected = it })
                        Text(stringResource(R.string.reg_role_protected))
                    }
                    // Erro se nenhum perfil for selecionado
                    if (errors.containsKey("role")) {
                        Text(
                            text =  errors["roles"]?.let { it -> stringResource(it) } ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            // Campo Código de Cancelamento (apenas se for Protegido)
            if (isProtected) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cancelCode,
                    onValueChange = { if (it.length <= 6) cancelCode = it },
                    label = { Text(stringResource(R.string.reg_cancel_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errors.containsKey("code"),
                    supportingText = { errors["code"]?.let { Text(stringResource(it)) } },
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // --- API Error Message ---
            registerError?.let {
                Text(
                    text = stringResource(id = registerError),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // --- Loading Indicator ---
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            } else {
                // --- Register Button ---
                Button(
                    onClick = {
                        // 1. Criar o objeto com os dados atuais
                        val registrationData = RegisterData(
                            name = name,
                            email = email,
                            pass = password,
                            isMonitor = isMonitor,
                            isProtected = isProtected,
                            cancelCode = if (isProtected) cancelCode else null
                        )

                        // 2. Pedir ao objeto para se validar
                        val validationErrors = registrationData.validate(confirmPassword)

                        // 3. Atualizar UI ou enviar
                        if (validationErrors.isEmpty()) {
                            errors = emptyMap()
                            viewModel.register(registrationData) {
                                Toast.makeText(context, context.getString(R.string.msg_reg_success), Toast.LENGTH_LONG).show()
                                onBackToLogin()
                            }
                        } else {
                            errors = validationErrors
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.reg_button))
                }
            }

            // --- Back to Login Link ---
            TextButton(onClick = onBackToLogin) {
                Text(stringResource(R.string.reg_already_has_account))
            }
        }
    }
}