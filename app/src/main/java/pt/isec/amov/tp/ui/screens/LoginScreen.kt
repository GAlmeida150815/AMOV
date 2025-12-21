package pt.isec.amov.tp.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.tp.R
import pt.isec.amov.tp.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = viewModel(),
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    // --- State Variables ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // --- Error State ---
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // --- String Resources ---
    val errRequired = stringResource(R.string.err_field_empty)
    val errInvalidEmail = stringResource(R.string.err_invalid_email)

    // --- ViewModel State ---
    val isLoading = viewModel.isLoading
    val loginError = viewModel.loginError
    val showResendButton = viewModel.showResendEmailButton
    val resendResult = viewModel.resendResult

    // --- Context ---
    val context = LocalContext.current

    // --- Toast Treatment ---
    LaunchedEffect(resendResult) {
        resendResult?.fold(
            onSuccess = {
                Toast.makeText(context, context.getString(R.string.mfa_resend_success), Toast.LENGTH_LONG).show()
                viewModel.resetResendResult()
            },
            onFailure = { e ->
                Toast.makeText(context, context.getString(R.string.mfa_resend_error, e.message), Toast.LENGTH_LONG).show()
                viewModel.resetResendResult()
            }
        )
    }

    // --- UI Layout ---
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Logo ---
            Image(
                painter = painterResource(id = R.drawable.ic_logo_safetysec),
                contentDescription = "Logo SafetYSec",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 32.dp),
            )
            // --- Title ---
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.login_welcome),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            // --- Input Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                val description = if (isPasswordVisible) "Hide password" else "Show password"
                                Icon(imageVector = icon, contentDescription = description)
                            }
                        },
                        isError = passwordError != null,
                        supportingText = { passwordError?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            }

            // --- Forgot Password Button ---
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { showResetDialog = true }) {
                    Text(stringResource(R.string.login_forgot_password), color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // --- API Error Message ---
            loginError?.let {
                Text(
                    text = stringResource(id = loginError),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 30.dp),
                    textAlign = TextAlign.Center
                )
            }

            // --- Resend Verification Email Button ---
            if (showResendButton) {
                Text(
                    text = stringResource(R.string.mfa_error_verify_email),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!isLoading) {
                    Button(
                        onClick = { viewModel.resendVerificationEmail(email, password) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(stringResource(R.string.mfa_resend_btn))
                    }
                }
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
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_login_success),
                                    Toast.LENGTH_SHORT
                                ).show()

                                onLoginSuccess()
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
    }

    // --- Password Reset Dialog ---
    if (showResetDialog) {
        var resetEmail by remember { mutableStateOf(email) }

        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.title_reset_password)) },
            text = {
                Column {
                    Text(stringResource(R.string.msg_enter_email_reset))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.login_email_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetEmail.isNotEmpty()) {
                            viewModel.sendPasswordReset(resetEmail) { result ->
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, context.getString(R.string.msg_reset_email_sent), Toast.LENGTH_LONG).show()
                                        showResetDialog = false
                                    },
                                    onFailure = {
                                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_send_email))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}